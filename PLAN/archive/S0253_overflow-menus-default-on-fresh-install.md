# S0253 - Overflow menus default ON for fresh installs

- Ticket: S0253
- Status: BlockNeedUserTest
<!-- auto-approved by /spec-all - 2026-05-19 -->
- Priority: 40
- Roadmap-id: ad-hoc
- Created: 2026-05-19
- Owner: serzhyale@gmail.com

## Goal

Включить по-умолчанию два переключателя — «Операции с ресурсами в меню» (`resource_ops_in_overflow_menu`) и «Операции в меню ⋮» для файлов (`file_ops_in_overflow_menu`) — для свежей установки приложения. У существующих пользователей значения по-умолчанию не меняются: тот, кто уже хоть раз обновлял или открывал предыдущую версию, после апдейта получает прежнее поведение (оба переключателя выключены, пока сам не включит).

Дискриминатор fresh-install vs existing-user — `PackageInfo.firstInstallTime == PackageInfo.lastUpdateTime`. Существующий пользователь после установки этой сборки получит `lastUpdateTime > firstInstallTime` → дефолт остаётся `false`. Свежая установка (или uninstall + reinstall) → таймстампы равны → дефолт = `true`.

Сопутствующий флаг `file_ops_overflow_menu_hint_shown` для свежей установки также выставляется в `true`, иначе при первом ручном выкл→вкл выскакивает obsolete-Toast «operations moved to menu», адресованный пользователям, у которых кнопки переехали из инлайна в меню — для свежей установки эта реальность отсутствует.

## Scope

- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` — добавить `private val isFreshInstall` (lazy, через `PackageManager`) и подставить как fallback для трёх DataStore-ключей.

## Non-scope

- `BackupData.fileOpsInOverflowMenu` default = `false` — это снапшот бэкапа, не пустой DataStore.
- `ImportSettingsUseCase` defaults в `toBoolean() ?: false` — те же причины.
- `AppSettings.kt` field defaults — это in-memory заглушки до первого emission из DataStore.
- UI, layouts, strings, миграции Room, BuildConfig-гейты — не затрагиваются.

## Phases

### Phase 1 - Settings default migration

- [x] **Step 1.1**: Open `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` and add a private lazy property right after the companion object's closing brace, scoped on the singleton:
  ```kotlin
  // Distinguishes a fresh install (firstInstallTime == lastUpdateTime) from an upgraded
  // existing install. Used only for opt-in defaults that should be ON for new users while
  // keeping every existing user's behavior unchanged after they update to this build.
  private val isFreshInstall: Boolean by lazy {
      runCatching {
          val info = context.packageManager.getPackageInfo(context.packageName, 0)
          info.firstInstallTime == info.lastUpdateTime
      }.getOrDefault(false)
  }
  ```
  - Verification: `Grep -n "isFreshInstall" app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` returns exactly four matches (declaration + 3 usages after step 1.2).

- [x] **Step 1.2**: In the same file replace three `?: false` fallbacks with `?: isFreshInstall`:
  - `fileOpsInOverflowMenu = preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] ?: false` → `?: isFreshInstall`
  - `fileOpsOverflowMenuHintShown = preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] ?: false` → `?: isFreshInstall`
  - `resourceOpsInOverflowMenu = preferences[KEY_RESOURCE_OPS_IN_OVERFLOW_MENU] ?: false` → `?: isFreshInstall`
  - Verification: `Grep -n "isFreshInstall" app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` returns 4 matches. `Grep -n "KEY_FILE_OPS_IN_OVERFLOW_MENU\] \?: false" app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` returns 0 (also for `KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN` and `KEY_RESOURCE_OPS_IN_OVERFLOW_MENU`).

- [x] **Step 1.3**: Build the target variant (`standardDebug`) to confirm compilation.
  - Command: `.\a.ps1 dq`
  - Verification: exit code 0.

- [x] **Step 1.4**: Catalog sync after `.kt` change.
  - Command: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`
  - Verification: exit code 0, no untracked changes in `dev/CATALOG/app_v2.jsonl` after second invocation.

**Implemented date:** 2026-05-19

## Last Audit

- 2026-05-19: Implementation matches spec. `assembleStandardDebug` PASS twice (initial + after Timber.d tag). Catalog sync clean. `isFreshInstall` lazy property: 1 decl + 3 fallback usages = 4 matches (verified). Three `?: false` fallbacks for the targeted keys: 0 remaining (verified). Status → `BlockNeedUserTest` for on-device verification of fresh-install vs upgrade path (PackageInfo timestamps cannot be unit-tested cleanly).

## Manual / device verification

- Свежая установка на эмулятор или после `adb uninstall com.sza.fastmediasorter` + `installDebug`: открыть Settings → General → «Операции с ресурсами в меню» должен быть ON; открыть Settings → Playback → «Операции в меню ⋮» должен быть ON.
- Обновление существующей установки (например `installDebug` поверх уже стоящей сборки): оба переключателя остаются в прежнем состоянии (для большинства — OFF).

## Revision History

- **2026-05-19** - by `/spec-update` (`gpt-5`, focus: consistency)
  - Applied: 2. Proposed (DISCUSS): 0.
