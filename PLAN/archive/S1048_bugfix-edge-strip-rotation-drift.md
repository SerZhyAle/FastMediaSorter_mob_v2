# S1048 - bugfix-edge-strip-rotation-drift

**Ticket:** S1048
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-15
**Tier:** 2 - Easy (ad-hoc bugfix)

## Problem

The four screenshot-gesture edge strips (`ScreenGestureOverlayManager`) are positioned once
in `show()` via `computeGeometry()`, which reads `windowManager.currentWindowMetrics` /
`resources.displayMetrics` at that single moment and bakes the result into each band's
`WindowManager.LayoutParams` (`x`, `y`, width, height). Neither host - `OverlayHostService`
(standard/noLegal, `TYPE_APPLICATION_OVERLAY`) nor `ScreenshotAccessibilityService` (noLegal,
`TYPE_ACCESSIBILITY_OVERLAY`) - overrides `onConfigurationChanged`, so when the display
rotates (e.g. YouTube's "expand to fullscreen" button forces landscape, or sensor-based
auto-rotate flips the device) the stale geometry is never recomputed. Bands keep their
old absolute coordinates re-interpreted in the new orientation's coordinate space, so some
land away from the physical edge - user-reported screenshot shows a band drifted to the
screen's vertical center.

## Approach

- `ScreenGestureOverlayManager.kt`: add a `relayout()` (or equivalent) method that recomputes
  `computeGeometry()` and applies fresh per-band `x`/`y`/width/height via
  `windowManager.updateViewLayout(view, params)` for every live band in `bandViews`, without
  removing/re-adding the windows.
- `OverlayHostService.kt`: override `onConfigurationChanged(Configuration)` and call the new
  relayout when bands are currently shown (`overlayVisible`).
- `ScreenshotAccessibilityService.kt` (noLegal): override `onConfigurationChanged(Configuration)`
  and call the same relayout on its own live `overlayManager` instance.

## Done criteria

- Rotating the device (sensor auto-rotate, or an app forcing orientation like YouTube
  fullscreen) with the gesture overlay active leaves all four bands flush against their
  original physical screen edges in the new orientation - none drift toward the center.
- Toggling back to the original orientation restores the original band positions exactly.

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (compact bugfix)
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0 · MANUAL 0

Device-verified on emulator-5554 via authoritative WindowManager band geometry (all four bands flush after portrait<->landscape<->portrait; relayout probe fired on both transitions). `S1048:` probes removed from OverlayHostService + ScreenshotAccessibilityService on Verified flip.

### Manual device test - 2026-07-15 (emulator-5554, standard-debug v2.60.7151.516)

Result: PASS

Method: visual screenshot verification was impossible - the app window captures as an
all-black frame (FLAG_SECURE; only the system status bar renders), so band positions were
verified against the authoritative WindowManager geometry (`dumpsys window windows`, each
band is a named `screen_gesture_overlay_*` overlay window whose `mAttrs={(x,y)(WxH)}` is the
exact requested LayoutParams). All four zones + strip-visibility were enabled via Settings ->
Management -> Edge screen gestures -> Configure gestures. Rotation forced with
`cmd window set-ignore-orientation-request true` + `settings put system user_rotation`.

Expected vs actual (screen edges = flush means left band `x=0` and right band `x+w` = screen width):

- Portrait (1080x2280): expected left_edge=0, right_edge=1080.
  - left_top x=0 w=50; left_bottom x=0 w=50; right_top x=1030 (edge 1080); right_bottom x=1030 (edge 1080). MATCH.
- Rotate -> Landscape (2280x1080): expected left_edge=0, right_edge=2280.
  - left_top x=0; left_bottom x=0; right_top x=2230 (edge 2280); right_bottom x=2230 (edge 2280); heights recomputed to landscape safe area (h 644 -> 284, top 281/1355 -> 161/635). MATCH - right bands moved from x=1030 to x=2230, i.e. no stale-coordinate drift toward center.
  - `S1048: OverlayHostService relayouting bands after configuration change` logged on the transition.
- Rotate back -> Portrait (1080x2280): geometry identical to the initial portrait capture
  (left x=0; right x=1030, edge 1080; h=644; top 281/1355). Second S1048 relayout probe logged. MATCH.

Evidence: temp/S1048/ (win_port.txt, win_land.txt, win_port_return.txt, probe log lines).
Note: OverlayHostService.onConfigurationChanged (standard flavor) exercised; the noLegal
ScreenshotAccessibilityService path was not device-tested here (same shared relayout()).
