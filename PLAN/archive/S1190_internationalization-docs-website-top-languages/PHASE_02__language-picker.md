# Phase 02 - Language picker

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Replace both three-position language selectors - Settings and Welcome - with one searchable list built from the catalog, each row carrying the language name in its own language plus a flag.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` free.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableLanguagePickerDialog.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/UiLanguagePickerItems.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout/page_welcome_enhanced.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml` | Modified | ≤ 400 |

> **File-list correction (2026-07-27, during execution).** The plan named `activity_welcome.xml` in three width variants; that file holds only the pager and the bottom navigation. The three-position language switch is a `MaterialButtonToggleGroup` in `page_welcome_enhanced.xml` (`layout` + `layout-land`, two variants, no `sw*dp` variants) bound by `WelcomePagerAdapter`, so those are the files edited. `WelcomeActivity` still owns the callback and is where the dialog is shown from.
>
> **Settings-row correction.** The plan assumed the settings row keeps its shape. It cannot: `spinnerLanguage` is a `SettingsDropdownRow` whose whole contract is an inline dropdown of entries. A row that opens a dialog is `SettingsSelectionRow` (canonical trigger row, S0648), so both `fragment_settings_general.xml` variants change the widget type. Rule 22 (settings docs sync) therefore applies to this phase.

---

## Steps

### Step 02.1 - Let the picker take an arbitrary language set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableLanguagePickerDialog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The dialog is currently built for the OCR translation catalog. Give it a second entry point that takes the UI language catalog instead: rows show the flag, the language name in its own language, and stay searchable by both that name and the English one. Do not fork the dialog - one implementation, two sources. Row text must remain the accessible label; the flag is decoration only (strategic ADR-3), so the flag must not be the sole content of any view a screen reader would announce.

**Verification:**

- `Grep` - `UiLanguageCatalog` reached from the dialog (through `UiLanguagePickerItems`, same package).
- `Grep` - `class SearchableLanguagePickerDialog` still matches exactly once (no fork).
- `Grep` - `contentDescription` or an accessible text binding present on the flag view.

**Status:** `[x] done`

---

### Step 02.2 - Settings language row opens the picker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Drop the four-item array and the `when` that maps a spinner position to a language code (`:69-88`, `:643-648`); the row now opens the searchable picker with "follow system" as its first entry followed by every catalog language, and shows the selected language's own name as its current value. Any code path that still needs a code from a position must ask the catalog, not a `when`. The file is over 500 lines: back it up under `temp/S1190/` first.

**Verification:**

- `Grep` - `R.string.language_russian` returns zero hits in this file.
- `Grep` - `UiLanguagePickerItems` referenced at least once.
- `Glob` - `temp/S1190/GeneralSettingsViewSetupHelper_*.kt` matches at least once.

**Status:** `[x] done`

---

### Step 02.3 - Welcome screen uses the same list

**Files:** `WelcomeActivity.kt`, `WelcomePagerAdapter.kt`, `res/layout/page_welcome_enhanced.xml`, `res/layout-land/page_welcome_enhanced.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> The Welcome screen's segmented three-position switch cannot hold thirteen languages (strategic §4.4). Replace it in both orientation variants with a single control that shows the current language - flag plus its own name - and opens the same searchable picker. Keep it inside the existing safe bounds and keyboard/D-pad reachable: focusable, clickable, in the existing focus order. This supersedes S0108's three-position form and its "no languages beyond EN/RU/UK" non-goal.

**Verification:**

- `Grep` - `btnLangEn`/`btnLangRu`/`btnLangUk` return zero hits across `res/` and `src/`.
- `Grep` - the new language control id is present in both `page_welcome_enhanced.xml` variants (two matches, one per file).
- `Grep` - `android:focusable="true"` present on that control in each variant.

**Status:** `[x] done`

---

### Step 02.4 - Retire the dead language resources

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> `string-array name="languages"` has no reference from code (strategic §4.1) and the four `language_*` keys lose their last UI-locale consumer in this phase - but they are still used by the stream/subtitle language pickers, so check each one with `set-android-string.ps1 -Action get` before removing anything. Remove only what is genuinely unreferenced, in every locale, and leave the rest alone (CLAUDE.md Rule 21).

**Verification:**

- `Grep` - `name="languages"` returns zero hits under `res/values*/`.
- `Grep` - every remaining `language_*` key has at least one code reference.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "language"` exits 0.

