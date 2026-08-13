# Phase 04 - Shared aspect crop

**Strategic spec:** [`../S1478_bugfix-headless-capture-ignores-camera-settings.md`](../S1478_bugfix-headless-capture-ignores-camera-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Extract the 16:9 post-capture crop into a helper both capture paths call, and apply it to the headless shot when the stored aspect-ratio setting says 16:9.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [x] Backup taken for `CameraCaptureSessionManager.kt` - it is 1064 LOC, over the 500-LOC backup threshold (CLAUDE.md Rule 5). See Step 04.1.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CapturedPhotoAspectCropper.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManager.kt` | Modified | ≤ 240 |

---

## Steps

### Step 04.1 - Back up the session manager before editing it

**Files:** `temp/S1478/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `CameraCaptureSessionManager.kt` to `temp/S1478/` under a timestamped name before any edit in this phase.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup under `temp/` before editing a file over 500 LOC, and this one is 1064.

**Verification:**

- `Glob` - a copy of `CameraCaptureSessionManager` exists under `temp/S1478/`.

**Status:** `[x] done`

---

### Step 04.2 - Extract the crop and its EXIF restore into `CapturedPhotoAspectCropper`

**Files:** `app_v2/.../helpers/CapturedPhotoAspectCropper.kt`, `app_v2/.../helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Move `cropToSixteenNine(file)` out of `CameraCaptureSessionManager` into a new `CapturedPhotoAspectCropper` in the same package, taking the `restoreExif` helper and the `JPEG_QUALITY` / `SIXTEEN_NINE_*` constants it depends on. Preserve the behaviour exactly, including the early return when the frame is already at or below 16:9 and the `runCatching` that downgrades a failure to a warning rather than losing the photo. Have the session manager call the helper instead of its own method. Do not add a method to the session manager - it sits on detekt's `TooManyFunctions` ceiling, and this extraction should lower its count, not raise it.

**Why:**

Strategic §3.4 forbids leaving the crop as a private method on a class with no detekt slack and equally forbids copying it, because a second copy would create exactly the divergence between the two capture paths that this ticket exists to remove.

**Verification:**

- `Glob` - `CapturedPhotoAspectCropper.kt` exists.
- `Grep` - `cropToSixteenNine` no longer declared in `CameraCaptureSessionManager.kt`.
- `Grep` - `CameraCaptureSessionManager.kt` references `CapturedPhotoAspectCropper`.
- `Grep` - `restoreExif` is called inside `CapturedPhotoAspectCropper.kt`.

**Status:** `[x] done`

---

### Step 04.3 - Pass the stored aspect ratio to the headless capture

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Read `settings.cameraAspectRatio` in `start()` alongside the `cameraGeotagEnabled` read that already happens there, and pass it down to the capture call the way `location` is passed today.

**Why:**

Strategic §3.4 names this manager as the component that already reads settings for this path, so the aspect ratio needs no new plumbing - only the field that was never read.

**Verification:**

- `Grep` - `cameraAspectRatio` referenced in `PhotoCaptureLaunchManager.kt`.
- `Grep` - it is read from the same settings object as `cameraGeotagEnabled`.

**Status:** `[x] done`

---

### Step 04.4 - Crop the headless result when the setting says 16:9

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> After `onImageSaved` and before the success callback, invoke `CapturedPhotoAspectCropper` on the output file when the passed aspect ratio is 16:9. Run it off the main thread - it decodes and re-encodes a JPEG - and release the camera first so the device is not held during the crop. A crop failure must still report success: the uncropped photo is saved and losing it would be worse than wrong proportions.

**Why:**

Strategic §3.4 requires the headless path to reach the same saved result as the on-screen path, which realises a 16:9 selection by cropping the 4:3 sensor frame rather than by narrowing the stream (S1066).

**Verification:**

- `Grep` - `CapturedPhotoAspectCropper` referenced in `HeadlessPhotoCapturer.kt`.
- `Grep` - the crop call is not on the main executor path - it runs on a background dispatcher or executor.
- `Grep -n "Log\.d\("` returns zero hits in `HeadlessPhotoCapturer.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `CameraCaptureSessionManager` declares no more functions than before this phase - it declares two fewer.
- [x] EXIF survival is NOT claimed statically beyond "restoreExif is called" - the GPS-tag check is device-only.

---

## Step Log

- 2026-08-07 - Step 04.1 Verification 1/1 PASS. Backup at `temp/S1478/CameraCaptureSessionManager.kt.20260807_1905.bak`.
- 2026-08-07 - Step 04.2 Verification 4/4 PASS. `CapturedPhotoAspectCropper` created with `cropToSixteenNine` plus the `restoreExif` helper, `PRESERVED_EXIF_TAGS`, `JPEG_QUALITY` and the `SIXTEEN_NINE_*` constants. Behaviour copied unchanged, including both early returns and the failure-swallowing `runCatching`. The session manager calls the object and declares two functions FEWER than before (`cropToSixteenNine` and the top-level `restoreExif` both left the file), so its detekt headroom grew.
- 2026-08-07 - Step 04.3 Verification 2/2 PASS. `PhotoCaptureLaunchManager.start()` reads `settings.cameraAspectRatio` from the same settings object as `cameraGeotagEnabled` and passes it to `capture()`.
- 2026-08-07 - Step 04.4 Verification 3/3 PASS. Crop runs on a single-thread executor after `release()`, so the camera device is free while the JPEG is re-encoded, and success is reported back on the main executor. Phase-boundary audit (Layer 2) found the worker could outlive the shot if the crop ever threw; fixed in the same phase by shutting the executor down immediately after submission.
- Compile: `.\a.ps1 fk` exit 0.
- EXIF survival beyond "restoreExif is called" is NOT claimed - the GPS-tag check is device-only.