# Phase 02 - SettingsDropdownRow

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (reuses compound-view conventions)
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce `SettingsDropdownRow` (Material3 exposed dropdown + integrated help icon) and remove every legacy raw `<Spinner>` from settings/dialog surfaces per ADR-1.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | +14 |
| `app_v2/src/main/res/layout/view_settings_dropdown_row.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt` | New | ≤ 240 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` (+`layout-land`) | Modified | ≤ 700 |
| `app_v2/src/main/res/layout/dialog_filter_resource.xml` (+`layout-land`) | Modified | - |
| `app_v2/src/main/res/layout/dialog_player_settings.xml` (+`layout-land`) | Modified | - |
| `app_v2/src/main/res/layout/dialog_translation_settings.xml` (+`layout-land`) | Modified | - |
| Controllers binding the above spinners | Modified | ≤ 500 |

---

## Steps

### Step 02.1 - Declare `sdr_*` styleable

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `declare-styleable name="SettingsDropdownRow"`: `sdr_title` (string|reference), `sdr_showHelp` (boolean), `sdr_helpTitle` (string|reference), `sdr_helpMessage` (string|reference), `sdr_entries` (reference). Prefix-only.

**Verification:**

- `Grep` - `declare-styleable name="SettingsDropdownRow"` matches once.
- `Grep` - `sdr_entries` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Added `SettingsDropdownRow` styleable (5 `sdr_*` attrs).

---

### Step 02.2 - Author `view_settings_dropdown_row.xml`

**Files:** `app_v2/src/main/res/layout/view_settings_dropdown_row.xml`
**Depends on:** Step 02.1
**Landscape:** widget layout - orientation-agnostic.

**Prompt for developer:**

> Create a `<merge>` layout: title + inline help icon (same pattern as Phase 01), then a `com.google.android.material.textfield.TextInputLayout` with `style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu"` wrapping a `MaterialAutoCompleteTextView` (`@+id/sdr_autocomplete`). Theme attrs only, no HEX.

**Verification:**

- `Glob` - file exists.
- `Grep` - `MaterialAutoCompleteTextView` and `@+id/sdr_autocomplete` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `view_settings_dropdown_row.xml` (title+helper + MaterialComponents exposed dropdown, `AutoCompleteTextView`, 0 HEX).

---

### Step 02.3 - Implement `SettingsDropdownRow.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Implement `class SettingsDropdownRow : LinearLayout` modelled on `SettingsToggleRow`. API: `setTitle`, `setEntries(List<CharSequence>)` (adapter-backed via `ArrayAdapter` on the `MaterialAutoCompleteTextView`), `setSelection(Int)` / `getSelectedIndex()`, `setOnItemSelectedListener((Int)->Unit)`, `setHelp(..)`. Row owns the help -> `TooltipDialog` wiring. Timber only.

**Verification:**

- `Grep` - `class SettingsDropdownRow` once.
- `Grep` - `fun setEntries` and `fun setOnItemSelectedListener` present.
- `Grep -n "Log\.d\("` zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `SettingsDropdownRow.kt` (adapter-backed `setEntries`/`setSelection`/`getSelectedIndex`/`setOnItemSelectedListener`, owns `TooltipDialog`). Timber only.

---

### Step 02.4 - Replace raw `<Spinner>` surfaces

**Files:** `fragment_settings_general.xml` (+land), `dialog_filter_resource.xml` (+land), `dialog_player_settings.xml` (+land), `dialog_translation_settings.xml` (+land), their binding controllers
**Depends on:** Step 02.3
**Landscape:** all four layouts have `layout-land/` counterparts - migrate symmetrically.

**Prompt for developer:**

> Replace the raw `<Spinner>` widgets surveyed in strategic §1.1 item 1 (Language, Color theme; Sort selector; Subtitle language, Audio track; Font size, Font family) with `<com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow>`. Update each binding controller to feed entries and read selection through the new API instead of `Spinner.adapter` / `onItemSelected`. Mirror every change into `layout-land/`.

**Verification:**

- `Grep "<Spinner"` over `app_v2/src/main/res/layout` + `layout-land` for the four migrated files returns zero hits in those files.
- `Grep` - `SettingsDropdownRow` present in both orientations of each migrated file.
- `/build` standard debug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS. Migrated 7 raw Spinners to `SettingsDropdownRow` (both orientations): Language + Color theme (general), Sort (filter), Subtitle + Audio (player), Font size + Font family (translation). Folded label TextViews into `sdr_title`; deleted unreferenced wrappers; `tvSubtitleLanguageLabel` folded into row (`setEnabled` dims it). Controllers rewired (Spinner API -> `setEntries`/`setSelection`/`getSelectedIndex`/`setOnItemSelectedListener`): `GeneralSettingsViewSetupHelper`/`ObserversHelper`/`ColorThemeHelper`, `FilterResourceDialog`, `PlayerSettingsDialog` (`setupLanguageSpinner` retyped to `SettingsDropdownRow`), `TranslationSettingsDialog` (`findViewById<SettingsDropdownRow>`). `a.ps1 fc` PASS; 0 `<Spinner>` in the 4 surfaces.
- NOTE: OCR spinners (`spinnerOcrFontSize/FontFamily/EngineType`, `spinnerPaddleOcrModel`) in `OtherMediaSettingsFragment` + the generic `BaseSettingsFragment.setupSpinner` remain raw Spinners - not in strategic §1.1's surveyed set; defer to Phase 06 `<Spinner>` audit (migrate or document as exception).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] `Grep "<Spinner"` over the four migrated layouts (both orientations) = zero.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Raw `Spinner` is now a legacy-only escape hatch in non-migrated surfaces; Phase 06 audit confirms no settings/dialog spinner regressions.

---

## Rollback Plan

Revert phase commit(s) - pure widget substitution, no persisted state changed.
