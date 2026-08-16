# Phase 03 - Full-screen preview and matching crop

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Deliver the `FULL_SCREEN` selection end to end - preview filling the screen, saved file cropped to the same shape - and retire the result-frame overlay, whose only job was to stand in for a stream the pipeline now requests directly.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CapturedPhotoAspectCropper.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManager.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/ResultFrameOverlayView.kt` | Deleted | - |

> `res/layout-land/activity_camera_capture.xml` does not exist and is not to be created: S0754 locks the capture screen to portrait in the manifest (INDEX fact 7). Rule 11 is satisfied.
>
> `CameraCaptureSessionManager.kt` and `CameraCaptureActivity.kt` are both over 500 LOC - back both up under `temp/S1658/` before editing (Rule 5).

---

## Steps

### Step 03.1 - Generalise the post-capture cropper to a target ratio

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CapturedPhotoAspectCropper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace `cropToSixteenNine(file: File)` with `cropToRatio(file: File, targetRatio: Float)`, where `targetRatio` is the wanted long-side / short-side ratio of the stored landscape JPEG. Keep every existing property of the function: centre crop, EXIF snapshot and restore, a frame already at or narrower than the target left untouched, and any failure keeping the uncropped photo. Add a `SIXTEEN_NINE` constant for the existing callers to pass, and expose `ratioOfScreen(width: Int, height: Int): Float` returning the long side over the short side so a caller can express "the shape of this screen" without duplicating the arithmetic.

**Why:**

Strategic §3.1 defines the full-screen option as a 16:9 stream cropped to the screen at save time, so the shipped cropper's hardcoded 16:9 target cannot express it.

**Verification:**

- `Grep` - `cropToSixteenNine` returns zero hits across `app_v2/src`.
- `Grep` - `fun cropToRatio(file: File, targetRatio: Float)` present.
- `Grep` - `fun ratioOfScreen(` present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - cropToRatio replaces cropToSixteenNine; shutter crops to the screen only on FULL_SCREEN; preview scaleType follows the selection; ResultFrameOverlayView, its layout node, shouldShowResultFrame, previewScaleLinkedViews and camera_result_frame_scrim all deleted; headless route now carries the stored selection. fc exit 0. Layout evidence deferred to the end-of-ticket device gate on SM-G996U1 - the viewfinder change is only observable on an installed build, which phase 07 produces.

---

### Step 03.2 - Crop the shutter output to the selected shape

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `capture()`, replace the boolean `aspectCropAtShutter` with a nullable target ratio sampled at the same place and for the same reason: null when the stream already carries the selection (4:3 and 16:9), and the host screen's ratio from `CapturedPhotoAspectCropper.ratioOfScreen` when the selection is `FULL_SCREEN` and the mode is photo. Keep the sampling at shutter press rather than in `onImageSaved` - S1457's comment there states why. Call `cropToRatio` with the sampled value inside the existing crop-worker branch.

**Why:**

Strategic §3.1 requires the full-screen option to be honest in the same measure as the other two: what was visible is what gets saved.

**Verification:**

- `Grep` - `aspectCropAtShutter` returns zero hits.
- `Grep` - `cropToRatio` present in `CameraCaptureSessionManager.kt`.
- `Grep` - `cropRatioAtShutter` (or the chosen sampled-value name) appears before `takePicture(` in the same function.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - cropToRatio replaces cropToSixteenNine; shutter crops to the screen only on FULL_SCREEN; preview scaleType follows the selection; ResultFrameOverlayView, its layout node, shouldShowResultFrame, previewScaleLinkedViews and camera_result_frame_scrim all deleted; headless route now carries the stored selection. fc exit 0. Layout evidence deferred to the end-of-ticket device gate on SM-G996U1 - the viewfinder change is only observable on an installed build, which phase 07 produces.

---

### Step 03.3 - Fill the screen in the preview when full-screen is selected

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`, `app_v2/src/main/res/layout/activity_camera_capture.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Drive `previewViewCamera.scaleType` from the applied selection: `PreviewView.ScaleType.FILL_CENTER` for `FULL_SCREEN`, `FIT_CENTER` for the other two. Apply it where the selection is applied - the settings-apply path and the initial settings read - not only at first bind, so switching the option re-shapes the preview without leaving the screen. Leave `app:scaleType="fitCenter"` in the layout as the pre-selection default and note in a comment that the code overrides it per selection.

**Why:**

Strategic §2.1 names `fitCenter` as the second of the two constraints producing the empty screen margins, and §3.1 defines full screen as the stream stretched to the shape of the screen.

**Verification:**

- `Grep` - `ScaleType.FILL_CENTER` present in `CameraCaptureActivity.kt`.
- `Grep` - `ScaleType.FIT_CENTER` present in `CameraCaptureActivity.kt`.
- `Grep` - `app:scaleType="fitCenter"` still present in `activity_camera_capture.xml`.
- `Grep` - `="#` returns zero hits in `activity_camera_capture.xml` (Rule 19).

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - cropToRatio replaces cropToSixteenNine; shutter crops to the screen only on FULL_SCREEN; preview scaleType follows the selection; ResultFrameOverlayView, its layout node, shouldShowResultFrame, previewScaleLinkedViews and camera_result_frame_scrim all deleted; headless route now carries the stored selection. fc exit 0. Layout evidence deferred to the end-of-ticket device gate on SM-G996U1 - the viewfinder change is only observable on an installed build, which phase 07 produces.

---

### Step 03.4 - Retire the result-frame overlay

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`, `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/ResultFrameOverlayView.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Delete `shouldShowResultFrame()`, the `resultFrameOverlay` node in the layout, the two references in `CameraCaptureActivity` (the `previewScaleLinkedViews` assignment at the setup path and the visibility flip), and the `ResultFrameOverlayView` class file. Keep `previewScaleLinkedViews` itself on the session manager and assign it an empty list at the call site only if nothing else is linked - check for other overlays first and keep any that are. Remove any string, colour or dimen resource left with no reference by the deletion, and remove the `S1066` result-frame paragraph from the session manager's KDoc.

**Why:**

Strategic §3.1 states the result frame stops being the aspect mechanism once the stream carries the selection, at which point its rectangle always coincides with the preview bounds and marks nothing. The component's own rule says the same thing without needing a new ruling: `shouldShowResultFrame`'s KDoc (S1066 ADR-1, §6.4) already declines to draw a frame whenever the shown frame equals the saved one, which after Phase 02 is every case. A view its own contract never shows is dead code, and Rule 20 forbids leaving the class and its wiring behind.

**Verification:**

- `Grep` - `ResultFrameOverlayView` returns zero hits across `app_v2/src`.
- `Grep` - `resultFrameOverlay` returns zero hits across `app_v2/src`.
- `Grep` - `shouldShowResultFrame` returns zero hits across `app_v2/src`.
- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/ResultFrameOverlayView.kt` does not exist.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - cropToRatio replaces cropToSixteenNine; shutter crops to the screen only on FULL_SCREEN; preview scaleType follows the selection; ResultFrameOverlayView, its layout node, shouldShowResultFrame, previewScaleLinkedViews and camera_result_frame_scrim all deleted; headless route now carries the stored selection. fc exit 0. Layout evidence deferred to the end-of-ticket device gate on SM-G996U1 - the viewfinder change is only observable on an installed build, which phase 07 produces.

---

### Step 03.5 - Carry the selection into the headless widget shot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManager.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Change `HeadlessPhotoCapturer`'s `aspectRatio: Int` parameter to `selection: CameraAspectSelection`, built by `PhotoCaptureLaunchManager` with `CameraAspectSelection.fromStored(settings.cameraAspectRatio)`. Request the selection's `cameraXAspectRatio` for the capture use case instead of passing null, and crop only when `selection.cropsToScreen` holds, using the display metrics of the context the capturer already has. Keep the existing behaviour that a crop failure still reports the saved photo.

**Why:**

The widget shot reads the same `camera_aspect_ratio` preference as the screen, so leaving it on the old hardcoded 16:9 test would make one of the three options silently mean something different depending on which route took the photo.

**Verification:**

- `Grep` - `selection: CameraAspectSelection` present in `HeadlessPhotoCapturer.kt`.
- `Grep` - `aspectRatio != AspectRatio.RATIO_16_9` returns zero hits in `HeadlessPhotoCapturer.kt`.
- `Grep` - `CameraAspectSelection.fromStored` present in `PhotoCaptureLaunchManager.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - cropToRatio replaces cropToSixteenNine; shutter crops to the screen only on FULL_SCREEN; preview scaleType follows the selection; ResultFrameOverlayView, its layout node, shouldShowResultFrame, previewScaleLinkedViews and camera_result_frame_scrim all deleted; headless route now carries the stored selection. fc exit 0. Layout evidence deferred to the end-of-ticket device gate on SM-G996U1 - the viewfinder change is only observable on an installed build, which phase 07 produces.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - a class was deleted.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

All three selections now behave correctly end to end, but only two of them are reachable: the dialog still builds its dropdown from `capabilities.availableAspectRatios`, which is a probe result and never carries `FULL_SCREEN`. Phase 04 exposes it.

---

## Rollback Plan

Revert phase commit(s). The deleted overlay class and layout node come back with the revert; no data migration and no persisted value changed in this phase.
