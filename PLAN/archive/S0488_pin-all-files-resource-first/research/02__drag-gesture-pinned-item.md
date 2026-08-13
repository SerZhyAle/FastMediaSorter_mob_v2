# Research 02 - Drag-gesture behavior for the pinned All-files row

**Spec:** S0488
**Strategic §6 item:** 2
**Status:** Resolved
**Date:** 2026-06-17

## Question

In manual mode the list supports drag-to-reorder. How should the pinned «Все файлы» row behave so it stays first without surprising the user?

## Findings

- `ResourceItemTouchCallback` drives drag-to-reorder: `isLongPressDragEnabled = false` (drag starts only from a drag handle), `onMove` calls `adapter.moveItem(from, to)` for live animation, `clearView` reads `adapter.getDragOrderedList()`, calls `adapter.submitList(newOrder)`, then `viewModel.saveResourceOrder(newOrder)` which persists `displayOrder` to the DB.
- `ResourceAdapter` already gates draggability per row: `val isDraggable = dragStartListener != null && resource.id != -100L` - the Favorites pseudo-row exposes no drag handle. This is the established pattern for a non-movable row.
- `saveResourceOrder` triggers a DB flow re-emission, so `MainViewModel.applyFiltersAndSorting` runs again and re-applies the pin on every emission. The pin is therefore self-healing: even a transient drag cannot leave another resource above the pinned one after the list settles.

## Decision

- Mirror the existing `-100L` pattern: the All-files predefined row exposes no drag handle (`isDraggable = false` for `profile == ResourceProfile.ALL_FILES`).
- In `ResourceItemTouchCallback`, prevent dropping any item above the pinned row: when the list's index 0 is the pinned All-files resource, clamp the `onMove` target to `>= 1` (and disallow moving the pinned row itself). This keeps the live drag animation from visually displacing the pinned row.
- No new persistence concept: the pin stays presentation-only (Research 01). `saveResourceOrder` continues to persist the other resources' order; the pinned row simply remains at index 0 on the next emission.
- This satisfies strategic §11 criterion 5 ("dragging other resources keeps All-files first") and wish §3.1.1 (dragging the pinned row is a no-op).