**Outcome:** nothing to remove. `string-array name="languages"` does not exist anywhere under `app_v2/src` or `wear/src` - strategic §4.1 described a resource that had already been deleted. All four `language_*` keys keep live consumers (`PlayerSettingsDialog`, `StreamsSettingsFragment`, `StreamsActivity`), and `language_default` gained one more in `UiLanguagePickerItems`.

**Status:** `[x] done`

---

## Step Log

- 2026-07-27 - Steps 02.1-02.4 executed. Backups: `temp/S1190/GeneralSettingsViewSetupHelper_20260727_192818.kt`, `WelcomeActivity_20260727_192818.kt`, `WelcomePagerAdapter_20260727_192818.kt`, `fragment_settings_general_portrait_20260727_192818.xml`, `fragment_settings_general_land_20260727_192818.xml`.
- 2026-07-27 - 02.1 design note: the language rows are built in `UiLanguagePickerItems`, not inside the dialog. Flags and native spellings come from `TranslationLanguageCatalog`, which already owns the exception where a language is drawn with an image flag instead of an emoji - a second flag source in `UiLanguageCatalog` would have surfaced the wrong flag for `ru`. That unused `UiLanguageCatalog.flagEmoji` (written in Phase 01) was removed with its test case.
- 2026-07-27 - 02.1 also collapses a row's label to a single name when the localized and native spellings coincide; the list contains rows ("Default", English while the app is English) where the doubled form read as a defect.
- 2026-07-27 - 02.2 renamed the row id `spinnerLanguage` -> `rowLanguage` (it is no longer a dropdown). The settings manifest regenerated onto the new key and `settings-annotations.json` was renamed to match: `settings annotations: OK - 207 unique keys, 0 orphans`.
- 2026-07-27 - Adding `Mode.UI_LANGUAGE` broke two exhaustive `when`s in `OtherMediaSettingsFragment`. Rather than add an unreachable branch, the translation picker now takes `isSource: Boolean` - that screen must never offer the interface language as a translation direction.
- 2026-07-27 - Evidence: `.\a.ps1 fc` BUILD SUCCESSFUL in 2m 11s; `scripts/quality/reindex-settings.ps1` -> `settings-doc-sync: OK` after regenerating the manifest and the four `SETTINGS_REFERENCE*.md`; `post-change.ps1 -ChangeType Mixed -ScopeToFile` -> `post-change: PASS (Mixed, 67305 ms)` with `detekt-gate PASS [scoped]`.
- 2026-07-27 - Phase-boundary audit. One finding fixed in place (P2): both new call sites could stack a duplicate picker on a double tap, while the existing translation picker already guarded against it - both now check `findFragmentByTag` first. `.\a.ps1 fc` BUILD SUCCESSFUL in 28s after that fix.
- 2026-07-27 - Audit also found a pre-existing defect outside this ticket: `SearchableLanguagePickerDialog` keeps its selection callback in a plain field, so a host recreate (rotation, theme change, process death) leaves the restored dialog closing without reporting a choice. It affects all five call sites, not only the two added here. Parked as **S1214** rather than fixed inline - the fix is a result-API/ViewModel change across the translation pickers too.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (one P2 fixed inline, one pre-existing item parked as S1214).

---

## Handoff Notes to Next Phase

Both language selectors read the catalog, so adding a locale to `locales_config.xml` now shows up in the UI with no code edit. Strings themselves are still only `en`/`ru`/`uk`.

---

## Rollback Plan

Revert the phase commit; the stored `selected_language` value is unchanged by this phase, so a rollback lands users on the old three-position switch with their language intact.
