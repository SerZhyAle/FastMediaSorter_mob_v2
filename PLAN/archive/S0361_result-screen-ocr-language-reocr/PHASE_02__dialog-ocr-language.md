# Phase 02 - Dialog OCR-language control

**Strategic spec:** [`../S0361_result-screen-ocr-language-reocr.md`](../S0361_result-screen-ocr-language-reocr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Add the OCR source-language picker back to the result-screen settings dialog, above the target language, and route Apply through the Phase 01 re-OCR branch.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` | Modified | ≤ 450 |

> Landscape parity: no `res/layout-land/dialog_camera_ocr_settings.xml` exists; the dialog is a single `ScrollView` with `fillViewport` - landscape-safe, no landscape variant needed.

---

## Steps

### Step 02.1 - Add the OCR source-language row to the dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Above the existing "Target Language Header" block, add an OCR source-language section that mirrors the target row: a header `TextView` with `android:text="@string/translation_source_language"` (reuse the existing string - "Исходный язык" / "Original Language" / "Вихідна мова"; no new string), followed by a `TextView` with `android:id="@+id/spinnerSourceLanguage"` using the same attributes as `spinnerTargetLanguage` (`@drawable/item_focus_selector`, `clickable`, `focusable`, `48dp` height, ellipsize end, paddings, `layout_marginBottom="16dp"`). Replace the S0354 removal comment with a short note that the OCR language re-runs recognition (S0361). Keep focus order top-to-bottom: source -> target -> OCR-only.

**Verification:**

- `Grep` - `@+id/spinnerSourceLanguage` matches exactly once in the layout.
- `Grep` - `@string/translation_source_language` present in the layout.
- `Grep` - `S0354: OCR source-language picker removed here` returns zero hits (comment replaced).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS (spinnerSourceLanguage ×1, source string present, removal comment gone). Files: dialog_camera_ocr_settings.xml (+22 LOC). Dev log recorded.

---

### Step 02.2 - Wire the OCR-language control and Apply in the Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `showCompactSettingsDialog()`:
> - Resolve `sourceView = view.findViewById<TextView>(R.id.spinnerSourceLanguage)` and a local `var selectedSourceLang = settings.translationSourceLanguage`.
> - Add `fun updateSourceView()` that renders the label and contentDescription via the existing `LanguageFlagFormatter` / `TranslationLanguageCatalog` path, using `R.string.translation_source_language` for the contentDescription prefix (mirror `applyLanguageLabel`; generalise it to take the title string res, or add a sibling helper - do not duplicate the whole body).
> - Set `sourceView.setOnClickListener` to open `showLanguagePicker(selectedSourceLang, SearchableLanguagePickerDialog.Mode.SOURCE, interfaceLang) { language -> selectedSourceLang = language.code; updateSourceView() }`. The OCR language control is always enabled when a source image is retained: gate it with `flowManager.hasRetainedSourceImage()` (disable + alpha 0.45 via the existing `updateTargetLanguageEnabled` pattern when false).
> - Call `updateSourceView()` during dialog setup.
> - In the positive button, change the call to `flowManager.applyLanguageSettings(selectedSourceLang, selectedTargetLang, cbOcrOnly.isChecked)`.
> - Update the S0354 KDoc/comment in this method to state that the OCR source language is editable again and re-runs recognition over the retained image (S0361).

**Verification:**

- `Grep` - `R.id.spinnerSourceLanguage` matches in the Activity.
- `Grep` - `applyLanguageSettings(selectedSourceLang, selectedTargetLang` present (new call shape).
- `Grep` - `SearchableLanguagePickerDialog.Mode.SOURCE` appears in `showCompactSettingsDialog` scope.
- `Grep -n "Log\.d\("` - zero hits in the file (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS (R.id.spinnerSourceLanguage, new applyLanguageSettings call shape, Mode.SOURCE in dialog, zero Log.d). Files: CameraOcrTranslateActivity.kt (+~35 LOC). Dev log recorded.

---

### Step 02.3 - Build the target variant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`, `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Build `standardDebug` via `/build`. The Phase 01 call-site mismatch must now be resolved by Step 02.2.

**Verification:**

- `/build` standardDebug - record `expected: SUCCESS | actual: <...>`. A mismatch is a hard failure.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - `.\a.ps1 dq` BUILD SUCCESSFUL in 2m25s (v2.60.6051.109). expected: SUCCESS | actual: SUCCESS.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

OCR source language is selectable in the result-screen dialog; Apply re-runs OCR over the retained image when it changed. No new string resources were added (reused `translation_source_language`), so no localization audit is required. Phase 03 handles catalog/dev-log/FEATURES.

---

## Rollback Plan

Revert phase commit(s) - layout + Activity wiring only; no data migration. Phase 01 orchestration can stand alone (call site reverts to target-only).
