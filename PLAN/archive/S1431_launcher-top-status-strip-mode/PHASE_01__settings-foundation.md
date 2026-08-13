# Phase 01 - Settings foundation

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Persist the new mode as one boolean setting and expose it to the launcher home surface as a flow. No UI,
no rendering change - after this phase the value exists and is readable, and nothing observes it yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 955 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 695 |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` | Modified | - |

> `AppSettings.kt` (515), `SettingsRepositoryImpl.kt` (945) and `LauncherHomeViewModel.kt` (683) are all
> over 500 LOC - step 01.1 takes the mandatory backups (Rule 5) before any of them is edited.

---

## Steps

### Step 01.1 - Back up the three files this phase edits

**Files:** `temp/S1431/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `AppSettings.kt`, `SettingsRepositoryImpl.kt` and `LauncherHomeViewModel.kt` into `temp/S1431/`
> with a timestamp in each name before editing any of them.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup under `temp/` before editing any file over 500 LOC, and
all three cross that line.

**Verification:**

- `Glob` - `temp/S1431/AppSettings*.kt` matches at least one file.
- `Glob` - `temp/S1431/SettingsRepositoryImpl*.kt` matches at least one file.
- `Glob` - `temp/S1431/LauncherHomeViewModel*.kt` matches at least one file.

**Status:** `[x] done`

---

### Step 01.2 - Add the mode field to `AppSettings`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `val launcherTopStatusStripMode: Boolean = false` next to `launcherReplaceSystemStatusArea`
> (around line 366), keeping the launcher block's existing grouping and ordering.

**Why:**

Strategic §3.2 fixes the data shape as one new boolean defaulting to off, so an existing install keeps
today's layout until the user opts in.

**Verification:**

- `Grep` - `launcherTopStatusStripMode: Boolean = false` matches exactly once in `AppSettings.kt`.
- `Grep` - `launcherReplaceSystemStatusArea` still matches in `AppSettings.kt`.

**Status:** `[x] done`

---

### Step 01.3 - Persist the field through the settings repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add DataStore key `launcher_top_status_strip_mode` beside `launcher_replace_system_status_area`
> (key block around line 225), read it into `AppSettings` in the mapping block around line 583-593, and
> write it in the update block around line 812-821. Follow the shape the neighbouring launcher booleans
> already use; do not reorder existing keys.

**Why:**

Strategic §11 criterion 9 requires the mode to survive being switched off and on, which it can only do
if the value is persisted rather than held in memory.

**Verification:**

- `Grep` - `launcher_top_status_strip_mode` matches exactly once in `SettingsRepositoryImpl.kt`.
- `Grep` - `launcherTopStatusStripMode` matches at least three times in `SettingsRepositoryImpl.kt`
  (key definition, read mapping, write mapping).

**Status:** `[x] done`

---

### Step 01.4 - Expose the mode to the launcher home surface

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `topStatusStripMode: StateFlow<Boolean>` mapped from settings, mirroring how
> `replaceSystemStatusArea` is built around line 191-194. Emit `true` only when both the new field and
> `launcherReplaceSystemStatusArea` are on, so no consumer has to re-check the gate.

**Why:**

Strategic §3.3 makes the mode available only together with replacement of the system status area, and
strategic risk row "режим включён, а замещение выключено" is prevented by combining the two once here
rather than in each of the three consumers.

**Verification:**

- `Grep` - `topStatusStripMode` matches in `LauncherHomeViewModel.kt`.
- `Grep` - `StateFlow<Boolean>` present on the `topStatusStripMode` declaration line.
- `Grep -n "Log\.d\("` - zero hits in `LauncherHomeViewModel.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`LauncherHomeViewModel.topStatusStripMode` emits `true` only when the mode and the status-area
replacement are both on. Downstream phases consume it directly and must not re-check
`replaceSystemStatusArea` themselves.

---

## Rollback Plan

Revert phase commit(s). The new DataStore key is additive and absent keys read as `false`, so a rollback
leaves stored preferences readable.

---

## Step Log

- 2026-08-09 - Step 01.1 done. Backed up AppSettings.kt (515 LOC), SettingsRepositoryImpl.kt (945), LauncherHomeViewModel.kt (683) to `temp/S1431/*_20260809_014838.kt`. expected: 3 files | actual: 3.
- 2026-08-09 - Step 01.2 done. Added `launcherTopStatusStripMode: Boolean = false` after `launcherReplaceSystemStatusArea`. expected: 1 declaration | actual: 1.
- 2026-08-09 - Step 01.3 done. Added `KEY_LAUNCHER_TOP_STATUS_STRIP_MODE` ("launcher_top_status_strip_mode") at line 227, read at 597, write at 826. expected: key literal 1, field refs >=3 | actual: 1, 4.
- 2026-08-09 - Step 01.4 done. Added `topStatusStripMode: StateFlow<Boolean>` folding the mode AND the replacement gate. expected: declaration present, Log.d 0 | actual: line 202, 0.
- 2026-08-09 - Phase build: `.\a.ps1 fk` BUILD SUCCESSFUL, exit 0. Only warning is pre-existing in BrowseStateUiUpdater.kt, outside this phase's files.
- 2026-08-09 - Phase-boundary audit (Layer 1 + Layer 2): the new flow mirrors the adjacent `replaceSystemStatusArea` exactly (`distinctUntilChanged` + `stateIn(viewModelScope, Eagerly, false)`); no listener, no Room, no dispatcher change. No P0/P1 findings.
- 2026-08-09 - Follow-up inside phase 01 (in-scope, caused by step 01.2). `assert-device-profile-matrix.ps1` went from PASS to exit 1: every `AppSettings` field needs a preset row AND an applier branch. Added an all-empty row via `check_device_profile_presets.ps1 -AddMissing` (deliberately preset by no profile - the neighbouring `launcherReplaceSystemStatusArea` is TRUE for tv_media_box/car_head_unit, but turning the new mode on there would be an owner-unreviewed behaviour change) plus the applier branch. Gate re-run: expected exit 0 | actual exit 0.
- 2026-08-09 - Follow-up defect found by the same trail: `ResetLauncherToDefaultsUseCase` enumerates every launcher field by name and was missing the new one, so "reset launcher" would have cleared `launcherReplaceSystemStatusArea` while leaving the mode stored as on - the exact inconsistency strategic risk row 6 names. Added the field. Rebuild `..ps1 fk`: expected exit 0 | actual exit 0.
