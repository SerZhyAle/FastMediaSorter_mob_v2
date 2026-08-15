# Phase 02 - Battery metric and runtime estimate

**Strategic spec:** [`../S1178_launcher-system-status-widgets.md`](../S1178_launcher-system-status-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Produce the battery metric - charge level plus a remaining-time estimate behind a replaceable estimator seam - with no UI.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - the 2026-08-02 ADR-3 refinement is Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `MetricValue` and `DeviceStatusProvider` from Phase 01 exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/BatteryStatus.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/BatteryRuntimeEstimator.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/DischargeRateBatteryRuntimeEstimator.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetBatteryStatusUseCase.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/DischargeRateBatteryRuntimeEstimatorTest.kt` | New | ≤ 200 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No file in this phase reaches 500 LOC.
>
> **Flavor placement.** Every production file above is shared code under `src/main/java/` and carries no flavor guard - strategic §3.2.

---

## Steps

### Step 02.1 - Add the battery status model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/BatteryStatus.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `BatteryStatus` with `percent: MetricValue<Int>`, `isCharging: Boolean`, `remainingMillis: MetricValue<Long>` and `isEstimateApproximate: Boolean`. KDoc `isEstimateApproximate` as the flag the view uses to mark a computed estimate, and state that `remainingMillis` stays `Unknown` until an estimate exists rather than falling back to a placeholder number.

**Why:**

Strategic §2.2 requires the gadget to show charge and a remaining-time estimate, and the §7 risk register demands that a computed estimate be marked approximate so the user is not shown a confident number no system produced.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/BatteryStatus.kt` exists.
- `Grep` - `data class BatteryStatus` matches exactly once.
- `Grep` - `isEstimateApproximate` present.
- `Grep` - `remainingMillis: MetricValue<Long>` present.

**Status:** `[x]` done

---

### Step 02.2 - Add the estimator seam and the discharge-rate implementation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/BatteryRuntimeEstimator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/DischargeRateBatteryRuntimeEstimator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `interface BatteryRuntimeEstimator` with `fun estimateRemainingMillis(percent: Int, isCharging: Boolean, elapsedRealtimeMillis: Long): MetricValue<Long>`. Add `DischargeRateBatteryRuntimeEstimator` as a `@Singleton` implementation that remembers the last observed percent and its timestamp, derives a discharge rate from the change between observations, and returns `MetricValue.Unknown` until a rate has actually been observed. Return `Unknown` while charging - the discharge rate says nothing about a rising level. Bind the implementation to the interface in `core/di/RepositoryModule.kt`.

**Why:**

Strategic ADR-3 permits a self-computed estimate only as a fallback that must remain replaceable, and §7 states the estimate must not be shown before a discharge rate has been gathered because a user believes a number no system stands behind.

**Verification:**

- `Glob` - both new files exist.
- `Grep` - `interface BatteryRuntimeEstimator` matches exactly once.
- `Grep` - `class DischargeRateBatteryRuntimeEstimator` matches exactly once and carries `@Singleton`.
- `Grep` - `bindBatteryRuntimeEstimator` present in `core/di/RepositoryModule.kt`.
- `Grep` - `Log\.d\(` returns zero hits in every file this step modifies.

**Status:** `[x]` done

---

### Step 02.3 - Add the battery status use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetBatteryStatusUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `GetBatteryStatusUseCase` implementing `DeviceStatusProvider<BatteryStatus>`. Read the level and charging state from the sticky `ACTION_BATTERY_CHANGED` intent obtained with a null receiver, exactly as `LauncherTrayManager.registerBattery` already does, so nothing new is registered and nothing needs unregistering. Query `BatteryManager.computeChargeTimeRemaining()` first and use its value when it is not `-1`; otherwise fall back to `BatteryRuntimeEstimator` and set `isEstimateApproximate = true`. Comment the `computeChargeTimeRemaining` call with the one fact that is not obvious from the API name: it reports time to full charge and answers `-1` while discharging, so in practice the fallback branch is the live one. Map a missing or malformed sticky intent to `MetricValue.Unknown` for the percent.

**Why:**

Strategic §11.3 requires a firmware without a system estimate to show the computed one marked approximate, and the 2026-08-02 §6 finding records that no public Android API reports time to depletion, so the system-first probe exists only to defer to a future OEM extension.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetBatteryStatusUseCase.kt` exists.
- `Grep` - `class GetBatteryStatusUseCase` matches exactly once and the declaration line contains `DeviceStatusProvider<BatteryStatus>`.
- `Grep` - `computeChargeTimeRemaining` present.
- `Grep` - `registerReceiver(null` present - the sticky read registers no receiver.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[~]` in progress

---

### Step 02.4 - Unit-test the discharge-rate estimator

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/DischargeRateBatteryRuntimeEstimatorTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add unit tests covering four cases: a single observation returns `Unknown`; two observations at a falling percent return a `Known` value proportional to the observed rate; a rising percent returns `Unknown`; and `isCharging = true` returns `Unknown` regardless of history. Drive the clock through the `elapsedRealtimeMillis` parameter rather than a real clock so the test is deterministic.

**Why:**

Strategic §7 rates "the remaining-charge estimate lies" as a high-probability risk with the worst consequence in the set, and the estimate is the only arithmetic in this ticket that no screenshot can falsify.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/DischargeRateBatteryRuntimeEstimatorTest.kt` exists.
- `Grep` - `@Test` matches at least four times in that file.
- `Grep` - `MetricValue.Unknown` present.
- Run `.\a.ps1 fu` and record `expected: DischargeRateBatteryRuntimeEstimatorTest passes | actual: <result>`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, APK produced, so the Hilt graph carrying the new `bindBatteryRuntimeEstimator` is validated and not merely compiled. The P1 audit fix landed after that build, and its proof is the unit run that followed: `check-standard-fast.ps1 -Mode Unit` compiled the whole `standardDebug` production source set green over the edited file and reported `Fast check passed`. The fix changed one private method body and added one private method - no signature, no binding, no graph edge - so no second graph validation is owed.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the phase - written by `scripts/post-change.ps1`, one row naming the whole seven-file set.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `[catalog-sync] PASS`, 2126 files / 2610 records.
- [x] Phase-boundary audit run - one P1 found and fixed in this phase (see Step Log), no unresolved P0/P1 remaining; the shared-mutable-state trigger fired on the estimator's remembered sample as the plan predicted, and it is what surfaced the P1.

---

## Step Log

- 2026-08-08 - Steps 02.1-02.3 executed in one run; every Verification predicate re-run and green. Same note as Phase 01 Step 01.3 applies to the 02.3 predicate "the declaration line contains `DeviceStatusProvider<BatteryStatus>`": the constructor list is multi-line, so the supertype sits on its closing line (`) : DeviceStatusProvider<BatteryStatus> {`) rather than on the `class` line. Both tokens verified present.
- 2026-08-08 - Two deliberate additions past the literal prompt text, both required by the strategic spec rather than invented here: (a) `GetBatteryStatusUseCase.read()` wraps the sticky-intent and `BatteryManager` reads in `withContext(Dispatchers.IO)` - strategic §3.2 puts every device read off the main thread, and Phase 01's `PlatformDeviceMemorySource` set the precedent; (b) `DischargeRateBatteryRuntimeEstimator.estimateRemainingMillis` is `@Synchronized` - the remembered sample is shared mutable state and the phase's own Done Criteria names that trigger.
- 2026-08-08 - Architecture check on `GetBatteryStatusUseCase` importing `android.content`/`android.os`: not a layer violation by this repo's standard - 77 of the existing `domain/usecase/**` files already import `android.*`. Recorded so the phase-boundary audit does not re-open it.
- 2026-08-08 - Step 02.4's test run was blocked by a defect outside this ticket: `StreamsViewModelAutoGridTest.kt` did not compile (`No value passed for parameter 'observeStreamPlayOutcomes'`), left behind when S1502 added that constructor parameter to `StreamsViewModel` without updating its only test. A non-compiling test file fails `compileStandardDebugUnitTestKotlin` for the whole source set, so no test in the project could run, this one included. Fixed inline rather than parked - CLAUDE.md §3.1 sends trivial fixes inline and this was three lines (import, mocked use case, constructor argument), mirroring the `observeStreamSources` mock already in that file.
- 2026-08-08 - Step 02.4 verification deviated from the literal `.\a.ps1 fu` in one way, deliberately: the run was scoped with `check-standard-fast.ps1 -Mode Unit -Tests "*DischargeRateBatteryRuntimeEstimatorTest*"`, which is the same script `fu` invokes, filtered to this class. The full suite has a standing habit of exhausting memory partway and truncating its own report, which would leave the predicate unanswerable. `expected: DischargeRateBatteryRuntimeEstimatorTest passes | actual: tests=5 skipped=0 failures=0 errors=0` from `app_v2/build/test-results/testStandardDebugUnitTest/TEST-com.sza.fastmediasorter.data.repository.DischargeRateBatteryRuntimeEstimatorTest.xml`.
- 2026-08-08 - Phase-boundary audit (Layer 1 architecture, Layer 2 coroutine/concurrency, Layer 3 ownership; Layer 4 not applicable - no Room surface). **One P1, fixed in this phase, not deferred.**
  - **P1 - the estimate was measured against the refresh interval instead of against the drop.** `observe()` re-anchored `lastPercent`/`lastTimestampMillis` on *every* read. The gadget reads every ten seconds while the charge level moves every few minutes, so the vast majority of reads saw an unchanged level and reset the clock - and when the level finally fell by one percent, the rate was computed as one percent per one refresh interval. At a ten-second period that reports roughly seventeen minutes of runtime on a full battery. This is exactly the §7 risk "the remaining-charge estimate lies", rated high probability with the worst consequence in the set, and no screenshot would have falsified it. Fixed by anchoring the sample to the last level *change*: an unchanged reading now returns early and leaves the baseline alone. Regression test added (`unchanged readings between two drops do not shorten the measured rate`) - it fails on the previous implementation and passes on the current one.
  - P3, not acted on: `estimateRemainingMillis` both reads and mutates, so any future second consumer would silently disturb the battery gadget's sample. Acceptable while the seam has exactly one caller, and the strategic §5.3 replaceability requirement is satisfied by the interface rather than by statelessness.
  - P3, not acted on: `BatteryRuntimeEstimator` sits in `domain/repository/` without being a repository. The tactical plan fixed that path and `domain/` already hosts non-repository seams elsewhere (`domain/detector/`, `domain/streams/`); moving it would churn the plan for no behavioural gain.
  - Clean: the singleton holds no `Context` and no listener, so there is nothing to unregister and no leak surface; every mutation of the shared sample is inside the one `@Synchronized` entry point; the platform reads sit on `Dispatchers.IO`.
- 2026-08-08 - `CODE.LOCK` held across the phase's contiguous edit run rather than re-acquired per step (`lock-status.ps1 -Name Code -Queue` reported an empty queue, so no sibling session was starved), and released by the batched `post-change.ps1 -Files` closure at the phase boundary. Closure batched per CLAUDE.md §12 journaling granularity - one dev-log row for the phase, not one per file.

---

## Handoff Notes to Next Phase

- `BatteryRuntimeEstimator` is the replaceable seam strategic §5.3 asks for; a future estimator swaps the binding in `RepositoryModule.kt` and touches nothing else.
- The battery read registers no receiver, so it adds nothing to unregister on detach.

---

## Rollback Plan

Revert phase commit(s) - new shared code plus two Hilt bindings, no data migration and no user-facing surface changed.
