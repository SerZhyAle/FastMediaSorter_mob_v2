# Phase 01 - Persisted preference

**Strategic spec:** [`../S1087_system-status-area-replace-option.md`](../S1087_system-status-area-replace-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 2

## Objective

Persist the launcher status-area replacement choice with a default that preserves the visible Android status bar.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImplTest.kt` | Modified or New | ≤ 500 |

## Steps

### Step 01.1 - Add the launcher status-area setting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Add `launcherReplaceSystemStatusArea: Boolean = false` beside the existing launcher settings. Persist and restore it using a dedicated preferences key. Do not place a flavor guard in `src/main`; the setting is shared, while the launcher surface remains gated by its contract.

**Verification:**

- `Grep` - `launcherReplaceSystemStatusArea` occurs in both files.
- `Grep` - a dedicated `KEY_LAUNCHER_REPLACE_SYSTEM_STATUS_AREA` occurs in `SettingsRepositoryImpl.kt`.
- `Grep -n "Log\.d\("` - zero hits in both Kotlin files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-18 - Verification 3/3 PASS. Files: AppSettings.kt, SettingsRepositoryImpl.kt.

### Step 01.2 - Prove the default survives repository round-trip

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImplTest.kt`

**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the existing settings-repository test surface, or add a focused test if absent, to assert that an unset preference reads as false and an explicit true value round-trips. Keep the test on the repository boundary.

**Verification:**

- `Grep` - `launcherReplaceSystemStatusArea` occurs in the test file.
- `Bash` - targeted unit test task exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-18 - Verification 2/2 PASS. `./a.ps1 fu` and `./a.ps1 fk` PASS.

## Phase Audit

- P0/P1: none. The preference is a DataStore value with a default-false read path and no lifecycle owner.

## Phase Done Criteria

- [ ] Every Step 01.* is `[x]` done.
- [ ] Project compiles - run `/build`.
- [ ] Phase-boundary audit has no unresolved P0/P1 findings.
