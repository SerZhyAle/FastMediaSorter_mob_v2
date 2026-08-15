**Status:** Archived

# S0598 - Settings search misses SettingsDropdownRow / SettingsSelectionRow rows

## Goal

Сделать настройки на компаундных строках `SettingsDropdownRow` и `SettingsSelectionRow` (S0567) находимыми через внутренний поиск настроек. Сканер `LayoutSettingsSearchSource` распознаёт toggle/header/button/text-input/spinner и `SettingsInputRow` (S0597), но пропускает эти два виджета, из-за чего 12 реальных пользовательских настроек (язык, цветовая тема, кэш предзагрузки, сортировка по умолчанию, профиль устройства, сохранённые авторизации, статистика, автозагрузка по ссылке, три жеста скриншота, папка скриншотов) не находятся поиском. Расширить сканер по паттерну S0597 и добавить EN/RU/UK аннотации для новых ключей, чтобы gate doc-sync остался зелёным.

<!-- auto-approved by /spec-all - 2026-06-21 -->

## 0. Capture (raw)

Surfaced while fixing S0597 (the `SettingsInputRow` search regression). The settings-search scanner `LayoutSettingsSearchSource` recognizes toggle rows, section headers, buttons, raw text inputs, raw spinners, and (after S0597) `SettingsInputRow`. It does NOT recognize the other two S0567 compound widgets:

- `SettingsDropdownRow` (4 in catalogued layouts): `spinnerLanguage`, `spinnerColorTheme`, `actvPrefetchCache`, `spinnerSortMode`.
- `SettingsSelectionRow` (8 in catalogued layouts): `rowDeviceProfile`, `row_saved_authorizations`, `rowOpenStatistics`, `row_link_autodownload_resource`, `rowScreenshotGestureActionUp/Right/Down`, `rowScreenshotDestination`.

So Language, Color theme, Prefetch cache, Default sort mode, Device profile, Saved authorizations, Statistics, and the screenshot-gesture rows are not findable via in-app settings search.

## 1. Problem

Unlike the S0597 inputs, these rows were never in the search manifest - pre-S0567 they were raw `Spinner` / custom rows with no scannable title, so this is a long-standing limitation, not a regression. But the new widgets now carry explicit labels (`app:sdr_title`, `app:ssr_title` / `app:ssr_subtitle`), so they can be indexed cheaply.

## 2. Evidence

- `LayoutSettingsSearchSource.kindFromTag` has no branch for `SettingsDropdownRow` / `SettingsSelectionRow`.
- Title attributes exist: `SettingsDropdownRow` -> `app:sdr_title`; `SettingsSelectionRow` -> `app:ssr_title` (+ `app:ssr_subtitle`). Help attributes (`*_helpTitle` / `*_helpMessage`) stay excluded per strategic §6.3.
- Pattern to follow: S0597 added `EntryKind.INPUT_ROW` + a `buildEntry` branch reading `app:sir_title` / `app:sir_hint`.
- The keyword collector (`LocalizedKeywordCollector`) and `SettingsManifestExportTest` consume `RawSettingsSearchEntry` fields generically, with no `when (kind)` switch - so the new kinds need no follow-up changes there.
- Each newly-indexed key needs an EN/RU/UK annotation in `docs/settings/settings-annotations.json` or the doc-sync gate Stage 3 fails.
- All 12 rows live in `src/main` catalogued layouts (`fragment_settings_general`, `fragment_settings_playback`, `fragment_settings_destinations`), so the standard-flavor manifest scan covers them.

## 3. Resolution

Index all 12 rows. The scanner gates by widget type, not by id, so recognizing the two compound widgets surfaces every instance - matching the S0597 `INPUT_ROW` approach. No per-row denylist: search is precisely the mechanism for finding niche settings (incl. the screenshot-gesture rows).

Row -> title attribute (all confirmed in layouts):

