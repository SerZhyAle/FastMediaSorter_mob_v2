# Phase 01 - setting-foundation

**Goal:** Add the persisted `secureSensitiveScreens` boolean (default true), mirroring `enableStatistics`.

## Steps

- [ ] **1.1** Add `val secureSensitiveScreens: Boolean = true` to `domain/model/AppSettings.kt` (near `enableStatistics`, ~:355), with a one-line KDoc: WHY default true (secure by default; user-disableable).
  - Verify: `AppSettings.kt` contains `secureSensitiveScreens`.
- [ ] **1.2** In `data/repository/SettingsRepositoryImpl.kt`: declare `booleanPreferencesKey("secure_sensitive_screens")`; load with `?: true` in the AppSettings mapping; write it in the save path (mirror `enableStatistics` at `:204/:528/:724`).
  - Verify: all three sites (key/load/save) reference the new key; `a.ps1 fk` compiles.
- [ ] **1.3** Add a DataStore round-trip unit test in `data/repository/SettingsRepositoryImplTest.kt`: default is `true` when unset; save `false` -> load `false`.
  - Verify: `gradlew testStandardDebugUnitTest --tests "*SettingsRepositoryImplTest*"` PASS.

## Done criteria
- New field persists; unit test green; compiles.
