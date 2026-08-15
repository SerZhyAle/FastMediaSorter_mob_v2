# Phase 03 - DragSelectTouchListener helper

**Goal:** A reusable `RecyclerView.OnItemTouchListener` that implements minimal drag-select:
- Mouse: band-select (rubber-band rectangle) starting on empty area or press-drag over items.
- Touch: press-drag over items while already in multi-select mode.

No AndroidX `SelectionTracker` (owner: minimal). The listener only reports position ranges; the host maps positions to selection via existing `selectRange()`/`toggleFileSelection()`.

## Steps

- [ ] Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/dragselect/DragSelectTouchListener.kt` implementing `RecyclerView.OnItemTouchListener`.
  - Constructor takes callbacks: `isActive(): Boolean` (host decides whether drag-select may start now - e.g. mouse always, touch only when in multi-select mode), `onSelectionStart(position: Int)`, `onSelectionRangeChanged(start: Int, end: Int)`, `onSelectionEnd()`.
  - On `ACTION_DOWN`/drag: resolve the child under the pointer via `findChildViewUnder`; track `start` position; on move resolve current position and call `onSelectionRangeChanged(start, current)` only when the end position changes (debounce per-position, not per-pixel).
  - Distinguish mouse via `MotionEvent.getToolType(0) == TOOL_TYPE_MOUSE` (reuse the predicate style from `MouseEventHandler`); for mouse, allow band-select when the down lands on empty area (no child under pointer); for touch, require `isActive()` true.
  - Auto-scroll when the pointer is dragged near the top/bottom edge (simple `scrollBy` on a posted runnable while in edge zone).
  - Verification: class compiles; implements all three `OnItemTouchListener` methods; no business logic beyond position math + callbacks.
- [ ] Keep the file under 300 LOC; no Hilt, no ViewModel reference (pure view-layer utility).
  - Verification: LOC < 300; no `@Inject`.
- [ ] Build: `.\a.ps1 fk`. Expect PASS.
  - Verification: exit 0.

## Notes

- This listener coexists with `BrowseFileDragTouchCallback` (an `ItemTouchHelper`, different mechanism) - no conflict.
- Focus is never requested by this listener (S0289 §2 goal 9).
