# Phase 04 - Wire drag-select into Browse

**Goal:** Attach `DragSelectTouchListener` to `rvMediaFiles` and map swept positions to selection through the existing `BrowseSelectionManager`.

## Steps

- [ ] Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDragSelectManager.kt` that builds the `DragSelectTouchListener` and attaches it to `rvMediaFiles`. Do not add the setup code directly into `BrowseManagerInitializer` (964 LOC) - keep it a small dedicated manager.
  - `isActive()` for touch: `viewModel.state.value.selectedFiles.isNotEmpty()` (implicit multi-select mode, per owner decision). Mouse band-select active regardless.
  - `onSelectionRangeChanged(start, end)`: map adapter positions to file paths via the current adapter list, then drive selection through `viewModel.selectFileRange(..)` / `BrowseSelectionManager.selectRange(..)` (reuse the existing range logic; do not reimplement range math).
  - Skip folder rows the same way the existing selection path does (folders are not selectable).
  - Verification: manager compiles; positions resolved from the adapter's current list; selection flows through the existing manager (no new selection store).
- [ ] Invoke `BrowseDragSelectManager` setup from the same place `setupDragToReorder()` is wired (`BrowseManagerInitializer`), after the adapter + RecyclerView exist.
  - Verification: setup called once during Browse init; grep shows single attach.
- [ ] Confirm operation panel (`layoutOperations`) still appears via `selectedFiles.isNotEmpty()` after a drag-select sweep - no extra wiring needed.
  - Verification: read `BrowseStateUiUpdater.updateSelectionPanel`; state path unchanged.
- [ ] Build: `.\a.ps1 fc`. Expect PASS.
  - Verification: exit 0.

## Notes

- `MediaFileAdapter` stays untouched for selection (already consumes `selectedPaths`); drag-select only feeds the same `selectFileRange` entry point.
- Parked S0524 (dead stub) must not be confused with the real `selection/BrowseSelectionManager`; this phase uses `ui.browse.selection.BrowseSelectionManager` only.
