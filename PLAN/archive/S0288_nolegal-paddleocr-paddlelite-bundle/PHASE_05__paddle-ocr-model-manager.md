# Phase 05 - paddle-ocr-model-manager

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Create `PaddleOcrModelManager` in the `noLegal` sourceSet to manage the lazy loading, downloading, and SHA-256 validation of the optimized `.nb` model files for the PP-OCRv5 pipeline (det, cls, and rec models).

---

## Prerequisites

- [ ] Strategic research on model sizes and SHA-256 checksums is completed.
- [ ] Remote model repository hosting verified `.nb` files is configured.
- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt` | New | ≤ 250 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt` | Modified | ≤ 500 |

---

## Steps

### Step 05.1 - Create PaddleOcrModelManager class in noLegal

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the `PaddleOcrModelManager` class inside `src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/`.
> The constructor must accept `@ApplicationContext private val context: Context`.
> Implement features to track whether models (`det.nb`, `cls.nb`, and `rec.nb`) are installed locally.
> Store the downloaded files inside the app's internal files subdirectory `files/paddleocr/`.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt` exists.
- `Grep` - `class PaddleOcrModelManager` matches exactly once.
- `Grep` - `fun isModelInstalled` matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt (+54 LOC). Expected: file exists, `class PaddleOcrModelManager` exists once, `fun isModelInstalled` exists | actual: all predicates passed. Build validation: `:app_v2:assembleNoLegalDebug` exit 0, log `temp/S0288_05_1_assembleNoLegalDebug.log`. Dev log and catalog sync recorded.

---

### Step 05.2 - Implement lazy downloading, progress callbacks, and SHA-256 checks

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Implement network download logic for fetching model files from a remote repository using secure HTTP connections.
> Add integrity validation: Calculate SHA-256 checksum of downloaded files and match them against hardcoded valid hashes for PP-OCRv5 det, cls, cyrillic rec, and east-slavic rec models.
> Provide progress updates via callbacks or a Kotlin Flow so the player can display an active progress indicator while models are being fetched.

**Verification:**

- `Grep` - `fun downloadModel` or download task matches.
- `Grep` - `verifyChecksum` or SHA-256 calculations exist in `PaddleOcrModelManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt (+140 LOC). Expected: `downloadModel` and `verifyChecksum` exist | actual: both predicates passed. Build validation: `:app_v2:assembleNoLegalDebug` exit 0, log `temp/S0288_05_2_assembleNoLegalDebug.log`. Source artifacts: official PaddleX-Lite PP-OCRv5 `.nb` tarballs. SHA-256 expected and actual: det `BEE84FDB3D7C5F312A797DA81F2DD44C89088C08E1F58A51044A823CDDFBD90C`, cls `A8955B3715620810789A012531F1E6DA796FB7D252B26B74D2886B350D698DBB`, generic rec `9D073B3EE01DEEE358BF929DD8952D4D355C9545F4A93D8070605581B4C21C0C`.

**Implementation note:**

- Official Paddle-Lite `.nb` assets exist for `PP-OCRv5_mobile_det`, `PP-LCNet_x0_25_textline_ori`, and generic `PP-OCRv5_mobile_rec`. Official Cyrillic/East Slavic PaddleOCR models are available as Paddle 3 PIR `inference.json` + `inference.pdiparams`; Paddle-Lite 2.14 `opt` and `paddlelite` Python wheel both reject that format. Runtime bootstrap therefore downloads the official generic PP-OCRv5 mobile recognizer until a `.pdmodel` export or official cyrillic/eslav Lite `.nb` is available.

---

### Step 05.3 - Integrate PaddleOcrModelManager into PaddleOcrEngine

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Inject `PaddleOcrModelManager` into the `PaddleOcrEngine` constructor.
> Before starting an OCR session inside `recognizeText` or `recognizeTextBlocks`, verify that all required model slices are present.
> If model files are missing, trigger the download via `PaddleOcrModelManager` and wait for completion before initializing the PaddlePredictor instance.

**Verification:**

- `Grep` - `paddleOcrModelManager` matches in `PaddleOcrEngine.kt`.
- `Grep` - `isModelInstalled` or model loading checks exist in `PaddleOcrEngine.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt (already modified in Phase 04). Expected: `paddleOcrModelManager` and `isModelInstalled`/model loading checks exist | actual: predicates passed. Build validation: `:app_v2:assembleNoLegalDebug` exit 0 from Step 04.3, log `temp/S0288_04_3_assembleNoLegalDebug.log`. Dev log already recorded with the Phase 04 engine changes.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build`. Expected: `:app_v2:assembleNoLegalDebug` exit 0 | actual: exit 0.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Model manager is complete and integrated into `PaddleOcrEngine`.
We are ready to tie the OCR engine selection registry into `TranslationManager.kt` in Phase 06.

---

## Rollback Plan

Revert phase commits. Delete `PaddleOcrModelManager.kt`. Revert modifications in `PaddleOcrEngine.kt`.
