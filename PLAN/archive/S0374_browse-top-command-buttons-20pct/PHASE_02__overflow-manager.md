# PHASE_02 - BrowseCommandOverflowManager (View-bound shell)

**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Status:** Pending
**Depends on:** PHASE_01

## Goal

A View-bound manager that measures the top bar, calls `allocateCommandBar`, force-hides overflowed buttons, and exposes the overflow set to `ResourceOpsMenuManager`.

## Steps

### Step 2.1 - Create the manager

Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCommandOverflowManager.kt`.

Constructor: `BrowseCommandOverflowManager(private val binding: ActivityBrowseBinding)`.

Priority-ordered candidate list (highest priority first) of `Pair<viewId, View?>`:
`btnBack, btnSort, btnFilter, btnRefresh, btnToggleView, btnSelectAll, btnDeselectAll, btnPlay, btnPlayRandom, btnMicRecord, btnCreateFolder, btnCreateTextFile, btnCreateDrawing`.
- `btnResourceOps` is NOT a candidate - it is the always-present anchor; reserve its measured width.
- `btnBack` is effectively always-fit (priority 0) but still measured.

State:
- `private val overflowedIds = mutableSetOf<Int>()`.
- `fun isOverflowed(viewId: Int): Boolean`.
- `var onOverflowChanged: (() -> Unit)? = null` - invoked after the visible/overflow partition changes (focus-chain repair hook).

### Step 2.2 - recompute()

`fun recompute()`:
- Post to `binding.layoutControls` (`layoutControls.post { ... }`) so measurement runs after layout. Re-entrancy guard: a `recomputing` flag, because setting visibility re-triggers layout.
- Eligible candidates = those whose current feature visibility is `VISIBLE` OR currently overflowed-by-this-manager (i.e. we hid them ourselves). Distinguish: a button hidden by feature gating must NOT be re-shown. Track ownership: only ids in `overflowedIds` were hidden by us; any other `GONE` button is feature-hidden and excluded.
- To measure a button we hid (now GONE), temporarily it still has a known width: cache the last measured VISIBLE width per id in a `mutableMapOf<Int,Int>()`; if never measured, briefly set `VISIBLE`, measure, then let allocation decide. Prefer caching the width captured on the last VISIBLE pass to avoid flicker.
- `availableWidthPx = binding.layoutControls.width - paddingStart - paddingEnd`.
- `reservedWidthPx = btnResourceOps.measuredWidth` (or its cached width) + its horizontal margins.
- Build `List<CommandSlot>` from eligible candidates with measured widths + priority = index in the candidate list.
- Call `allocateCommandBar(...)`.
- Apply: for each eligible candidate, set `isVisible = id in allocation.visibleIds`. Update `overflowedIds` to `allocation.overflowIds`.
- If the overflow set changed vs previous, invoke `onOverflowChanged?.invoke()`.

WHY-comment: width is read from `layoutControls` (the constrained container), not from any scroll view - the HSV is gone after PHASE_03.

### Step 2.3 - Measurement helper

Add `private fun measuredWidthOf(view: View): Int` using `view.measuredWidth` when `>0`, else measure with `View.MeasureSpec.UNSPECIFIED`, plus `MarginLayoutParams` horizontal margins.

**Verification:**
- `Grep` `class BrowseCommandOverflowManager` → expected: 1 | actual: record.
- `Grep` `allocateCommandBar` in `BrowseCommandOverflowManager.kt` → expected: ≥1 | actual: record.
- `Grep` `btnResourceOps` in `BrowseCommandOverflowManager.kt` → expected: ≥1 (reserved width) | actual: record.
- Compile gate deferred to PHASE_05 build (manager is wired there).

## Phase Done Criteria

- [ ] `BrowseCommandOverflowManager.kt` exists with `recompute()`, `isOverflowed()`, `onOverflowChanged`.
- [ ] Reserves `btnResourceOps` width; never adds it to candidates.
- [ ] Re-entrancy guard present (visibility writes re-trigger layout).
- [ ] No reference to `topCommandScroll` (removed in PHASE_03).
- [ ] Catalog sync run.
