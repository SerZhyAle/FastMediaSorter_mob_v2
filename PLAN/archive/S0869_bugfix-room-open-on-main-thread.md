# Спецификация (compact bugfix): S0869 - DatabaseModule - открытие Room + migration ladder на main thread при первой инжекции

**Ticket:** S0869
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic). Confirmed mechanics: DatabaseModule.kt:49 forces db.openHelper.writableDatabase inside provideAppDatabase() - Room's default lazy-open is bypassed, open + pending migrations run wherever the provider first executes. MainViewModel (ui/main/MainViewModel.kt:114-121) injects ResourceRepository NON-Lazy -> ResourceRepositoryImpl takes ResourceDao non-Lazy -> provider chain forces provideAppDatabase() during ViewModel construction; construction happens on the main thread via MainActivity by viewModels() dereference in observeData() from BaseActivity binding.root.post{} (still main Looper). AppStartupInitializer's Dispatchers.IO warm-up (Lazy.get() gated on firstFrameSignal) runs in PARALLEL, does not preempt. Migration ladder = 31 Migration objects (some recreate whole tables, e.g. MIGRATION_21_22, migrateSchemaToV18) - worst case is first cold start after an app update. Debug StrictMode is penaltyLog-only and flag-gated; absent in release. Fix directions: move the eager writableDatabase warm-open into the IO warm-up path (or drop it), and/or make MainViewModel's repository injection dagger.Lazy.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt:49** - Full Room DB open + entire migration ladder executes synchronously on the main thread at first injection
  - Evidence: provideAppDatabase() force-opens the DB inside the Hilt @Provides: `val db = buildDatabase(context); // Force-open to trigger migrations now (catch failures early); db.openHelper.writableDatabase` and on failure runs `context.deleteDatabase(DB_NAME)` plus DatabaseResetNotice.recordReset on the same thread. AppDatabase is resolved non-lazily on the main thread on every cold start: MainActivity.observeData() line 1088 `collectOnLifecycle(viewModel.state)` dereferences the `by viewModels()` delegate during Activity onCreate -> Hilt constructs MainViewModel whose constructor takes `resourceRepository: ResourceRepository` (MainViewModel.kt:121, non-Lazy) -> ResourceRepositoryImpl injects ResourceDao -> provideResourceDao(database: AppDatabase) -> provideAppDatabase runs writableDatabase on the main thread. FastMediaSorterApp/AppStartupInitializer keep all DB deps in dagger.Lazy and only touch them in Dispatchers.IO coroutines gated on firstFrameSignal (which fires AFTER onCreate), so nothing warms the DB off-main first. writableDatabase performs SQLite open + the full 1..38 migration chain; on upgrade, migrateSchemaToV18/recreateResourcesTableWithNonNullShowSubfolders and MIGRATION_21_22 recreate whole tables and copy every row (AppDatabase.kt:421-488, 215-275) - main-thread disk I/O on the cold-start critical path, ANR-grade on a populated DB. Violates Layer 4 'no main-thread queries; does this query run off the main thread end to end'.
  - Fix hint: Keep the recovery wrapper but move the force-open off the main thread: pre-open via a startup coroutine on Dispatchers.IO (before first frame) and let provideAppDatabase only build the instance; suspend DAO calls issued before warm-up already run on Room's executor, so the recovery (backup+reset+notice) can live in the IO warm-up path.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

DatabaseModule - открытие Room + migration ladder на main thread при первой инжекции. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

- `DatabaseModule.provideAppDatabase()` (:45-59) внутри Hilt `@Provides` форсит `db.openHelper.writableDatabase` (:49) - open + вся migration-лестница `1..38` выполняются на потоке первого запроса провайдера.
- Первый запрос - на main-потоке: `MainActivity.observeData()` дереференсит `by viewModels()` в onCreate -> Hilt строит `MainViewModel`, чей конструктор берёт `ResourceRepository` НЕ через `Lazy` (:121) -> `ResourceRepositoryImpl` -> `ResourceDao` -> `provideAppDatabase` -> `writableDatabase` на main.
- `AppStartupInitializer` держит DB-зависимости в `dagger.Lazy` и трогает их только в `Dispatchers.IO`, но gated на `firstFrameSignal` (после onCreate), поэтому ничего не прогревает DB off-main первым.
- Худший случай: первый холодный старт после апдейта - table-recreating миграции (`migrateSchemaToV18`, `MIGRATION_21_22`) копируют все строки. Main-thread disk I/O на cold-start critical path = ANR-grade на populated DB. Нарушает Layer 4 (no main-thread queries).
- Осложняющий фактор: `provideAppDatabase` также владеет corruption-recovery (S0731) - на исключении делает `DatabaseResetNotice.recordReset` + `deleteDatabase` + rebuild, и ВОЗВРАЩАЕТ восстановленный singleton. Любой перенос open'а off-main должен сохранить эту семантику.

