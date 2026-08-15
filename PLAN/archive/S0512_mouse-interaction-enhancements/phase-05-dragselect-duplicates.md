# Phase 05 - Wire drag-select into Duplicates (within-group)

**Goal:** Drag-select files within a single expanded duplicate group, reusing the same `DragSelectTouchListener`.

Constraint (owner decision): cross-group drag-select is architecturally blocked by the nested RecyclerView (groups -> files). Scope is within one group's inner file list.

## Steps

- [ ] In `DuplicateGroupAdapter.GroupViewHolder`, attach a `DragSelectTouchListener` to the inner files RecyclerView (`rvFiles`) when the group binds.
  - `isActive()`: Duplicates selection is always active (checkboxes permanently visible) - return `true`; mouse band + touch drag both allowed within the group.
  - `onSelectionRangeChanged(start, end)`: map inner-adapter positions to file paths and call `onToggleSelection` / a new range callback into `DuplicatesViewModel`. Prefer adding `selectFileRange(paths: List<String>)` to `DuplicatesViewModel` (additive selection of a range) rather than toggling each (avoids flip-flop while sweeping).
  - Verification: inner RV gets the listener on bind; range maps to a single `_state.update` (one set mutation per sweep step).
- [ ] Add `DuplicatesViewModel.selectFileRange(paths: List<String>)` that unions the range into `selectedFilePaths` in one `update`.
  - Verification: method present; single state mutation; unit-test-friendly (pure Kotlin).
- [ ] Detach/clean the listener on `GroupViewHolder` recycle to avoid leaking listeners across rebinds.
  - Verification: `onViewRecycled` (or equivalent) removes the listener.
- [ ] Build: `.\a.ps1 fc`. Expect PASS.
  - Verification: exit 0.

## Notes

- Flicker from `DuplicateGroupAdapter` full `notifyDataSetChanged()` during sweeps is tracked separately as S0525 - do not fix here.
- Within-group only: no attempt to span the outer group RecyclerView.
