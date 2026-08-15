# S0236 — Cloud Path Legacy Format Fix

Status: BlockNeedUserTest
Priority: 70
Type: bug-fix
Roadmap-id: ad-hoc
Created: 2026-05-17
Updated: 2026-05-17

<!-- auto-approved by /spec-all — 2026-05-17 -->

## Goal

Существующие облачные ресурсы (Google Drive, потенциально Dropbox/OneDrive), добавленные в более ранних сборках, хранят `resources.path` в устаревшем однослэшевом формате `cloud:/<provider>/<id>`. Текущие читатели — `CloudMediaScanner` и `CleanupOrphanedTempFilesUseCase` — принимают только новый формат `cloud://<provider>/<id>`, из-за чего ресурс открывается пустым и `cleanup` шумит `FileNotFoundException`. Триггер раскрытия — переключение `allFiles`, инвалидирующее in-memory кэш. Чинится двумя путями: (a) defensive-нормализация на входе обоих читателей через существующий `CloudPathParser`, (b) одноразовая Room-миграция `v29 → v30`, переписывающая `resources.path` для всех затронутых записей.

## Architecture Decision

- Защитная нормализация — в точке входа двух читателей; миграция — единственный долговременный фикс.
- Используется существующий `CloudPathParser` (singleton) — не создаётся новый API.
- Никаких изменений сигнатур публичных функций; только инъекция `CloudPathParser` в два класса.
- Room-миграция исполняет `UPDATE resources SET path = REPLACE(path, 'cloud:/', 'cloud://') WHERE path LIKE 'cloud:/_%' AND path NOT LIKE 'cloud://%'` — затрагивает только записи в legacy-формате, идемпотентна.

## Out of Scope

- Расширение проверок `cloud:/` в местах, где уже стоит `isCloudPath()` / двойная проверка `startsWith("cloud://") || startsWith("cloud:/")` — текущая защита работает корректно.
- `cloudFolderId` нормализация — поле уже хранит чистый folder id без префикса, проблеме не подвержено.
- Изменения в `MediaFilesCacheManager.fixCloudPaths()` — продолжает работать как страховочный нормализатор in-memory кэша.

## Phases

### Phase 1 — Defensive normalization in cloud readers

