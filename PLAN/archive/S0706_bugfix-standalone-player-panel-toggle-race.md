# Draft: S0706 - StandalonePlayerActivity panel toggle bypasses coordinator

**Ticket:** S0706
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** Ad-hoc (bugfix, low)
**Source:** Parked by S0703 shared-state mutation audit (stage 2 adjudication, confirmed REAL, low severity).

> Draft inbox - raw capture. Not yet researched/approved. Style gate exempt.

## 0. Raw finding (audit evidence)

`StandalonePlayerActivity` writes `binding.topCommandPanel.isVisible = !binding.topCommandPanel.isVisible` directly in gesture callbacks (`StandalonePlayerActivity.kt:339, 391`), bypassing any state coordinator. In `PlayerActivity` the declared owner is `PlayerDialogAndUiStateManager.updatePanelVisibility()`. A ViewModel state emission arriving after the gesture can re-set the panel to the pre-toggle value.

## 1. Problem

In `StandalonePlayerActivity` the command-panel visibility toggle has no single owner: a raw gesture toggle competes with reactive panel-visibility updates, so a concurrent state emission can undo the user's toggle.

## 2. Direction (rough)

Route the standalone panel toggle through the same visibility coordinator instead of writing the view directly. Detail in /spec-tech.

## Related

- Parent audit: S0703.

## Last Audit

### Manual / on-device

Outcome: PASS - 2026-06-26, emulator-5554 (standard debug 2.60.6261.106). Standalone panel toggle routes through the single coordinator; panel + system bars + fullscreen button stay in sync and the toggle survives rotation.

- [x] External VIEW (`am start -a VIEW -t image/png -n .../StandalonePlayerActivity`) opened IMG_001_green.png in StandalonePlayerActivity; initial panel + status bar visible.
- [x] Keyboard panel-toggle = Ctrl+P (`key:44:4096` = system.toggle_controls -> onToggleCommandPanel) logged `S0706: panel toggle via coordinator (onToggleCommandPanel)` on every press - toggle goes through `toggleStandaloneFullscreen` -> fullscreenManager coordinator, not a raw view write.
- [x] Each toggle hides/shows topCommandPanel + system bars + flips the fullscreen button together (verified via view hierarchy: panel present vs absent, photoView re-anchored y=253 vs full-bleed 0,0).
- [x] Fullscreen button tap uses the same coordinator (panel + bars hidden in sync).
- [x] Rotation does not revert the toggle: panel hidden -> rotate portrait->landscape -> panel STAYS hidden (no state emission undoes it; activity is configChanges-handled). Ctrl+P in landscape restored panel in sync.

Note: the keyboard context-menu binding is not F9 on this build's keymap (keycode 139 reached the handler but did not resolve to file_ops); the S0706-relevant trigger is `system.toggle_controls` = Ctrl+P, which carries the debug tag and was exercised. Evidence: temp/s0706_logcat.txt, temp/s0706_01_initial_panel_visible.png, temp/s0706_03_fullscreen_panel_hidden.png, temp/s0706_04_landscape_panel_still_hidden.png.
