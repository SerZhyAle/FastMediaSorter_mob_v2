# Phase 06 — Startup Workers Defer

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 4 / 5
**Started:** 2026-05-15
**Completed:** —

---

## Objective

Audit all work executed synchronously in `Application.onCreate` / first 5 seconds. Reclassify as "must run early" or "can defer 30..60 seconds". Move all deferrable work behind a `WorkManager` `OneTimeWorkRequest` with `INITIAL_DELAY` 30 seconds, decoupling it from the UI critical path while keeping only the Glide cache-size mirror on the eager path.

**Hazards confirmed by research 2026-05-15** (full per-task table available in `temp/S0207_phase06_audit_20260515_2335.md`):

- **D10** — `TranslationCacheManager.clearAll()` at `FastMediaSorterApp.kt:158` runs **synchronously on the main thread** (no `applicationScope.launch` wrapper). Deferral fixes the main-thread call as a side effect.
- **D17** — `logAppStartupInfo()` at `FastMediaSorterApp.kt:204` (impl at `:482-486`) performs `StatFs(Environment.getDataDirectory()).blockSizeLong` **on the main thread**.
- **E10** — `ConnectionThrottleManager` flow collect at `AppStartupInitializer.kt:412` is a **never-completing `applicationScope.launch { … collect { } }`**. Deferral must guarantee it is started exactly once (use `AtomicBoolean.compareAndSet(false, true)` gate).
- **D20** — the existing `delay(2000)` block at `FastMediaSorterApp.kt:231-268` already uses a hard-coded 2 s delay for WorkManager scheduling. Generalize this into a shared deferral primitive instead of stacking a second 30 s delay on top.
- **No Application-level first-frame signal exists today**. `core/ui/BaseActivity.kt:88` has an Activity-scoped `binding.root.post { … }` but Application scope has nothing. Phase 06 must introduce one — recommended: `ProcessLifecycleOwner.lifecycle` `ON_START` listener already attached at `FastMediaSorterApp.kt:126-140` + `AtomicBoolean.compareAndSet(false, true)` gate + `applicationScope.launch { delay(30..60s); ... }`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 01 `MEM_PROBE | checkpoint=APP_STARTED` and `MAIN_DRAWN` baseline values recorded.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/FirstFrameSignal.kt` | New | ≤ 80 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/init/FirstFrameSignalTest.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/init/AppStartupInitializerTest.kt` | New | ≤ 120 |

> One or more existing single-purpose startup workers (`StreamingCacheStartupGcWorker`, `ThumbnailCleanup`, etc.) may also be touched for INITIAL_DELAY tuning — confirm in Step 06.1 audit.

---

## Steps

### Step 06.1 — Audit of startup work (Research item 5)

**Files:** —
**Depends on:** — start of phase

**Prompt for developer:**

> Open `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`, `AppStartupInitializer.kt`, and every existing startup worker / initializer. For each piece of work that runs in the first 5 seconds, classify in a table:
>
> | Work | File | Class | Currently runs | Can defer? | Justification |
> |------|------|-------|----------------|------------|----------------|
>
> Candidates known from logs:
> - Translation cache cleanup (`TranslationCacheManager`)
> - Failed-video cache clear (`NetworkFiles*Cache`)
> - Thumbnail cache cleanup (`ThumbnailCleanup`)
> - Streaming cache GC (`StreamingCacheStartupGcWorker` — already on WorkManager but may run too early)
> - `UnifiedFileCache.clear()` (from `BrowseLifecycleSetupManager`)
>
> Output the table as a markdown block appended to this phase's "Audit results" section below. Mark with ✅ those safe to defer, ❌ those that MUST run early (with reason).
>
> Record the audit in `temp/S0207_phase06_audit_<timestamp>.md` for traceability.

**Verification:**

- `Glob` — `temp/S0207_phase06_audit_*.md` exists with the table.
- Table contains a row for each of the five candidates above (plus any additional discovered).

**Status:** `[x]` done — audit captured in `temp/S0207_phase06_audit_20260515_2335.md`; nine tasks were marked safe to defer and two were kept early.

---

### Step 06.2 — Implement `DeferredStartupWorker`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Create `@HiltWorker class DeferredStartupWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, /* inject deferrable use cases */) : CoroutineWorker(context, params)`. Body of `doWork()`: execute each deferrable task (from Step 06.1 audit) sequentially, swallow individual failures (`runCatching { .. }`), log each via Timber.i. Return `Result.success()` regardless of individual task outcomes (best-effort).
>
> Constraints: no `ExpeditedWork` — this is explicitly low priority.

**Verification:**

