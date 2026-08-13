# Phase 02 - Crop wiring: FlowManager + Activity

**Ticket:** S0354
**Status:** Pending

## Steps

1. In `CameraOcrFlowManager.Callback`, add `renderCropLanguages(sourceCode: String, targetCode: String, translationAvailable: Boolean)`.
   - **Verification:** `rg -n "renderCropLanguages" app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`. expected: declared in Callback | actual: record.

2. In `CameraOcrFlowManager`, after `showCropStep` (in `onPhotoCaptured`), read settings and emit `renderCropLanguages`:
   - `translationAvailable = settings.enableTranslation && !settings.cameraOcrOnly` (no `BuildConfig` flavor guard - Rule 15; this screen only runs in translation-capable flavors).
   - Pass `settings.translationSourceLanguage`, `settings.translationTargetLanguage`.
3. Add `fun setCropSourceLanguage(code: String)` and `fun setCropTargetLanguage(code: String)`: persist the changed field to settings, then re-read and re-emit `renderCropLanguages`.
   - **Verification:** `rg -n "setCropSourceLanguage|setCropTargetLanguage" app_v2/.../CameraOcrFlowManager.kt`. expected: 2 functions | actual: record.

4. In `CameraOcrTranslateActivity`, implement `renderCropLanguages`: set compact labels (flag + code uppercase via `TranslationLanguageCatalog`), toggle `ivCropLangArrow` and `btnCropTargetLang` visibility by `translationAvailable`.
   - Add helper `compactLanguageLabel(code, interfaceLang)` returning `"<flag> <CODE>"`; fallback to `CODE` when flag blank.
5. Wire clicks: `btnCropOcrLang` → existing `showLanguagePicker(SOURCE)` → on pick `flowManager.setCropSourceLanguage(code)`; `btnCropTargetLang` → `showLanguagePicker(TARGET)` → `flowManager.setCropTargetLanguage(code)`. Reuse the existing `showLanguagePicker` + `SearchableLanguagePickerDialog`.
   - **Verification:** `rg -n "btnCropOcrLang|btnCropTargetLang|renderCropLanguages" app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`. expected: click wiring + render | actual: record.

6. Apply system-bar insets already cover `layoutCropState`; new controls inside it inherit padding. No extra inset work.

7. Build closure (Kotlin change).
   - **Verification:** `.\a.ps1 dq` (assembleStandardDebug) PASS. expected: BUILD SUCCESSFUL | actual: record.

## Done criteria

- On crop step, the OCR-language button shows flag+code; arrow and target button appear only when translation is available.
- Tapping a button opens the searchable picker; selection persists to global settings and the cluster re-renders.
- `assembleStandardDebug` passes.
