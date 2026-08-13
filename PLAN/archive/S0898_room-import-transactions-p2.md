# S0898 - Room import paths without transactions: row-by-row inserts (P2 cluster)

**Ticket:** S0898
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: CODE_AUDIT_PROTOCOL "Room main-safety" - atomic multi-step writes must be wrapped in @Transaction/withTransaction.

- app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt:55 - insertAllAsOverrides is non-transactional and its hasOverrides() guard makes a partial apply permanent
- app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt:29 - M3U playlist import writes N rows as N separate transactions (no withTransaction), unlike the sibling mergeCatalog
- app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportFavoritesUseCase.kt:104 - Favorites import inserts row-by-row without a transaction

## 1. Goal (RU)

Три пути импорта пишут N строк как N отдельных транзакций. Kill процесса в середине оставляет частично применённый импорт. Обернуть каждый цикл записи в одну Room-транзакцию (`db.withTransaction`) - импорт становится all-or-nothing на краше.

Эталон - sibling `StreamSourceRepository.mergeCatalog` (уже в `db.withTransaction`, S0732). Схема БД не меняется (AppDatabase v38); добавляется только инжект `AppDatabase` в два класса (constructor wiring).

Особый случай: `insertAllAsOverrides` вызывается из `AppStartupInitializer` под гардом `!hasOverrides()` - частичный seed делает `hasOverrides()==true` навсегда, пропуская доузел. Атомарность чинит именно это.

## 2. Constraints

- No schema change - `AppDatabase.version` stays 38. Injecting `AppDatabase` is `@Inject constructor` wiring only, no new scope/qualifier.
- Favorites loop keeps its per-row try/catch and best-effort per-row reporting. `FavoritesDao.insert` is `OnConflictStrategy.REPLACE` (duplicates never throw), so a caught mid-loop error does not poison the transaction under normal operation. Only genuine failures propagate to the existing outer catch.
- Preserve return values: `addAllIgnoringDuplicates` still returns the inserted count.

## 3. Phases

### Phase 1 - `InputBindingRepository.insertAllAsOverrides` atomic

- Step 1.1: Add `private val db: AppDatabase` to the constructor; import `androidx.room.withTransaction` and `com.sza.fastmediasorter.data.local.db.AppDatabase`.
- Step 1.2: Wrap the `bindings.forEach { .. dao.upsert(..) }` body in `db.withTransaction { }`.
  - Verification: grep - method body opens with `db.withTransaction {`; all `dao.upsert` calls inside it. `AppDatabase.inputBindingDao()` exists (v38), so the transaction covers these writes.

### Phase 2 - `StreamSourceRepository.addAllIgnoringDuplicates` atomic

- Step 2.1: `db: AppDatabase` and `androidx.room.withTransaction` already present. Wrap the `for (source in sources) { .. }` counting loop in `db.withTransaction { }` and return `inserted`.
  - Verification: grep - loop is inside `db.withTransaction {`; matches the sibling `mergeCatalog` pattern; return value unchanged.

### Phase 3 - `ImportFavoritesUseCase.invoke` atomic insert loop

- Step 3.1: Add `private val db: AppDatabase` to the constructor; import `androidx.room.withTransaction` and `com.sza.fastmediasorter.data.local.db.AppDatabase`.
- Step 3.2: Wrap the `for (exported in model.favorites) { .. }` loop in `db.withTransaction { }`. Keep the inner per-row `try/catch` and all counters/`details` mutation (captured `var`s mutate inside the inline suspend block).
  - Verification: grep - the import loop is inside `db.withTransaction {`; per-row try/catch and reporting intact; outer catch (`isSuccess=false`) still wraps the whole `invoke`.

### Phase 4 - Build gate

- Step 4.1: `standard debug` compiles (`a.ps1 fk`). Detekt-clean on the three touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0732 (mergeCatalog transaction - reference pattern), S0821 (catalog prune bind-limit - same file).

## Related

- S0878 (audit tail container - triage source).
- S0732 (`mergeCatalog` transaction - reference pattern).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** Verified.

All three import paths wrapped in a single `db.withTransaction { }`; `standard debug` Kotlin compile PASS; detekt gate PASS (no new findings on touched files). No schema change (`AppDatabase.version` = 38 unchanged).

- **`InputBindingRepository.insertAllAsOverrides`** - `AppDatabase` injected (constructor wiring); the `bindings.forEach { dao.upsert(..) }` body now runs inside `db.withTransaction`. A kill mid-seed can no longer leave a partial override set that flips `hasOverrides()` true permanently (startup seed guard in `AppStartupInitializer:119`).
- **`StreamSourceRepository.addAllIgnoringDuplicates`** - `db`/`withTransaction` already present; the counting loop is now one transaction, mirroring sibling `mergeCatalog` (S0732). Return value (`inserted` count) preserved.
- **`ImportFavoritesUseCase.invoke`** - `AppDatabase` injected; the per-row import loop wrapped in `db.withTransaction`. Per-row `try/catch` and best-effort `imported/skipped/failed/unresolved` + `details` reporting preserved. `FavoritesDao.insert` is `OnConflictStrategy.REPLACE`, so ordinary duplicates never throw and cannot abort the transaction; genuine failures still surface via the outer catch.

**Evidence rung:** static + compile + detekt (P2). Crash-atomicity is only observable under process kill mid-import - not device-reproducible by a gesture; happy-path import behavior is unchanged and covered by `/spec-prerelease` smoke. No device gate.

**Follow-up (2026-07-03, unit-test collateral).** The original close validated with `fk` (main compile) only, so the `AppDatabase` constructor param added to `InputBindingRepository` / `ImportFavoritesUseCase` silently broke `InputBindingRepositoryTest` + `ImportFavoritesUseCaseTest` (test source set is not compiled by `fk`). Surfaced by S0904's `testStandardDebugUnitTest` run and fixed here: both tests now inject a `mockk<AppDatabase>` and stub `db.withTransaction` via `mockkStatic("androidx.room.RoomDatabaseKt")` to run the wrapped block. `testStandardDebugUnitTest --tests "*InputBindingRepositoryTest" --tests "*ImportFavoritesUseCaseTest"` PASS.
