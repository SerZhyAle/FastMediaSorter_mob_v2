# Research 01 - Multi-select contract + mouse/hover infra

**Source:** android-solution-researcher, 2026-06-19 (F2 input for S0512).

## Affected scope

- Module `app_v2`, all flavors (interaction code in `src/main/`).
- Browse: `ui/browse/`, `ui/browse/managers/`, `ui/browse/selection/`.
- Duplicates: `ui/duplicates/`.
- Mouse infra: `ui/common/MouseEventHandler.kt`, `ui/common/ActivityMouseDispatchHelper.kt`, `core/ui/BaseActivity.kt`.

## Multi-select contract

- Browse selection state owned by `ui/browse/selection/BrowseSelectionManager.kt` (161 LOC): `MutableStateFlow<SelectionState>`, methods `toggleSelection`, `selectRange`, `selectAll`, `clearSelection`.
- `BrowseState.selectedFiles: Set<String>` is the authoritative set. No `isMultiSelectMode` boolean - mode is implicit in `selectedFiles.isNotEmpty()`.
- Range-select already exists: `BrowseSelectionManager.selectRange()` (lines 54-91) computes `startIndex..endIndex` from `lastSelectedPath`. Drag-select reuses it.
- Pipeline: long-press -> `onFileLongClick` -> `viewModel.selectFileRange` -> `BrowseSelectionManager` -> flow -> `BrowseState.selectedFiles` -> `mediaFileAdapter.setSelectedPaths()`.
- `MediaFileAdapter` (1239 LOC): 3 ViewHolders (List/Grid/GridNoThumb), `cbSelect` checkboxes. Approaching 1500-LOC limit - drag-select touch handling must go in a new helper.
- No ActionMode anywhere; operation panel `layoutOperations` shows when `selectedFiles.isNotEmpty()` (`BrowseStateUiUpdater`).
- Duplicates: `DuplicatesViewModel.selectedFilePaths: Set<String>`, `toggleFileSelection(path)`. `DuplicateGroupAdapter` (124 LOC) is a nested ListAdapter (groups -> inner `FileAdapter`); selection setter calls `notifyDataSetChanged()` (full rebind). Two-level nested RecyclerView - cross-group drag-select architecturally blocked.

## Mouse / hover infra (S0289)

- `MouseEventHandler` parses `ACTION_HOVER_ENTER/EXIT` -> `callbacks.onHoverEnter/Exit(view)`, returns `false` (does not consume). No caller sets hover highlight today.
- `ActivityMouseDispatchHelper.onHoverEnter` is called with the Activity root view, not the hovered item. Per-item hover requires item-level `setOnHoverListener`/`setOnGenericMotionListener` on ViewHolders.
- `item_focus_selector.xml` already handles `state_hovered` -> `@color/item_hovered`. Browse item root (`item_media_file.xml:3`) uses it as `android:background`.
- BLOCKER: `ListViewHolder.bind()` calls `root.setBackgroundColor(backgroundColor)` (`MediaFileAdapter.kt:754-759`), which replaces the selector drawable - `state_hovered` is dead in list mode unless fixed. Grid/GridNoThumb use `CardView.setCardBackgroundColor` (card color != root background) - selector on root still functional there.

## Drag-select feasibility

- No existing `OnItemTouchListener` on `rvMediaFiles` - clean slate.
- `BrowseFileDragTouchCallback` is an `ItemTouchHelper.Callback` for manual-sort reorder only; `isLongPressDragEnabled() = false`. Separate mechanism from `OnItemTouchListener` - no conflict.
- A second `ItemTouchHelper` or `RecyclerView.addOnItemTouchListener` can coexist.

## API constraints

- `state_hovered` in StateListDrawable: API 14+. `?attr/selectableItemBackground` hover: API 21+. All flavors (minSdk 23 legacy / 26 others) covered - no `@RequiresApi`.
- `ACTION_HOVER_*`, `TOOL_TYPE_MOUSE`: API 14+.

## Owner decisions (2026-06-19, from AskUserQuestion)

- Hover list-mode: move `item_focus_selector` to `android:foreground`, keep alternating-row striping on `android:background`.
- Duplicates drag-select: within-group only (cross-group blocked by nested RV).
- Browse touch drag-select entry: implicit mode (`selectedFiles.isNotEmpty()`), no new `BrowseState` flag.

## Parked findings

- S0524 - dead `BrowseSelectionManager` stub in `ui/browse/managers/` (37 LOC, unreachable).
- S0525 - `DuplicateGroupAdapter` full `notifyDataSetChanged()` on selection change (flicker during drag-select).
