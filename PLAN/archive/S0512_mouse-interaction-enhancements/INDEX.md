# S0512 - Mouse interaction enhancements - Tactical Plan

**Strategic spec:** `PLAN/S0512_mouse-interaction-enhancements.md`
**Status:** Tactical
**Complexity:** Full

## Scope

Two genuine gaps after S0289 (XButton1/2 + middle-click already shipped):

1. Hover-highlight - visual hovered-state on list items via the stock `item_focus_selector` (which already carries `state_hovered`).
2. Drag-select (minimal) - mouse band-select + touch press-drag while in the existing multi-select mode, integrated with `BrowseSelectionManager.selectRange()`.

Target surfaces: Browse (grid + list + gridNoThumb) and Duplicates (within-group). Other lists are in scope only if they already expose a multi-select mode; none found in research beyond these two.

## Owner / research decisions

- Drag-select built minimally - reuse `selectRange()`, no full AndroidX `SelectionTracker`.
- Hover list-mode: selector moves to `android:foreground`, striping stays on `android:background`.
- Duplicates drag-select: within one expanded group only (nested RV blocks cross-group).
- Browse touch drag entry: implicit mode via `selectedFiles.isNotEmpty()`, no new state flag.
- Research artifact: `research/01__multiselect-and-mouse-infra.md`.

## Phases

- [x] Phase 01 - Hover-highlight Browse (foreground selector, list/grid/gridNoThumb) (auto-build - PASS)
- [x] Phase 02 - Hover-highlight Duplicates items (auto-build - PASS)
- [x] Phase 03 - DragSelectTouchListener helper (mouse band + touch drag) (auto-build - PASS)
- [x] Phase 04 - Wire drag-select into Browse (auto-build - PASS)
- [x] Phase 05 - Wire drag-select into Duplicates (within-group) (auto-build - PASS)
- [x] Phase 06 - Device-test tags inserted, build PASS; dev-log + inventory + BlockNeedUserTest pending finalize

## Out of scope (parked)

- S0524 - delete dead `BrowseSelectionManager` stub.
- S0525 - `DuplicateGroupAdapter` full-rebind optimization (perf of drag-select).
