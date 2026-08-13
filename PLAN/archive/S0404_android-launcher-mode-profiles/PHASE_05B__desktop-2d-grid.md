# Phase 05B - Desktop 2D Grid

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06, 07
**Steps done:** 5 / 5
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Why this phase exists (inserted 2026-07-17, after Phase 05 was Done)

Phase 04 rendered the desktop with `RecyclerView` + `GridLayoutManager`. That was wrong, and two audits found it from opposite ends:

- The **phases 01-04 audit** found `LauncherCell.spanH` had no framework mechanism behind it - `GridLayoutManager` supports only HORIZONTAL spans - so every planned `2×2` gadget would render one row tall. It recorded a workaround (force the item height to `cellSizePx × spanH`) and handed it to Phase 06.
- The **Phase 06 discovery sweep** then verified that workaround and found it incomplete on four counts: the adapter has no access to the RecyclerView width or span count; a rotation changes the column count but never rebinds, leaving stale heights; a tall gadget makes its row taller without stretching its 1-row siblings, so they get a dead gap beneath them; and `rowIndex`/`colIndex` stay ignored regardless.

The root cause is not `spanH`. It is that **the persistence model describes a 2D canvas and the renderer is a linear flow**. `rowIndex`, `colIndex`, `spanW`, `spanH` have been written to Room since Phase 02 and never reached the screen. Phase 07 (drag a shortcut to a position, strategic §3.3) needs true 2D anyway, so the workaround would be paid for twice.

Owner decision 2026-07-17: **true 2D**. See ADR-9. This phase replaces the renderer; the data, codec and command layers (phases 01-03) are untouched, and Phase 05's taskbar/Start menu are untouched.

---

## Prerequisites

- [x] Phase 04 is ✅ Done.
- [x] CODE.LOCK acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherDesktopLayout.kt` | New (the 2D ViewGroup) | ≤ 190 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | Modified (rows/placement helpers) | +40 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | New (was `LauncherCellAdapter`) | ≤ 170 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellAdapter.kt` | **Deleted** | -140 |
| `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml` | Modified (RecyclerView -> ScrollView + LauncherDesktopLayout) | ~+8 |
| `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml` | Modified (same) | ~+8 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified (drop LayoutManager/SpanSizeLookup wiring) | ~-25 |

> `item_launcher_cell_shortcut.xml` and `item_launcher_cell_gadget.xml` are REUSED as-is - they are inflated by the binder instead of a ViewHolder. Do not rewrite them; only their root `layout_height` changes (step 05B.3).

---

## Steps

### Step 05B.1 - Geometry: rows, placement, cell rect

