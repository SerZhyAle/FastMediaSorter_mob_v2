# Phase 02 - Domain + Data Cleanup (AppSettings, DataStore, Backup)

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Remove the three dead fields (`vrForcedPlatFormat`, `vrForcedSphericalFormat`, `vrRememberFileFormat`) from the domain `AppSettings` model, drop their DataStore keys + read/write code from `SettingsRepositoryImpl`, drop their backup serialization fields and mapping. Old backup files that still carry these keys must continue to import without failure (unknown-keys-are-ignored discipline - no schema version bump).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | < current size |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | < current size |

---

## Steps

### Step 02.1 - Strip three fields from `AppSettings`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete three properties from the `AppSettings` data class:
>
> - `vrForcedPlatFormat: String = "AUTO"` (line 180 area)
> - `vrForcedSphericalFormat: String = "AUTO"` (line 181 area)
> - `vrRememberFileFormat: Boolean = true` (line 183 area)
>
> Keep `playerShowFps`, `allowSeparateWindow`, and any other VR-adjacent field (e.g. detection settings) untouched - those are owned by other specs. Update any KDoc that references the removed fields.

**Verification:**

- `Grep -n "vrForcedPlatFormat"` in this file → 0 hits.
- `Grep -n "vrForcedSphericalFormat"` in this file → 0 hits.
- `Grep -n "vrRememberFileFormat"` in this file → 0 hits.
- `Grep -n "data class AppSettings"` in this file → 1 hit (declaration intact).
- `Grep -n "playerShowFps"` in this file → exactly 1 hit (field still present).
- `Grep -n "allowSeparateWindow"` in this file → exactly 1 hit (field still present).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 6/6 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` (+0 LOC; already clean). Dev log deferred to Step 02.4 per phase plan.

---

### Step 02.2 - Drop DataStore keys + read/write in `SettingsRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete the following from `SettingsRepositoryImpl`:
>
> - `private val KEY_VR_FORCED_PLAT_FORMAT = stringPreferencesKey("vr_forced_plat_format")` (and the analog spherical/remember keys near it).
> - `private fun readVrForcedPlatFormat(...)` helper if it exists, and the same for spherical.
> - In the `getSettings()` flow / mapping function: the three lines that build the `AppSettings(.., vrForcedPlatFormat = readVrForcedPlatFormat(preferences), vrForcedSphericalFormat = readVrForcedSphericalFormat(preferences), vrRememberFileFormat = preferences[KEY_VR_REMEMBER_FILE_FORMAT] ?: true, ..)`.
> - In the write path: the three `preferences[KEY_VR_*] = settings.vr*` assignments.
>
> Keep all unrelated keys, helpers, and assignments unchanged. After the edit, no other file should reference any `KEY_VR_FORCED_PLAT_FORMAT` / `KEY_VR_FORCED_SPHERICAL_FORMAT` / `KEY_VR_REMEMBER_FILE_FORMAT` constant.

**Verification:**

- `Grep -n "KEY_VR_FORCED_PLAT_FORMAT"` in this file → 0 hits.
- `Grep -n "KEY_VR_FORCED_SPHERICAL_FORMAT"` in this file → 0 hits.
- `Grep -n "KEY_VR_REMEMBER_FILE_FORMAT"` in this file → 0 hits.
- `Grep -n "vrForcedPlatFormat"` in this file → 0 hits.
- `Grep -n "vrForcedSphericalFormat"` in this file → 0 hits.
- `Grep -n "vrRememberFileFormat"` in this file → 0 hits.
- Repo-wide grep for the three KEY_VR_* names → 0 hits anywhere.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` (+0 LOC; already clean). Backup created in `temp/`. Dev log deferred to Step 02.4 per phase plan.

---

### Step 02.3 - Drop fields from `BackupData` and `BackupMapper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `BackupData.kt`:
>
> - Delete the three fields `vrForcedPlatFormat: String = "AUTO"`, `vrForcedSphericalFormat: String = "AUTO"`, `vrRememberFileFormat: Boolean = true` from `BackupSettings` (around lines 153, 154, 156 in the current file).
> - Keep the legacy `vrForcedFormat: String? = null` field (line 162 area). It is already null-fallback and may still appear in extremely old backups. Reading it has no effect (no migration target). Add a one-line KDoc note: `Deprecated since S0251 - kept only so old JSON backups still deserialize.`
>
> In `BackupMapper.kt`:
>
> - Delete from `toBackupSettings`: the three lines `vrForcedPlatFormat = settings.vrForcedPlatFormat`, `vrForcedSphericalFormat = settings.vrForcedSphericalFormat`, `vrRememberFileFormat = settings.vrRememberFileFormat` (around lines 215-218).
> - Delete from the `toAppSettings` migration / mapping function: the block that calls `backup.vrForcedFormat?.gsonSafe("AUTO")` and the conditional rules that map `legacyVrForcedFormat` to `current.vrForcedPlatFormat` / `current.vrForcedSphericalFormat` (around lines 259-268). Also remove the trailing `vrForcedPlatFormat = backup.vrForcedPlatFormat.gsonSafe(migratedPlatFormat)`, `vrForcedSphericalFormat = backup.vrForcedSphericalFormat.gsonSafe(migratedSphericalFormat)`, `vrRememberFileFormat = backup.vrRememberFileFormat` lines (around 377-380).
> - Ensure the desearializer in `BackupData`/Gson configuration still tolerates unknown keys (Gson does so by default - confirm there is no strict-mode flag set).

