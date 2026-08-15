# Phase 01 - Crop Strings & Geometry

**Strategic spec:** [`../S0338_camera-ocr-crop-region.md`](../S0338_camera-ocr-crop-region.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (build/test deferred - `--no-build`)
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add trilingual crop-step strings and an EXIF-aware bitmap crop helper that maps a normalized selection rectangle to a cropped bitmap; no UI wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CropRegionManager.kt` | New | ≤ 150 |

---

## Steps

### Step 01.1 - Add trilingual crop-step strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these string keys to all three `strings.xml` files (EN, RU, UK) near the existing `camera_ocr_*` block. Use `..` not `...` and `ё`/`Ё` in Russian where correct. Keys and English text:
> - `camera_ocr_crop_title` = "Select area"
> - `camera_ocr_crop_hint` = "Drag the frame to crop, or tap OK to use the whole photo"
> - `camera_ocr_crop_retry` = "Retry"
> - `camera_ocr_crop_confirm` = "OK"
> - `camera_ocr_crop_frame_desc` = "Crop selection frame" (content description for the overlay)
>
> RU values must use `ё`/`Ё` where applicable (e.g. "Выделите область", "всё фото"). Verify the new strings pass `docs/COMMUNICATION_POLICY.md` §2 (instructional/action formula) and §6 (tone checklist) - short, action-oriented, no jargon.

**Verification:**

- `Grep` - `camera_ocr_crop_title` matches once in each of the three files.
- `Grep` - `camera_ocr_crop_confirm` matches once in each of the three files.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Files: values/-ru/-uk strings.xml (+5 keys each). Strings short/action-oriented, policy §6 OK.

---

### Step 01.2 - Create CropRegionManager (EXIF-aware load + crop)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CropRegionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create class `CropRegionManager` (no constructor dependencies). Provide:
> - `fun loadOrientedBitmap(file: File): Bitmap?` - decode the file via `BitmapFactory`, read EXIF orientation via `androidx.exifinterface.media.ExifInterface`, and return a bitmap rotated/flipped to display orientation so preview, OCR and saved image are consistent. Return `null` on failure and log via `Timber.e`.
> - `fun cropToNormalizedRect(source: Bitmap, rect: RectF): Bitmap` - `rect` holds normalized coordinates in `[0f,1f]` (left/top/right/bottom). Convert to integer pixel bounds, clamp inside the bitmap, enforce a minimum side of 1px, and return `Bitmap.createBitmap(source, x, y, w, h)`. If the rect covers the full image, may return `source` unchanged.
>
> Logging: Timber only (no `Log.d`). Keep all geometry pure and side-effect free for unit testing.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CropRegionManager.kt` exists.
- `Grep` - `class CropRegionManager` matches exactly once.
- `Grep` - `fun loadOrientedBitmap` and `fun cropToNormalizedRect` both present.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Files: CropRegionManager.kt (New, +130 LOC). loadOrientedBitmap + cropToNormalizedRect + pure toPixelRect; Timber only.

---

### Step 01.3 - Unit-test crop geometry

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameraocr/CropRegionManagerTest.kt` (New, ≤ 120)
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a JVM unit test for `cropToNormalizedRect` geometry that does not require Android bitmap internals where avoidable. If `Bitmap.createBitmap` cannot run on JVM without Robolectric, restrict the test to the pixel-bounds conversion (extract the normalized-rect → integer `Rect` mapping into an internal pure function `toPixelRect(width, height, rect): Rect` and test that). Cover: full-frame rect maps to full bounds; a centered half rect maps to the expected integer bounds; out-of-range values clamp to image bounds; degenerate rect clamps to minimum 1px side.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameraocr/CropRegionManagerTest.kt` exists.
- `Grep` - `toPixelRect` referenced in both `CropRegionManager.kt` and the test.
- Test class compiles and the targeted test passes (run only this test class).

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Static verification PASS (file exists, toPixelRect referenced in both). Robolectric test for full/half/clamp/degenerate cases authored. Test EXECUTION deferred per --no-build.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `CropRegionManager` class) via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`CropRegionManager` supplies oriented bitmaps and normalized-rect cropping. Phase 02's overlay view emits the normalized `RectF` consumed here; Phase 04 wires both into the flow.

---

## Rollback Plan

Revert phase commit(s) - new strings and a new helper class; no data migration or user-facing surface changed yet.
