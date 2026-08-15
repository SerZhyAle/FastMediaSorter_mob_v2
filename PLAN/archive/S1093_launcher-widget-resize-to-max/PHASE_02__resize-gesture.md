# Phase 02 - Resize Gesture

**Strategic spec:** [`../S1093_launcher-widget-resize-to-max.md`](../S1093_launcher-widget-resize-to-max.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 02.1-02.4 grep-verified (handle layout + preview drawable + currentCellSize(); LauncherResizeManager preview-overlay gesture; binder handle on GADGET cells only; Activity wires resizeManager via lateinit + gadgetRegistry already injected; launcher_edit_resize_handle EN/RU/UK). `.\a.ps1 fc` BUILD SUCCESSFUL. Audit: real cell untouched during drag, move-drag scrim intact, no P0/P1.

---

## Objective

Add a bottom-right resize handle to each GADGET cell in edit mode; dragging it previews a candidate footprint (floor = gadget seed size, ceiling = full width x viewport height) and, on release, commits a collision-checked resize. The real cell is never mutated during the drag - only the authoritative cells Flow changes it - so a rejected resize needs no snap-back logic.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`viewModel.resizeCell` available).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_resize_handle.xml` | New | ≤ 25 |
| `app_v2/src/launcherEnabled/res/drawable/bg_launcher_resize_preview.xml` | New | ≤ 15 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherDesktopLayout.kt` | Modified | ≤ 130 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResizeManager.kt` | New | ≤ 150 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 320 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 720 |
| `app_v2/src/main/res/values/strings.xml` (+ values-ru, values-uk) | Modified | - |

> The resize handle/preview are code-driven overlays on the existing desktop; no `activity_launcher_home.xml` change, so its layout-land counterpart is untouched. New item/drawable are orientation-neutral (no land variant).

---

## Steps

### Step 02.1 - Handle layout, preview drawable, and a public cell-size accessor

**Files:** `item_launcher_cell_resize_handle.xml`, `bg_launcher_resize_preview.xml`, `LauncherDesktopLayout.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `item_launcher_cell_resize_handle.xml`: a 48dp x 48dp `ImageView` (id `@+id/resizeHandle`, `layout_gravity="bottom|end"`, `scaleType="center"`, `src="@drawable/ic_tune"` as a placeholder resize glyph, `background="?attr/colorControlHighlight"` shaped by a circular ripple is optional; keep it a plain focusable ImageView with `focusable="true"`). Create `bg_launcher_resize_preview.xml`: a `<shape rectangle>` with `<solid android:color="?attr/colorPrimaryContainer"/>`, `<corners android:radius="12dp"/>`, `<stroke android:width="2dp" android:color="?attr/colorPrimary"/>` (theme-attr solid/stroke valid from API 21). No hardcoded hex (Rule 20). In `LauncherDesktopLayout`, add `fun currentCellSize(): Int = cellSize(width)` so the resize manager reads the one authoritative cell size (do not re-derive it at the call site).

**Verification:**

- `Glob` - both new resource files exist.
- `Grep` - `@+id/resizeHandle` in the handle layout; `?attr/colorPrimary` in the preview drawable; no `="#` in either.
- `Grep` - `fun currentCellSize()` in `LauncherDesktopLayout.kt`.

**Status:** `[x]` done

---

### Step 02.2 - LauncherResizeManager (preview-overlay gesture)

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResizeManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `LauncherResizeManager(container: LauncherDesktopLayout, viewport: View, gadgetRegistry: LauncherGadgetRegistry, viewModel: LauncherHomeViewModel)`. Public `fun attachHandle(handle: View, cellUi: LauncherCellUi)` sets an `OnTouchListener` on `handle`:
> - Compute the gadget seed floor once: decode `cellUi.cell.target` via `gadgetRegistry.decodeTarget(...)` -> key -> `gadgetRegistry.byKey(key)`; `floorW = gadget?.defaultSpanW ?: cellUi.cell.spanW`, `floorH = gadget?.defaultSpanH ?: cellUi.cell.spanH`.
> - `ACTION_DOWN`: `viewport.parent?.requestDisallowInterceptTouchEvent(true)`; record `downRawX/downRawY`, `baseW = cellUi.cell.spanW`, `baseH = cellUi.cell.spanH`, `cellSize = container.currentCellSize().coerceAtLeast(1)`, `ceilingW = container.columns`, `ceilingH = (viewport.height / cellSize).coerceAtLeast(floorH)`; add a preview `View` (background `@drawable/bg_launcher_resize_preview`) to `container` with `CellLayoutParams(cell.rowIndex, cell.colIndex, baseW, baseH)`; return true.
> - `ACTION_MOVE`: `dW = ((event.rawX - downRawX) / cellSize).roundToInt()`, `dH = ((event.rawY - downRawY) / cellSize).roundToInt()`; `candW = (baseW + dW).coerceIn(floorW, ceilingW)`, `candH = (baseH + dH).coerceIn(floorH, ceilingH)`; if changed, set the preview's `CellLayoutParams` to `(row, col, candW, candH)` and `container.requestLayout()`.
> - `ACTION_UP` / `ACTION_CANCEL`: `requestDisallowInterceptTouchEvent(false)`; remove the preview view from `container`; if `candW != baseW || candH != baseH` call `viewModel.resizeCell(cellUi.cell.id, candW, candH)`. Do NOT touch the real cell view - the cells Flow rebinds it on success; on a rejected resize nothing changes.
>
> Keep the manager free of business logic beyond this mapping (Rule 3). Import `kotlin.math.roundToInt`.