- `Glob` — `DeferredStartupWorker.kt` exists.
- `Grep` — `@HiltWorker` and `class DeferredStartupWorker` present.
- `Grep` — `class DeferredStartupWorker` matches exactly once.
- `Grep` — `runCatching` present (defensive pattern).

**Status:** `[x]` done — `DeferredStartupWorker` landed as a best-effort `@HiltWorker` with sequential `runTask(...)` logging around each deferred task.

---

### Step 06.3 — Wire deferred enqueue + remove eager calls

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`

**Depends on:** Step 06.2

**Prompt for developer:**

> In `FastMediaSorterApp.onCreate` (or wherever the current early-startup orchestration lives — `AppStartupInitializer` per file name), perform two changes:
> 1. **Remove** synchronous invocations of every "✅ Can defer" task identified in Step 06.1.
> 2. **Add** a single enqueue of `DeferredStartupWorker` with `setInitialDelay(30, TimeUnit.SECONDS)`. Use `ExistingWorkPolicy.KEEP` so multiple cold starts in 30 seconds do not queue duplicates.
>
> Keep all "❌ Must run early" tasks where they are. Do not modify those.
>
> Backup both files.

**Verification:**

- `Glob` — backups exist in `temp/` for touched files only if any exceed the repo's 500 LOC backup threshold. The landed `FastMediaSorterApp.kt` / `AppStartupInitializer.kt` edits stayed below that threshold.
- `Grep` — `DeferredStartupWorker::class.java` (or fully-qualified equivalent) referenced in the orchestration site.
- `Grep` — `setInitialDelay(30, TimeUnit.SECONDS)` present.
- `Grep` — `ExistingWorkPolicy.KEEP` present in the same enqueue block.
- For each "✅ Can defer" task in the audit table: `Grep` for the call no longer matches in the orchestration site (still allowed to exist inside `DeferredStartupWorker`).

**Status:** `[x]` done — eager startup calls were removed from the hot path and replaced with a single 30 s `ExistingWorkPolicy.KEEP` enqueue.

---

### Step 06.4 — Add Application-level first-frame signal + generalize the existing `delay(2000)`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/core/init/FirstFrameSignal.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`

**Depends on:** Step 06.3

**Prompt for developer:**

> Create `@Singleton class FirstFrameSignal @Inject constructor()` in `core/init/`. State: `private val fired = AtomicBoolean(false)`. API:
> - `fun signal()` — `fired.compareAndSet(false, true)` returns `true` on first call, `false` afterwards. On first call: log via `Timber.i("FirstFrameSignal: fired (uptime=<ms>)")`.
> - `fun hasFired(): Boolean` — `fired.get()`.
> - `suspend fun await(timeoutMs: Long = 60_000)` — suspends until `signal()` is called or timeout elapses. Use `kotlinx.coroutines.flow.MutableStateFlow<Boolean>` internally for the suspension primitive.
>
> In `FastMediaSorterApp.kt`:
> - Inject `firstFrameSignal: dagger.Lazy<FirstFrameSignal>` (preserve S0194 lazy pattern).
> - In the existing `ProcessLifecycleOwner.lifecycle.addObserver { … }` block at `:126-140`, on the **first** `Lifecycle.Event.ON_START`, call `firstFrameSignal.get().signal()`. Use a local `AtomicBoolean` or the signal's own `compareAndSet` to ensure it only fires once.
> - **Generalize the existing `applicationScope.launch(IO) { delay(2000); WorkManager … }` at `:231-268`**: replace `delay(2000)` with `firstFrameSignal.get().await(timeoutMs = 60_000)` so all WorkManager scheduling is anchored on the same shared deferral primitive used by `DeferredStartupWorker` (Step 06.3). Comment line explaining the change.
> - Move the following from synchronous main-thread calls into `applicationScope.launch(IO) { firstFrameSignal.get().await(); … }` blocks:
>   - **D10**: `TranslationCacheManager.clearAll()` at `:158` (today main-thread; deferral fixes both the synchronous call and the main-thread violation).
>   - **D17**: `logAppStartupInfo()` at `:204` (disk I/O via `StatFs` on main thread).
> - **E10 sanity check**: `AppStartupInitializer.initializeConnectionThrottleManager()` at `:412` already launches a never-completing collect. After deferral via `DeferredStartupWorker`, confirm it is still launched exactly once. If the worker re-runs (it should not — `ExistingWorkPolicy.KEEP` blocks duplicates), `ConnectionThrottleManager` would receive duplicate collectors. Document the invariant in the worker KDoc.
>
> Add or update a narrow test around the single-fire startup contract: first-frame signal fires once, deferred enqueue stays single-shot, and the initializer path does not schedule duplicate work on repeated lifecycle transitions.

