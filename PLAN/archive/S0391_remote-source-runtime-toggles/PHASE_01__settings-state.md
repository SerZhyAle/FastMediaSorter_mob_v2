# Phase 01 - Settings State

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Add six per-source enabled flags (default true) to `AppSettings`, persist them through a new `RemoteSourceSettingsStore`, and include them in reset and backup. No gate, UI, or consumption changes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/RemoteSourceSettingsStore.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 650 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 650 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 400 |

> `SettingsRepositoryImpl.kt` is already large - back it up to `temp/` before editing (>500 LOC).

---

## Steps

### Step 01.1 - Add six per-source flags to AppSettings

**Files:** `domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add six `Boolean` fields to the `AppSettings` data class, each defaulting to `true`: `smbEnabled`, `sftpEnabled`, `ftpEnabled`, `googleDriveEnabled`, `oneDriveEnabled`, `dropboxEnabled`. Default `true` preserves current behavior on upgrade. Do not add comments restating the field name.

**Verification:**

- `Grep` - `val smbEnabled: Boolean = true` matches once in `AppSettings.kt`.
- `Grep` - `dropboxEnabled` matches once in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. Added 6 fields (smbEnabled..dropboxEnabled, default true) to AppSettings.kt. Per S0327 KDoc invariant, scaffolded matching empty rows in device_profile_presets.csv via check_device_profile_presets.ps1 -AddMissing (also closed 4 pre-existing CSV gaps). Empty rows = never applied by presets, default true preserved. Dev log recorded.

---

### Step 01.2 - Persist flags via RemoteSourceSettingsStore + repository wiring

**Files:** `data/repository/settings/RemoteSourceSettingsStore.kt` (New), `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `RemoteSourceSettingsStore` following the `AudioSettingsStore` object pattern: declare six `booleanPreferencesKey` constants (`source_smb_enabled`, `source_sftp_enabled`, `source_ftp_enabled`, `source_gdrive_enabled`, `source_onedrive_enabled`, `source_dropbox_enabled`), a `read(prefs)` that defaults missing keys to `true`, and a `write(mutablePrefs, settings)`. Wire both into `SettingsRepositoryImpl`'s settings-mapping flow and `updateSettings()`. Back up `SettingsRepositoryImpl.kt` to `temp/` first.

**Verification:**

- `Glob` - `data/repository/settings/RemoteSourceSettingsStore.kt` exists.
- `Grep` - `booleanPreferencesKey("source_smb_enabled")` matches once in `RemoteSourceSettingsStore.kt`.
- `Grep` - `RemoteSourceSettingsStore` matches in `SettingsRepositoryImpl.kt` (read and write call sites).
- `Grep -n "Log\.d\("` - zero hits in both touched files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 4/4 PASS. Created RemoteSourceSettingsStore (6 boolean keys, default true). Wired into SettingsRepositoryImpl: import, read (`remoteSource`), 6 constructor fields, write call. Backup at temp/backups/. No Log.d. Dev log recorded.

---

### Step 01.3 - Include flags in reset and backup

**Files:** `ui/settings/SettingsViewModel.kt`, `domain/model/BackupData.kt`, `domain/usecase/BackupMapper.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `SettingsViewModel.resetGeneralSection()`, restore all six flags to their defaults (true) so reset returns to the default-all-enabled state, matching the existing `defaults.<field>` style. In `BackupData` (under `domain/usecase/`) add the six flags and map them in `BackupMapper` both directions, defaulting missing (older backup) values to `true`.

**Verification:**

- `Grep` - `smbEnabled = defaults.smbEnabled` present in `SettingsViewModel.kt` reset path.
- `Grep` - `dropboxEnabled` present in both `BackupData.kt` and `BackupMapper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification PASS (VM reset 6/6 via defaults.X, BackupData 1, BackupMapper 2 both directions). Corrected BackupData path domain/model -> domain/usecase. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (47s; Kotlin-only phase, compile-ladder per CLAUDE.md).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new `RemoteSourceSettingsStore`).

---

## Handoff Notes to Next Phase

Six persisted, default-true booleans exist on `AppSettings` and survive restart. Phase 02 reads them through `SettingsRepository` to build the gate snapshot.

---

## Rollback Plan

Revert phase commit(s) - additive fields with `true` defaults, no migration, no user-facing surface changed.
