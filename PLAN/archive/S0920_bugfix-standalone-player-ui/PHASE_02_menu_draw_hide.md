# Phase 02 - Hide draw-overlay menu item (#3)

**Goal:** the text (and audio) standalone overflow menu does not show "Draw overlay".

Root cause: shared `overflow_menu_standalone_player.xml` declares `menu_draw_overlay` visible-by-default (image-host feature, S0410). Text and audio hosts omit it from their hide lists.

## Steps

1. In `TextStandaloneActivity` overflow-menu setup, add `R.id.menu_draw_overlay` to the `isVisible = false` hide list (alongside `menu_edit_image`, `menu_ocr_image`, etc.).
   - Verification: grep shows `menu_draw_overlay` in the text host hide list.

2. In `AudioStandaloneActivity` overflow-menu setup, add `R.id.menu_draw_overlay` to its hide list (same leak class - owner asked to check all players).
   - Verification: grep shows `menu_draw_overlay` in the audio host hide list.

## Verification predicates

- `.\a.ps1 fk` PASS.
- On-device (device gate): text and audio standalone overflow menus have no "Draw overlay" item; image/video still show it for static images.
