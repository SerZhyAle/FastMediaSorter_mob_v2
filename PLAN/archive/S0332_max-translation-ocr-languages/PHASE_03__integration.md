# Phase 03 - integration

**Strategic spec:** [`../S0332_max-translation-ocr-languages.md`](../S0332_max-translation-ocr-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Integrate the `SearchableLanguagePickerDialog` into the settings screen, the player translation settings dialog, and the Camera OCR translation screen.

---

## Prerequisites

- [ ] Phase 02 UI picker is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 650 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/dialog_translation_settings.xml` | Modified / Checked | ≤ 200 |
| `app_v2/src/main/res/layout-land/dialog_translation_settings.xml` | Modified / Checked | ≤ 200 |
| `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml` | Modified / Checked | ≤ 200 |

---

## Steps

### Step 03.1 - Integrate into Settings Screen

**Files:** `app_v2/src/main/res/layout/fragment_settings_other.xml`, `app_v2/src/main/res/layout-land/fragment_settings_other.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> 1. In `fragment_settings_other.xml`, replace the `AppCompatSpinner` widgets for translation source and target languages with clickable `TextView` (or custom select container) components that carry the same view IDs to preserve ViewBinding signatures where possible.
> 2. In `OtherMediaSettingsFragment.kt`, remove the old `Spinner` adapter code and item selection listeners. Instead, attach click listeners to the language rows/text views that construct and show `SearchableLanguagePickerDialog` for source and target languages.
> 3. Update the text views during settings observation to display the emoji flag and formatted language name (e.g. `🇩🇪 Немецкий (Deutsch)`).

**Verification:**

- `Grep` - `SearchableLanguagePickerDialog` present in `OtherMediaSettingsFragment.kt`.
- `Grep` - `AppCompatSpinner` references for translation languages removed from `OtherMediaSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS. Expected: `SearchableLanguagePickerDialog` present, translation `AppCompatSpinner` setup removed from fragment Kotlin. Actual: present; old translation adapter/listener helpers absent. Files: settings fragment + portrait/land layouts. Backup: `temp/OtherMediaSettingsFragment_20260603_004323.kt.bak`. Dev log recorded; catalog sync PASS.

---

### Step 03.2 - Integrate into Player Settings Dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt`, `app_v2/src/main/res/layout/dialog_translation_settings.xml`, `app_v2/src/main/res/layout-land/dialog_translation_settings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> 1. Open `TranslationButtonManager.kt` and modify `showTranslationSettingsDialog()`.
> 2. Instead of setup of standard spinner adapters and selection listeners, wire the custom click views to trigger the new `SearchableLanguagePickerDialog`.
> 3. Update dialog text elements to show the selected language with its flag and native name formatting dynamically.
> 4. Ensure swapping language button and OK/Cancel actions use the new selected values.

**Verification:**

- `Grep` - `SearchableLanguagePickerDialog` present in `TranslationButtonManager.kt`.
- `Grep` - `spinnerSource.adapter` or `spinnerSource.setSelection` logic removed.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS. Expected: `SearchableLanguagePickerDialog` present in `TranslationButtonManager.kt`, old `spinnerSource.adapter` / `spinnerSource.setSelection` logic absent. Actual: present / absent. Portrait and landscape dialog layouts updated to clickable selectors. Dev log recorded; catalog sync PASS.

---

### Step 03.3 - Integrate into Camera OCR Screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`, `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> 1. Open `CameraOcrTranslateActivity.kt` and replace the spinner selection layout for source and target languages with searchable picker invocations.
> 2. Ensure clicking the language selections opens the `SearchableLanguagePickerDialog`.
> 3. Update the text/views to show flag emoji and `Name (Native)` in real-time.

**Verification:**

- `Grep` - `SearchableLanguagePickerDialog` present in `CameraOcrTranslateActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 1/1 PASS. Expected: `SearchableLanguagePickerDialog` present in `CameraOcrTranslateActivity.kt`. Actual: present. Additional structural check: old `ArrayAdapter` / `Spinner` language selection paths absent. Files: activity + compact settings layout. Dev log recorded; catalog sync PASS.
- 2026-06-03 - Supporting fix: `TranslationManager.languageCodeToMLKit("cs")` now preserves Czech instead of falling back to English. Backup: `temp/TranslationManager_20260603_005556.kt.bak`. Verification: targeted `TranslationLanguageCatalogTest` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\gradlew.bat :app_v2:assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0.
- [x] Dev log entries added.
- [x] Catalog sync completed via `catalog_sync.ps1`.

---

## Handoff Notes to Next Phase

Integration is complete. Searchable language picking with flags and bilingual native names is active in settings, player dialogs, and Camera OCR.

---

## Rollback Plan

Revert code changes in integration files to restore standard spinner selections.