- `spinnerLanguage` -> `@string/language` (general)
- `spinnerColorTheme` -> `@string/color_theme` (general)
- `actvPrefetchCache` -> `@string/pref_prefetch_cache_title` (general)
- `spinnerSortMode` -> `@string/default_sort_mode` (playback)
- `rowDeviceProfile` -> `@string/settings_profile_title` (general)
- `row_saved_authorizations` -> `@string/setting_saved_authorizations_title` + `@string/setting_saved_authorizations_summary` (general)
- `rowOpenStatistics` -> `@string/settings_statistics_open` (general)
- `row_link_autodownload_resource` -> `@string/link_autodownload_resource_label` (destinations)
- `rowScreenshotGestureActionUp` -> `@string/setting_screenshot_gesture_action_up_title` (destinations)
- `rowScreenshotGestureActionRight` -> `@string/setting_screenshot_gesture_action_right_title` (destinations)
- `rowScreenshotGestureActionDown` -> `@string/setting_screenshot_gesture_action_down_title` (destinations)
- `rowScreenshotDestination` -> `@string/setting_screenshot_destination_title` (destinations)

### Phase 01 - Recognize the two compound rows in the scanner

- Add `DROPDOWN_ROW` and `SELECTION_ROW` to `EntryKind` (`RawSettingsSearchEntry.kt`), each with a one-line KDoc naming the source widget and label attribute.
- In `LayoutSettingsSearchSource.kindFromTag`, map `SettingsDropdownRow -> DROPDOWN_ROW` and `SettingsSelectionRow -> SELECTION_ROW`.
- In `buildEntry`, add a `DROPDOWN_ROW` branch reading the title from `app:sdr_title` (resource ref first, inline fallback).
- In `buildEntry`, add a `SELECTION_ROW` branch reading the title from `app:ssr_title` and the subtitle from `app:ssr_subtitle` (resource ref first, inline fallback), mirroring the `TOGGLE_ROW` subtitle handling.
- Update the class KDoc list of recognized kinds.
- Verification: `.\a.ps1 fk` exits 0 (Kotlin compiles); the `buildEntry` `when (kind)` stays exhaustive.

### Phase 02 - Regenerate manifest, annotate, re-render, pass the gate

- Regenerate the manifest: run `*SettingsManifestExportTest` with `-Dsettings.manifest.generate=true`; confirm the 12 new keys appear with EN/RU/UK titles.
- Add one EN/RU/UK annotation per new key to `docs/settings/settings-annotations.json`.
- Re-render `docs/SETTINGS_REFERENCE*.md` via `scripts/docs/render-settings-reference.ps1`.
- Verification: `scripts/quality/assert-settings-doc-sync.ps1 -Gate` exits 0 (all 5 stages).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0597 (parent regression fix), S0567 (compound-widget unification)
- **Searchable scope:** all 12 rows indexed; no per-row denylist - the scanner gates by widget type, and search is the mechanism for finding niche settings
- **User-facing strings:** no new app strings; 12 new EN/RU/UK doc annotations authored in the settings-annotations sidecar

## Last Audit

Verdict: Verified.

- `EntryKind` gained `DROPDOWN_ROW` / `SELECTION_ROW`; `LayoutSettingsSearchSource.kindFromTag` maps the two compound widgets; `buildEntry` extracts `app:sdr_title` (dropdown) and `app:ssr_title` + `app:ssr_subtitle` (selection).
- Regenerated `docs/settings/settings-manifest.json`: the 12 target keys now appear (4 `DROPDOWN_ROW`, 8 `SELECTION_ROW`) with EN/RU/UK titles. The manifest is produced by the same `collect()` the app ships, so the in-app search index now surfaces these rows.
- 12 EN/RU/UK annotations added to `docs/settings/settings-annotations.json`; `SETTINGS_REFERENCE*.md` re-rendered.
- `assert-settings-doc-sync.ps1 -Gate` exits 0 (all 5 stages: catalog, manifest-fresh, annotations, reference, HOW_TO). `assert-neuroslop.ps1` exits 0 (all deltas 0).
- The regen also swept in 3 pre-existing uncommitted-WIP keys (`btnTakeScreenshotNow`, `headerBackgroundAudio`, `rowEnableStreams`) already annotated by that WIP - not part of this change, no action.

Follow-up (parked, out of scope): S0599 - 4 of the 12 rows (`rowScreenshotGestureActionUp/Right/Down`, `rowScreenshotDestination`) are capability-gated and runtime-hidden in flavors without the screen-capture capability, so search surfaces dead results there. `SettingsSearchAvailability` filters only media-sections, not per-row capability. Pre-existing for `btnTakeScreenshotNow`; S0598 adds 4 more instances. Needs a capability-aware availability mechanism.