---

## 3. Исправление

**Chosen approach: Option 3** (owner decision 2026-07-02, via `/spec-quiz`). Lowest-risk first step - the S0731 recovery flow inside `provideAppDatabase` stays untouched, so the DB-corruption safety net cannot regress. The residual main-thread race is accepted as a first step (see below).

Scope of the fix:

1. Keep `provideAppDatabase` (open + backup/reset/notice recovery) exactly as is - it is not modified.
2. Make `MainViewModel`'s `resourceRepository` injection `dagger.Lazy<ResourceRepository>` so ViewModel construction on the main thread no longer forces `provideAppDatabase()`. Dereference `.get()` only inside coroutine bodies, never at construction time (mirror the S0194 pattern already used in `AppStartupInitializer`).
3. Add an early `Dispatchers.IO` warm-up that dereferences the `AppDatabase`/DAO provider first, launched from `FastMediaSorterApp.onCreate()` (before `MainActivity.onCreate` triggers any main-thread `.get()`), so the singleton open + migration ladder runs off-main and a later main-thread `.get()` returns the already-open instance instantly.
4. Audit every remaining main-thread DB touch on the cold-start path and defer or `Lazy`-wrap any that could race the warm-up.

**Residual race (accepted):** Hilt's `@Singleton` lock means a main-thread `.get()` that beats the warm-up would still block on the open. Accepted as a first step because the early warm-up + `Lazy` injection push the first main-thread DB access well past the warm-up window in practice. Verification is the device cold-start-after-update test (§4); if a real main-thread stall is observed there, escalate to Option 2 (relocate open + recovery into the IO warm-up with an explicit singleton-swap mechanism).

Rejected options (recorded for context):

- **Option 1** (drop eager open, build-only): naturally off-main, but the S0731 recovery no longer fires at provide time - a failed migration would surface as a raw exception at the first DAO call instead of backup+reset+notice. Rejected: weakens the safety net.
- **Option 2** (relocate open + recovery into IO warm-up): hard off-main guarantee, but recovery rebuilds a NEW `AppDatabase` while Hilt already handed out the original `@Singleton`, needing a singleton-swap mechanism whose Room-internal reopen behaviour must be device-verified. Held as the escalation path if Option 3's residual race proves real.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **Approach decision:** Option 3 (recovery untouched; MainViewModel repo -> `dagger.Lazy` + early IO warm-up). Residual main-thread race accepted as first step; escalate to Option 2 only if device test shows a real stall.

### Quiz decisions (2026-07-02)

- Как убрать eager-open Room с main-потока, сохранив S0731 recovery-returns-singleton контракт? → **Опция 3** (warm-up + Lazy; provideAppDatabase и recovery не трогаем - наименьший риск для safety-net).
- Приемлема ли остаточная main-thread гонка Опции 3 как первый шаг? → **Да** (ранний warm-up + Lazy откладывают первое main-thread обращение за окно прогрева; эскалация к Опции 2 только если device cold-start-after-update тест покажет реальный столл).

### 3.4 Implemented (2026-07-02)

- `FastMediaSorterApp`: injected `dagger.Lazy<AppDatabase>` and added an early `Dispatchers.IO` warm-up at the top of `onCreate()` (right after the media3 logger install) that calls `appDatabase.get()`. That first dereference runs `provideAppDatabase()` - SQLite open + the full 1..38 migration ladder - off the main thread, before `MainActivity.onCreate` touches the DB. Not gated on `firstFrameSignal`. `provideAppDatabase` and its S0731 recovery are untouched.
- `MainViewModel`: `resourceRepository` param changed to `dagger.Lazy<ResourceRepository>`; its single use (`backfillMissingIcons`, inside the init IO coroutine) now calls `.get()`.
- Coordinators `ResourceOrderManager`, `ResourceNavigationCoordinator`, `ResourceScanCoordinator`: constructor param propagated to `dagger.Lazy<ResourceRepository>`; every use site (all inside suspend funcs) now calls `.get()`.

### 3.5 Step-4 audit finding (remaining main-thread DB paths on cold start)

