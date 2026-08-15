# Phase 01 - viewport-coverage-and-aspect

**Goal:** Preview and capture share one field of view via a `ViewPort`; preview shows the full frame (photo) or the recorded region (video); the saved file matches.

## Context

- Root cause A: `PreviewView` `scaleType="fillCenter"` stretches the preview to the full-screen view and crops sensor edges, so the preview shows less than the file.
- Root cause B: `CameraCaptureSessionManager.bindToLifecycle` binds `preview` + `captureUseCase` separately (no `UseCaseGroup`/`ViewPort`), so preview and capture resolve their own crop.

## Steps

- [x] **1.1** Layout: change `previewViewCamera` `app:scaleType` from `fillCenter` to `fitCenter` in `res/layout/activity_camera_capture.xml` (no `-land` counterpart - the host is portrait-locked, S0918). This makes the preview letterbox to the full ViewPort region instead of cropping.
  - Verify: `a.ps1 fr` PASS.
- [x] **1.2** Session bind: in `CameraCaptureSessionManager.bindToLifecycle`, replace the direct `provider.bindToLifecycle(owner, selector, preview, captureUseCase)` with a `UseCaseGroup` that carries an explicitly-built `ViewPort`.
  - ViewPort aspect ratio (as a `Rational`): photo mode -> native full frame (4:3); video mode -> the selected ratio (`selectedAspectRatio ?: 4:3`).
  - Build the ViewPort MANUALLY via `ViewPort.Builder(Rational, targetRotation).setScaleType(ViewPort.FILL_CENTER).build()` - FILL_CENTER = the largest centred crop of the target aspect on the sensor (max FOV at that ratio). Do NOT use `previewView.getViewPort()` (it derives the view's tall aspect - the current bug) and do NOT set both a group ViewPort and a preview-derived one (verification Caveat B: mixing scale types breaks preview/capture parity).
  - PreviewView `FIT_CENTER` (step 1.1) only governs how the delivered ViewPort-cropped buffer is displayed (letterboxed); the captured pixels are the ViewPort region, so preview == file.
  - JPEG file crop already rides the correct path: capture uses `takePicture(OutputFileOptions,...)` which is physically cropped to the ViewPort (verification Caveat A - the in-memory `OnImageCapturedCallback` path would NOT be; do not switch to it).
  - Add every use case (preview + capture) to the group; bind the group.
  - Verify: `a.ps1 fk` PASS.
- [x] **1.3** Photo file aspect crop: when photo mode and `selectedAspectRatio == RATIO_16_9`, centre-crop the saved JPEG to 16:9 after capture. Reuse the existing `cropCenter` + `restoreExif` (S0765) path - generalise it from a zoom-factor crop to a target-aspect crop (compute the 16:9 rect centred inside the 4:3 JPEG, keep pixels in stored orientation so `TAG_ORIENTATION` stays valid). At 4:3 no crop.
  - The existing digital-zoom JPEG crop (Phase 04 territory) composes with this: apply zoom crop then aspect crop, or one combined rect.
  - Verify: `a.ps1 fk` PASS; note the composed crop-rect math.
- [x] **1.4** Rebind on the axes that change the ViewPort: `applyMode` (photo<->video changes ViewPort ratio), `setAspectRatioAndResolution` (video ViewPort ratio; photo overlay + crop only). Confirm each of these already calls `bindToLifecycle` (they do) so the new ViewPort recomputes. Keep `ResolutionSelector` for photo resolution selection; the ViewPort governs FOV, the ResolutionSelector governs output size.
  - Verify: reasoning note - which rebind path each axis takes.
- [x] **1.5** Overlay bounds: the grid / focus-ring / result-frame overlays are constrained to `previewViewCamera` (full screen) but the image now occupies a letterboxed sub-rect. Expose the displayed-image rect (compute from the ViewPort ratio + rotation fit-centred into the view size) so overlays can map into it. Provide a helper the host/overlay can query; Phase 02 consumes it.
  - Verify: `a.ps1 fk` PASS.

## Done criteria
- Photo: preview shows the full sensor frame letterboxed; the saved 4:3/16:9 file matches the preview (16:9 = centred crop).
- Video: preview shows exactly the recorded region; the file matches.
- No regression to night/HDR/manual/torch/focus/lens-switch (all rebind through the same path).
