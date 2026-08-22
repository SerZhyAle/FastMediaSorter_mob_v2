# Phase 02 - Chrome-manager wiring

**Strategic spec:** [`../S1672_bugfix-command-bar-last-item-clipped.md`](../S1672_bugfix-command-bar-last-item-clipped.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** -
**Completed:** 2026-08-15

---

## Objective

Replace the fixed single-victim rule in `MainLayoutChromeManager.applyControlBarOverflow()` with a measure-plan-apply cycle driven by `MainCommandBarPlanner`, and publish the resulting overflow set to the host.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `MainCommandBarPlanner.plan()` exists and its test passes.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt` | Modified | ≤ 400 |

> The file is 283 LOC - under the 500-LOC backup threshold, so no backup sub-step is required. No `res/layout*` file is touched, so CLAUDE.md Rule 11 landscape parity does not apply.

---

## Steps

### Step 02.1 - Build the candidate list and its anchor reserve

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inside `MainLayoutChromeManager`, add a private list of the bar's overflow candidates in layout order: `btnExit`, `btnAddResource`, `btnFilter`, `btnRefresh`, `btnSettings`, `btnToggleView`, `btnFavorites`, `btnStartPlayer`. The `layoutMainDropdownMenu` wrapper is not a candidate - it is the reserved anchor, like `btnPath`/`btnResourceOps` on the browse bar - and the three ids already listed in `inlineChipIds` stay excluded from the fit sum exactly as today.
>
> Add a private helper that measures one child's width including its horizontal margins with the existing `UNSPECIFIED`/`AT_MOST` spec pair, and use it for both the candidates and the anchor. Measure the anchor even while its wrapper is `GONE`, and always subtract its width from the budget: the anchor can be summoned by an overflow at any moment (Phase 03), and reserving a slot that ends up unused only leaves the bar wider than it needed to be, while not reserving it would clip the bar the moment it appears.

**Why:**

The strategic spec's §2 states that the only place comparing the row against the real screen width is this guard, and that widths are never derived from screen width anywhere else, so the measurement of every candidate has to happen here for any allocation rule to be correct.

**Verification:**

- `Grep` - `btnStartPlayer` and `btnFavorites` both appear in the new candidate list in `MainLayoutChromeManager.kt`.
- `Grep` - `S1443` still explained in the file, and no chip id appears among the candidates. (Predicate corrected during implementation: the rewrite measures a named candidate list instead of walking the bar's children, so chips are excluded by construction and the `inlineChipIds` filter set it replaced is now dead code, removed under Rule 20.)
- `Grep` - `layoutMainDropdownMenu` present in the file (anchor reserve).

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 applied in one pass: candidate list + anchor reserve, two-mode measurement, planner call, plan application with isOverflowed/onOverflowChanged, S1258 heal extracted and extended. Greps: MainCommandBarPlanner.plan 1, fun isOverflowed 1, onControlBarFreeWidth 1, restitchControlBarFocusChain 2, forceLayout 1, S1258 1, S1672 3; btnStartPlayer.visibility=GONE 0, 'sacrifice the last button' 0. File 344 LOC (budget 400). a.ps1 fk exit 0 (compileStandardDebugKotlin executed). Step 02.1 chip predicate corrected in the phase file - chips are now excluded by construction, inlineChipIds removed as dead code.

---

### Step 02.2 - Measure both label modes and call the planner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Rewrite the body of `applyControlBarOverflow()` inside its existing `doOnLayout` block. Reset every candidate to `VISIBLE` first, as the current code does for `btnStartPlayer` alone, so the decision is made against the full set rather than a previous `GONE`.
>
> Measure each candidate twice when the layout is the wide one (labels on): once as laid out, then with every label temporarily set to `null` for the icon-only width, restoring the labels afterwards. In the narrow layout the two widths are the same measurement, because labels are already off.
>
> Feed `MainCommandBarPlanner.plan()` the candidate list, the bar's inner width (`width - paddingStart - paddingEnd`), the anchor reserve, and `labelsPreferred = config.isWideLayout()`. Keep the existing early return when the inner width is not positive.

**Why:**

The strategic spec's §2 records that the wide layout turns labels on for every button at once, which is exactly the condition where the required width peaks, so the planner cannot choose the icon-only stage without a real measurement of that mode.

**Verification:**

- `Grep` - `MainCommandBarPlanner.plan(` matches exactly once in `MainLayoutChromeManager.kt`.
- `Grep` - `isWideLayout()` present in `applyControlBarOverflow`'s body.
- `Grep` - `btnStartPlayer.visibility = View.GONE` returns zero hits (the fixed victim is gone).
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 applied in one pass: candidate list + anchor reserve, two-mode measurement, planner call, plan application with isOverflowed/onOverflowChanged, S1258 heal extracted and extended. Greps: MainCommandBarPlanner.plan 1, fun isOverflowed 1, onControlBarFreeWidth 1, restitchControlBarFocusChain 2, forceLayout 1, S1258 1, S1672 3; btnStartPlayer.visibility=GONE 0, 'sacrifice the last button' 0. File 344 LOC (budget 400). a.ps1 fk exit 0 (compileStandardDebugKotlin executed). Step 02.1 chip predicate corrected in the phase file - chips are now excluded by construction, inlineChipIds removed as dead code.

---

### Step 02.3 - Apply the plan and publish the overflow set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Apply the returned plan: set each candidate `VISIBLE` when its id is in `visibleIds` and `GONE` otherwise, and set the labels of all candidates from `plan.labelsVisible` using the same string resources `updateToolbarButtonLabels` already uses. Make `updateToolbarButtonLabels(config)` set its labels and then delegate the final label state to `applyControlBarOverflow()` so exactly one place decides it.
>
> Expose the result: add `fun isOverflowed(viewId: Int): Boolean` backed by the ids the plan pushed off the bar, and add a constructor callback `onOverflowChanged: () -> Unit = {}` fired only when the overflow set actually changes, following `BrowseCommandOverflowManager.onOverflowChanged`.
>
> Keep the two existing contracts intact: `onControlBarFreeWidth` still reports zero whenever anything overflowed and the leftover width otherwise, and `restitchControlBarFocusChain()` still runs at the end of the block so a hidden button leaves the focus chain.

**Why:**

Owner decision 2 requires an evicted command to remain reachable through the "⋮" menu, which cannot be built without the manager publishing which commands were evicted; the free-width contract must survive untouched because S1443's chip mechanic reads it to keep chips out of a bar that is already overflowing.

**Verification:**

- `Grep` - `fun isOverflowed(` matches exactly once in `MainLayoutChromeManager.kt`.
- `Grep` - `onOverflowChanged` present in the file.
- `Grep` - `onControlBarFreeWidth(` still present.
- `Grep` - `restitchControlBarFocusChain()` still called from `applyControlBarOverflow`.
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 applied in one pass: candidate list + anchor reserve, two-mode measurement, planner call, plan application with isOverflowed/onOverflowChanged, S1258 heal extracted and extended. Greps: MainCommandBarPlanner.plan 1, fun isOverflowed 1, onControlBarFreeWidth 1, restitchControlBarFocusChain 2, forceLayout 1, S1258 1, S1672 3; btnStartPlayer.visibility=GONE 0, 'sacrifice the last button' 0. File 344 LOC (budget 400). a.ps1 fk exit 0 (compileStandardDebugKotlin executed). Step 02.1 chip predicate corrected in the phase file - chips are now excluded by construction, inlineChipIds removed as dead code.

---

### Step 02.4 - Keep the S1258 heal and re-check the file's comments

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Keep the posted `forceLayout()` + `requestLayout()` heal that follows the probe measurement, and extend it to cover the second measuring pass added in step 02.2. Update the `applyControlBarOverflow` KDoc: the 2026-07-06 single-victim directive it currently cites is superseded by owner decision 1, and the KDoc must say what the new rule is rather than leave the old rationale in place.

**Why:**

CLAUDE.md Rule 8 makes an existing comment a requirement rather than decoration, and the S1258 note records that a probe measurement leaves labels riding several pixels high unless the posted heal runs after the frame settles - a second probe pass doubles that exposure.

**Verification:**

- `Grep` - `forceLayout()` still present in `applyControlBarOverflow`'s posted block.
- `Grep` - `S1258` comment retained in the file.
- `Grep` - `sacrifice the last button` returns zero hits (stale rationale removed).
- `Grep` - `S1672` present in the updated KDoc.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 02 applied in one pass: candidate list + anchor reserve, two-mode measurement, planner call, plan application with isOverflowed/onOverflowChanged, S1258 heal extracted and extended. Greps: MainCommandBarPlanner.plan 1, fun isOverflowed 1, onControlBarFreeWidth 1, restitchControlBarFocusChain 2, forceLayout 1, S1258 1, S1672 3; btnStartPlayer.visibility=GONE 0, 'sacrifice the last button' 0. File 344 LOC (budget 400). a.ps1 fk exit 0 (compileStandardDebugKotlin executed). Step 02.1 chip predicate corrected in the phase file - chips are now excluded by construction, inlineChipIds removed as dead code.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The bar no longer clips: every command either sits on the bar or is reported by `isOverflowed()`. Until Phase 03 lands, an evicted command is unreachable, so the two phases ship together.

---

## Rollback Plan

Revert the phase commit - one modified file, no persisted state and no schema involved.
