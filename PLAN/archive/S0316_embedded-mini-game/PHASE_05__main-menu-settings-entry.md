# Phase 05 - Main Menu And Settings Entry

Goal: expose the default-off game toggle in Settings and show the main dropdown command only when enabled.

## Files

Modify:
- `app_v2/src/main/res/layout/fragment_settings_general.xml`
- `app_v2/src/main/res/layout-land/fragment_settings_general.xml` if present
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`

Create:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainMiniGameMenuManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/game/GameLaunchIntents.kt`

## Steps

- [x] Back up `MainActivity.kt` before editing because it is over 500 LOC.
- [x] Use the reusable `SettingsToggleRow` pattern for the game toggle; do not add raw `SwitchMaterial + TextView + ImageButton` triplets.
- [x] Add `SettingsActivity.EXTRA_HIGHLIGHT_SETTING` or equivalent local extra so widget fallback can open General Settings and focus/scroll the game toggle.
- [x] Wire `GeneralSettingsFragment` helpers to show title, subtitle, help tooltip, current state, and `viewModel.updateEmbeddedGameEnabled`.
- [x] Ensure the toggle remains default off after reset and reflects imported settings.
- [x] Create `GameLaunchIntents` for launching `GameActivity` and launching Settings with the game-toggle highlight.
- [x] Create `MainMiniGameMenuManager` to compute menu item count and populate/handle the game menu action.
- [x] Modify `MainActivity` only as glue: observe settings, delegate menu count/populate to the helper, and call `refreshMainWindowDropdownMenuVisibility()` when `embeddedGameEnabled` changes.
- [x] Preserve `restitchControlBarFocusChain()` behavior and `btnMainDropdownMenu` hidden state when only one or zero menu items exist.
- [x] If `Timber.d("S0319:` is still stale and S0319 is not `BlockNeedUserTest`, remove it while touching the same area and record the cleanup.

## Verification

- `rg "SettingsToggleRow.*game|game_settings" app_v2/src/main/res/layout app_v2/src/main/java/com/sza/fastmediasorter/ui/settings` shows the canonical toggle path.
- `rg "MainMiniGameMenuManager|GameLaunchIntents" app_v2/src/main/java/com/sza/fastmediasorter` finds the new helper classes.
- `rg "embeddedGameEnabled" app_v2/src/main/java/com/sza/fastmediasorter/ui/main app_v2/src/main/java/com/sza/fastmediasorter/ui/settings` finds observer and toggle wiring.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` succeeds.
- Manual check or UI test confirms `btnMainDropdownMenu` hides the game item when disabled and shows it when enabled.

## Done

- [x] Settings toggle exists, is localized, and defaults off.
- [x] Main dropdown item appears only while enabled.
- [x] Widget fallback can target the game toggle in Settings.

## Step Log

- 2026-05-31: PASS - Added portrait/landscape `SettingsToggleRow` for the embedded game, Settings highlight extra, `GameLaunchIntents`, `MainMiniGameMenuManager`, and MainActivity glue for `embeddedGameEnabled`. `MainActivity.kt`, portrait Settings layout, and landscape Settings layout were backed up under `temp/` before editing.
- 2026-05-31: PASS - `scripts/check_strings_localized.ps1 -KeyPrefix "game_"`, `scripts/catalog_sync.ps1 -Module app_v2`, VS Code Problems, and required `rg` static checks passed. S0319 remains `BlockNeedUserTest`, so the `Timber.d("S0319:` probe in `MainActivity` was intentionally preserved.