**Files:** `ui/launcher/grid/LauncherGridGeometry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `LauncherGridGeometry` today has exactly three functions (verbatim):
> ```kotlin
> fun columns(availableWidthDp: Float, densityFactor: Float): Int
> fun cellSizePx(availableWidthPx: Int, columns: Int): Int   // availableWidthPx / columns
> fun spanFor(cell: LauncherCell, columns: Int): Int          // cell.spanW.coerceIn(1, columns)
> ```
> `cellSizePx` currently has ZERO callers (the audit flagged it as dead weight pending exactly this phase). Keep `columns` and `cellSizePx` unchanged. **Delete `spanFor`** - it exists only to feed `GridLayoutManager.SpanSizeLookup`, which this phase removes; its clamping moves into `boundsFor` below.
>
> Add, with KDoc:
> - `fun rowsFor(cells: List<LauncherCell>): Int` = `1 + max(cell.rowIndex + cell.spanH - 1)` over the list, min 1, so the canvas is exactly tall enough for the lowest occupied row. Empty list -> 1.
> - `data class CellBounds(val left: Int, val top: Int, val width: Int, val height: Int)`.
> - `fun boundsFor(cell: LauncherCell, cellSize: Int, columns: Int): CellBounds` - `spanW` clamped to `1..columns`; `colIndex` clamped to `0..(columns - spanW)` so a cell saved for a wider layout still lands on-canvas instead of off the right edge (this is REAL: the density factor and rotation both change the column count under a persisted layout, and `spanW`/`colIndex` were saved against the old one). `spanH` clamped to `>= 1`. Returns pixel rect.
>
> No Android imports beyond what is already there - this object stays a pure-Kotlin unit-testable helper.

**Verification:**

- `Grep` - `fun rowsFor` and `fun boundsFor` match once each in the file; `fun spanFor` returns ZERO hits repo-wide (deleted, no orphan callers).
- Read `boundsFor` and confirm both clamps are present (`spanW` against `columns`, `colIndex` against `columns - spanW`).

**Status:** `[x]` done

---

### Step 05B.2 - LauncherDesktopLayout (the 2D ViewGroup)

**Files:** `ui/launcher/grid/LauncherDesktopLayout.kt`
**Depends on:** Step 05B.1

**Prompt for developer:**

> New `class LauncherDesktopLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr)`.
>
> State: `var columns: Int = LauncherGridGeometry.MIN_COLUMNS` and `var rows: Int = 1`, each with a setter that `requestLayout()`s **only when the value actually changed** (an unconditional requestLayout in a setter called from a Flow collector is a layout loop).
>
> `LayoutParams`: `class CellLayoutParams(val row: Int, val col: Int, val spanW: Int, val spanH: Int) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)`. Override `generateDefaultLayoutParams()` and `checkLayoutParams()` accordingly. The binder attaches children WITH these params, so the layout never has to know what a cell means.
>
> `onMeasure`: width = `MeasureSpec.getSize(widthMeasureSpec)` (the layout always fills its parent's width - it lives in a ScrollView, so only height is unbounded). `cellSize = LauncherGridGeometry.cellSizePx(width - paddingLeft - paddingRight, columns)`. For each child, read its `CellLayoutParams`, compute `LauncherGridGeometry.boundsFor(...)` and call `child.measure(MeasureSpec.makeMeasureSpec(bounds.width, EXACTLY), MeasureSpec.makeMeasureSpec(bounds.height, EXACTLY))`. `setMeasuredDimension(width, paddingTop + rows * cellSize + paddingBottom)`. Skip `GONE` children.
>
> `onLayout`: for each non-GONE child, recompute bounds the same way and `child.layout(paddingLeft + bounds.left, paddingTop + bounds.top, ..., ...)`.
>
> KDoc must state WHY this is not a RecyclerView (ADR-9, one sentence): the persisted model is a 2D canvas with gaps and vertical spans, which no stock LayoutManager expresses; a desktop is dozens of cells, so recycling buys nothing.
>
> Do NOT add drag handling, empty-cell hit-testing, or edit-mode affordances here - that is Phase 07. This class only measures, lays out, and knows nothing about commands.

**Verification:**

- `Grep` - `class LauncherDesktopLayout` matches once; `class CellLayoutParams` present; `MeasureSpec.EXACTLY` present.
- `Grep` - `RecyclerView` returns ZERO hits in `LauncherDesktopLayout.kt`.
- Read both setters and confirm each guards `requestLayout()` behind a changed-value check.

**Status:** `[x]` done

---

### Step 05B.3 - LauncherCellViewBinder (replaces LauncherCellAdapter)

**Files:** `ui/launcher/grid/LauncherCellViewBinder.kt` (new), `ui/launcher/grid/LauncherCellAdapter.kt` (delete), `res/layout/item_launcher_cell_shortcut.xml`, `res/layout/item_launcher_cell_gadget.xml`
**Depends on:** Step 05B.2

**Prompt for developer:**

> `LauncherCellAdapter` today is a `ListAdapter<LauncherCellUi, ..>` with a `ShortcutViewHolder` + `GadgetViewHolder`, a `gadgetBinder` hook, a `DIFF`, and a `bindUnavailable()` path keyed on `item.visual == null`. Port its RENDERING verbatim into a plain binder and delete the adapter. Preserve exactly:
>
> - `var gadgetBinder: ((LauncherCellUi, FrameLayout) -> Unit)? = null` - Phase 06 consumes it unchanged.
> - The shortcut visual: icon + label from `item.visual`, mode badge from `item.modeBadge`.
> - `bindUnavailable()`: `R.string.launcher_home_cell_unavailable` + `R.drawable.ic_launcher_mode` + `UNAVAILABLE_ALPHA`. Keep the constant and the string; only the trigger changes (see below).
>
> API: `class LauncherCellViewBinder(private val onCellClick: (LauncherCellUi) -> Unit)` with `fun bind(container: LauncherDesktopLayout, cells: List<LauncherCellUi>, columns: Int)`. It removes all views, then for each cell inflates `item_launcher_cell_shortcut` or `item_launcher_cell_gadget` with `LayoutInflater.from(container.context)` (container context = the themed Activity - a MaterialCardView inflated from an unthemed context crashes), fills it, and `addView(view, LauncherDesktopLayout.CellLayoutParams(cell.rowIndex, cell.colIndex, cell.spanW, cell.spanH))`. Set `container.columns` and `container.rows = LauncherGridGeometry.rowsFor(...)` before adding.
>
> `item.visual == null` must NOT keep meaning "broken": `LauncherCellUi.visual` is documented as "null for a GADGET cell (the gadget view draws itself)", so it is null for every gadget, resolvable or not. Branch on `cell.kind` FIRST (`LauncherCellKind.GADGET` -> gadget path, else shortcut), and only inside the shortcut branch treat `visual == null` as unavailable. Phase 06 owns unknown-gadget-key detection.
>
> Both item layouts' root `layout_height` becomes `match_parent` (the parent now measures them `EXACTLY`); `item_launcher_cell_shortcut.xml` root is `wrap_content` today and `minHeight="88dp"` on its inner LinearLayout becomes dead weight - remove it, the cell size is now authoritative. Keep everything else in those layouts.
>
> Rebuilding all views on every emission is deliberate and correct here (dozens of cells, and the desktop only changes when the user edits it) - the diffing `ListAdapter` existed to serve RecyclerView, not the user. Say so in one KDoc line so a later reviewer does not "restore" DiffUtil.

**Verification:**

- `Grep` - `class LauncherCellViewBinder` matches once; `gadgetBinder` present in it.
- `Grep` - `LauncherCellAdapter` returns ZERO hits repo-wide (file deleted, no stale imports).
- `Grep` - `minHeight` returns ZERO hits in `item_launcher_cell_shortcut.xml`.
- Read `bind` and confirm the `cell.kind` branch precedes any `visual == null` check.

**Status:** `[x]` done

---

### Step 05B.4 - Host wiring (activity + both layouts)

**Files:** `ui/launcher/LauncherHomeActivity.kt`, `res/layout/activity_launcher_home.xml`, `res/layout-land/activity_launcher_home.xml`
**Depends on:** Step 05B.3

**Prompt for developer:**

> In BOTH orientation layouts (Rule 11 - they are edited together), replace the `androidx.recyclerview.widget.RecyclerView` with id `@+id/launcherGrid` by a vertical `androidx.core.widget.NestedScrollView` (same id constraints, `android:fillViewport="true"`) wrapping `com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout` with id `@+id/launcherDesktop`. Strategic §3.3: "one screen + scroll down, no desktop pages" - the scroll view IS that decision, `fillViewport` is what makes a short desktop still fill the screen. Keep the existing constraints against `launcherTaskbar` and the existing paddings untouched. `launcher_taskbar.xml` references `@id/launcherGrid` in `nextFocusUp` on three views - repoint all three to `@id/launcherDesktop` (a dangling `@id` in `nextFocusUp` silently breaks D-pad, Rule 16).
>
> In `LauncherHomeActivity`:
> - Delete `createLayoutManager()` and the `SpanSizeLookup` (both are `GridLayoutManager`-only).
> - `cellAdapter` becomes `private val cellBinder = LauncherCellViewBinder(onCellClick = { viewModel.onCellTapped(it) })`.
> - `observeData()`'s cells collector becomes `cellBinder.bind(binding.launcherDesktop, cells, currentColumns())`.
> - `applyGridGeometry()` keeps `viewModel.setOrientation(...)` + `viewModel.persistColumns(...)` and now just re-binds at the new column count. The audit's rotation-staleness trap dies here by construction: the bind IS the re-layout, there is no stale bound state to miss.
> - Keep `applySystemBarInsetPadding()`, the Back callback, `onResumeWithViews()`, `onLayoutConfigurationChanged()` and the taskbar/tray wiring exactly as they are.
>
> `LauncherHomeActivity` must not grow logic (Rule 3): if wiring exceeds ~10 added lines, the bind call belongs in the binder, not in the activity.

**Verification:**

- `Grep` - `LauncherDesktopLayout` present in BOTH `activity_launcher_home.xml` variants.
- `Grep` - `GridLayoutManager` and `SpanSizeLookup` return ZERO hits in `LauncherHomeActivity.kt`.
- `Grep` - `@id/launcherGrid` returns ZERO hits in `launcher_taskbar.xml` (all three `nextFocusUp` repointed).
- `.\a.ps1 fc` → BUILD SUCCESSFUL.

**Status:** `[x]` done

---

### Step 05B.5 - Geometry unit tests + build

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometryTest.kt`
**Depends on:** Step 05B.4

