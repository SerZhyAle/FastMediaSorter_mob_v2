**Status:** Tactical

# S1042 - Tactical Plan - Screenshot OCR crop-flow unification

Strategic: [../S1042_screenshot_ocr_crop_flow_unification.md](../S1042_screenshot_ocr_crop_flow_unification.md)

Route all OCR entry points (screenshot gesture `OCR_TRANSLATE`, `TAKE_PHOTO_OCR_TRANSLATE`, OCR widgets) into the existing photo crop + language + OCR/translate screen ([CameraOcrTranslateActivity] + [CameraOcrFlowManager]), with the screenshot as source. Straight to crop; only the cropped frame saved.

## Architecture anchor

The OCR flow is already source-agnostic after decode: `onPhotoCaptured()` decodes the temp capture into `orientedBitmap`, then `showCropStep` -> language cluster -> `onCropConfirmed` -> `runRecognition` -> results. Only the *entry* (`startCapture` -> `CameraCaptureActivity` -> temp jpg) is camera-specific. The plan adds a parallel `startWithImage(File)` entry that decodes an existing file into `orientedBitmap` and joins the same path.

`CropRegionManager.loadOrientedBitmap(file: File)` is the shared decode. `CameraOcrStorageManager.saveBitmapToGallery` writes only the cropped result. `ScreenCaptureService.processCapture` currently always gallery-saves via `SaveScreenshotUseCase` before `runPostSave`.

## Phases

### Phase 01 - Source-agnostic entry in the OCR flow

- In [CameraOcrFlowManager](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt) add `fun startWithImage(sourceFile: File)`: decode via `cropRegionManager.loadOrientedBitmap(sourceFile)` on `Dispatchers.IO`; on null -> `showToast(camera_ocr_camera_error)` + `finishFlow()`; else set `currentTimestamp = newTimestamp()`, `recycleOrientedBitmap()`, `orientedBitmap = bitmap`, `showCropStep(bitmap)` + `emitCropLanguages()`. Delete `sourceFile` after decode (temp cleanup) - it is app-owned.
- In [CameraOcrTranslateActivity](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt): add companion `createIntent(context, sourceImagePath: String)` overload + `EXTRA_SOURCE_IMAGE_PATH`. In `onCreate`, when `savedInstanceState == null`: if the extra is present -> `flowManager.startWithImage(File(path))`, else `flowManager.startCapture()` (unchanged).
- **Verification:** `a.ps1 fk` compiles; grep `startWithImage` present in manager + activity; the camera path (`startCapture`) still reachable when no extra.

### Phase 02 - Reroute screenshot OCR gestures to the unified screen

- In [ScreenshotGestureActionDispatcher](../../app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt):
  - `runPostSave` `OCR_TRANSLATE`: replace `openInViewer(context, savedUri, AUTO_ACTION_TRANSLATE)` with launching `CameraOcrTranslateActivity.createIntent(context, tempPath)` (`FLAG_ACTIVITY_NEW_TASK`). The temp path is supplied by the capture service (Phase 03); until then, if only `savedUri` is available, resolve it to a readable file path.
  - `handlePreCaptureAction` `TAKE_PHOTO_OCR_TRANSLATE`: launch `CameraOcrTranslateActivity.createIntent(context)` (camera source, no path) with `FLAG_ACTIVITY_NEW_TASK` instead of `launchPhotoCapture(context, AUTO_ACTION_TRANSLATE)`. Drop the `PhotoCaptureLaunchActivity` trampoline for this action.
- **Verification:** `a.ps1 fk` compiles; grep confirms no `AUTO_ACTION_TRANSLATE` reference remains in the dispatcher.

### Phase 03 - Capture pipeline: temp-stage raw frame for OCR actions

- In [ScreenCaptureService](../../app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt) `processCapture`: resolve the gesture action *before* saving (move the action resolution out of `runPostSaveAction`). Branch:
  - OCR action (`OCR_TRANSLATE`): keep the clipboard copy from the live bitmap; **skip** `saveScreenshotUseCase`; write the bitmap to an app-cache temp file (`cacheDir/ocr_capture_<ts>.png`) via a small helper; suppress the "saved to" toast; call the dispatcher to open the OCR screen with the temp path.
  - Non-OCR action: unchanged (gallery-save + `runPostSave`).
- Add a temp-stage helper (in the service or a tiny writer) returning the `File`. Recycle the bitmap after staging.
- The OCR screen (`CameraOcrFlowManager.onCropConfirmed` -> `saveBitmapToGallery`) is the sole gallery writer for OCR actions.
- **Verification:** `a.ps1 fkn` (screenCapture set builds under noLegal too) + `a.ps1 fk`; reasoning that only the cropped bitmap reaches the gallery for OCR actions.

### Phase 04 - Retire dead auto-translate path + supersede S1041

- If `AUTO_ACTION_TRANSLATE` has no remaining caller after Phases 02-03: remove the `AUTO_ACTION_TRANSLATE` handling and the S1041 `onImageReady` deferral wiring in [PhotoVideoStandaloneActivity](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt) and the `onImageReady` param in [StandaloneViewManager] if unused. Keep the manual `menu_translate_image` translate button (it reads an already-loaded drawable).
- Archive S1041 (`/spec-arc S1041`) as superseded; delete its `Timber.d("S1041:` tag.
- Widgets parity audit: confirm [CameraOcrTranslateWidgetProvider] + [CaptureOcrPanelWidgetProvider] land on `CameraOcrTranslateActivity`; adjust if they still route to the old path.
- **Verification:** `a.ps1 fk`; grep `AUTO_ACTION_TRANSLATE` and `S1041:` return nothing in `src/main`.

