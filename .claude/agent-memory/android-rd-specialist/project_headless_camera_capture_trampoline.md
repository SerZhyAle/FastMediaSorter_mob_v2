---
name: headless-camera-capture-and-noHistory-trampoline
description: Edge-gesture photo capture is headless (CameraX ImageCapture without Preview, no camera activity); why noHistory + startActivityForResult trampolines silently lose their result
type: project
---

S0790-S0794 (take-photo edge gestures) capture HEADLESSLY: `HeadlessPhotoCapturer` (ui/cameracapture/helpers) binds ONLY a CameraX `ImageCapture` use case (NO `Preview`) to the trampoline `PhotoCaptureLaunchActivity`'s own lifecycle and calls `takePicture` in-process. No visible camera screen, no second activity.

**Why headless is possible:** `Preview` is a separate CameraX use case from `ImageCapture`. Binding ImageCapture alone opens the camera device with no UI. The S0790 spec's original non-goal "CameraX inevitably shows a camera screen" (owner 2026-07-01) was technically WRONG and was reversed (owner GO 2026-07-04). Only the system "camera in use" indicator (Android 12+) is truly unavoidable; the first frame has no 3A (auto-exposure/focus) convergence because there is no preview stream warming the pipeline.

**The noHistory + startActivityForResult pitfall (root of the S0790-S0794 device fail "camera opens and closes but no photo appears"):** the trampoline had `android:noHistory="true"`. Launching a full-screen `CameraCaptureActivity` for a result sent the trampoline to the background, so the system destroyed it immediately -> `onActivityResult`/`registerForActivityResult` callback never fired -> the capture result was lost, nothing saved/routed. The same teardown also breaks the first-use CAMERA permission dialog (it too backgrounds the trampoline).

**Why sibling trampolines don't suffer:** `CameraLaunchActivity` (S0568) also has noHistory but binds `multiCapture=true` - the host `CameraCaptureActivity` saves each frame itself and never relies on a result returning to the trampoline. Only a single-shot, result-returning trampoline is vulnerable.

**How to apply:**
- Never combine `android:noHistory` with `startActivityForResult` to a foreground activity - the caller dies before the result returns.
- For a silent single-shot capture, prefer headless CameraX (ImageCapture-only) bound to the trampoline's own lifecycle over launching a preview activity - it removes the flashing UI AND the lost-result class of bug.
- `EXTRA_AUTO_CAPTURE` / `CameraCaptureActivity.maybeAutoCapture` / `CameraCaptureFlowManager.autoCapture` REMAIN in use for S0926 video auto-record (which intentionally shows the preview so the user can stop recording) - do not delete them as "dead code". Only `createAutoCaptureIntent` (photo-only) was orphaned and removed.

Related: [[camera-capture-permission-constraint]]
