# Research 03 - Fixed-orientation camera with rotating icons

**Spec:** S0754
**§6 item:** 3 (orientation approach)
**Status:** Resolved
**Date:** 2026-06-28

## Goal

Shutter + controls stay physically fixed on screen when the phone rotates (native-camera pattern). Lock the screen orientation, rotate only the control icons/labels, set CameraX `targetRotation` from the device sensor so captures are oriented correctly. No separate landscape layout, no recreate.

## Current state

- `CameraCaptureActivity` manifest: `android:configChanges="keyboardHidden"` (S0753 removed `orientation|screenSize` so it recreates and uses `layout-land`). To pin orientation we re-lock and stop relying on `layout-land`.
- `capture.targetRotation = previewView.display?.rotation` (`CameraCaptureSessionManager.kt:286`). With a locked-orientation activity the display rotation is static, so captures in a physical landscape hold would be mis-oriented unless `targetRotation` is driven by an `OrientationEventListener`. **This is the key fix.**
- Project orientation policy: `core/orientation/AppOrientationManager.kt` applies `requestedOrientation` to every activity on `onActivityResumed` EXCEPT those implementing `SelfManagedScreenOrientation` (`AppOrientationManager.kt:87-91`). The player uses `ScreenRotationManager` (`ui/player/helpers/ScreenRotationManager.kt`) with a self-managed policy. **The camera must implement `SelfManagedScreenOrientation`** or `AppOrientationManager` will fight its lock on resume.

## Resolution / approach for the plan

1. `CameraCaptureActivity` implements `SelfManagedScreenOrientation` and locks itself to portrait (`requestedOrientation = SCREEN_ORIENTATION_PORTRAIT`), so the layout never reflows and `AppOrientationManager` leaves it alone.
2. An `OrientationEventListener` buckets the device angle to 0/90/180/270. On change:
   - Rotate each control view (top-bar buttons, shutter stays centred but its glyph rotates, lens/label, zoom value, send-to, three-dots) via `View.animate().rotation(targetDegrees)` so icons/labels stay upright; positions do not move.
   - Feed the rotation to CameraX: set `imageCapture.targetRotation` / `videoCapture.targetRotation` (and the preview) to the matching `Surface.ROTATION_*` so the saved photo/video is oriented correctly.
3. `res/layout-land/activity_camera_capture.xml` becomes dead (portrait lock) - retire it; document the Rule 11 exemption in the cleanup phase (single locked layout, no landscape counterpart needed).
4. PreviewView fills the (portrait) window; the sensor FOV is shown upright because the preview transform + targetRotation compensate. No letterboxing concern beyond the existing `fillCenter` scaleType.

## Decisions

- Camera is unconditionally self-managed portrait-lock (does not follow the global OS-rotation setting) - matches the owner's "physically fixed controls" intent and is the simplest robust path.
- Rotation is icon-only; control container positions are constant -> removes the landscape overlap (thumbnail/pause over top row) and system-bar intrusion reported on the old `layout-land`.

## Sources

- `core/orientation/AppOrientationManager.kt:87-91`, `core/orientation/SelfManagedScreenOrientation` (marker)
- `ui/player/helpers/ScreenRotationManager.kt`
- `ui/cameracapture/helpers/CameraCaptureSessionManager.kt:286` (targetRotation)
- AndroidManifest.xml CameraCaptureActivity entry
- developer.android.com: OrientationEventListener, ImageCapture.setTargetRotation
