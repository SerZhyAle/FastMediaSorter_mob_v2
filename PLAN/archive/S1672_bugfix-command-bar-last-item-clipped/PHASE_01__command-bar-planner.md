# Phase 01 - Command-bar planner (pure)

**Strategic spec:** [`../S1672_bugfix-command-bar-last-item-clipped.md`](../S1672_bugfix-command-bar-last-item-clipped.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-08-15

---

## Objective

Add the Android-free planner that decides the command bar's label mode and its visible/overflow split, plus the unit test covering both stages. No caller changes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - spec has no §6 section.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandBarPlanner.kt` | New | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandBarPlannerTest.kt` | New | ≤ 170 |

> No `res/layout*` file is touched in this phase, so CLAUDE.md Rule 11 landscape parity does not apply here.

---

## Steps

### Step 01.1 - Write the pure planner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandBarPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MainCommandBarPlanner` as a Kotlin `object` in package `com.sza.fastmediasorter.ui.main.helpers`, following the shape of the neighbouring `MainCollapsedChipPlacementPlanner`: no Android type may appear in the file.
>
> Declare `data class CommandCandidate(val viewId: Int, val labelledWidthPx: Int, val iconOnlyWidthPx: Int)` and `data class CommandBarPlan(val labelsVisible: Boolean, val visibleIds: List<Int>, val overflowIds: List<Int>)`.
>
> Declare `fun plan(availableWidthPx: Int, reservedWidthPx: Int, labelsPreferred: Boolean, candidates: List<CommandCandidate>): CommandBarPlan`. `candidates` arrive in left-to-right layout order.
>
> The decision runs in two stages. Stage one: when `labelsPreferred` is true and the sum of `labelledWidthPx` fits the budget (`availableWidthPx - reservedWidthPx`), return every candidate visible with `labelsVisible = true`. Stage two: otherwise set `labelsVisible = false`, switch to `iconOnlyWidthPx`, and if the icon-only sum fits, return every candidate visible. Only when icon-only still does not fit, walk the candidates left to right accumulating `iconOnlyWidthPx` and stop at the first candidate that does not fit; that candidate and every candidate after it go to `overflowIds`, which is how removal happens from the right edge.
>
> Edge cases: an empty candidate list returns empty buckets with `labelsVisible = labelsPreferred`; a budget of zero or less sends every candidate to `overflowIds`. Both returned lists preserve the caller's order. Keep the KDoc to the invariants a reader cannot infer - the right-edge removal rule and its owner decision, and the reason the walk stops at the first miss instead of best-fitting.

**Why:**

The strategic spec's root cause is that the current guard removes a fixed number of buttons - exactly one - rather than as many as the shortfall requires, and never re-measures; owner decision 1 replaces it with removal from the right edge in layout order, and owner decision 3 requires the icon-only rollback to be tried before any button is removed.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandBarPlanner.kt` exists.
- `Grep` - `object MainCommandBarPlanner` matches exactly once in that file.
- `Grep` - `fun plan(` present in that file.
- `Grep` - `data class CommandCandidate` and `data class CommandBarPlan` each present once.
- `Grep` - `android\.` returns zero hits in that file (purity).
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - MainCommandBarPlanner.kt created (67 LOC): two-stage plan(), icon rollback then right-edge prefix cut. Greps: object 1, fun plan 1, data classes 1+1, android. 0, Log.d 0.

---

### Step 01.2 - Unit-test both stages

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandBarPlannerTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `MainCommandBarPlannerTest` next to `MainCollapsedChipPlacementPlannerTest`, using plain JUnit assertions and no Robolectric.
>
> Cover, one test each: everything fits with labels on, so `labelsVisible` stays true and `overflowIds` is empty; labels do not fit but icon-only does, so `labelsVisible` flips to false and all nine ids stay visible; icon-only also does not fit, so the visible set is the fitting left prefix and the remaining right-hand ids land in `overflowIds`; a narrower budget pushes strictly more ids into overflow than a wider one over the same candidates; `labelsPreferred = false` never returns `labelsVisible = true`; a reserved width large enough to swallow the budget overflows every candidate; an empty candidate list returns empty buckets.
>
> Name each test with the behaviour it pins, and assert on both `visibleIds` and `overflowIds` so an off-by-one in the cut cannot pass.

**Why:**

The strategic spec's §4 verification list requires a unit test proving the planner returns a fitting prefix rather than a fixed count, and a second one proving the icon-only stage is tried before any button is removed; `MainLayoutChromeManager` has no tests at all today, which is how the fixed-victim rule survived a rotation-only review.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainCommandBarPlannerTest.kt` exists.
- `Grep` - `class MainCommandBarPlannerTest` matches exactly once.
- `Grep` - `@Test` returns at least 7 hits in that file.
- Run `.\a.ps1 fu` (or the scoped `--tests "*MainCommandBarPlannerTest*"` run) - every test in the class passes; record `expected: PASS | actual: <result>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Scoped run passed once the sibling session repaired CloudFileOperationHandlerTest: testStandardDebugUnitTest --tests *MainCommandBarPlannerTest* exit 0, TEST-...MainCommandBarPlannerTest.xml reads tests=7 skipped=0 failures=0 errors=0. expected: PASS | actual: PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`MainCommandBarPlanner.plan()` is the single owner of the label-mode and visible/overflow decision. Phase 02 measures widths and applies the returned plan; it must not re-decide either question in the manager.

---

## Rollback Plan

Revert the phase commit - two new files, no caller depends on them yet, no user-facing surface changed.
