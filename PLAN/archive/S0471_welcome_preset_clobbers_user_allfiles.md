# S0471 - Welcome device-profile preset clobbers user's All Files choice

**Status:** Archived

## 0. Symptom / evidence

- User selected "All Files" during onboarding; afterwards the `allFiles` setting was OFF and the predefined "All Files" resource was absent.
- Log `logs/current.log` (2026-06-17, noLegal/VR_HEADSET):
  - `02:40:30` user toggles ON: `SettingsRepo: updateSettings called with allFiles=true` -> `Saved allFiles=true`.
  - OS permission already granted: `MANAGE_EXTERNAL_STORAGE (AllFiles) = true` (startup), `grant-all run finished (shown 0 special permissions)`.
  - `02:41:46` welcome completion applies VR_HEADSET preset (`ApplyProfilePresetUseCase`, 103 overrides) -> `updateSettings called with allFiles=false` -> `Saved allFiles=false`.
- Root cause: the device-profile preset is applied at welcome **completion** (`WelcomeViewModel.saveDeviceProfile`, on Finish), which runs **after** the functionality/permissions pages where the user toggled `allFiles`. The preset's `allFiles` value for `vr_headset` is `FALSE` (`assets/device_profile_presets.csv`), so it overwrites the user's choice.

## 1. Intended behaviour

- The capability/permission toggles in Welcome must already reflect the **selected device profile** (the profile is the starting point for all settings).
- Any change the user makes to a toggle **after** the profile is chosen is a deliberate deviation from the profile and must survive to completion.
- Therefore the preset must be applied **before** the user reaches those pages, not re-applied at Finish.

## 2. Fix

- Split `ApplyProfilePresetUseCase` into settings-application vs profile bookkeeping:
  - `applySettingsOnly(profileType)` - folds CSV overrides into settings + VR sync, writes, returns whether the profile had overrides. No profile-repository write.
  - `markPresetApplied(presetVersion)` - records the version on the saved profile.
  - `apply()` keeps its current combined behaviour for the Settings re-entry and confirmed-reapply callers.
- `WelcomeViewModel`:
  - `applyFirstRunPresetForSelectedProfile()` - first-run only; applies the selected profile's settings preset once per profile (deduped), so the later pages render its defaults.
  - `saveDeviceProfile` first-run branch: ensure the preset settings ran (covers the Enable-all path that skips the pages), then `markPresetApplied`; it no longer re-writes settings, so user deviations survive.
- `WelcomeActivity.onPageSelected`: once the user advances past the device-profile page, trigger `applyFirstRunPresetForSelectedProfile()`.
- `WelcomeFunctionalityController`: turning the All Files toggle ON now also materializes the predefined "All Files" resource (Welcome has no separate "create resource" button like Settings), mirroring the Enable-all sequence.

## 3. Scope

- First-run onboarding only. Settings re-entry (unchanged-profile skip, changed-profile confirm) keeps current semantics.
- Enable-all shortcut keeps applying the OTHER preset + enabling everything at Finish.

## Last Audit

**Date:** 2026-06-17
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Static contract (§2) fully corroborates the on-device PASS:
- `ApplyProfilePresetUseCase.applySettingsOnly` folds CSV overrides + VR sync, writes settings, no profile-repo write, returns `hadOverrides`.
- `markPresetApplied` records the version separately; `apply()` keeps the combined Settings-re-entry behaviour.
- `WelcomeViewModel.applyFirstRunPresetForSelectedProfile()` is first-run-only and deduped per profile.
- `WelcomeViewModel.saveDeviceProfile` first-run branch saves the profile + `markPresetApplied`, and only re-runs settings when the early apply was skipped (Enable-all path) - never re-writes on the normal page-walk, so user deviations survive Finish.
- `WelcomeActivity.onPageSelected` triggers the early apply once past `profilesPageIndex`.
- `WelcomeFunctionalityController` materializes the predefined All-Files resource when the toggle goes ON.
- Debug-tag invariant satisfied: the lone `Timber.d("S0471:` probe was removed on this Verified flip (WelcomeViewModel.kt).

### Manual / on-device (2026-06-17, /spec-sweep step 5.1)

- Result: PASS
- Device: emulator-5554, sdk_gphone16k_x86_64, Android 17 (SDK 37)
- Flavor/build: noLegal debug, v2.60.6170.932-NoLegal-DEBUG, package `com.sza.fastmediasorter.debug`
- Method: `pm clear` to force first-run, drive Welcome via mobile-mcp, decode persisted `all_files` from `files/datastore/settings.preferences_pb` (source of truth), harvest `S0471:` probes from logcat.

Run 1 - user deviation survives (the bug):
- Profile picked: Video player (`vr_headset` not offered in phone UI; `video_player` preset has `allFiles=FALSE`, reproducing the clobber condition).
- Functionality page rendered "Allow All Files mode" OFF (matches the preset default applied early) - expected; confirms the preset is applied before the toggle pages.
- User toggled "Allow All Files mode" ON (deliberate deviation), then finished the flow.
- Expected: `all_files=true` persists after Finish; predefined "All Files" resource exists.
- Actual: persisted `all_files` protobuf = `08 01` (TRUE) after Finish; `EnsureAllFilesPredefinedResourceUseCase: Created predefined All Files resource: id=1`. The Finish-time path did NOT re-write the setting back to FALSE.
- Probe fired: `WelcomeViewModel: S0471: applying first-run preset settings early for VIDEO_PLAYER (leaving profiles page)` at 09:36:14, before the user's toggle at 09:36:52; Finish at 09:37:44 left `allFiles=true`.

Run 2 - smartphone profile, no regression:
- Profile picked: Personal smartphone (`personal_smartphone` preset has `allFiles=TRUE`), no toggle deviation.
- Expected: `all_files=true` after Finish; no clobber.
- Actual: probe `S0471: applying first-run preset settings early for PERSONAL_SMARTPHONE (leaving profiles page)`; `allFiles=true` set early, predefined resource created (id=1), `saveDeviceProfile` at Finish recorded the profile without re-writing settings; persisted `all_files` = `08 01` (TRUE). No regression.

Note (not a defect): MANAGE_EXTERNAL_STORAGE was NOT granted on this emulator, so the "Allow All Files mode" UI switch reflected the missing OS permission (visually OFF) even when the internal `all_files` setting was true. S0471 concerns the clobber of the internal setting + predefined resource, both verified correct; the OS-grant state is orthogonal.
- Evidence: temp/S0471_devtest/ (logcat_filtered.txt, logcat_run2.txt, settings.preferences_pb, settings_run2.preferences_pb, 01-03 screenshots).
