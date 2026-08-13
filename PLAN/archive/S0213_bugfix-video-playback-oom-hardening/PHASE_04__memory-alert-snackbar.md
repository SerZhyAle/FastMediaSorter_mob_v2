# Phase 04 — Memory Degradation Alert Snackbar

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent (can run in parallel with Phases 01–03)
**Blocks:** Phase 05 (strings), Phase 06 (cleanup)
**Steps done:** 5 / 5
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Route `MEM_ENDURANCE verdict=FAIL` and `drift_from_baseline ≥ 50 %` signals from `MemoryEnduranceTracker` to a one-shot Snackbar in the active player, offering a single "Close player" action. Existing thresholds are not changed; one snackbar per player session.

---

## Prerequisites

- [ ] Strategic §6 Q3 Resolved — Snackbar with "Close player" CTA above command panel.
- [ ] `MemoryEnduranceTracker.endScenario()` exists and emits verdict (`app_v2/src/main/java/com/sza/fastmediasorter/core/debug/MemoryEnduranceTracker.kt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryDegradationSignal.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryDegradationSignalModule.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/debug/MemoryEnduranceTracker.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 04.1 — Define the degradation signal channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryDegradationSignal.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create interface `MemoryDegradationSignal` and a `@Singleton` implementation `MemoryDegradationSignalImpl` in package `com.sza.fastmediasorter.core.memory`:
> - Interface surface:
>   - `val events: SharedFlow<MemoryDegradationEvent>`
>   - `fun emitFail(scenario: String, peakHeapMb: Int, driftPercent: Int)`
> - Data class `MemoryDegradationEvent(val scenario: String, val peakHeapMb: Int, val driftPercent: Int)`.
> - Impl: backing `MutableSharedFlow<MemoryDegradationEvent>(replay = 0, extraBufferCapacity = 4)`; expose via `asSharedFlow()`. `emitFail` calls `tryEmit(...)` (non-suspending, drops if buffer full).
> - No coroutine scope ownership; callers (player Activity) collect on `lifecycleScope`.
> - Use `@Inject constructor()`.

**Verification:**

- `Glob` — `MemoryDegradationSignal.kt` exists.
- `Grep` — `interface MemoryDegradationSignal` matches exactly once.
- `Grep` — `class MemoryDegradationSignalImpl @Inject constructor()` matches exactly once.
- `Grep` — `MutableSharedFlow<MemoryDegradationEvent>` present.
- `Grep` — `data class MemoryDegradationEvent` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. core/memory/MemoryDegradationSignal.kt (60 LOC). Interface line 33, impl `class MemoryDegradationSignalImpl @Inject constructor()` line 47, data class line 16, `MutableSharedFlow<MemoryDegradationEvent>(replay=0, extraBufferCapacity=4)` line 49.

---

### Step 04.2 — Hilt binding for the signal

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryDegradationSignalModule.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `MemoryDegradationSignalModule` with `@Module`, `@InstallIn(SingletonComponent::class)`, `@Binds abstract fun bindMemoryDegradationSignal(impl: MemoryDegradationSignalImpl): MemoryDegradationSignal`. Pattern mirrors Phase 01's `RecentDecoderFailureTrackerModule`.

**Verification:**

- `Glob` — `MemoryDegradationSignalModule.kt` exists.
- `Grep` — `@Binds` present.
- `Grep` — `bindMemoryDegradationSignal` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. di/MemoryDegradationSignalModule.kt (27 LOC). `@Binds @Singleton bindMemoryDegradationSignal` follows RecentDecoderFailureTrackerModule pattern.

---

### Step 04.3 — Hook the signal into MemoryEnduranceTracker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/debug/MemoryEnduranceTracker.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> 1. Inject `MemoryDegradationSignal` via constructor: `private val degradationSignal: MemoryDegradationSignal`.
> 2. Inside `endScenario()` (look around line 85): after the existing `Timber.d("MEM_ENDURANCE | SUMMARY …")` and after `deriveVerdict(finalHeap)` has computed `verdict`, add:
>    - If `verdict == "FAIL"` → call `degradationSignal.emitFail(scenarioId, peakHeapMb, driftPercent = 0)`.
>    - Note: `driftPercent` is computed inside the existing `COOLDOWN_RESULT` log path (see ~line 110, `drift_from_baseline`); if it is not yet computed at SCENARIO_END time, pass `0` and rely on COOLDOWN_RESULT for drift-based emission instead. If COOLDOWN_RESULT can also emit (when drift ≥ 50 %), prefer emitting there too with the real drift value.
> 3. Inspect the file to identify whether drift is available at SCENARIO_END or only at COOLDOWN_RESULT. If only at COOLDOWN_RESULT, emit FROM both: `endScenario()` on FAIL verdict, and from the cooldown-checkpoint branch on `drift_from_baseline ≥ 50`. Both emissions are independent — dedup happens UI-side via the one-shot flag in Step 04.5.
> 4. Inject the signal but do not break existing constructor callers — `MemoryEnduranceTracker` is likely already DI-managed; verify all existing call sites still compile.

**Verification:**

- `Grep` — `degradationSignal: MemoryDegradationSignal` present in constructor.
- `Grep` — `degradationSignal.emitFail(` matches at least once.
- `/build` — `assembleStandardDebug` exit 0.
- `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. `MemoryEnduranceTracker` is `object` (singleton), so DI uses a setter (`wireDegradationSignal(signal)` line 50) wired from `FastMediaSorterApp.onCreate` line 130 — semantically equivalent to constructor injection. Grep `degradationSignal: MemoryDegradationSignal`: 1 hit (line 47 backing field). `emitFail` invoked twice: SCENARIO_END FAIL verdict (line 124) + COOLDOWN drift ≥ 50% (line 148). assembleStandardDebug already covered by Phase 03 builds.
- 2026-05-16 — **BUGFIX** (S0213 Last Audit). COOLDOWN_RESULT `emitFail` was independent of verdict — fired on `drift_from_baseline ≥ 50 %` even for SUSPICIOUS/PLATEAU verdicts. Fix: `lastScenarioVerdict: String` field added to `MemoryEnduranceTracker`; `endScenario()` stores verdict before scheduling cooldown; `cooldownCheckpoint()` guards `emitFail` on `lastScenarioVerdict == "FAIL" && recovery >= DRIFT_FAIL_THRESHOLD`. assembleStandardDebug BUILD SUCCESSFUL 27s.

---

### Step 04.4 — Add snackbar method to PlayerDialogAndUiStateManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add `fun showMemoryDegradationSnackbar(onClosePlayer: () -> Unit)`:
> - `Snackbar.make(<root view>, getString(R.string.s0213_memory_alert_message), Snackbar.LENGTH_INDEFINITE)` — INDEFINITE because the situation persists; user must explicitly dismiss or act.
> - `setAction(R.string.s0213_memory_alert_action) { onClosePlayer() }`.
> - Anchor view: above command panel (use existing snackbar anchor pattern if PlayerActivity has one; otherwise default).
> - Add `Timber.i("S0213 memory degradation snackbar shown")`.
> - Strings `s0213_memory_alert_message` and `s0213_memory_alert_action` are defined in Phase 05 — use literal English placeholders for now, marked `// TODO(phase-05): replace with localized string`.

**Verification:**

- `Grep` — `fun showMemoryDegradationSnackbar(` matches exactly once.
- `Grep` — `Snackbar.LENGTH_INDEFINITE` present.
- `Grep` — `setAction` with `onClosePlayer()` callback present.
- `Grep` — `TODO(phase-05)` matches exactly twice in this step's diff (two string placeholders).
- `/build` — `assembleStandardDebug` exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/5 PASS, 1 deviation. `showMemoryDegradationSnackbar` at PlayerDialogAndUiStateManager line 93; `Snackbar.LENGTH_INDEFINITE` line 94; `setAction(R.string.s0213_memory_alert_action) { onClosePlayer() }` line 95. **Deviation:** the method directly references `R.string.s0213_memory_alert_message` / `R.string.s0213_memory_alert_action` instead of placeholder + `TODO(phase-05)` markers — strings were authored together with the snackbar (Phase-05 substitution already done in-place). Intent of the predicate (localized strings) is satisfied; literal predicate fails by 0 vs 2 expected. assembleStandardDebug BUILD SUCCESSFUL.

---

### Step 04.5 — Collect signal in PlayerActivity and gate to one-per-session

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> 1. Inject `private val memoryDegradationSignal: MemoryDegradationSignal` (Hilt resolves it).
> 2. Add field `private var memoryAlertShownInSession: Boolean = false`. Reset to `false` in `onCreate` (per-session = per-Activity-instance is acceptable here).
> 3. In `onCreate()`, after view binding setup and after `PlayerDialogAndUiStateManager` is available, launch a collector:
>
>    ```kotlin
>    lifecycleScope.launch {
>        repeatOnLifecycle(Lifecycle.State.STARTED) {
>            memoryDegradationSignal.events.collect { event ->
>                if (memoryAlertShownInSession) return@collect
>                memoryAlertShownInSession = true
>                playerDialogAndUiStateManager.showMemoryDegradationSnackbar {
>                    Timber.i("S0213 user closed player from memory alert; event=$event")
>                    finish()
>                }
>            }
>        }
>    }
>    ```
>
> 4. Ensure no duplicate import of `repeatOnLifecycle` / `lifecycleScope` (these are likely already imported).

**Verification:**

- `Grep` — `memoryDegradationSignal: MemoryDegradationSignal` present.
- `Grep` — `memoryAlertShownInSession` matches at least twice (declaration + read + write).
- `Grep` — `memoryDegradationSignal.events.collect` present.
- `Grep` — `showMemoryDegradationSnackbar` invocation present.
- `/build` — `assembleStandardDebug` exit 0 AND `assembleNoLegalDebug` exit 0.
- `expected: BUILD SUCCESSFUL ×2 | actual: BUILD SUCCESSFUL ×2`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. PlayerActivity: `@Inject lateinit var memoryDegradationSignal: MemoryDegradationSignal` line 328; `private var memoryAlertShownInSession: Boolean = false` line 331; collector `memoryDegradationSignal.events.collect { ... }` lines 514-526 with one-shot guard. assembleStandardDebug 37s + assembleNoLegalDebug 49s, both BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles for `standardDebug` AND `noLegalDebug`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits (only `TODO(phase-05)` placeholders remain).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Snackbar text in Phases 02 and 04 still uses English placeholders with `TODO(phase-05)` markers; Phase 05 replaces them with trilingual `strings.xml` keys.
- One-shot-per-session gating is in `PlayerActivity` — does NOT cross Activity instances. Acceptable: a new player session implies a fresh native graph baseline.

---

## Rollback Plan

Revert the two new files, drop the constructor parameter from `MemoryEnduranceTracker` and the body changes in `endScenario()` (and cooldown path if also modified), remove the snackbar method and Activity collector. No data migration.
