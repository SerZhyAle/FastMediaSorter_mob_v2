# Phase 05 - settings-ui-toggle

**Goal:** Add the user-facing toggle row mirroring `rowEnableStatistics`.

Depends on: Phase 01.

## Steps

- [ ] **5.1** Strings via `scripts/utils/set-android-string.ps1 -Action add` across EN/RU/UK:
  - `settings_secure_sensitive_screens_title` (EN "Secure sensitive screens" / RU "Защищать секретные экраны" / UK "Захищати секретні екрани")
  - `settings_secure_sensitive_screens_summary` (EN "Block screenshots and Recents preview on screens showing passwords" / RU/UK equivalents; use `..` not `...`, ё where grammatical)
  - Verify: `scripts/check_strings_localized.ps1 -KeyPrefix "settings_secure_sensitive_screens"` exit 0.
- [ ] **5.2** Add a `SettingsToggleRow` (id `rowSecureSensitiveScreens`) to `res/layout/fragment_settings_general.xml` in the Authorization section (`csh_title=settings_category_authorization`); check `res/layout-land/` counterpart if one exists. No hardcoded hex (`?attr`/`@color`). Verify: row present; `a.ps1 fr` PASS.
- [ ] **5.3** Wire it: `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` `setOnCheckedChangeListener` -> update `AppSettings.secureSensitiveScreens` via the same path `enableStatistics` uses; mirror observer sync in `GeneralSettingsObserversHelper.kt` (`setCheckedSilently`). Verify: toggle reads/writes the setting; `a.ps1 fk` compiles.

## Done criteria
- Toggle appears in the Authorization section, persists, defaults ON.
