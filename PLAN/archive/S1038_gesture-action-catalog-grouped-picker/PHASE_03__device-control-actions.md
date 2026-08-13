# Phase 03 - device-control-actions

**Goal:** Add device-control actions (all gesture flavors), dispatched via a new per-class `DeviceActionHandler` / `MediaActionHandler` (ADR-2).

## Steps

- [x] **3.1** New enum values + catalog entries (group DEVICE): `TOGGLE_FLASHLIGHT`, `BRIGHTNESS_MAX`, `BRIGHTNESS_NORMAL`, `VOLUME_UP`, `VOLUME_DOWN`, `VOLUME_MUTE`. Group MEDIA: `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREV`. Add each to `runPostSave`/`handlePreCaptureAction` (these are pre-capture, no screenshot) - update the exhaustive `when`. Verify: enum + catalog + when all cover the new values; compiles.
- [x] **3.2** `MediaActionHandler`: volume via AudioManager `setStreamVolume(STREAM_MUSIC, adjust)` (mirror StandaloneVideoTouchDelegate); mute via `adjustStreamVolume(..., ADJUST_TOGGLE_MUTE)`; media keys via AudioManager `dispatchMediaKeyEvent(KeyEvent(PLAY_PAUSE/NEXT/PREVIOUS))`. Verify: handler compiles; wired from dispatcher.
- [x] **3.3** `DeviceActionHandler`: flashlight via `CameraManager.setTorchMode` (toggle, no camera preview; guard no-flash devices with Timber.w degrade); brightness via `Settings.System.putInt(SCREEN_BRIGHTNESS ...)` gated on `Settings.System.canWrite` (grant flow in Phase 06). Verify: handler compiles.
- [x] **3.4** Extract the per-class handlers out of `ScreenshotGestureActionDispatcher` so it delegates by class (keep dispatcher under the LOC ceiling). Verify: dispatcher delegates; `a.ps1 dq` standard debug PASS.

## Done criteria
- [x] Device + media actions present, dispatched via handlers; standard debug builds green.

## Step Log

- 2026-07-19 - Steps 3.1-3.4 done. Enum +9 (DEVICE group per strategic §2.1; the tactical "MEDIA group" label is a dispatch-class name, not a picker group - GestureActionGroup has no MEDIA and the owner-signed strategic spec lists exactly 7 groups). Catalog +9 DEVICE entries. New `MediaActionHandler` (volume via AudioManager.adjustStreamVolume STREAM_MUSIC + FLAG_SHOW_UI, mute via ADJUST_TOGGLE_MUTE, transport via dispatchMediaKeyEvent) and `DeviceActionHandler` (flashlight via CameraManager.setTorchMode with local best-effort state; brightness via Settings.System gated on canWrite). Dispatcher delegates by class in handlePreCaptureAction; runPostSave when kept exhaustive. Files: ScreenshotGestureAction.kt, ScreenshotGestureActionCatalog.kt, ScreenshotGestureActionDispatcher.kt, gesture/MediaActionHandler.kt (new), gesture/DeviceActionHandler.kt (new), values/values-ru/values-uk strings.xml (+18 keys x3). Verification: `a.ps1 fk` standard debug BUILD SUCCESSFUL. Fast gates: neuroslop PASS; listener-symmetry +1 and no-ticket-logs (4x S1114) are concurrent-WIP noise outside S1038's touched files (S1114 Tactical, updated same session).
- AUDIT-FIX: initial DeviceActionHandler registered a CameraManager.TorchCallback (unbalanced process-lifetime listener → listener-symmetry +1). Replaced with a local best-effort torch flag; re-verified compile green.
