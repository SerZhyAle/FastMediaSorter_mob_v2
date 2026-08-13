# Phase 02 - Image rotation apply

**Status:** Pending

Apply the session angle to whichever image view is active, aspect-preserving, no bitmap edit.

## Files touched

- `ui/player/ImageLoadingManager.kt` (view selection at `:465,473-475,530`; views `imageView`/`photoView`)
- `ui/player/helpers/StandaloneViewManager.kt` (standalone image render)

## Steps

1. In `ImageLoadingManager`, add `applyRotation(angle: Int)` that, for the currently-visible view:
   - `photoView` visible -> `photoView.setRotationTo(angle.toFloat())` (PhotoView Matrix rotation, recomputes fit).
   - plain `imageView` visible -> `imageView.rotation = angle.toFloat()` PLUS scale-to-fit-container compensation at 90/270: when angle ∈ {90,270}, scale so the rotated bounds fit `mediaContentArea` (swap the fit basis w<->h). Use `imageView.post { }` if container dimensions are needed after layout (see memory: Glide onResourceReady can fire before view bind - guard `drawable != null`).
   - Verify: `Grep setRotationTo ImageLoadingManager.kt` present; `imageView.rotation` set; 90/270 branch computes a fit scale (no naive rotation-only).
2. Re-apply on every image load: after `setImage(...)` finishes binding, call `applyRotation(currentSessionAngle)` so the angle carries to the next file. Thread the current angle in from the ViewModel state (Phase 01) via the handle/host.
   - Verify: navigation to next image re-applies angle (trace the setImage completion path).
3. Mirror the same apply in `StandaloneViewManager` for `PhotoVideoStandaloneActivity`'s image path (same `photoView`/`imageView` ids per `activity_standalone_photo_video.xml`).
   - Verify: standalone image path calls the same rotation apply.

## Done criteria

- Rotating an image cycles 90/180/270/0 visually, aspect preserved, no file write (no `RotateImageUseCase` call).
- Angle re-applies to the next image within the session.
- Both families' image paths covered. Project compiles.

## Notes

- `RotateImageUseCase` must NOT be invoked here (that is destructive edit, non-goal).
- Do not touch `btnEditRotate`/`toggleRotationSensor` (screen sensor).