**Verification:**

- `Glob` — `FirstFrameSignal.kt` exists.
- `Grep` — `class FirstFrameSignal` matches exactly once.
- `Grep` — `AtomicBoolean(false)` present in `FirstFrameSignal.kt`.
- `Grep` — `firstFrameSignal.get().signal()` referenced from `FastMediaSorterApp.kt`.
- `Grep` — `firstFrameSignal.get().await(` referenced from `FastMediaSorterApp.kt`.
- `Grep` in `FastMediaSorterApp.kt` — `delay(2000)` no longer present (or commented out with a `// Phase 06:` marker).
- `Grep` in `FastMediaSorterApp.kt` — `TranslationCacheManager.clearAll()` no longer appears as a top-level call inside `onCreate` (only inside a `launch(IO)` block).
- `Grep` in `FastMediaSorterApp.kt` — `logAppStartupInfo()` no longer appears as a top-level call inside `onCreate` (only inside a deferred block).
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — `FirstFrameSignal` landed, `FastMediaSorterApp` now waits for the first visible frame before scheduling deferred startup work, `AppStartupInitializer` splits eager vs deferred tasks with a single-shot throttle bootstrap guard, and targeted JVM coverage was added for both startup primitives. The app currently keeps a process-local `lazy` `FirstFrameSignal` inside `FastMediaSorterApp`; functionally it still behaves as the intended single-process first-frame gate.

---

### Step 06.5 — Calibration measurement (startup)

**Files:** —
**Depends on:** Step 06.4 + project compiles

**Prompt for developer:**

> Cold-start the app. Capture `logs/current.log`. Check:
> - `MEM_PROBE | checkpoint=APP_STARTED` native value vs Phase 01 baseline → expected: equal or lower.
> - `MEM_PROBE | checkpoint=MAIN_DRAWN` native value vs Phase 01 baseline → expected: meaningfully lower (≥ 5 MB delta).
> - 30..40 seconds after cold start, `DeferredStartupWorker` log entries appear (one per deferred task).
>
> Record actual deltas in Blockers Log of INDEX.md.

**Verification:**

- `Grep` in `logs/current.log` — at least one Timber line whose tag references `DeferredStartupWorker` in the most recent session.
- Two `MEM_PROBE` lines (APP_STARTED, MAIN_DRAWN) present and recorded.

**Status:** `[manual — deferred to human]` — requires device/emulator run; deferred to BlockNeedUserTest operator test.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles — `./gradlew.bat :app_v2:compileStandardDebugKotlin :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.core.init.FirstFrameSignalTest" --tests "com.sza.fastmediasorter.core.init.AppStartupInitializerTest"` PASS.
- [x] Audit document written in `temp/`.
- [ ] Deferred worker executes 30s after cold start, completes successfully.
- [x] Narrow coverage exists for `AppStartupInitializer` / `FirstFrameSignal` single-shot behaviour. Compile-only evidence is not enough for this phase.
- [x] Dev log entry added.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 07 (idle disconnect) is independent. None of its targets (SFTP, SMB, FTP) overlap with the deferred-work set audited here.

---

## Rollback Plan

Revert phase commits. Deferred worker stops being enqueued; orchestration site re-runs all tasks synchronously as before. No data migration.

---

## Revision History

- **2026-05-15** — manual implementation sync after Phase 06 code landed
  - Applied: marked Steps 06.1..06.4 complete; recorded the actual audit artifact path, added the startup single-shot JVM tests, noted that `FastMediaSorterApp` currently keeps a local lazy `FirstFrameSignal` instance while preserving the intended single-process gate semantics, and left Step 06.5 open pending cold-start logcat calibration.
- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness, verifiability)
  - Applied: Objective extended with concrete hazards D10 / D17 / E10 / D20 + first-frame-signal gap; "Files Touched" extended with `FirstFrameSignal.kt`; new Step 06.4 (`FirstFrameSignal` singleton + generalize the existing `delay(2000)` at `FastMediaSorterApp.kt:231` + move D10 `TranslationCacheManager.clearAll` and D17 `logAppStartupInfo` off the main thread + E10 single-launch invariant); calibration renumbered 06.4 → 06.5; phase counter 4 → 5. Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/05_startup_workers_audit.md` (full task-by-task table, classification, file:line) + `00_SUMMARY.md` F9, F10.
- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: verifiability)
  - Applied: added explicit startup-single-shot test guidance for `AppStartupInitializer` / `FirstFrameSignal` so Phase 06 is not closed on build output alone. Proposed (DISCUSS): 0.
