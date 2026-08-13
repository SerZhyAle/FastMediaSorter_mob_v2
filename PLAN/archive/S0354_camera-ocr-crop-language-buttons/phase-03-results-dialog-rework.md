# Phase 03 - Results dialog rework + re-translate

**Ticket:** S0354
**Status:** Pending

## Steps

1. In `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml`, remove the source-language header `TextView` and `spinnerSourceLanguage`. Keep target-language header + `spinnerTargetLanguage` and the OCR-only checkbox block.
   - **Verification:** `rg -n "spinnerSourceLanguage|spinnerTargetLanguage" app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml`. expected: only spinnerTargetLanguage remains | actual: record.

2. In `CameraOcrTranslateActivity.showCompactSettingsDialog`, drop all `sourceView` wiring (lookup, label, click, picker). Keep `targetView` + `cbOcrOnly`. The Apply handler calls the reworked `flowManager.applyLanguageSettings(targetLang, ocrOnly)`.
   - **Verification:** `rg -n "spinnerSourceLanguage|selectedSourceLang|Mode.SOURCE" app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` - SOURCE references only remain in the crop path (Phase 02), not in the results dialog. expected: no SOURCE in dialog | actual: record.

3. In `CameraOcrFlowManager.applyLanguageSettings`, change signature to `(targetLang: String, ocrOnly: Boolean)`:
   - Persist `translationTargetLanguage = targetLang`, `cameraOcrOnly = ocrOnly` (keep existing `translationSourceLanguage`).
   - If `ocrOnly` or `recognizedOriginalText` blank → `showResults(recognizedOriginalText, "", ocrOnly)`.
   - Else → show loading, call `translationManager.translate(recognizedOriginalText, source=mlKit(settings.translationSourceLanguage), target=mlKit(targetLang))`; on non-null store `translatedOutputText` and `showResults(..., ocrOnly=false)`; on null keep prior translation and surface the existing engine-error toast.
   - **Verification:** `rg -n "fun applyLanguageSettings|translationManager.translate" app_v2/.../CameraOcrFlowManager.kt`. expected: new signature + translate call | actual: record.

4. Build closure (Kotlin + XML change).
   - **Verification:** `.\a.ps1 dq` PASS. expected: BUILD SUCCESSFUL | actual: record.

## Done criteria

- Results dialog has no OCR-language control; only target language + OCR-only.
- Changing the target language re-translates the existing recognized text without re-capturing or re-running OCR.
- `assembleStandardDebug` passes.
