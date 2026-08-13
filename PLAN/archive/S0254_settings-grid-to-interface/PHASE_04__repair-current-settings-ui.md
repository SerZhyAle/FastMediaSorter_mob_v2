# Phase 04 - Repair current SettingsToggleRow implementation

**Strategic spec:** [`../S0254_settings-grid-to-interface.md`](../S0254_settings-grid-to-interface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** `/spec-check S0254`
**Steps done:** 4 / 4
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Repair the Broken 2026-05-30 audit state against the current settings UI. The current app no longer uses the obsolete `switchGridMode` / `switchFileOpsOverflowMenu` ids from Phases 01-03; it uses `SettingsToggleRow` ids. Move the current grid/file rows out of Playback and into General -> Interface, restore the missing hide-grid-actions row in General, and keep backing settings unchanged.

---

## Prerequisites

- [x] Strategic spec re-opened from `Broken` to `Tactical` by owner-requested `/spec-update` force override.
- [x] Branch confirmed as a DEBUG branch.
- [x] Existing dirty tree reviewed; unrelated user changes are left untouched.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | existing |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | existing |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | existing |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ existing |
| `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` | Modified (regen) | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

---

## Steps

### Step 04.1 - Add current grid/file controls to General Interface

**Files:** `fragment_settings_general.xml`, `layout-land/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Append the current controls at the end of `containerInterface`, after the existing Interface rows and without adding a new header, divider, or nested card.
>
> Portrait:
> - Add direct `SettingsToggleRow` children in this order:
>   1. `@+id/rowDefaultGridMode` with `@string/default_grid_mode` and `@string/setting_default_grid_mode_desc`.
>   2. `@+id/rowHideGridActionButtons` with `@string/hide_grid_action_buttons` and `@string/setting_hide_grid_action_buttons_desc`.
>   3. `@+id/rowFileOpsInOverflowMenu` with `@string/file_ops_in_overflow_menu` and `@string/setting_file_ops_in_overflow_menu_desc`.
>
> Landscape:
> - Add the same controls as the final visual block of `containerInterface`.
> - Use one horizontal row for `rowDefaultGridMode` and `rowHideGridActionButtons`, then one full-width `rowFileOpsInOverflowMenu` below it. This preserves visual order while avoiding a three-column long-label row.
>
> Preserve the existing `SettingsToggleRow` style and use only existing strings.

**Verification:**

- `rg -n "rowDefaultGridMode|rowHideGridActionButtons|rowFileOpsInOverflowMenu" app_v2/src/main/res/layout/fragment_settings_general.xml` returns exactly 3 matches.
- `rg -n "rowDefaultGridMode|rowHideGridActionButtons|rowFileOpsInOverflowMenu" app_v2/src/main/res/layout-land/fragment_settings_general.xml` returns exactly 3 matches.
- In both General layouts, `containerInterface` appears before all three moved row ids.
- `rg -n "settings_category_grid_view|switchGridMode|switchFileOpsOverflowMenu" app_v2/src/main/res/layout/fragment_settings_general.xml app_v2/src/main/res/layout-land/fragment_settings_general.xml` returns zero matches.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. General portrait moved-row count expected: 3 | actual: 3; General landscape moved-row count expected: 3 | actual: 3; obsolete ids expected: 0 | actual: 0; order expected: `containerInterface < rowDefaultGridMode < rowHideGridActionButtons < rowFileOpsInOverflowMenu` | actual portrait `30 < 166 < 174 < 182`, landscape `30 < 154 < 163 < 174`. Build `.\a.ps1 dq` exit 0. Files: `fragment_settings_general.xml`, `layout-land/fragment_settings_general.xml`.

---

### Step 04.2 - Wire General listeners and observers

**Files:** `GeneralSettingsViewSetupHelper.kt`, `GeneralSettingsObserversHelper.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `GeneralSettingsViewSetupHelper.setupSwitches()`, add listeners for:
> - `binding.rowDefaultGridMode` -> `viewModel.updateSettings(current.copy(defaultGridMode = isChecked))`.
> - `binding.rowHideGridActionButtons` -> `viewModel.updateSettings(current.copy(hideGridActionButtons = isChecked))`.
> - `binding.rowFileOpsInOverflowMenu` -> `viewModel.updateSettings(current.copy(fileOpsInOverflowMenu = isChecked))`.
>
> Use the existing `getIsUpdatingSpinner()` guard pattern and avoid duplicate updates when the current value already equals `isChecked`.
>
> In `GeneralSettingsObserversHelper.observeData()`, reconcile all three rows from `settings.defaultGridMode`, `settings.hideGridActionButtons`, and `settings.fileOpsInOverflowMenu` using `setCheckedSilently`, inside the existing programmatic-update guard block.
>
> Do not add `Timber.d("S0254: ..")` tags in this step. Debug verification tags are inserted only when the ticket transitions to `BlockNeedUserTest`.

**Verification:**

- `rg -n "rowDefaultGridMode\\.setOnCheckedChangeListener|rowHideGridActionButtons\\.setOnCheckedChangeListener|rowFileOpsInOverflowMenu\\.setOnCheckedChangeListener" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` returns exactly 3 matches.
- `rg -n "defaultGridMode|hideGridActionButtons|fileOpsInOverflowMenu" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` returns at least 3 matches.
- `rg -n "Timber\\.d\\(\"S0254:" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` returns zero matches while the ticket is not `BlockNeedUserTest`.
- `rg -n "Log\\.d\\(" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` returns zero matches.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. Listener count expected: 3 | actual: 3. Observer field refs expected: >=3 | actual: 6. `Timber.d("S0254:` count expected: 0 | actual: 0. `Log.d(` count expected: 0 | actual: 0. `scripts/catalog_sync.ps1 -Module app_v2` exit 0. Build `.\a.ps1 dq` exit 0. Files: `GeneralSettingsViewSetupHelper.kt`, `GeneralSettingsObserversHelper.kt`.

---

### Step 04.3 - Remove moved controls from Playback

**Files:** `fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml`, `PlaybackSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Remove `rowDefaultGridMode` and `rowFileOpsInOverflowMenu` from Playback portrait and landscape layouts. Leave the Sorting & Slideshow section itself intact with sort mode, slideshow interval, and Play Video to End.
>
> Remove the matching listener and observer reconciliation blocks from `PlaybackSettingsFragment.kt`.
>
> `PlaybackSettingsFragment.kt` is over 500 LOC. Create a timestamped backup in `temp/` before editing, then edit the live file. Do not touch unrelated Playback rows.

**Verification:**

- `rg -n "rowDefaultGridMode|rowFileOpsInOverflowMenu" app_v2/src/main/res/layout/fragment_settings_playback.xml app_v2/src/main/res/layout-land/fragment_settings_playback.xml` returns zero matches.
- `rg -n "rowDefaultGridMode|rowFileOpsInOverflowMenu|defaultGridMode|fileOpsInOverflowMenu" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` returns zero matches.
- `rg -n "rowPlayToEnd|spinnerSortMode|etSlideshowInterval" app_v2/src/main/res/layout/fragment_settings_playback.xml app_v2/src/main/res/layout-land/fragment_settings_playback.xml app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` returns at least 6 matches.
- `rg -n "Log\\.d\\(" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` returns zero matches.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. Backup created: `temp/PlaybackSettingsFragment.kt.20260601_130318.S0254_phase04_3.backup`. Playback layout moved ids expected: 0 | actual: 0. Playback Kotlin moved refs expected: 0 | actual: 0. Sorting/slideshow anchors expected: >=6 | actual: 20. `Log.d(` count expected: 0 | actual: 0. `scripts/catalog_sync.ps1 -Module app_v2` exit 0. Build `.\a.ps1 dq` first failed on unrelated dirty-tree `PdfViewerManager.kt` named-argument mismatch; fixed by changing the call to positional argument only. Final build `.\a.ps1 dq` exit 0. Files: `fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml`, `PlaybackSettingsFragment.kt`; validation unblock: `PdfViewerManager.kt`.

---

### Step 04.4 - Restore icon-size UI, align reset/search ownership, and close repair

**Files:** `fragment_settings_general.xml`, `layout-land/fragment_settings_general.xml`, `GeneralSettingsViewSetupHelper.kt`, `GeneralSettingsObserversHelper.kt`, `SettingsViewModel.kt`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Restore the missing icon-size control required by strategic §11.1:
> - Add `tilIconSize`, `etIconSize`, and `iconHelpGridSize` to General -> Interface portrait and landscape layouts near the relocated grid controls, with existing strings `default_icon_size`, `default_icon_size_value`, `tooltip_grid_size_title`, and `tooltip_grid_size_message`.
> - Wire `setupIconSizeInput()` in `GeneralSettingsViewSetupHelper` using the existing dropdown/focus pattern: options from `32` through `256` in 8dp increments; write to `defaultIconSize`; clamp invalid manual input by restoring the current setting.
> - Wire `iconHelpGridSize` to the existing tooltip dialog.
> - Reconcile `etIconSize` from `settings.defaultIconSize` in `GeneralSettingsObserversHelper`.
>
> Move reset ownership for the relocated controls to the General section:
> - Add `defaultGridMode`, `hideGridActionButtons`, `fileOpsInOverflowMenu`, and `defaultIconSize` to `resetGeneralSection()`.
> - Remove those same fields from `resetPlaybackSection()`.
> - Leave storage keys and repository fields unchanged.
>
> Search routing is layout-driven through `SettingsSearchLayoutCatalog` and `SettingsSearchTabMapping`; do not add manual index records. Instead, verify the rows and `etIconSize` now live only in `fragment_settings_general`, so auto-indexing routes them to `SettingsSearchDestination.GENERAL`.
>
> Run `scripts/catalog_sync.ps1 -Module app_v2` after Kotlin edits.

**Verification:**

- `rg -n "tilIconSize|etIconSize|iconHelpGridSize" app_v2/src/main/res/layout/fragment_settings_general.xml app_v2/src/main/res/layout-land/fragment_settings_general.xml` returns exactly 6 matches total.
- `rg -n "tilIconSize|etIconSize|iconHelpGridSize" app_v2/src/main/res/layout/fragment_settings_playback.xml app_v2/src/main/res/layout-land/fragment_settings_playback.xml` returns zero matches.
- `rg -n "setupIconSizeInput|etIconSize|iconHelpGridSize|defaultIconSize" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` returns at least 8 matches.
- `rg -n "defaultGridMode|hideGridActionButtons|fileOpsInOverflowMenu|defaultIconSize" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` shows those fields inside `resetGeneralSection()` and not inside `resetPlaybackSection()`.
- `rg -n "rowDefaultGridMode|rowHideGridActionButtons|rowFileOpsInOverflowMenu" app_v2/src/main/res/layout/fragment_settings_general.xml app_v2/src/main/res/layout-land/fragment_settings_general.xml` returns exactly 6 matches total.
- `rg -n "rowDefaultGridMode|rowHideGridActionButtons|rowFileOpsInOverflowMenu" app_v2/src/main/res/layout/fragment_settings_playback.xml app_v2/src/main/res/layout-land/fragment_settings_playback.xml` returns zero matches.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- Build: `.\a.ps1 dq` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 7/7 PASS. General icon ids expected: 6 | actual: 6. Playback icon ids expected: 0 | actual: 0. Icon helper refs expected: >=8 | actual: 16. Reset ownership expected in `resetGeneralSection()`: 4 | actual: 4; expected in `resetPlaybackSection()`: 0 | actual: 0. General moved-row ids expected: 6 | actual: 6. Playback moved-row ids expected: 0 | actual: 0. `scripts/catalog_sync.ps1 -Module app_v2` exit 0. Build `.\a.ps1 dq` exit 0. Files: General layouts, General helpers, `SettingsViewModel.kt`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] General portrait and landscape layouts contain the three relocated rows exactly once each.
- [x] Playback portrait and landscape layouts contain none of the relocated rows.
- [x] General helper owns all three listeners and observers.
- [x] Playback fragment no longer references the relocated setting fields.
- [x] Settings search auto-indexing routes the rows through `fragment_settings_general`.
- [x] `scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- [x] `.\a.ps1 dq` exits 0.
- [x] No `Timber.d("S0254:` tags are present until the final transition to `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final repair phase. After Phase 04 passes, `/spec-dev` should transition S0254 back toward implementation closure and then into `BlockNeedUserTest` with fresh debug tags for the three General-row listeners.

---

## Rollback Plan

Revert Phase 04 changes only: remove the three General rows/listeners/observers, restore the two Playback rows/listeners/observers from the timestamped backup or VCS, and move reset ownership back to Playback.
