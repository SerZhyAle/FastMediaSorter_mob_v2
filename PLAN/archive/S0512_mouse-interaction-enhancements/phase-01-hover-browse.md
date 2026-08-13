# Phase 01 - Hover-highlight in Browse

**Goal:** Show a hovered-state overlay on Browse items under a mouse pointer, without stealing focus and without breaking selected/alternating-row backgrounds.

Problem: list-mode `ListViewHolder.bind()` calls `root.setBackgroundColor(..)` (`MediaFileAdapter.kt:754-759`) for selected/alternate/normal, which overwrites any `android:background` selector. So hover must live in `android:foreground`.

## Steps

- [ ] Create `app_v2/src/main/res/drawable/item_interaction_overlay.xml` - a selector with translucent transient-state layers only: `state_pressed` -> `@color/item_pressed`, `state_focused` (with 2dp `@color/focus_indicator` stroke, mirroring `item_focus_selector`), `state_hovered` -> `@color/item_hovered`, and a transparent default item. No `state_activated`/opaque normal fill (selection + striping stay on the background).
  - Verification: file exists; `<item android:state_hovered="true"` present; no opaque `item_normal`/`item_selected` default fill.
- [ ] Set `android:foreground="@drawable/item_interaction_overlay"` on the item root of `item_media_file.xml`, `item_media_file_grid.xml`, and `item_media_file_grid_no_thumb.xml` (confirm exact layout file names via Glob `app_v2/src/main/res/layout*/item_media_file*.xml`). Add the matching `res/layout-land/` counterpart edits if a landscape variant exists.
  - Verification: each item layout root carries `android:foreground="@drawable/item_interaction_overlay"`; grep finds it in every variant.
- [ ] Confirm Grid/GridNoThumb selection still uses `CardView.setCardBackgroundColor` and is not double-painted by the foreground overlay (overlay is translucent, card color shows through). No code change expected; only verify.
  - Verification: read `GridViewHolder`/`GridNoThumbViewHolder` bind; selection highlight intact.
- [ ] Build: `.\a.ps1 fc` (resources + Kotlin). Expect PASS.
  - Verification: exit 0.

## Notes

- `item_focus_selector.xml` already had `state_hovered`; the new overlay reuses the same colors so light/dark `@color/item_hovered` values need no change.
- Focus-ring is not forced on hover (S0289 §2 goal 9): hover layer is a fill, focus layer keeps its stroke - separate states.