1. **Inject `CloudPathParser` into `CleanupOrphanedTempFilesUseCase`.**
   - File: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt`
   - Add `private val cloudPathParser: CloudPathParser` to the `@Inject constructor`.
   - Add import `com.sza.fastmediasorter.data.cloud.CloudPathParser`.
   - **Verification:** `grep -n "cloudPathParser" CleanupOrphanedTempFilesUseCase.kt` → expected: at least 2 matches (constructor + usage).

2. **Replace `startsWith("cloud://")` skip check with `cloudPathParser.isCloudPath(directoryPath)` in `CleanupOrphanedTempFilesUseCase.invoke`.**
   - Old: `if (directoryPath.startsWith("cloud://", ignoreCase = true))` at L35.
   - New: `if (cloudPathParser.isCloudPath(directoryPath))`.
   - Keep the `Timber.d("…Skipping cloud path: $directoryPath")` log and `Result.success(0)` return.
   - **Verification:** `grep -n 'startsWith("cloud://")' CleanupOrphanedTempFilesUseCase.kt` → expected: 0 matches; `grep -n "isCloudPath" CleanupOrphanedTempFilesUseCase.kt` → expected: 1 match.

3. **Inject `CloudPathParser` into `CloudMediaScanner`.**
   - File: `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudMediaScanner.kt`
   - Add `private val cloudPathParser: CloudPathParser` as a constructor parameter (it lives in the same package, no import needed).
   - **Verification:** `grep -n "cloudPathParser" CloudMediaScanner.kt` → expected: at least 2 matches.

4. **Normalize `path` at the entry of `CloudMediaScanner.scanFolderInternal`.**
   - Immediately after the `try {` at L56, before the `isFullCloudPath` check, insert: `val normalizedPath = cloudPathParser.normalizePath(path)`.
   - Replace all subsequent reads of `path` inside the `try` block of `scanFolderInternal` with `normalizedPath` (L60, L72, L80 — the log message, path-matching `find`, etc.). The function parameter name stays `path` for API stability.
   - **Verification:** `grep -nE "\\bpath\\b" CloudMediaScanner.kt` inspected manually — inside `scanFolderInternal`'s `try` block, the only `path` reference must be the line `val normalizedPath = cloudPathParser.normalizePath(path)`; all other uses must read `normalizedPath`.

5. **Build standard debug.**
   - Run: `\\\\.\\\\a.ps1 bd` (BUILD-REQUIRED).
   - **Verification:** exit 0 + APK present in `app_v2/build/outputs/apk/standard/debug/`.

### Phase 2 — Room migration v29 → v30

1. **Bump Room database version.**
   - File: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
   - Change `version = 29` → `version = 30` inside `@Database(...)`.
   - **Verification:** `grep -n "version = " AppDatabase.kt` → expected: line contains `version = 30`.

2. **Add `MIGRATION_29_30` companion-object property.**
   - Inside `AppDatabase.Companion`, next to the existing `MIGRATION_*` declarations, add:
     ```kotlin
     val MIGRATION_29_30 = object : Migration(29, 30) {
         override fun migrate(db: SupportSQLiteDatabase) {
             // S0236: normalize legacy cloud:/<provider>/<id> paths to cloud://<provider>/<id>.
             // Created by older builds; current readers (CloudMediaScanner, CleanupOrphanedTempFilesUseCase)
             // only accept the double-slash form. Idempotent — only rows matching the legacy shape.
             db.execSQL(
                 "UPDATE resources SET path = REPLACE(path, 'cloud:/', 'cloud://') " +
                 "WHERE path LIKE 'cloud:/_%' AND path NOT LIKE 'cloud://%'"
             )
         }
     }
     ```
   - **Verification:** `grep -n "MIGRATION_29_30" AppDatabase.kt` → expected: ≥ 2 matches (declaration + reference in migration list).

3. **Register `MIGRATION_29_30` in the database builder migration list.**
   - Find the `Room.databaseBuilder(...).addMigrations(...)` chain (anywhere in `AppDatabase` companion or DI module that constructs the database).
   - Append `MIGRATION_29_30` after `MIGRATION_28_29`.
   - **Verification:** `grep -nE "addMigrations.*MIGRATION_29_30" -r app_v2/src/main/java/com/sza/fastmediasorter/data` → expected: ≥ 1 match.

4. **Build standard debug.**
   - Run: `\\\\.\\\\a.ps1 bd` (BUILD-REQUIRED).
   - **Verification:** exit 0. Room compiler must accept the new migration.

### Phase 3 — Functionality log + dev changelog

1. **Append `dev/FUNCTIONALITY.log` FIX entry.**
   - Run: `\\\\.\\\\scripts\\\\add_to_functionality_log.ps1 -Id S0236 -Op FIX -Description "Restore content visibility for cloud resources stored in legacy cloud:/ path format (Room migration v29->v30 + defensive normalization in readers)"`.
   - **Verification:** `grep -n "S0236" dev/FUNCTIONALITY.log` → expected: ≥ 1 line.

2. **Append `dev/CHANGELOG.md` entries (one per touched file).**
   - Run for each touched file: `\\\\.\\\\scripts\\\\add_to_dev_log.ps1 "<path>" "S0236" "Cloud path legacy format fix"`.
   - **Verification:** `grep -c "S0236" dev/CHANGELOG.md` → expected: ≥ 3.

3. **Catalogue sync.**
   - Run: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
   - Run: `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.
   - **Verification:** exit 0 on both.

### Phase 4 — On-device verification (MANUAL)

1. **Install debug APK on device with an existing Google Drive resource added in a prior build (path stored as `cloud:/google_drive/<id>`).**
2. **Observe:** opening the resource shows the actual content (folders + files of the Drive folder).
3. **Observe in logcat:** the line `CloudMediaScanner: Found resource id=…, name=…, cloudFolderId=…` appears (i.e. resource lookup succeeded).
4. **Observe:** no `FileNotFoundException: Directory not found: cloud:/…` warnings remain.
5. **Observe:** `S0236:` Timber probe fires at the entry of `scanFolderInternal` exactly once per resource open.
6. **Verification:** all four points pass on Samsung S25-class device (standard flavor debug).

## Open items

None — all dependencies (CloudPathParser, AppDatabase migration pattern) exist in code.

## Risks

- **`CloudMediaScanner.scanFolderInternal` parameter shadowing.** Renaming all `path` references to `normalizedPath` inside the `try` block — risk of missing one occurrence. Mitigated by explicit verification step that greps and inspects.
- **Migration order.** `MIGRATION_29_30` must follow `MIGRATION_28_29` in the `addMigrations` chain — Room normally accepts any order, but explicit ordering matches the file's existing style.
- **Other cloud readers.** This patch covers `CloudMediaScanner` and `CleanupOrphanedTempFilesUseCase`. The migration (Phase 2) is the long-term fix and protects every other reader that reads `resources.path`.

## Last Audit

_None yet — pre-implementation._