**Prompt for developer:**

> The iteration-1 boundary excludes unit tests for launcher-mode **UI** (owner validation decision). `LauncherGridGeometry` is not UI - it is a pure-Kotlin object with no Android imports, and it is now the single point where a persisted layout meets a changed column count. That is worth a test; it is also cheap.
>
> **Note the source set:** `LauncherGridGeometry` lives in `src/launcherEnabled`, so the test must be reachable from the `standard` unit-test variant. Confirm `app_v2/build.gradle.kts` mounts a test source set for it; if it does not, place the test in `src/test` and verify `.\a.ps1 fu` actually compiles it. If it cannot be reached without new Gradle wiring, STOP - do not add build plumbing for a test - record that in the Step Log and skip this step.
>
> Cover only the branches that can bite:
> - `rowsFor` - empty list -> 1; a `rowIndex=3, spanH=2` cell -> 5.
> - `boundsFor` - `spanW` wider than `columns` clamps to `columns`; `colIndex` that would overflow the right edge clamps to `columns - spanW`; a 1×1 cell at `(2,1)` with `cellSize=100` -> `left=100, top=200, width=100, height=100`.
> - `columns` - `densityFactor` of 0 does not divide by zero (read the guard first, then assert what it actually does).

**Verification:**

- `.\a.ps1 fu` → the new test class runs and passes (or the Step Log records the source-set blocker with evidence).
- `.\a.ps1 fkn` → BUILD SUCCESSFUL (noLegal also mounts `launcherEnabled`; the deleted adapter must not have left a dangling reference there).

