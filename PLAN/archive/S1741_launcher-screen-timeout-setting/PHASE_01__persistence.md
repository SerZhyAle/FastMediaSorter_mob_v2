# Phase 01 - Persistence

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Completed:** 2026-08-17

## Objective

Persist the launcher-private timeout and preserve it through launcher reset and backup/restore.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1,500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 1,500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` | Modified | ≤ 500 |

## Steps

### Step 01.1 - Add launcher timeout setting

**Files:** `AppSettings.kt`, `SettingsRepositoryImpl.kt`

**Prompt for developer:**

> Persist `launcherScreenBlackoutTimeoutSeconds` as a non-negative launcher setting with zero representing Off. Read missing or invalid values as zero.

**Why:**

The strategic spec requires a launcher-only timeout that neither changes nor depends on Android's system timeout.

**Verification:**

- `launcherScreenBlackoutTimeoutSeconds` is declared, read, written and has a zero default. (PASS)

**Status:** `[x]` done

### Step 01.2 - Include launcher setting in backup

**Files:** `BackupData.kt`, `BackupMapper.kt`

**Prompt for developer:**

> Carry the launcher blackout seconds through backup mapping, preserving the current value when an old backup has no field.

**Why:**

The launcher timeout is a user-selected launcher setting and must not disappear during a backup round trip.

**Verification:**

- Both backup directions map the setting. (PASS)

**Status:** `[x]` done

### Step 01.3 - Reset timeout with launcher state

**Files:** `ResetLauncherToDefaultsUseCase.kt`

**Prompt for developer:**

> Restore the timeout to the default in the launcher-only reset path.

**Why:**

The reset use case is the canonical inventory of launcher-owned state.

**Verification:**

- Reset copies the default blackout timeout. (PASS)

**Status:** `[x]` done