### Phase 05 - Strings, inventory, build, device gate

- No new user-visible strings expected (photo-OCR UI reused). If the suppressed "saved" toast leaves an OCR action with no feedback, add a short "opening OCR" affordance only if needed.
- Record delivered capability in `docs/ALL_FEATURES.jsonl` (CHANGE - screenshot OCR now uses the crop/language screen) on `Implemented`.
- Build `standard debug`; set `BlockNeedUserTest` with an on-device test note (gesture -> crop -> languages -> OCR/translate; only cropped saved). Device-test gate.

## Sequencing

01 -> 02 -> 03 are the core (entry, reroute, temp-stage). 04 is cleanup once the new path is proven to have no remaining caller of the old one. 05 finalizes. Phases 01, 02, 04 are lower-risk; 03 is the capture-pipeline change and the main verification focus.

## Implementation status (2026-07-13)

- [x] **Phase 01** - `startWithImage(File)` in `CameraOcrFlowManager`; `EXTRA_SOURCE_IMAGE_PATH` + `createIntent(context, path)` overload + onCreate branch in `CameraOcrTranslateActivity`.
- [x] **Phase 02** - dispatcher: `TAKE_PHOTO_OCR_TRANSLATE` -> `launchOcrCaptureFlow` (camera source); `OCR_TRANSLATE` post-save handled pre-save (service); `launchOcrCropFlow(context, file)` helper added.
- [x] **Phase 03** - `ScreenCaptureService.processCapture` resolves action first; OCR action stages the raw frame to `cacheDir/ocr_capture/*.png` (`stageOcrSourceFile`), skips gallery-save + "saved" toast, keeps clipboard copy, opens the crop flow. Non-OCR path unchanged.
- [x] **Phase 04** - rerouted the app-launch panel `takePhotoOcrTranslate` to `CameraOcrTranslateActivity`; removed the now-orphaned `AUTO_ACTION_TRANSLATE` const + `maybeRunAutoAction` branch + KDoc ref. Widgets already route to `CameraOcrTranslateActivity` (no change). S1041 `onImageReady` kept (still serves DRAW / CROP_AND_SHARE).
- [x] **Phase 05** - standard + noLegal compile PASS; `S1042` debug tag in `startWithImage`; status `BlockNeedUserTest`; device gate (see Last Audit).
- [x] **Phase 06 (post-device-test-1)** - the owner runs **noLegal** (`*-NoLegal-DEBUG`), whose screenshot backend is `ScreenshotAccessibilityService` (`src/noLegal`), NOT the standard `ScreenCaptureService`. Rev 1 only touched the standard service, so on noLegal `runPostSave(OCR_TRANSLATE)` became a no-op (saved raw screenshot, then nothing). Fix: shared `stageOcrSourceFile` moved into the dispatcher (`src/main`); the OCR pre-save branch mirrored into `ScreenshotAccessibilityService.saveBitmap` (skip save, stage temp, `launchOcrCropFlow`). Both backends now behave identically. Added capture-chain diagnostic probes (`S1042: capture action`, `OCR branch staged`, `launchOcrCropFlow startActivity OK/FAILED`).

## Last Audit

2026-07-13 - code-level review (device confirmation pending, `BlockNeedUserTest`).

- **Reuse over rewrite:** the entire crop -> language -> OCR/translate -> result path is shared with the photo flow; only a source-agnostic entry (`startWithImage`) was added. No crop-screen redesign.
- **Only cropped saved (owner input):** OCR actions no longer invoke `SaveScreenshotUseCase`; the raw frame is a private `cacheDir` PNG deleted after decode (`startWithImage` -> `cleanupTempFile`); the sole gallery writer for OCR is `onCropConfirmed` -> `saveBitmapToGallery`.
- **Clipboard preserved:** `copyScreenshotToClipboard` still fires from the live bitmap before staging; the "saved to gallery" toast is suppressed for OCR actions (raw frame not persisted).
- **No flavor guards in src/main (Rule 14):** the capture entry (`ScreenCaptureService`) lives in `src/screenCapture`; `src/main` code is flavor-agnostic. Both standard + noLegal compile.
- **When-exhaustiveness:** dispatcher `runPostSave` keeps an `OCR_TRANSLATE -> return` branch (service handles it pre-save) so the `when` stays total.
- **Dead-weight (Rule 20):** `AUTO_ACTION_TRANSLATE` fully removed - grep confirms zero references.

**Verified 2026-07-13 (owner, on-device noLegal):** the screenshot OCR gesture opens the crop + language + OCR/translate screen (not the full-screen viewer) and the flow works end to end. Crop-screen UX refinement folded in: removed the two-line `camera_ocr_crop_hint` (it pushed the preview so its bottom edge hid under the bottom command bar); preview keeps `fitCenter` above the bar. Debug `S1042:` probes removed; status `Verified`.

**Post-device-test-2 (noLegal backend):** the owner runs noLegal, whose screenshot backend is `ScreenshotAccessibilityService` (`src/noLegal`), not the standard `ScreenCaptureService`. The OCR pre-save branch was mirrored there; `stageOcrSourceFile` shared via the dispatcher so both backends behave identically.