**Status:** `[x]` done

---

## Implementation Log (2026-07-17)

All five steps landed as written; `.\a.ps1 fc` passed first try, `.\a.ps1 fkn` (noLegal also mounts `launcherEnabled`) passed, geometry tests **11/11 PASS** (verified by reading `TEST-*.xml`, not by trusting BUILD SUCCESSFUL - a `--tests` filter that matches nothing can still look green).

Deviations and decisions worth recording:

- **`rowsFor` is `maxOf(rowIndex + spanH)`, not the prompt's `1 + max(rowIndex + spanH - 1)`.** Algebraically identical; the shorter form is the one that reads as "where does this cell end".
- **`LauncherDesktopLayout.boundsOf` duplicates the clamps of `LauncherGridGeometry.boundsFor` rather than calling it.** The layout holds `CellLayoutParams` (row/col/spans), not a `LauncherCell`, and the geometry object is deliberately free of Android types so it stays unit-testable. Passing a synthetic `LauncherCell` into the measure pass just to reuse a function would allocate per child per frame. The duplication is 6 lines and both sides carry a comment naming the other - if a third caller appears, extract a params-shaped overload rather than widening the model.
- **`item_launcher_cell_shortcut.xml` root went `wrap_content` -> `match_parent`** and BOTH item layouts lost `minHeight="88dp"`. The grid now measures every cell `EXACTLY`, so a floor could only fight it. Both layouts carry a comment saying so.
- **Both orientation layouts changed together** (Rule 11): `RecyclerView@launcherGrid` -> `NestedScrollView@launcherGridScroll` + `LauncherDesktopLayout@launcherDesktop`, `fillViewport="true"`, paddings preserved (`margin_small` portrait / `margin_medium` land).
- **`launcher_taskbar.xml` had three `nextFocusUp="@id/launcherGrid"`** pointing at the deleted id. Repointed to `@id/launcherDesktop`. A dangling `@id` in `nextFocusUp` compiles fine and silently breaks D-pad (Rule 16) - the exact class of defect only a device pass catches.
- **The test lives in `src/testStandard`, not `src/test`.** `launcherEnabled` is mounted into the `standard`/`noLegal` FLAVOR source sets, so `LauncherGridGeometry` does not exist for `lite`/`photos`/`legacy`/`vr`; in shared `src/test` the class would break those unit-test variants. Precedent confirmed: `src/testNoLegal` and `src/testVr` already exist and are NOT mounted in `build.gradle.kts` - pure AGP convention, so `src/testStandard` needed no build wiring. The phase file's "if it needs new Gradle plumbing, STOP" escape hatch was therefore not taken.
- **`applyGridGeometry()` now re-binds instead of mutating `spanCount`.** The audit's rotation-staleness trap (a changed column count with no rebind, leaving a stale forced height) cannot exist here: the bind IS the layout.

