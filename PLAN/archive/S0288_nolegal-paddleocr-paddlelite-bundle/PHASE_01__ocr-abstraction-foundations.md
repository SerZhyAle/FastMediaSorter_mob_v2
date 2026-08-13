# Phase 01 - ocr-abstraction-foundations

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Introduce the `OfflineOcrEngine` abstraction and refactor Tesseract to implement it. Provide default Hilt bindings in `src/main/` without affecting the `standard` build.

---

## Prerequisites

- [ ] Strategic §6 research items are resolved or under active investigation.
- [ ] Working tree is clean on the development branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OfflineOcrEngine.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OcrTextBlock.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/OcrModule.kt` | New | ≤ 60 |

---

## Steps

### Step 01.1 - Create OfflineOcrEngine interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OfflineOcrEngine.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the `OfflineOcrEngine` interface that defines general offline OCR processing capabilities.
> It must expose methods to recognize raw text and retrieve segmented text blocks with coordinates.
> Methods to include:
> `suspend fun recognizeText(bitmap: Bitmap, languageCode: String): String?`
> `suspend fun recognizeTextBlocks(bitmap: Bitmap, languageCode: String): List<OcrTextBlock>?`
> `fun release()`

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OfflineOcrEngine.kt` exists.
- `Grep` - `interface OfflineOcrEngine` matches exactly once.
- `Grep` - `suspend fun recognizeText` matches declaration.
- `Grep` - `suspend fun recognizeTextBlocks` matches declaration.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OfflineOcrEngine.kt (+26 LOC). Dev log recorded.

---

### Step 01.2 - Create OcrTextBlock data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OcrTextBlock.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the `OcrTextBlock` data class that models a recognized text fragment with its spatial coordinates and confidence.
> The class must expose `text: String`, `boundingBox: Rect`, and `confidence: Float`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OcrTextBlock.kt` exists.
- `Grep` - `data class OcrTextBlock` matches exactly once.
- `Grep` - `val boundingBox: Rect` matches declaration.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OcrTextBlock.kt (+13 LOC). Dev log recorded.

---

### Step 01.3 - Refactor TesseractManager to implement OfflineOcrEngine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Refactor the existing `TesseractManager` to implement `OfflineOcrEngine`.
> Map `recognizeTextBlocks` return type from `TranslationManager.TesseractTextBlock` to the new `OcrTextBlock`.
> Ensure all existing Tesseract functionality, including fallbacks and download management, remains fully intact.
> Note: Keep `TesseractManager` class name but extend `OfflineOcrEngine`.

**Verification:**

- `Grep` - `class TesseractManager(.*) : OfflineOcrEngine` matches exactly once.
- `Grep` - `override suspend fun recognizeText` matches declaration.
- `Grep` - `override suspend fun recognizeTextBlocks` matches declaration.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt (-12 LOC). Dev log recorded.

---

### Step 01.4 - Introduce Hilt OcrModule for standard engines binding

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/OcrModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create a new Hilt module `OcrModule` in `src/main/java/com/sza/fastmediasorter/di/`.
> The module should provide default binding or factory instances of `OfflineOcrEngine` (e.g. using `@Provides` or `@Binds` returning `TesseractManager` instance as the default offline engine).
> Annotate the module with `@Module` and `@InstallIn(SingletonComponent::class)`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/di/OcrModule.kt` exists.
- `Grep` - `@Module` matches exactly once.
- `Grep` - `fun provideOfflineOcrEngine` matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/di/OcrModule.kt (+20 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Abstraction of OCR is complete. Tesseract now implements `OfflineOcrEngine`. Default wiring via Hilt is in place.
We are now ready to extend the Settings model and UI in Phase 02.

---

## Rollback Plan

Revert phase commits. Delete new files under `domain/ocr/` and `di/OcrModule.kt`. Revert `TesseractManager.kt`.
