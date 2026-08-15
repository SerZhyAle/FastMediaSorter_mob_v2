# Phase 02 - settings-ui-extension

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

Extend `AppSettings` and the settings repository to store OCR configuration fields. Update the Settings UI to display these settings exclusively in the `noLegal` flavor under the translation section.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 300 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 50 |

---

## Steps

### Step 02.1 - Add OCR engine settings to AppSettings and Repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `ocrEngineType` (String, default "TESSERACT") and `paddleOcrModel` (String, default "CYRILLIC") to `AppSettings.kt`.
> Add corresponding keys `KEY_OCR_ENGINE_TYPE` and `KEY_PADDLE_OCR_MODEL` to `SettingsRepositoryImpl.kt`.
> Update reading mapping in `getSettings()` and writing mapping in `updateSettings()` to persist these settings in Preferences DataStore.
> Ensure that standard backup and import/export use cases also serialize these new keys.

**Verification:**

- `Grep` - `val ocrEngineType: String` matches in `AppSettings.kt`.
- `Grep` - `val paddleOcrModel: String` matches in `AppSettings.kt`.
- `Grep` - `KEY_OCR_ENGINE_TYPE` matches in `SettingsRepositoryImpl.kt`.
- `Grep` - `KEY_PADDLE_OCR_MODEL` matches in `SettingsRepositoryImpl.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Add OCR engine type selectors to layout and strings

**Files:** `app_v2/src/main/res/layout/fragment_settings_other.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a drop-down selector spinner for OCR Engine Type (Tesseract / PaddleOCR) and PaddleOCR Model (Cyrillic PP-OCRv5 / East Slavic PP-OCRv5) under the Translation settings category in `fragment_settings_other.xml`.
> Define new string values for labeling these spinners and options. Provide professional, dry, and context-appropriate trilingual translations matching `docs/COMMUNICATION_POLICY.md` §2 and tone checklist §6.
> Ensure that all new elements support TalkBack accessibility descriptions and clear D-pad navigation mappings.
> Landscape Check: Verify portrait and landscape layout variations (`res/layout-land/fragment_settings_other.xml`) parity if present.

**Verification:**

- `Grep` - `spinnerOcrEngineType` matches in `fragment_settings_other.xml`.
- `Grep` - `<string name="ocr_engine_type"` matches in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `VerificationPredicate` - Strings pass `COMMUNICATION_POLICY §6` tone checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/res/layout-land/fragment_settings_other.xml (+16 LOC). Dev log recorded.

---

### Step 02.3 - Implement selector logic in Settings Fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Integrate spinners and selectors logic into `OtherMediaSettingsFragment.kt`.
> Add conditional visibility checks: These OCR settings rows should only be displayed if `BuildConfig.IS_NO_LEGAL_FLAVOR` is `true`.
> In non-noLegal flavors, set visibility to `View.GONE`.
> Set up item selection listeners to update the settings repository when a user changes their preferred OCR engine or PaddleOCR model.
> Conditionally show PaddleOCR model selection only when `ocrEngineType` is set to `PADDLE_OCR`.

**Verification:**

- `Grep` - `BuildConfig.IS_NO_LEGAL_FLAVOR` matches in `OtherMediaSettingsFragment.kt`.
- `Grep` - `spinnerOcrEngineType` matches in `OtherMediaSettingsFragment.kt`.
- `Grep` - `paddleOcrModel` matches in `OtherMediaSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt (+95 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Settings UI and preferences persistence are ready. We can configure the OCR runtime or implement PaddleOCR engine next.

---

## Rollback Plan

Revert phase commits. Revert `AppSettings.kt`, `SettingsRepositoryImpl.kt`, `OtherMediaSettingsFragment.kt`, layouts and string resources.
