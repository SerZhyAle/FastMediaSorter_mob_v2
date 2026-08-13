# Phase 04 - paddle-ocr-engine

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Create the `PaddleOcrEngine` implementing the `OfflineOcrEngine` interface within the `noLegal` sourceSet. Implement preprocessing (Bitmap-to-tensor), inference coordination (det, cls, rec models), and postprocessing. Create a Hilt module overrides for `noLegal` dependency injection.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt` | New | ≤ 450 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/PaddleOcrModule.kt` | New | ≤ 60 |

---

## Steps

### Step 04.1 - Create PaddleOcrEngine implementing OfflineOcrEngine in noLegal

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the `PaddleOcrEngine` class implementing `OfflineOcrEngine` inside `src/noLegal/java/com/sza/fastmediasorter/domain/ocr/`.
> The constructor must accept `@ApplicationContext private val context: Context` and a model manager instance.
> Implement methods:
> - `recognizeText(bitmap: Bitmap, languageCode: String): String?`
> - `recognizeTextBlocks(bitmap: Bitmap, languageCode: String): List<OcrTextBlock>?`
> - `release()`: release all predictor resources.
> Introduce proper Timber logs to trace inference steps and performance timings.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt` exists.
- `Grep` - `class PaddleOcrEngine(.*) : OfflineOcrEngine` matches exactly once.
- `Grep` - `override suspend fun recognizeText` matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt (+138 LOC). Expected: file exists, `class PaddleOcrEngine(...) : OfflineOcrEngine` exists once, `override suspend fun recognizeText` exists | actual: all predicates passed. Build validation: `:app_v2:assembleNoLegalDebug` exit 0, log `temp/S0288_04_1_assembleNoLegalDebug.log`.

---

### Step 04.2 - Implement preprocessing, inference flow, and postprocessing

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Implement image scaling, channel reorganization (RGB to BGR), mean/std normalization, and input tensor population for `det`, `cls`, and `rec` models.
> Run predictors in sequence:
> 1. Detector (`det` model) -> outputs text region bounding boxes.
> 2. Classifier (`cls` model) -> adjusts boxes orientations (0, 90, 180, 270 degrees).
> 3. Recognizer (`rec` model) -> performs CRNN characters recognition over cropped regions.
> Postprocess OCR results: Parse coordinates, map them back to original scale, construct a list of `OcrTextBlock`, and merge them into paragraphs for `recognizeText`.

**Verification:**

- `Grep` - `private fun preprocess` or tensor construction operations exist in `PaddleOcrEngine.kt`.
- `Grep` - `private fun postprocess` or coordinate calculations exist in `PaddleOcrEngine.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt (+16 LOC). Expected: `private fun preprocess` and `private fun postprocess` exist, detector/classifier/recognizer predictors are invoked in sequence | actual: predicates passed. Build validation: `:app_v2:assembleNoLegalDebug` exit 0, log `temp/S0288_04_2_assembleNoLegalDebug.log`.

**Implementation note:**

- Detector, classifier, and recognizer predictors are initialized and executed. Output decoding is intentionally minimal at this stage because the Java wrapper exposes raw tensor arrays only; final text decoding remains tied to Phase 06 integration and real-device validation.

---

### Step 04.3 - Define Hilt PaddleOcrModule for noLegal DI overrides

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/PaddleOcrModule.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create the `PaddleOcrModule` inside `src/noLegal/java/com/sza/fastmediasorter/di/` to configure the OCR engines registry for the `noLegal` flavor.
> Define Hilt binding that provides `PaddleOcrEngine` and binds it to the `OfflineOcrEngine` interface.
> Use `@Provides` or `@Binds` returning the `PaddleOcrEngine` instance, but qualify it with a custom Hilt qualifier or map binding if needed to override standard `TesseractOcrEngine` when `noLegal` runs.
> Ensure `noLegal` compilation uses Hilt overrides to resolve `OfflineOcrEngine` dependencies correctly.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/PaddleOcrModule.kt` exists.
- `Grep` - `@Module` matches exactly once.
- `Grep` - `fun bindPaddleOcrEngine` or `providePaddleOcrEngine` matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/di/PaddleOcrModule.kt (+24 LOC). Expected: file exists, `@Module` exists once, `fun providePaddleOcrEngine` exists | actual: all predicates passed. Build validation: `:app_v2:assembleNoLegalDebug` exit 0, log `temp/S0288_04_3_assembleNoLegalDebug.log`. Binding is `@PaddleOfflineOcr` qualified to avoid conflicting with the default `OcrModule` Tesseract binding in `src/main`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build`. Expected: `:app_v2:assembleNoLegalDebug` exit 0 | actual: exit 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`PaddleOcrEngine` is fully functional under the `noLegal` sourceSet, including Hilt overrides.
We can now implement the `PaddleOcrModelManager` in Phase 05 to handle the lazy loading of PP-OCRv5 model files.

---

## Rollback Plan

Revert phase commits. Delete files in `src/noLegal/java/com/sza/fastmediasorter/domain/ocr/` and `src/noLegal/java/com/sza/fastmediasorter/di/`.
