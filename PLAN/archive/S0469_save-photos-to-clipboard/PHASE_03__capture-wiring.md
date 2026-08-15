# Phase 03 - capture-wiring

**Goal:** Copy a captured photo to the system clipboard at the single shared finalization point (`CameraCaptureSaver.save()`), gated on `MediaType.IMAGE` and the new flag, and surface a short confirmation in both callers.

**Depends on:** 01, 02

---

## Steps

- [ ] **03.1 - `SaveResult.Success` carries a clipboard flag.**
  - In `app_v2/.../data/capture/CameraCaptureSaver.kt`: add `val copiedToClipboard: Boolean = false` to `SaveResult.Success`.
  - **Verification:** `compileStandardDebugKotlin` - existing `SaveResult.Success(savedPath)` sites still compile (defaulted param).

- [ ] **03.2 - Clipboard step inside `CameraCaptureSaver.save()`.**
  - Inject `SettingsRepository` and `ImageClipboardWriter` into `CameraCaptureSaver`.
  - Before the temp file is deleted and only when `MediaTypeUtils.getMediaType(name) == MediaType.IMAGE`: read the current settings; if `cameraCaptureCopyToClipboard` is on, call `imageClipboardWriter.copyImageFile(tempFile)` and remember the result.
  - The clipboard step must NOT change save success/failure routing (strategic goal 4 - works alongside the assigned operation); it is additive and runs off the UI thread (writer already uses `Dispatchers.IO`).
  - Pass the boolean into `SaveResult.Success(savedPath, copiedToClipboard = ..)`. Video/audio captures are excluded by the `MediaType.IMAGE` gate.
  - **Verification:** photo save with flag on -> writer invoked; with flag off or non-image -> not invoked; `.\a.ps1 fk` PASS.

- [ ] **03.3 - Confirmation in both callers.**
  - `ui/browse/managers/BrowseCameraCaptureManager.kt`: on `SaveResult.Success`, when `result.copiedToClipboard`, additionally show a short confirmation (string `camera_capture_copied_to_clipboard`) without replacing the existing "saved" feedback.
  - `widget/CameraQuickCaptureLaunchManager.kt`: same, using its `toast(..)` path.
  - **Verification:** both callers reference `result.copiedToClipboard` and `R.string.camera_capture_copied_to_clipboard`; `.\a.ps1 fk` PASS.

---

## Phase Done Criteria

- A photo captured with the option enabled lands on the clipboard from the one shared backend, for both Browse and the quick-capture widget, with the assigned operation still running and a short confirmation shown.
