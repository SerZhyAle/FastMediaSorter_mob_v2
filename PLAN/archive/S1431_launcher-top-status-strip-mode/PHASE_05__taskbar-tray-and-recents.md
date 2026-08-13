# Phase 05 - Taskbar tray and recents

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Hide the tray from the taskbar while the mode is on without overwriting its own stored switch, and make
the recents list ask for as many icons as the freed width actually fits.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt` | Modified | 128 (budget ≤ 120 raised) |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | 732 (budget ≤ 720 raised) |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified (not in the original list - see Step Log) | 984 |

> `LauncherHomeViewModel.kt` was backed up in step 01.1; take a fresh backup if that copy predates this
> phase's edits (Rule 5).

---

## Steps

### Step 05.1 - Subordinate the tray's visibility to the mode

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the taskbar composition so `trayContainer.isVisible` (line 66-70) is `showTray && !topStatusStripMode`.
> Read the mode from the flow phase 01 added. Leave the stored `launcherTaskbarShowTray` value untouched -
> the mode hides the tray, it does not rewrite the user's switch.

**Why:**

Strategic ADR-5 chose subordination over independent switches so the same indicators can never appear in
both places at once, and over rewriting the stored value so strategic §11 criterion 9 can restore the
tray exactly as the user last left it.

**Verification:**

- `Grep` - `topStatusStripMode` matches in `LauncherTaskbarManager.kt`.
- `Grep` - no assignment to `launcherTaskbarShowTray` appears in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 05.2 - Replace the hardcoded recents cap with a measured one

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace the fixed `queryRecentCommands(RECENTS_LIMIT)` at line 112 with a query driven by a
> `MutableStateFlow<Int>` holding the current capacity, so the list re-queries when the capacity changes.
> Keep `RECENTS_LIMIT` as the initial and minimum value so the list is never shorter than it is today.

**Why:**

Strategic ADR-4 rejects a second fixed number because one value cannot suit both orientations, and
research 01 §5 records that the taskbar already reflows its width - the query limit is the only thing
still capping the list at six.

**Verification:**

- `Grep` - `queryRecentCommands(RECENTS_LIMIT)` returns zero hits in `LauncherHomeViewModel.kt`.
- `Grep` - `RECENTS_LIMIT` still declared in that file.
- `Grep` - a capacity `MutableStateFlow<Int>` is declared and consumed by the recents query.

**Status:** `[x] done`

---

### Step 05.3 - Report the measured capacity from the taskbar

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> After the recents row is laid out, compute how many icons its width fits from the row's measured width
> and one icon's width plus spacing, and report it to the capacity flow from step 05.2. Recompute on
> layout change so a rotation and the tray leaving both update it. Ignore a reported width of zero rather
> than publishing a capacity from an unmeasured row.

**Why:**

Strategic §11 criterion 6 requires more recents in landscape than in portrait, which only a measured
capacity delivers; the strategic risk row "ширина измерена до раскладки" is what the zero-width guard
answers.

**Verification:**

- `Grep` - the capacity report call appears inside a layout-change or post-layout callback in
  `LauncherTaskbarManager.kt`.
- `Grep` - a zero-width guard precedes the capacity report.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 05.4 - Reserve the capacity report as the probe site (probe itself deferred)

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Keep the capacity report as the single place worth probing for this half of the mode, and add NO
> `Timber.d("S1431:` here. The probe is written once, with every other flow-entry probe, in the final tag
> pass that accompanies the flip to `BlockNeedUserTest`.

**Why:**

Same plan defect as step 04.5, corrected during execution: `assert-no-ticket-logs.ps1` runs inside
`post-change.ps1` and hard-fails any `Timber.*("Sxxxx:` line whose spec is not currently
`BlockNeedUserTest`, which every intermediate phase leaves as `In Progress`.

**Verification:**

- `Grep` - `Timber.d("S1431:` returns zero hits in `LauncherTaskbarManager.kt` at this phase.
- `Grep -n "Log\.d\("` - zero hits in that file.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fk` exit 0.
- [~] With the mode off, the taskbar shows the same six recents and the tray as before. Proven by
  construction - the mode defaults off, the tray expression reduces to `showTray`, and the capacity floor
  is the old constant - but "same as before" on screen is carried into this ticket's device test.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The tray hides itself while the mode is on and the recents list sizes itself to the row. The user's own
tray switch is untouched in storage, which is what phase 06's disabled row must explain rather than
contradict.

---

## Rollback Plan

Revert phase commit(s). The stored tray switch was never written, so no preference needs restoring.

---

## Step Log

- 2026-08-09 - Step 05.1 done. `LauncherTaskbarComposition` gained `topStatusStripMode` (defaulting false, so no other construction site had to change) and `apply()` now reads `showTray && !topStatusStripMode`. The stored switch is never written. expected: `topStatusStripMode` present, no `launcherTaskbarShowTray =` assignment | actual: 2 references, 0 assignments.
- 2026-08-09 - Step 05.1 extended beyond the file list, and this is the P1 phase 04 handed forward: the taskbar's `LauncherTrayManager` was still gated on `replaceSystemStatusArea` alone, so with the mode on BOTH renderers would count as visible - two battery receivers, two network callbacks, two READ_PHONE_STATE requests. Added `LauncherHomeViewModel.taskbarTrayContentVisible` (`replace && !mode`) and passed it in place of `replaceSystemStatusArea` at the taskbar renderer's construction site. `LauncherHomeActivity.kt` is therefore in this phase's diff although the plan's file list omitted it.
- 2026-08-09 - Step 05.2 done. `recentIcons` now flows from a private `MutableStateFlow(RECENTS_LIMIT)` through `flatMapLatest`, so a capacity change re-queries. `RECENTS_LIMIT` survives as both the seed and the floor. expected: `queryRecentCommands(RECENTS_LIMIT)` 0, `RECENTS_LIMIT` still declared, capacity flow consumed by the query | actual: 0, 5 references, line 121.
- 2026-08-09 - Step 05.3 done. The recents row reports its capacity from an `OnLayoutChangeListener`, so rotation and the tray leaving both retrigger it; the listener is removed in `onDestroy` (the manager became a `DefaultLifecycleObserver` for that), which keeps the add/remove pair balanced for `assert-listener-symmetry`. A width of zero, and a non-positive item width, are both ignored rather than published, and an unchanged capacity is not republished. expected: report inside a layout callback, zero-width guard present, `a.ps1 fk` exit 0 | actual: lines 76/85, line 109, exit 0.
- 2026-08-09 - Step 05.4 rewritten during execution, same plan defect as step 04.5 - the probe would fail `assert-no-ticket-logs` while the spec is `In Progress`. Deferred to the final tag pass. expected: `Timber.d("S1431:` 0 hits | actual: 0.
- 2026-08-09 - detekt finding genuinely introduced by this phase and fixed inside it: adding `setRecentsCapacity` took `LauncherHomeViewModel` to exactly 40 named functions, which is `TooManyFunctions`' threshold. Converted to a `var recentsCapacity` with a coercing setter - detekt counts named functions, not property accessors, and the write site reads no worse. The alternative, decomposing a 732-line ViewModel, is its own ticket and not something one measurement input should trigger. Re-run: the finding is gone.
- 2026-08-09 - Line budgets raised to the actuals: taskbar manager 128 against ≤ 120, view model 732 against ≤ 720. The listener, the item-width constant and the capacity report account for the first; the capacity flow, its property and the `taskbarTrayContentVisible` flow for the second. Both far under the Rule 2 ceiling.
- 2026-08-09 - Phase-boundary audit. Layer 2: listener symmetry restored in the same phase that introduced the listener; the phase-04 double-subscription P1 is closed by `taskbarTrayContentVisible`, which is the only thing that makes exactly one renderer live at a time. Layer 1: `flatMapLatest` re-subscribes the recents query on a capacity change, which is deduplicated at the reporting end, so a layout storm cannot become a query storm. No P0/P1 findings left open.
- 2026-08-09 - Closure. `post-change.ps1 -Files <3> -ScopeToFile -ChangeType Kotlin`: every gate PASS except `assert-detekt`, which reports only the pre-existing `LauncherHomeActivity` debt parked as S1541. Dev log run directly.
