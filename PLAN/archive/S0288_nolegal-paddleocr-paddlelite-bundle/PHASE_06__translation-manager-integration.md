# Phase 06 - translation-manager-integration

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Integrate the `OfflineOcrEngine` registry or factory into `TranslationManager.kt`. Update OCR pipeline methods to utilize the user-selected engine from `AppSettings` (Tesseract or PaddleOCR). Ensure strict compile-time isolation is maintained in standard builds.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 04 is ✅ Done.
- [ ] Phase 05 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OfflineOcrEngineProvider.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OcrEngineContributor.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/OcrContributorModule.kt` | New | ≤ 40 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngineContributor.kt` | New | ≤ 80 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/PaddleOcrModule.kt` | Modified | ≤ 60 |

---

## Steps

### Step 06.1 - Inject OfflineOcrEngine or provider into TranslationManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Modify `TranslationManager.kt` constructor to inject `OfflineOcrEngine` (or a Hilt `Provider<OfflineOcrEngine>` or a factory).
> Since Hilt handles flavor overrides, `TranslationManager` can simply inject `OfflineOcrEngine` abstraction.
> In `standard` flavor, Hilt resolves this to `TesseractManager` (acting as the sole engine).
> In `noLegal` flavor, Hilt provides the engine registry or a wrapper that selects `TesseractManager` or `PaddleOcrEngine` at runtime.

**Verification:**

- `Grep` - `OfflineOcrEngine` or a provider is added as constructor parameter in `TranslationManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: `TranslationManager.kt`, `OfflineOcrEngineProvider.kt`, `OcrEngineContributor.kt`, `OcrContributorModule.kt`, `PaddleOcrEngineContributor.kt`, `PaddleOcrModule.kt`. Dev log recorded.

---

### Step 06.2 - Redirect recognizeText calls based on Settings configuration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Update `recognizeText(bitmap, sourceLangCode)` inside `TranslationManager.kt`.
> Instead of directly invoking `tesseractManager.recognizeText(...)`, fetch `settings.ocrEngineType` from preferences.
> If engine type is `PADDLE_OCR` and language matches Cyrillic, route the bitmap through `PaddleOcrEngine` instance.
> Otherwise, fall back to default `TesseractManager` or ML Kit Latin OCR as before.

**Verification:**

- `Grep` - `ocrEngineType` matches inside `recognizeText` method.
- `Grep` - `PaddleOcrEngine` or delegate logic exists.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: `TranslationManager.kt`, `OfflineOcrEngineProvider.kt`, `PaddleOcrEngineContributor.kt`. Dev log recorded.

---

### Step 06.3 - Update block-based OCR translation logic

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Update `recognizeAndTranslateBlocks(bitmap, sourceLang, targetLang)` inside `TranslationManager.kt`.
> If `settings.ocrEngineType` is `PADDLE_OCR`, invoke `recognizeTextBlocks` from the active `PaddleOcrEngine` instance.
> Handle returned `OcrTextBlock` objects, filtering them by confidence, and translating original text blocks to target language.
> Maintain standard Latin ML Kit and Tesseract flows as robust fallbacks if PaddleOCR is not selected or fails.

**Verification:**

- `Grep` - `recognizeTextBlocks` called on the resolved engine inside `recognizeAndTranslateBlocks`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: `TranslationManager.kt`, `OfflineOcrEngineProvider.kt`. Dev log recorded.

---

### Step 06.4 - Show progress animation during OCR translation in PlayerImageTranslationManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> In `PlayerImageTranslationManager.kt`, update `translateCurrentImage()` to display the progress indicator while OCR processing is running.
> Before launching the translation job or in the beginning of the scope, set `activity.activityBinding.progressBar.isVisible = true`.
> In the translation callbacks (`onSuccess`, `onEmpty`, `onError`) and inside the `finally` or exception handling block of the coroutine, ensure `activity.activityBinding.progressBar.isVisible = false` is called to hide the progress bar.
> Ensure that this progress animation is properly managed under all execution paths (Google Lens path and Legacy text viewer path).

**Verification:**

- `Grep` - `progressBar.isVisible = true` in `PlayerImageTranslationManager.kt`.
- `Grep` - `progressBar.isVisible = false` in `PlayerImageTranslationManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: `PlayerImageTranslationManager.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS + `assembleNoLegalDebug` PASS (2026-05-21).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`TranslationManager` integration is completed. Selected OCR pipelines run via Tesseract or PaddleOCR depending on settings.
We are ready to proceed with documentation updates, catalog sync, and final code cleanup in Phase 07.

---

## Rollback Plan

Revert phase commits. Revert `TranslationManager.kt` changes.
