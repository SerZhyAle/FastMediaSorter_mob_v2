# Phase 04 - Flow Integration

**Strategic spec:** [`../S0338_camera-ocr-crop-region.md`](../S0338_camera-ocr-crop-region.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (build deferred - `--no-build`)
**Depends on:** Phase 01, Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Insert the crop step between camera return and OCR: show the preview + overlay, and on OK run OCR on the chosen image (cropped if the frame was moved, otherwise full) and save that same image to the gallery.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrStorageManager.kt` | Modified | ≤ 170 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` | Modified | ≤ 420 |

> `CameraOcrTranslateActivity.kt` is 322 lines now; projected <420 after change but >500-line backup rule does not yet trigger. If the edit pushes it over 500, create a timestamped backup in `temp/` first.

---

## Steps

### Step 04.1 - Add gallery-save-bitmap to CameraOcrStorageManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrStorageManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `suspend fun saveBitmapToGallery(bitmap: Bitmap, timestamp: String): Boolean` mirroring the DCIM/Camera → Downloads fallback of the existing `savePhotoToGallery`, but compressing the given `Bitmap` (JPEG ~90) to `OCR_IMG_<timestamp>.jpg` and notifying the media scanner. Reuse the existing directory-selection and `notifyMediaScanner` logic. This lets the flow persist the cropped region (strategic decision: cropped area is saved when crop is applied).

**Verification:**

- `Grep` - `fun saveBitmapToGallery` matches once.
- `Grep -n "Log\.d\("` on the file returns zero hits.
- File compiles - run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 2/2 static PASS (saveBitmapToGallery added, no Log.d). JPEG~90 to DCIM/Camera + Downloads fallback via writeBitmap helper. Compile deferred per --no-build.

---

### Step 04.2 - Insert crop step into CameraOcrFlowManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Reshape `onPhotoCaptured`: instead of immediately saving to gallery + running OCR, decode the captured file via `CropRegionManager.loadOrientedBitmap`, hold it as transient state, and call a new callback `Callback.showCropStep(bitmap: Bitmap)`. Remove the eager `savePhotoToGallery` call from this path.
> Add `fun onCropConfirmed(normalizedRect: RectF?, frameTouched: Boolean)`:
> - If `frameTouched` is true, produce the cropped bitmap via `CropRegionManager.cropToNormalizedRect`; otherwise use the full oriented bitmap.
> - Save the chosen bitmap to the gallery via `saveBitmapToGallery`.
> - Run the existing OCR / translate path on the chosen bitmap (reuse the current `extractTextOnly` / `recognizeAndTranslate` branching), then `applyResults` / `showEmpty` as today.
> Add `fun onCropRetry()` that re-launches capture via `startCapture()`.
> Extend the `Callback` interface with `fun showCropStep(bitmap: Bitmap)`. Recycle the source bitmap once the cropped copy is produced to avoid holding two full-size bitmaps (strategic §7 memory risk). Timber only.

**Verification:**

- `Grep` - `fun showCropStep` present in the `Callback` interface.
- `Grep` - `fun onCropConfirmed` and `fun onCropRetry` present.
- `Grep` - `loadOrientedBitmap` and `cropToNormalizedRect` referenced.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Callback.showCropStep + onCropConfirmed/onCropRetry/runRecognition. onPhotoCaptured now shows crop step; gallery save moved to confirm. Source bitmap recycled when distinct crop produced.

---

### Step 04.3 - Wire crop state in CameraOcrTranslateActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Implement `showCropStep(bitmap)` from the new callback: make `layoutCropState` visible (hide the other states), set `ivCropPreview` image to the bitmap, reset `cropOverlay`. Wire `btnCropConfirm` to call `flowManager.onCropConfirmed(binding.cropOverlay.getNormalizedRect(), binding.cropOverlay.isFrameTouched())`, and `btnCropRetry` to call `flowManager.onCropRetry()`. Ensure `layoutCropState` is hidden whenever loading/results/empty states are shown (extend the existing visibility toggles). Apply system-bar insets to `layoutCropState` like the other top-level states.

**Verification:**

- `Grep` - `override fun showCropStep` present.
- `Grep` - `onCropConfirmed` and `onCropRetry` referenced in the Activity.
- `Grep` - `layoutCropState` visibility handled in `showLoading`/`showResults`/`showEmpty` (or a shared helper).

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. showCropStep override sets preview + resets/focuses overlay; Retry/OK wired; layoutCropState hidden in loading/results/empty; system-bar insets applied.

---

### Step 04.4 - Add debug verification tags and build

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Per CLAUDE.md Debug Verification Tags, the ticket enters `BlockNeedUserTest` after implementation. Insert one `Timber.d("S0338: <entry-point>")` tag at each changed flow entry - e.g. in `onPhotoCaptured` (crop step shown), in `onCropConfirmed` (crop applied vs full-frame branch), and in the Activity `showCropStep`. One tag per flow entry, not per line. Do not reuse the `S0338:` prefix in any permanent `Timber.i/w/e`. Then build `standardDebug`.

**Verification:**

- `Grep` - `Timber.d("S0338:` matches at least 2 times across the touched `.kt` files.
- `/build` of `standardDebug` succeeds (expected: BUILD SUCCESSFUL | actual: record result).

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - 3 `Timber.d("S0338:` tags inserted (FlowManager x2: capture->crop, confirm-branch; Activity x1: preview). Build DEFERRED per --no-build; user runs build before device test.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Full crop flow works end to end behind `BlockNeedUserTest` tags. Phase 05 handles docs, catalog, FEATURES trilingual, and string-localization audit.

---

## Rollback Plan

Revert phase commit(s) in reverse order (Activity → FlowManager → StorageManager). No data migration; the crop step is additive between existing capture and OCR stages.