**Verification:**

- `Glob` - `LauncherResizeManager.kt` exists.
- `Grep` - `fun attachHandle(handle: View, cellUi: LauncherCellUi)` present.
- `Grep` - `viewModel.resizeCell(` called in `ACTION_UP` path; `requestDisallowInterceptTouchEvent(true)` present.
- `Grep` - `coerceIn(floorW, ceilingW)` and `coerceIn(floorH, ceilingH)` present.

**Status:** `[x]` done

---

### Step 02.3 - Binder adds the handle to GADGET cells in edit mode

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a binder constructor callback `onAttachResizeHandle: (handle: View, cellUi: LauncherCellUi) -> Unit = { _, _ -> }`. In `decorateForEdit`, AFTER the scrim and remove badge are added and only when `item.cell.kind == LauncherCellKind.GADGET`, inflate `item_launcher_cell_resize_handle.xml` into `view` (the cell FrameLayout), set its `contentDescription = view.context.getString(R.string.launcher_edit_resize_handle)`, add it as the last child (so it draws and takes touches on top of the scrim), and call `onAttachResizeHandle(handle, item)`. Shortcuts get no handle. Do not change the shortcut/move paths.

**Verification:**

- `Grep` - `onAttachResizeHandle` in the binder constructor and invoked inside `decorateForEdit`.
- `Grep` - `item.cell.kind == LauncherCellKind.GADGET` guards the handle inflate.
- `Grep` - `R.layout.item_launcher_cell_resize_handle` inflated.

**Status:** `[x]` done

---

### Step 02.4 - Wire the manager in the Activity + resize-handle string

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`, `app_v2/src/main/res/values/strings.xml` (+ values-ru, values-uk)
**Depends on:** Step 02.3

**Prompt for developer:**

> Add the trilingual string first: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_edit_resize_handle -En "Resize" -Ru "Изменить размер" -Uk "Змінити розмір"` (COMMUNICATION_POLICY §2/§6). In `LauncherHomeActivity`: inject `@Inject lateinit var gadgetRegistry: LauncherGadgetRegistry` if not already available; in `setupViews` construct `val resizeManager = LauncherResizeManager(binding.launcherDesktop, binding.launcherGridScroll, gadgetRegistry, viewModel)` after the desktop exists, and pass `onAttachResizeHandle = resizeManager::attachHandle` into the `cellBinder` construction (add the argument where `cellBinder` is built). Keep a field reference if the binder is built before `setupViews`. The taskbar (`binding.launcherTaskbar`) is a sibling of the desktop and is never hidden during resize - no change needed for strategic §11.5.

**Verification:**

- `Grep` - `LauncherResizeManager(` constructed in `LauncherHomeActivity.kt`.
- `Grep` - `onAttachResizeHandle = ` passed to the cell binder.
- `Grep` - `launcher_edit_resize_handle` present in all three `values*/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_edit_resize_handle"` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager class).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (Layer 2 touch/gesture: the manager mutates only the preview overlay, never the real cell; the existing move-drag scrim is untouched; confirm the handle sits above the scrim so its touches are not swallowed).

---

## Handoff Notes to Next Phase

Gadgets resize by dragging the bottom-right handle in edit mode, floor = seed size, ceiling = full width x viewport height; shortcuts stay 1x1. Phase 03 records + regenerates.

---

## Rollback Plan

Revert the phase commit(s) - additive overlay + manager; the move gesture, persistence and shortcut cells are untouched.