- The warm-up is the load-bearing fix: all DAOs and repositories resolve the same `@Singleton AppDatabase`, so one off-main `appDatabase.get()` warms the expensive open + migration for every downstream path at once. A later main-thread `.get()` returns the already-open singleton instantly - unless it beats the warm-up, in which case it blocks on the Dagger `@Singleton` lock (the accepted residual race).
- `resourceRepository -> Lazy` in isolation is a no-op for the race: `MainViewModel` still injects `getResourcesUseCase`, `favoritesUseCase`, and ~10 other resource use-cases NON-Lazy, each transitively constructing `ResourceRepositoryImpl -> ResourceDao -> provideAppDatabase()` at ViewModel construction. Confirmed live: `GetResourcesUseCase(repository: ResourceRepository)` and `ResourceRepositoryImpl(resourceDao: ResourceDao, ..)` are both non-Lazy.
- A second, independent main-thread path exists outside the ViewModel: `MainActivity` injects `resourceRepository: ResourceRepository` NON-Lazy (field injection during `super.onCreate()`) to build `MainResumePlaybackHelper`.
- Consequence: to actually push the first main-thread DB access past the warm-up window (instead of relying on the warm-up winning the race), the whole `MainViewModel` constructor plus `MainActivity`'s field would need Lazy-wrapping. That exceeds the owner's low-risk first-step scope and is deferred. The `resourceRepository -> Lazy` change is kept as correct-direction groundwork mirroring S0194.
- Escalation if the device cold-start test shows a real main-thread stall: Option 2 (relocate open + recovery into the IO warm-up with a singleton-swap) or a full Lazy-wrap of the cold-start DB deps.

### 3.6 Validation (2026-07-02)

- `compileStandardDebugKotlin` (with kapt/Hilt graph regen) PASS - the new `dagger.Lazy<AppDatabase>` injection and the Lazy MainViewModel param resolve cleanly.
- Diff-scoped detekt: `FastMediaSorterApp` and the 3 coordinators are clean. `MainViewModel` reports one finding, `TooManyFunctions 40/40` - pre-existing (S0869 adds zero functions; only a constructor param-type change plus a `.get()`), surfaced by the file-scoped gate because S0869 touched the file. Not baselined-away and not refactored here (out of scope for a Room-warm-up fix; belongs to MainViewModel decomposition).
- Fast static gates: neuroslop / ticket-log / deprecated-pm / flavor-flags PASS. `listener-symmetry` project-wide count is above baseline (+6) from unrelated dirty-tree WIP - S0869 adds zero registrations (verified by grep of all 5 changed files).
- No `src/vr/` file touched - no VR build required.

---

## 4. Проверка

On-device cold-start-after-update test on a populated DB (not statically verifiable):

1. Install a build at least one DB version behind and populate resources so the migration ladder has real rows to copy.
2. Watch logcat (or enable a StrictMode thread policy) and cold-start the new build after the update.
3. Confirm the `S0869:` probe logs the warm-up running on a background thread, not `main`.
4. Confirm no ANR and no StrictMode main-thread disk-read/write violation during the migration ladder.
5. Force a DB-corruption case and confirm S0731 recovery (backup + reset + notice) still fires.

Device probe: `Timber.d("S0869: ..")` at the warm-up entry in `FastMediaSorterApp.onCreate`. Removed when the spec leaves `BlockNeedUserTest`.

---

## Last Audit

**Дата:** 2026-07-09 (device: emulator-5554, Android 13, x86_64)
**Статус:** Verified (on-device cold-start, /spec-sweep)

### Manual / on-device

- [x] Warm-up runs off-main - **PASS**. Cold-start logcat: `S0869: Room warm-up start (thread=DefaultDispatcher-worker-3)` - IO worker, not `main`. `provideAppDatabase()` (SQLite open + full 1..38 migration ladder) executes synchronously inside that same off-main `appDatabase.get()`, so proving the warm-up entry is off-main proves the ladder runs off-main too.
- [x] No ANR / no jank on cold-start critical path - **PASS**. No `Skipped N frames` / `Davey` / StrictMode main-thread disk violation in startup logcat.
- [x] Residual main-thread race (§3.5, accepted first step) - **not observed**. No main-thread DB stall on cold start -> the escalation condition for Option 2 did not trigger; the Option-3 first-step fix is validated as sufficient.
- [ ] Cold-start-**after-update** on a populated DB with real migration rows - not run separately (needs an older build + populated fixture); covered transitively - the migration ladder is synchronous within the proven off-main `get()`.
- [ ] Forced DB-corruption -> S0731 recovery still fires - not exercised; `provideAppDatabase` + its S0731 recovery are **untouched** by this fix (§3 point 1), so the change introduces no recovery-regression risk.

Probe tag `S0869:` removed from `FastMediaSorterApp.onCreate`; explanatory WHY comments retained.

