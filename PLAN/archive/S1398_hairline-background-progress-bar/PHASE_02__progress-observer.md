# Phase 02 - Single background-work observer

**Strategic spec:** [`../S1398_hairline-background-progress-bar.md`](../S1398_hairline-background-progress-bar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Introduce the app-scoped observer that turns the existing transfer work state into `BackgroundOperationBarState`, so any screen can read background-work progress without knowing about WorkManager or about the Browse screen.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `BackgroundOperationBarState` from Phase 01 compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationTrackManager.kt` | New | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationTrackManagerTest.kt` | New | ≤ 120 |

> No new Hilt `@Module` and no new scope: the class is `@Singleton` with an `@Inject constructor`, which the existing `SingletonComponent` satisfies without a binding declaration.

---

## Steps

### Step 02.1 - Add the track manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationTrackManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `BackgroundOperationTrackManager` as a `@Singleton` class with an `@Inject constructor(private val browseTransferCoordinator: BrowseFileTransferCoordinator)`. Expose one public method, `fun barState(): Flow<BackgroundOperationBarState>`, that maps `browseTransferCoordinator.activeTransferFlow()`:
>
> - `state.isActive == false` maps to `BackgroundOperationBarState.Hidden`;
> - active with `state.progress == null` maps to `Indeterminate`;
> - active with a progress snapshot maps through `transferBytePercentOrNull(completedOperationBytes, totalOperationBytes)`: a non-null percent becomes `Determinate(percent)`, and `null` becomes `Indeterminate`.
>
> Apply `distinctUntilChanged()` to the mapped flow. Do not call `transferOverallPercent`, do not call `TransferProgressReporter`, and do not add any polling, timer or `WorkManager` query of your own - the coordinator flow is the only input.

**Why:**

Strategic §6.5 requires the bar to read the same nullable form of the percent formula the progress dialog reads, so that criterion §11.5 - the bar's percent equals the dialog's throughout the operation - holds at the one moment it can break, namely while the total size is still unknown. ADR-4 and §11.6 forbid a second source of ticks, which is why the existing coordinator flow is subscribed rather than re-queried; `distinctUntilChanged` then keeps byte-level ticks that do not move the whole percent from re-rendering the view.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationTrackManager.kt` exists.
- `Grep` - `class BackgroundOperationTrackManager` matches exactly once.
- `Grep` - `@Singleton` and `@Inject constructor` each present.
- `Grep` - `fun barState(): Flow<BackgroundOperationBarState>` present.
- `Grep` - `transferBytePercentOrNull` present.
- `Grep` - `transferOverallPercent` returns zero hits in the file.
- `Grep` - `WorkManager`, `delay(`, `Timer` each return zero hits in the file.
- `Grep` - `distinctUntilChanged` present.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 12/12 PASS. Files: ui/common/backgroundop/BackgroundOperationTrackManager.kt (New, 47 LOC). Written in a zero-`return` `when` form rather than guard clauses, because detekt's `ReturnCount` caps a function at two and the natural shape here needs three. First run of the `WorkManager` zero-hit predicate failed on the class's own KDoc, which named the scheduler in prose; the doc was reworded rather than the predicate weakened, since the predicate exists to prove no second query path was added. Dev log recorded.

---

### Step 02.2 - Unit-test the state mapping

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationTrackManagerTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a unit test covering the four mapping branches of `barState()` with a fake coordinator flow: inactive maps to `Hidden`; active without a snapshot maps to `Indeterminate`; active with `totalOperationBytes == 0` maps to `Indeterminate`; active with known totals maps to `Determinate` carrying exactly the value `transferBytePercentOrNull` returns for the same inputs. Assert the last case against the function's own result rather than a literal, so the test cannot drift from the formula. Run the class alone rather than the full suite:
>
> ```powershell
> pwsh -NoProfile -File ./a.ps1 fu
> ```

**Why:**

Criterion §11.5 - the bar and the dialog never disagree - is a claim about a pure mapping, so it is provable statically instead of only on device, and the branch that actually breaks it is the unknown-total one that no short device test reliably reaches.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationTrackManagerTest.kt` exists.
- `Grep` - four `@Test` annotations present.
- `Grep` - `transferBytePercentOrNull` present in the assertion for the determinate case.
- Unit run for this class reports PASS with a fresh timestamp on its results XML.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 4/4 PASS. Files: app_v2/src/test/.../BackgroundOperationTrackManagerTest.kt (New, 97 LOC), four `@Test` methods, mockk stubbing `activeTransferFlow()`. Run scoped with `check-standard-fast.ps1 -Mode Unit -Tests "*BackgroundOperationTrackManagerTest"` rather than the whole suite, exit 0. Exit code alone was not taken as proof: `TEST-..BackgroundOperationTrackManagerTest.xml` reads `tests="4" skipped="0" failures="0" errors="0"` with a timestamp 11 s before the check, so the class genuinely ran instead of being filtered out. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - the scoped unit run compiles both `compileStandardDebugKotlin` and `compileStandardDebugUnitTestKotlin` and then runs the class, exit 0. Re-run after the detekt reformat: exit 0 again.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1`. First closure attempt exited 1 on `detekt-gate` with two findings in the changed files - a missing newline after `->` in the `when` entry, and `kotlinx.coroutines.flow.flowOf` imported before `.first` in the test. Both fixed; because the gates run before the mutating steps, the failed attempt wrote no changelog row and the re-run produced exactly one.
- [x] Phase-boundary audit run - no P0/P1 findings. Layer 1: `NounVerbManager` naming holds, the class carries only a mapping, 47 LOC. Layer 2 (coroutine/Flow): the `map` does arithmetic only and adds no disk or parse work to the collector's thread, so it does not reintroduce the StrictMode read that `activeTransferFlow`'s own S1230 comment warns about. Layer 3: no listener registered, nothing to unregister. Layer 4 not applicable - no Room surface. One P3 finding, corrected in the handoff below: the phase was planned believing a `@Singleton` would share one upstream subscription across collectors, which a cold flow does not do.

---

## Handoff Notes to Next Phase

`barState()` is the single entry point for background-work progress; Phase 03 collects it and passes each value straight to `BackgroundOperationBarView.render`, adding no logic of its own.

Correction to an assumption this phase was planned on: the manager is `@Singleton`, but `barState()` returns a **cold** flow, so each collector builds its own chain onto `activeTransferFlow()` rather than sharing one upstream subscription. That is bounded in practice - `collectOnLifecycle` stops collection at STOPPED, so only the foreground Activity holds one - and it needs no fix, but Phase 03 must not be written as though the subscription were shared.

---

## Rollback Plan

Revert phase commit(s) - one new production class and one new test, no caller yet, no data migration.
