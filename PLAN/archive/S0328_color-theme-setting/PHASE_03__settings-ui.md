# Phase 03 - Settings UI Selector

**Strategic spec:** [`../S0328_color-theme-setting.md`](../S0328_color-theme-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-02
**Completed:** 2026-06-02 (build: standardDebug SUCCESSFUL)

---

## Objective

Add a "Color theme" Spinner to General settings (Auto / Light / Dark), persisting the choice and prompting for restart using the project's existing restart-dialog pattern.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 1500 |

> Landscape parity: `layout-land/fragment_settings_general.xml` exists - the Spinner block MUST be added to both portrait and landscape in the same step.

---

## Steps

### Step 03.1 - Add trilingual label + options array

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In each of the three `strings.xml` files add a `<string name="color_theme">` label and a `<string-array name="color_theme_options">` with exactly three items in order: Auto (follow device), Light, Dark.
> - EN: `Color theme` / `Auto (follow device)`, `Light`, `Dark`.
> - RU: `Цветовая тема` / `Авто (за устройством)`, `Светлая`, `Тёмная`.
> - UK: `Колірна тема` / `Авто (за пристроєм)`, `Світла`, `Темна`.
> Use `..` not `...`; keep `ё` in Russian. Verify the new label against `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - `name="color_theme"` matches in all three `strings.xml` files.
- `Grep` - `name="color_theme_options"` matches in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "color_theme"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 4/4 PASS (keys ×3 locales; audit exit 0; tone OK). Files: values/, values-ru/, values-uk/ strings.xml. Dev log recorded.

---

### Step 03.2 - Add Spinner block to portrait + landscape layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Mirror the existing language block (`layoutLanguageSelection` + `spinnerLanguage`). Add a sibling row near it with a `TextView` (`@string/color_theme`) and `<Spinner android:id="@+id/spinnerColorTheme" android:entries="@array/color_theme_options" .../>`, reusing the same dimens (`settings_spinner_min_width`, margins) for visual consistency. Apply the identical block to BOTH portrait and landscape files. Ensure the Spinner is focusable in the existing focus chain (default Spinner focus behavior matches `spinnerLanguage`).

**Verification:**

- `Grep` - `@+id/spinnerColorTheme` matches in `layout/fragment_settings_general.xml`.
- `Grep` - `@+id/spinnerColorTheme` matches in `layout-land/fragment_settings_general.xml`.
- `Grep` - `@array/color_theme_options` matches in both layout files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 3/3 PASS (spinner id + array in portrait & land). Files: layout/ + layout-land/ fragment_settings_general.xml. Dev log recorded.

---

### Step 03.3 - Create `GeneralSettingsColorThemeHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `class GeneralSettingsColorThemeHelper` modelled on the language handling in `GeneralSettingsObserversHelper` / `GeneralSettingsViewSetupHelper`. It must:
> - Map spinner position ⇄ raw value: position 0 = `AUTO`, 1 = `LIGHT`, 2 = `DARK`.
> - Set the initial Spinner selection from the current `AppSettings.colorTheme` (guard with the shared `isUpdatingSpinner` flag to suppress the change listener during programmatic selection, same pattern as language).
> - On a real user selection: persist via `viewModel.updateSettings(current.copy(colorTheme = value))`, write the synchronous mirror via `ColorThemePrefs.setMode(context, value)`, then show the existing restart dialog (`R.string.restart_required_title` / `R.string.restart_required_message`, positive `R.string.restart_now` → `LocaleHelper.restartApp(activity)`, negative `R.string.restart_later` → dismiss).
> Use `Timber` only.

**Verification:**

- `Glob` - `GeneralSettingsColorThemeHelper.kt` exists.
- `Grep` - `class GeneralSettingsColorThemeHelper` matches exactly once.
- `Grep` - `ColorThemePrefs.setMode(` matches in the helper.
- `Grep` - `R.string.restart_required_title` matches in the helper.
- `Grep -n "Log\.d\("` on the helper returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 5/5 PASS (class, setMode, restart_required_title, Log.d=0, exists). Referenced strings + LocaleHelper methods confirmed present. Files: GeneralSettingsColorThemeHelper.kt (New, +94 LOC). Dev log recorded.

---

### Step 03.4 - Wire helper into GeneralSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Instantiate `GeneralSettingsColorThemeHelper` alongside the other `GeneralSettings*Helper` instances and invoke its setup from the same place the language spinner is initialized/observed (so the initial selection reflects current settings and selection changes are handled). Pass the shared `isUpdatingSpinner` getter/setter, `binding`, `viewModel`, and `this` fragment, matching the existing helper construction signatures.

**Verification:**

- `Grep` - `GeneralSettingsColorThemeHelper(` matches in `GeneralSettingsFragment.kt`.
- `Grep -n "Log\.d\("` on `GeneralSettingsFragment.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS (ctor + setup call; Log.d=0). Files: GeneralSettingsFragment.kt. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (target `standardDebug`).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "color_theme"` exits 0.
- [ ] Dev log entry added for every touched file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

User can select Auto/Light/Dark; selection persists, mirror updates, and a restart applies the theme. Phase 04 finalizes docs, catalog metadata, and changelog.

---

## Rollback Plan

Revert phase commit(s). Layout/strings/helper additions are isolated; no data migration. The persisted `colorTheme` value (if already written) remains harmless and defaults to `AUTO` behavior.
