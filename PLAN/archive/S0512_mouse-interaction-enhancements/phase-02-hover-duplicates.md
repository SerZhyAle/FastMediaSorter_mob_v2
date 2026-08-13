# Phase 02 - Hover-highlight in Duplicates

**Goal:** Hovered-state feedback on Duplicate file rows under a mouse pointer.

Duplicate file rows are bound by the inner `FileAdapter` of `DuplicateGroupAdapter`. Selection there is a checkbox + permanently visible, so there is no `setBackgroundColor` overwrite on the file-row root - the stock selector can sit on `android:background` or `android:foreground`.

## Steps

- [ ] Identify the duplicate file-row item layout (Glob `app_v2/src/main/res/layout*/item_duplicate*.xml` / item used by inner `FileAdapter`). Read its root to see current background.
  - Verification: layout file path identified; current root background known.
- [ ] If the file-row root has no interaction background, add `android:foreground="@drawable/item_interaction_overlay"` (the Phase 01 overlay). If it already paints a solid background per-row, use `android:foreground` so hover overlays without clobbering it. Mirror into `res/layout-land/` if a counterpart exists.
  - Verification: file-row root carries the overlay; grep confirms in all variants.
- [ ] Verify the group header row (outer `GroupViewHolder` layout) - add the same overlay only if mouse hover on a group header is meaningful; otherwise leave untouched (within-scope = file rows).
  - Verification: decision recorded inline; no unintended header change.
- [ ] Build: `.\a.ps1 fc`. Expect PASS.
  - Verification: exit 0.

## Notes

- No per-item `setOnHoverListener` needed: Android sets `state_hovered` automatically on the view under the pointer when it has a stateful background/foreground drawable. The S0289 callbacks are not required for this passive path.
