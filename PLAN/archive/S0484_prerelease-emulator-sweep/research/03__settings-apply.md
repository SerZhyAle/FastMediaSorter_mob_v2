# Research §6.3 - Significant settings: apply + verify

**Strategic item:** §6.3
**Status:** Resolved
**Date:** 2026-06-17

## Question

Which settings to change in the sweep, and how to apply/verify them programmatically.

## Findings

- Main settings live in Jetpack **DataStore Preferences** (binary protobuf) at `files/datastore/settings.preferences_pb`. Read live via a `Flow`, so most changes take effect without restart, but the binary file **cannot** be injected as XML via adb while the app runs.
- Theme is a separate SharedPreferences mirror `color_theme_prefs.xml` (key `color_theme` = AUTO/LIGHT/DARK), read **synchronously at process start** - changing it needs a process restart.
- Language is in SharedPreferences `app_settings.xml` (key `selected_language`) and/or `LocaleManager` on API 33+ - changing needs restart (auto on `cmd locale`).
- There is **no** adb broadcast / debug intent hook to mutate DataStore settings at runtime.
- `DebugActivity` (`adb shell am start -n <pkg>/.ui.debug.DebugActivity`) exists with a reset-preferences button (deletes the DataStore file) - usable as a pre-sweep reset, but its reset does not clear theme/language SharedPrefs.

## Decision

Drive settings through the **UI via mobile-mcp** (ADR-2 intact, exercises the real settings code path), with two adb shortcuts where they are clean and representative:

- DataStore-backed toggles (sort mode, grid mode, trash/confirm-delete, accept-shared-files / primary-player): set via Settings UI; they apply live.
- Theme: set DARK via Settings UI, then relaunch the app (theme is read at process start) and screenshot to confirm.
- Language: optional; if exercised, use `adb shell cmd locale set-app-locales <pkg> --locales ru` (system handles the restart).

**Chosen significant-settings set (5):**

1. `accept_shared_files` + `is_primary_media_player` → true (gates intent-handler registration).
2. `default_sort_mode` → DATE_DESC (exercises sort path in every browse).
3. `color_theme` → DARK (forces dark mode across all screens; restart required).
4. `use_trash` true + `confirm_delete` false (changes the destructive-op flow).
5. `default_grid_mode` → true (grid layout + thumbnail path).

**Verification:** logcat `SettingsRepositoryImpl: updateSettings` lines for DataStore changes; `ColorThemePrefs: applied color theme mode=DARK` after relaunch for theme; screenshot for grid/dark visual confirmation. Note the idempotency guard - writing an already-current value emits no log, so the sweep must change values away from defaults to get a confirmation line.

## Impact on plan

- Phase 02 `Settings` config lists the 5 settings with target values + verify markers.
- Phase 02 `prerelease-configure.ps1` applies them (UI-driven where DataStore; relaunch step for theme); optional `cmd locale` for language.
- Pre-sweep reset can use `DebugActivity` reset button plus explicit theme/language SharedPrefs clear.

## Out-of-scope findings (parked)

- `DebugActivity` reset does not clear `color_theme_prefs.xml` / `app_settings.xml`.
- No adb-accessible debug settings-injection hook (would make the sweep faster/robust, but is app code).
