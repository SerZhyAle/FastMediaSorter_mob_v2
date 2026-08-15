# Phase 03 - Migrate OCR inline spinners to trigger rows (G-J)

**Strategic spec:** [`../S0646_settings-simple-list-dialog-unification.md`](../S0646_settings-simple-list-dialog-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-24
**Completed:** 2026-06-24

> Step Log (batched): four OCR spinners replaced by `SettingsSelectionRow` + `SimpleValueChoiceDialog` in both portrait + landscape `fragment_settings_other.xml`; fragment rewired (rows `rowOcrFontSize/rowOcrFontFamily/rowOcrEngineType/rowPaddleOcrModel`), noLegal gate + engine->model coupling preserved. In-scope follow-on: settings-search gates + tests + manifest/annotations updated for the spinner->row id rename. Compiled in the consolidated clean build.

---

## Objective

Convert the four raw `AppCompatSpinner` selectors on the OCR section of the "Other" settings tab (font size, font family, OCR engine, PaddleOCR model) into `SettingsSelectionRow` trigger rows that open `SimpleValueChoiceDialog`, matching the unified tap-row pattern (strategic §6.1). Preserve the noLegal capability gate (engine/model rows) and the engine -> PaddleOCR-model visibility coupling.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`SimpleValueChoiceDialog` available).
- [ ] `ui/common/widget/SettingsSelectionRow.kt` present (verified - S0567).
- [ ] `res/layout/fragment_settings_other.xml` and `res/layout-land/fragment_settings_other.xml` both present (verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 320 |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 500 |

> **Landscape parity (mandatory):** the OCR selector blocks exist in both `layout/` and `layout-land/` variants of `fragment_settings_other.xml`; both are edited in Step 03.1.

---

## Steps

### Step 03.1 - Replace OCR spinner blocks with trigger rows (XML, portrait + landscape)

**Files:** `res/layout/fragment_settings_other.xml`, `res/layout-land/fragment_settings_other.xml`
**Depends on:** start of phase

**Prompt for developer:**

> In BOTH layout files, replace each of the four `LinearLayout` blocks (`layoutOcrEngineType`, `layoutPaddleOcrModel`, `layoutOcrFontSize`, `layoutOcrFontFamily` - each wrapping a label `TextView` + `AppCompatSpinner`) with a single `com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow`.
> Name the rows `rowOcrEngineType`, `rowPaddleOcrModel`, `rowOcrFontSize`, `rowOcrFontFamily`. Carry over: `android:layout_width="match_parent"`, `android:layout_height="wrap_content"`, the existing `android:visibility="gone"` (visibility is driven from the fragment), the existing start margin, and `app:ssr_title` = the row's current label string (`@string/ocr_engine_type`, `@string/paddle_ocr_model`, `@string/ocr_font_size`, `@string/ocr_font_family`). The chevron shows by default; the value is set from the fragment.
> Do not hardcode hex colors; use the row widget defaults. Keep the surrounding `rowEnableOcr` / `tvOcrSummary` views unchanged.

**Verification:**

- `Grep -n "AppCompatSpinner"` in both `fragment_settings_other.xml` files returns zero hits.
- `Grep` - `rowOcrFontSize`, `rowOcrFontFamily`, `rowOcrEngineType`, `rowPaddleOcrModel` each present in both portrait and landscape files.
- `Grep -n "#[0-9a-fA-F]\{6\}"` in the edited regions returns zero new hardcoded colors.

**Status:** `[x] done`

---

### Step 03.2 - Rewire font size + family rows to dialogs (G, H)

**Files:** `OtherMediaSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace `setupOcrFontSpinners()` body with row wiring. Build two ordered option lists of `SimpleValueChoiceDialog.Option(key, label)`:
> - font size: `AUTO/MINIMUM/SMALL/MEDIUM/LARGE/HUGE` -> `font_size_auto/minimum/small/medium/large/huge`.
> - font family: `DEFAULT/SERIF/MONOSPACE` -> `font_family_default/serif/monospace`.
> For each row: set `setOnRowClickListener` to open `SimpleValueChoiceDialog(requireContext(), viewLifecycleOwner, title = getString(R.string.ocr_font_size /* or ocr_font_family */), options, currentKey = <persisted value>, onSelected = { key -> key?.let { viewModel.updateSettings(viewModel.settings.value.copy(ocrDefaultFontSize = it)) } })`.
> Add a private helper to set the row's value text to the label matching the persisted key, and call it from `observeData()` / `updateOcrVisibility(..)` so the trailing value reflects the current setting. Drop the `isUpdatingFromSettings`-guarded `onItemSelectedListener` blocks (no longer applicable).

**Verification:**

- `Grep -n "onItemSelectedListener"` for the font-size/family spinners returns zero hits in the file.
- `Grep` - `rowOcrFontSize` and `rowOcrFontFamily` referenced with `setOnRowClickListener`.
- `Grep` - `SimpleValueChoiceDialog` present.
- `Grep` - `ocrDefaultFontSize` and `ocrDefaultFontFamily` still written via `updateSettings(..copy(`.

**Status:** `[x] done`

---

### Step 03.3 - Rewire engine + PaddleOCR-model rows, preserve gate + coupling (I, J)

**Files:** `OtherMediaSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace `setupOcrEngineSpinners()` body. Keep the early-return that hides `rowOcrEngineType` + `rowPaddleOcrModel` when `!capabilityAvailability.isOcrEngineSelectionAvailable()` (noLegal gate). Build option lists:
> - engine: `TESSERACT/PADDLE_OCR` -> `ocr_engine_type_tesseract/paddleocr`.
> - model: `CYRILLIC/EAST_SLAVIC` -> `paddle_ocr_model_cyrillic/eslav`.
> Engine row click opens `SimpleValueChoiceDialog` (title `R.string.ocr_engine_type`); `onSelected` writes `ocrEngineType` via `updateSettings(..copy(`, refreshes the engine row value, AND re-applies the PaddleOCR-model row visibility: `binding.rowPaddleOcrModel.isVisible = settings.enableOcr && key == "PADDLE_OCR"` (preserve the existing coupling). Model row click opens `SimpleValueChoiceDialog` (title `R.string.paddle_ocr_model`) writing `paddleOcrModel`.
> Update `updateOcrVisibility(..)` and `observeData()` to reference the new row ids (`rowOcrFontSize`, `rowOcrFontFamily`, `rowOcrEngineType`, `rowPaddleOcrModel`) and to refresh each row's value text from persisted settings.

**Verification:**

- `Grep -n "onItemSelectedListener"` returns zero hits in the file (all four spinners gone).
- `Grep` - `isOcrEngineSelectionAvailable()` still referenced (noLegal gate preserved).
- `Grep` - `rowPaddleOcrModel.isVisible` set with `PADDLE_OCR` condition (engine->model coupling preserved).
- `Grep` - `binding.spinnerOcr` and `binding.spinnerPaddleOcrModel` return zero hits (no stale spinner refs).

**Status:** `[x] done`

---

### Step 03.4 - Compile and confirm OCR section

**Files:** (build only)
**Depends on:** Steps 03.1-03.3

**Prompt for developer:**

> Build standard debug. Confirm `FragmentSettingsOtherBinding` no longer exposes `spinnerOcr*` fields (they are removed) and the fragment compiles against the new row ids.

**Verification:**

- `/build` -> `standard debug` (`a.ps1 dq`) exits 0.
- `Grep -n "spinnerOcr"` across `OtherMediaSettingsFragment.kt` returns zero hits.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` -> `standard debug`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Both portrait and landscape `fragment_settings_other.xml` edited (no portrait-only change).
- [ ] Dev log entry added (batched) via `.\scripts\add_to_dev_log.ps1`.
- [ ] Settings doc-sync gate handled in Phase 05 (OCR row behavior/presentation changed).

---

## Handoff Notes to Next Phase

OCR section is fully on trigger-rows + `SimpleValueChoiceDialog`. The noLegal engine/model gate and the engine->model visibility coupling are preserved at runtime (not via source sets). Phase 04 applies the same pattern to the audio visualizer selector, which additionally carries an on-demand delivery gate.

---

## Rollback Plan

Revert phase commit(s): XML reverts to spinner blocks, fragment reverts to `onItemSelectedListener` wiring. No data migration; persisted OCR setting keys are unchanged.
