# Phase 03 - OCR-translate flow uses in-app capture

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Switch the Camera-OCR-Translate capture from `MediaStore.ACTION_IMAGE_CAPTURE` to the in-app `CameraCaptureActivity`, so the shutter leads straight to the existing crop + language screen with no OEM confirmation screen. Denial shows the rationale and exits.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`CameraCaptureActivity` available).
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` | Modified | ≤ 410 |

---

## Steps

### Step 03.1 - Build the in-app capture intent in the flow manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `startCapture()`, replace the `Intent(MediaStore.ACTION_IMAGE_CAPTURE)` construction with an intent targeting `CameraCaptureActivity` (via its `createIntent(context, uri)`), keeping the existing `pendingTempFile` + capture-URI preparation unchanged (the output URI semantics are identical). The result still arrives through `Callback.onPhotoCaptured()` / `onCaptureCancelled()`. Remove the now-unused `MediaStore.ACTION_IMAGE_CAPTURE` import. WHY-comment: in-app capture removes the OEM confirmation step (S0359).

**Verification:**

- `Grep -n "ACTION_IMAGE_CAPTURE"` returns zero hits in `CameraOcrFlowManager.kt`.
- `Grep` - `CameraCaptureActivity` referenced in `CameraOcrFlowManager.kt`.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

### Step 03.2 - Activity launcher + denial handling

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> The existing `cameraLauncher` (a `StartActivityForResult`) already handles `RESULT_OK -> onPhotoCaptured()` and else `onCaptureCancelled()`; it works unchanged because `CameraCaptureActivity` returns `RESULT_OK`. Confirm `launchCamera(intent)` simply launches the supplied intent. If the flow needs to surface the CAMERA-denied rationale at the Activity level (when `CameraCaptureActivity` returns `RESULT_CANCELED` due to denial), keep the current quiet finish - the rationale is shown inside `CameraCaptureActivity`. No duplicate permission prompt here.

**Verification:**

- `Grep` - `cameraLauncher` present and unchanged contract (`onPhotoCaptured` / `onCaptureCancelled`).
- `Grep -n "ACTION_IMAGE_CAPTURE"` returns zero hits in `CameraOcrTranslateActivity.kt`.

**Status:** `[x] done`

---

### Step 03.3 - Verify on device path tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> On the `BlockNeedUserTest` transition, insert one `Timber.d("S0359: in-app camera capture launched for OCR")` at the `startCapture()` entry, and remove it again when the spec leaves that status.

**Verification:**

- `Grep -n "Log\.d\("` returns zero hits in the file.
- `Grep` - `S0359: in-app camera capture launched for OCR` present once in `CameraOcrFlowManager.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug (translation-capable flavor).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -n "ACTION_IMAGE_CAPTURE"` zero hits across `ui/cameraocr/`.
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

OCR capture is now fully in-app. The OEM Retry/OK screen no longer appears for the OCR flow (the original S0359 trigger). Phase 06 applies the same swap to the Browse capture flow.

---

## Rollback Plan

Revert phase commit(s) - restoring the `ACTION_IMAGE_CAPTURE` intent in `startCapture()` returns the OCR flow to the system camera. No persisted state changed.
