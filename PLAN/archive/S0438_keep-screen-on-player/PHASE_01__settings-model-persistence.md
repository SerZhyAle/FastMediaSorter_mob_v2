# Phase 01 - Settings model & persistence

**Strategic spec:** [`../S0438_keep-screen-on-player.md`](../S0438_keep-screen-on-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Introduce the persisted `keepScreenOnPlayer` setting field with default `true`, wire it through DataStore read/write, the device-profile preset matrix, and all settings transfer channels (backup, restore, import). No application or UI behavior yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 500 |

---

## Steps

### Step 01.1 - Add `keepScreenOnPlayer` field to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val keepScreenOnPlayer: Boolean = true` to `AppSettings`, placed directly after `preventSleep`. Default `true` so that on upgrade and while the global `preventSleep` is on, the player still keeps the screen on (no behavior change). Add a one-line comment stating the dependent semantics: effective only when `preventSleep` is off; when `preventSleep` is on, this field is logically treated as on and hidden in UI.

**Verification:**

- `Grep` - `val keepScreenOnPlayer: Boolean = true` matches exactly once in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 1/1 PASS. Files: domain/model/AppSettings.kt (+3 LOC). Dev log recorded.

---

### Step 01.2 - Persist `keepScreenOnPlayer` in DataStore

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `private val KEY_KEEP_SCREEN_ON_PLAYER = booleanPreferencesKey("keep_screen_on_player")` next to `KEY_PREVENT_SLEEP`. In the settings read mapping add `keepScreenOnPlayer = preferences[KEY_KEEP_SCREEN_ON_PLAYER] ?: true` next to the `preventSleep` read. In the write mapping add `preferences[KEY_KEEP_SCREEN_ON_PLAYER] = settings.keepScreenOnPlayer` next to the `preventSleep` write.

**Verification:**

- `Grep` - `keep_screen_on_player` matches exactly once (key declaration).
- `Grep` - `keepScreenOnPlayer = preferences\[KEY_KEEP_SCREEN_ON_PLAYER\]` present.
- `Grep` - `preferences\[KEY_KEEP_SCREEN_ON_PLAYER\] = settings.keepScreenOnPlayer` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. Files: data/repository/SettingsRepositoryImpl.kt (+3 LOC; backup in temp/). Dev log recorded.

---

### Step 01.3 - Add preset matrix row and applier case

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`, `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `keepScreenOnPlayer` row to `device_profile_presets.csv` mirroring the column layout of the existing `preventSleep` row (same per-profile boolean values). In `DeviceProfilePresetApplier`, add a `when` case `"keepScreenOnPlayer" -> settings.copy(keepScreenOnPlayer = raw.toBool())` next to the `preventSleep` case. Run `scripts/check_device_profile_presets.ps1` to confirm matrix parity.

**Verification:**

- `Grep` - `^"keepScreenOnPlayer"` matches once in `device_profile_presets.csv`.
- `Grep` - `"keepScreenOnPlayer" -> settings.copy(keepScreenOnPlayer = raw.toBool())` present in `DeviceProfilePresetApplier.kt`.
- Script - `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. Files: assets/device_profile_presets.csv (+1 row), data/preset/DeviceProfilePresetApplier.kt (+1 LOC). Dev log recorded.

---

### Step 01.4 - Carry field through backup/restore/import

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `val keepScreenOnPlayer: Boolean = true` to `BackupData` next to its `preventSleep` field. In `BackupMapper`, map `keepScreenOnPlayer` in both directions (settings → backup model at the `preventSleep = settings.preventSleep` site, and backup model → settings at the `preventSleep = backup.preventSleep` site). In `GeneralSettingsCredentialHelper` settings import, add `keepScreenOnPlayer = settings.optBoolean("keepScreenOnPlayer", currentSettings.keepScreenOnPlayer)` next to the `preventSleep` import line.

**Verification:**

- `Grep` - `keepScreenOnPlayer` present in `BackupData.kt`.
- `Grep` - `keepScreenOnPlayer` matches twice in `BackupMapper.kt` (both directions).
- `Grep` - `optBoolean\("keepScreenOnPlayer"` present in `GeneralSettingsCredentialHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. Files: BackupData.kt (+1), BackupMapper.kt (+2), GeneralSettingsCredentialHelper.kt (+1). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (AppSettings public API changed).

---

## Handoff Notes to Next Phase

- `AppSettings.keepScreenOnPlayer` (default `true`) is readable from `SettingsRepository.getSettings()` and survives restart, backup/restore, import, and preset apply.
- Default `true` means the dependent value preserves current always-on player behavior the first time a user turns the global setting off.

---

## Rollback Plan

Revert phase commit(s) - no Room migration; DataStore key is additive and ignored when absent (defaults to `true`).