**Note for Phase 07:** `LauncherDesktopLayout` deliberately has no drag handling, no empty-cell hit-testing and no edit affordances. Drop-target math is `(x - paddingLeft) / cellSize`, `(y - paddingTop) / cellSize`; "which cell is under this point" is a `boundsFor` rect containment test, which handles a 2×2 gadget correctly anywhere inside it.

---

## Phase Done Criteria

- [x] Every `Step 05B.*` above is `[x] done`.
- [x] `Grep` - `LauncherCellAdapter` and `spanFor` return zero hits repo-wide (Rule 21). expected: 0 | actual: 0.
- [x] `.\a.ps1 fc` and `.\a.ps1 fkn` → BUILD SUCCESSFUL. Geometry tests: expected 11 pass | actual 11 tests, 0 failures, 0 errors, 0 skipped (read from `TEST-com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometryTest.xml` - a `--tests` filter matching nothing still reports BUILD SUCCESSFUL, so the report is the evidence, not the exit code).
- [ ] **DEFERRED-DEVICE** - a seeded 2×2 gadget cell renders two rows tall, a gap between two shortcuts stays a gap, and rotation re-lays-out without losing placement. No device seeding path exists until Phase 08 and no device is attached; covered by the Phase 10 `BlockNeedUserTest` pass. **Not ticked on a build alone** - the whole point of Phase 05B is a visual model no compiler can check.
- [x] Dev log + `catalog_sync.ps1`; CODE.LOCK released.

---

## Handoff Notes to Next Phase

- Phase 06 binds gadgets through the SAME `gadgetBinder` hook, which now receives a container already measured `EXACTLY` at `cellSize × spanW/spanH`. **The `cellSizePx × spanH` height decision recorded in PHASE_06 is superseded and must not be implemented** - the parent owns the height now, and a gadget setting its own `layoutParams.height` would fight it.
- Phase 06 still owns: unknown-gadget-key rendering (the `visual == null` path cannot serve it), and a teardown hook for gadgets that own a subscription - `LauncherCellViewBinder.bind` removes all views on every rebind, so a gadget view must cancel its own work in `onDetachedFromWindow`, since no `onViewRecycled` exists any more.
- Phase 07 gets what it actually needed: touch coordinate -> `(row, col)` is `y / cellSize`, `x / cellSize` on `LauncherDesktopLayout`; an empty cell is a coordinate with no child whose bounds contain it.

---

## Rollback Plan

Revert phase commit(s) - the Phase 04 RecyclerView renderer returns, gadgets lose vertical span, component still disabled by default for users.