**Verification:**

- `Grep -n "vrForcedPlatFormat"` in `BackupData.kt` → 0 hits.
- `Grep -n "vrForcedSphericalFormat"` in `BackupData.kt` → 0 hits.
- `Grep -n "vrRememberFileFormat"` in `BackupData.kt` → 0 hits.
- `Grep -n "vrForcedFormat"` in `BackupData.kt` → exactly 1 hit (the deprecated null-fallback field).
- `Grep -n "vrForcedPlatFormat"` in `BackupMapper.kt` → 0 hits.
- `Grep -n "vrForcedSphericalFormat"` in `BackupMapper.kt` → 0 hits.
- `Grep -n "vrRememberFileFormat"` in `BackupMapper.kt` → 0 hits.
- `Grep -n "legacyVrForcedFormat"` in `BackupMapper.kt` → 0 hits.
- Repo-wide grep for `vrForcedPlatFormat`, `vrForcedSphericalFormat`, `vrRememberFileFormat` → 0 hits anywhere except in `PlayerStereoModeCoordinator.kt` (cleaned in Phase 03) and any test fixtures.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 9/9 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` (-3 LOC), `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` (-28 LOC). `RestoreFromGoogleDriveUseCase` uses lenient Gson and no strict unknown-key mode. Dev log deferred to Step 02.4 per phase plan.

---

### Step 02.4 - Update dev log entries and run target build

**Files:** dev log; build verification
**Depends on:** Steps 02.1 - 02.3

**Prompt for developer:**

> Append a dev log line per modified file:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt" "S0251" "Phase 02: drop dead VR forced-format fields (vrForcedPlatFormat, vrForcedSphericalFormat, vrRememberFileFormat)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt" "S0251" "Phase 02: drop DataStore keys + read/write for removed VR forced-format fields"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt" "S0251" "Phase 02: drop three backup fields; keep legacy vrForcedFormat as null-only fallback for old JSON"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt" "S0251" "Phase 02: drop forced-format mapping + legacy migration block"
> ```
>
> Then run `/build` for `standardDebug`, `vrDebug`, `noLegalDebug`. Confirm compile passes.

**Verification:**

- `Grep -n "S0251.*Phase 02"` in `dev/CHANGELOG.md` → exactly 4 hits.
- `/build` exits cleanly (compile + assembly) for the three variants.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Files: `dev/CHANGELOG.md`. Dev log added 4 current-session entries; broad `S0251.*Phase 02` count is 6 because 2 earlier partial-run entries already existed. Builds PASS: `.\gradlew.bat assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0; `.\gradlew.bat assembleVrDebug "-Pchaquopy.enabled=false"` exit 0; `.\gradlew.bat assembleNoLegalDebug "-Pchaquopy.enabled=true" --no-configuration-cache` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `/build` for `standardDebug`, `vrDebug`, `noLegalDebug`.
- [x] Repo-wide `Grep -n "vrForcedPlatFormat|vrForcedSphericalFormat|vrRememberFileFormat"` returns hits only in `PlayerStereoModeCoordinator.kt` (cleaned next in Phase 03) - no other source code or resources.
- [x] Dev log carries 4 new S0251 Phase 02 entries.

---

## Handoff Notes to Next Phase

`AppSettings`, repository, and backup no longer reference the three dead fields. The only remaining surface is `PlayerStereoModeCoordinator`, which holds private fields and an unused `applySettings()` method that took these three values as parameters. Phase 03 removes that vestigial code.

---

## Rollback Plan

Revert the four file diffs in reverse order (Mapper → Data → Repository → Settings). DataStore continues to carry the old keys for already-installed builds, so old write paths would still hydrate without crash. No Room/SQL state changed.
