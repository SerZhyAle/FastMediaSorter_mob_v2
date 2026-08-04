---
name: reference_emulator_capture_family_testing
description: What the x86 phone emulator CAN and CANNOT verify for the S0672/S0724 edge-capture family; adb cmd statusbar QS-tile technique; aapt2 build-content check
metadata:
  type: reference
---

How to device-test the screen-capture / edge-gesture family (S0672 strip, S0724 grey strip, S0713 QS tiles, S0683 panel tile) on the standard x86 phone emulator.

**QS TileService bind + click WITHOUT UI fiddling (reliable):**
- Add a tile: `adb shell cmd statusbar add-tile <pkg>/<fully.qualified.TileService>`
- Click it: `adb shell cmd statusbar click-tile <pkg>/<...>`
- `add-tile` + opening QS (`cmd statusbar expand-settings`) fires `onTileAdded`/`onStartListening`; `click-tile` fires `onClick`. This verified S0713 (both tiles bind, panel tile launches AppLaunchPanelActivity) and the full S0672 QS-tile fallback path (click -> ScreenCaptureConsentActivity -> disclosure -> MediaProjection consent -> entire-screen capture saved to /sdcard/Pictures/Screenshots -> service onDestroy, no FGS exception, no LeakCanary leak). Tile component for the screenshot fallback: `com.sza.fastmediasorter.screencapture.ScreenshotGestureTileService` (mounted only when `fms.edgeGestureTile=on`).

**Confirm a flag-gated source set actually shipped in the build (when `dumpsys package` lies):**
- A service with NO intent-filter (e.g. `OverlayHostService`, exported=false) does NOT appear in `dumpsys package`'s resolver tables, so a grep there gives a false "not registered". Use aapt2 on the built APK instead: `aapt2 dump badging <apk> | grep -iE "SPECIAL_USE|SYSTEM_ALERT_WINDOW|PROPERTY_SPECIAL_USE"`. The specialUse PROPERTY presence proves OverlayHostService is in the manifest. (build-tools aapt2 at `<sdk>/build-tools/<ver>/aapt2.exe`.)

**What the emulator CANNOT do (-> real-device session, = the Play demo video):**
- Enable the overlay via Settings: the "Left-edge screen gestures" COLLAPSIBLE group does not expand reliably via mobile-mcp/adb taps on the near-square sdk_gphone AVD (settings-search finds the toggles because it indexes the static manifest, but the live group stays collapsed). So the gesture-overlay enable toggle + the S0724 grey-strip recolour can't be exercised on emulator.
- The TYPE_APPLICATION_OVERLAY edge strip is FLAG_NOT_FOCUSABLE; `adb input swipe` does not reach it, so swipe -> capture can't be driven on emulator.
- Net: emulator verifies the QS-tile path + the shared capture engine + build contents + (static) the FGS-start guard; the overlay-strip enable/recolour/swipe need a real Android-15 device.

**screencap dies while the display is rotated (2026-07-28, sdk_gphone64_x86_64 API 33):** with `user_rotation=1` every capture path returns a zero-byte file and `screencap -p` prints its usage text - `adb.ps1 shot`, `exec-out screencap`, and screencap-to-file alike. At rotation 0 the same AVD needs an explicit `-d <id>` from `dumpsys SurfaceFlinger --display-id` (`adb.ps1 shot` now falls back to that automatically). Net: **never rotate the AVD to test landscape - reshape the display instead** (`wm size 2400x1080`, or `wm size 1024x600` + `wm density 160` for an in-car head unit), which keeps rotation 0 and capture alive while still selecting the landscape/w600dp resource bucket.

**Display-aspect tricks (reused for S0670/S0693):** `adb shell wm size WxH; wm density N` then relaunch to fake a tall phone (1080x2400@400 ~ 432dp) or a narrow phone (<600dp) without booting another AVD - the current sdk_gphone AVD is near-square (~852dp wide), so it already triggers wide-layout paths. Reset with `wm size reset; wm density reset`.

Related: [[play-capture-family-status]], [[screencapture-split-standard-vs-nolegal]], [[emulator-verifies-mediaprojection-screenshot]], [[bottomsheet-menu-untappable-emulator]].
