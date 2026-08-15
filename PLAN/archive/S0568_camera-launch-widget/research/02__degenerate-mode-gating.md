# Research 02 - Degenerate single-mode gating

**Strategic §6 item:** 2 (поведение при недоступности видео в варианте сборки)
**Date:** 2026-06-20

## Question

When video capture is unavailable (flavor without video, or `disableVideoCapture` set), does the launch widget need a dedicated branch, or does the unified camera host already degrade?

## Findings

- The unified host contract (`CameraCaptureContract.createSwitchableIntent`) takes `allowModeSwitch` and `initialMode`. The S0563 host shows the in-screen `PHOTO|VIDEO` switch only when `allowModeSwitch` is true; otherwise it opens fixed in `initialMode`.
- `MainCameraCaptureManager.captureCamera(photoAvailable, videoAvailable)` already encodes the gating:
  - `allowSwitch = photoAvailable && videoOk`
  - `initialMode = if (photoAvailable) PHOTO else VIDEO`
  - aborts with `camera_capture_error_no_camera_app` when neither is available.
- Availability is computed in `MainActivity` as:
  - `photoAvailable = !settings.disableCameraCapture && mediaCapabilities.supportsImages`
  - `videoAvailable = !settings.disableVideoCapture && mediaCapabilities.supportsVideo`
- `mediaCapabilities` is `core/capability/MediaCapabilities.kt` (fields `supportsImages`, `supportsVideo`, `supportsMicRecording`), reachable from a widget context via `di/MediaCapabilitiesEntryPoint` (used by `CameraPhotosWidgetProvider`) and injectable directly into an `@AndroidEntryPoint` activity.

## Decision

- The launch widget needs NO dedicated single-mode branch. It computes `photoAvailable` / `videoAvailable` exactly like `MainActivity` and hands `allowSwitch = photoAvailable && videoAvailable`, `initialMode = if (photoAvailable) PHOTO else VIDEO` to `createSwitchableIntent`.
- The S0563 host's existing degenerate gating then opens the single available mode with no switch. No new code path beyond mirroring the availability computation.
- When neither mode is available, abort with the existing `camera_capture_error_no_camera_app` message (no crash).
