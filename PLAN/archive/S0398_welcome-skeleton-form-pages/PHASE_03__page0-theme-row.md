# Phase 03 - Page 0 Theme Row

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Add a colour-theme picker (Auto/Light/Dark, 3 buttons) to page 0 beneath the existing language picker, writing both the DataStore setting and the synchronous `ColorThemePrefs` mirror, with copy stating the theme applies after setup (welcome is force-light, no live preview - research/02).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/page_welcome_enhanced.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings_setup.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_setup.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_setup.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt` | Modified | n/a |

> Portrait + landscape `page_welcome_enhanced.xml` change in lockstep (Rule 11). The existing language picker (`@id/layoutLanguagePicker`, buttons `btnLangEn/Ru/Uk`) is the structural template to mirror.
> Two files added to the table during execution (plan omission): the dual-write needs a DataStore persist (`WelcomeViewModel.saveColorTheme`) and the holder pre-check needs a synchronous read (`ColorThemePrefs.getMode`). Welcome strings live in `strings_setup.xml`, not `strings.xml` (corrected from the original table).

---

## Steps

### Step 03.1 - Add the theme strings

**Files:** `res/values{,-ru,-uk}/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add EN/RU/UK keys in one lockstep call (`scripts/utils/set-android-string.ps1 -Action add`): `welcome_theme_picker_hint` (e.g. "Theme" / applies-after-setup note), `welcome_theme_applies_after_setup` (a short line that the theme takes effect once setup finishes). Reuse the existing `color_theme_options` array semantics for the three button labels if practical; if dedicated button labels are needed add `welcome_theme_auto`, `welcome_theme_light`, `welcome_theme_dark`. All copy passes `docs/COMMUNICATION_POLICY.md` §2 (informational) and §6 tone checklist - no scare wording, plain statement of effect.

**Verification:**

- `Grep` - `welcome_theme_applies_after_setup` present in all three `values*/strings.xml`.
- `Bash` - `scripts/check_strings_localized.ps1 -KeyPrefix "welcome_theme"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Added 5 keys to `strings_setup.xml` (welcome strings live there, not `strings.xml`): `welcome_theme_picker_hint`, `welcome_theme_applies_after_setup`, `welcome_theme_auto/light/dark`, EN/RU/UK lockstep via `set-android-string.ps1 -Action add`. `check_strings_localized -KeyPrefix welcome_theme` exit 0; §6 tone OK (plain informational caption). Dev log recorded.

---

### Step 03.2 - Add the theme toggle group to page 0 layout (portrait + land)

**Files:** `res/layout/page_welcome_enhanced.xml`, `res/layout-land/page_welcome_enhanced.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Below `@id/layoutLanguagePicker` in both orientation layouts, add a `MaterialButtonToggleGroup @id/layoutThemePicker` (`app:singleSelection="true"`, `app:selectionRequired="true"`) with three 48dp `MaterialButton`s `@id/btnThemeAuto`, `@id/btnThemeLight`, `@id/btnThemeDark`, plus a caption `TextView` showing `welcome_theme_applies_after_setup`. Mirror the language picker's styling, focusability and `nextFocus*` chaining so D-pad traversal reaches the new row. Use `?attr/` colours, never hardcoded hex (Rule 19).

**Verification:**

- `Grep` - `layoutThemePicker` and `btnThemeAuto` present in both `page_welcome_enhanced.xml` files.
- `Grep` - `="#` returns zero hits in the added blocks (no hardcoded colour).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Added `layoutThemePicker` toggle group (`btnThemeAuto/Light/Dark`, 48dp, OutlinedButton style mirroring the language picker) + `tvThemeAppliesHint` caption in both portrait and `layout-land` (lockstep, identical ids). Both start `visibility="gone"` (shown by the holder when `showThemePicker`). `="#` hits = 0 in both files; `?attr/` colours only. Dev log recorded for both orientation files.

---

### Step 03.3 - Wire the theme picker (dual-write)

**Files:** `ui/welcome/WelcomePagerAdapter.kt`, `ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Mirror the language-picker wiring: add `showThemePicker: Boolean` + `onThemeSelected: ((mode: String) -> Unit)?` to `WelcomePage`; in `EnhancedViewHolder.bind()` show `layoutThemePicker` when `showThemePicker`, pre-check the button matching the current `ColorThemePrefs` value, and on check call `onThemeSelected(code)` with `"AUTO"|"LIGHT"|"DARK"`. In WelcomeActivity set `showThemePicker = true` on page 0 and implement the callback to dual-write: persist via the settings repository (`colorTheme`) AND `ColorThemePrefs.setMode(...)` so the synchronous startup mirror is current; do NOT call `LocaleHelper.restartApp` or recreate (welcome stays force-light; effect is deferred). Name the settings-repository update call used.

**Verification:**

- `Grep` - `showThemePicker` and `onThemeSelected` present in WelcomePagerAdapter.kt.
- `Grep` - `ColorThemePrefs` referenced in WelcomeActivity.kt (dual-write mirror).
- `Grep` - `restartApp` returns zero hits in WelcomeActivity.kt (no mid-flow restart).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. `WelcomePage` gained `showThemePicker`/`onThemeSelected`; `EnhancedViewHolder.bind()` shows `layoutThemePicker`+caption when set, pre-checks the button for `ColorThemePrefs.getMode(...)` (new accessor mirroring `setMode`), and emits "AUTO"|"LIGHT"|"DARK" on check. WelcomeActivity sets `showThemePicker=true` on page 0 and `onWelcomeThemeSelected` dual-writes (Settings split): `viewModel.saveColorTheme(mode)` → `settingsRepository.updateSettings(colorTheme=…)` (DataStore) + `ColorThemePrefs.setMode(this, mode)` (mirror). No `restartApp`/`recreate`/`applyMode` - welcome stays force-light, effect deferred to next cold start. Catalog re-synced; flavor-flag 167≤178; neuroslop 0. Dev log recorded for all 4 source files.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (1m41s, v2.60.6110.138).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry for every modified file (9 files).

---

## Handoff Notes to Next Phase

Page 0 now offers language + theme. The theme write hits both stores; the next cold start applies it.

---

## Rollback Plan

Revert phase commit(s) - removes the theme row from both layouts and the wiring. No data migration.
