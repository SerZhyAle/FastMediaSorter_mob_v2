# Phase 01 - Tray indicator registry and persisted composition

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Introduce the six-indicator registry and its persisted per-indicator switches, with no change to what the tray
draws yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTrayIndicator.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 30 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 40 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | ≤ 40 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` | Modified | ≤ 15 added |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
> `SettingsRepositoryImpl.kt` and `DeviceProfilePresetApplier.kt` are both over 500 LOC - back each up under
> `temp/S1415/` before the first edit, per CLAUDE.md Rule 5.

---

## Steps

### Step 01.1 - Add the six per-indicator settings fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add six booleans to `AppSettings`, each defaulting to `true`, next to the existing
> `launcherTaskbarShowTray`: `launcherTrayShowClock`, `launcherTrayShowBluetooth`, `launcherTrayShowSim1`,
> `launcherTrayShowSim2`, `launcherTrayShowNetwork`, `launcherTrayShowBattery`. Leave
> `launcherTaskbarShowTray` in place - it stays the master switch for the whole tray block.

**Why:**

Strategic §2 goal 1 requires the composition of the status area to be chosen by the user with one switch per
indicator, and strategic §3.2 forbids a migration, so the switches can only arrive as additive keys with
defaults.

**Verification:**

- `Grep` - each of the six names matches exactly once in `AppSettings.kt`.
- `Grep` - `launcherTaskbarShowTray` still matches in `AppSettings.kt`.

**Status:** `[x]` done

---

### Step 01.2 - Persist the six fields in DataStore

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Back up the file under `temp/S1415/` first. Then add one `booleanPreferencesKey` per new field
> (`KEY_LAUNCHER_TRAY_SHOW_CLOCK` and the other five), read each in the settings mapper with `?: true`, and
> write each in the save block, mirroring the three existing `KEY_LAUNCHER_TASKBAR_SHOW_*` keys line for line.

**Why:**

Strategic §3.2 states new settings are additive keys with defaults and no migration; a missing key must
therefore read as the default rather than as `false`, which is what the `?: true` fallback guarantees.

**Verification:**

- `Grep` - `KEY_LAUNCHER_TRAY_SHOW_` matches 18 times in the file (6 declarations + 6 reads + 6 writes).
- `Grep` - `?: true` appears on each of the six read lines.

**Status:** `[x]` done

---

### Step 01.3 - Accept the six fields in device-profile presets

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Back up the file under `temp/S1415/` first. Add a `when` branch per new field, named exactly as the
> `AppSettings` property, following the shape of the existing `"launcherTaskbarShowTray"` branch.

**Why:**

The three existing taskbar composition fields are all preset-addressable, so omitting the six new ones would
leave a device profile able to configure the tray block but not its contents - an inconsistency strategic §2
goal 1 does not intend.

**Verification:**

- `Grep` - `"launcherTrayShow` matches 6 times in the file.
- `Grep` - each branch calls `applyLauncherField`.

**Status:** `[x]` done

---

### Step 01.4 - Restore the six fields on launcher reset

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the six new fields to the settings copy this use case builds, each taking its value from `defaults`,
> alongside the existing `launcherTaskbarShow*` lines.

**Why:**

Strategic §2 goal 1 puts the tray composition under user control, and the launcher reset action exists to undo
every launcher choice - a switch left untouched by the reset would survive an action the user believes cleared
it.

**Verification:**

- `Grep` - `launcherTrayShow` matches 6 times in the file.
- `Grep` - each of the six lines reads from `defaults.`.

**Status:** `[x]` done

---

### Step 01.5 - Declare the indicator registry

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTrayIndicator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the file with an `enum class LauncherTrayIndicator` whose constants are declared in the left-to-right
> order `CLOCK, BLUETOOTH, SIM1, SIM2, NETWORK, BATTERY`, and a `data class LauncherTrayComposition` holding one
> boolean per indicator plus a `fun isEnabled(indicator: LauncherTrayIndicator): Boolean`. Add a companion
> factory that builds the composition from an `AppSettings`. Document in the KDoc that the declaration order is
> the render order and that S1431 reuses this registry for the top-strip placement.

**Why:**

Strategic §5.1 makes the registry the single place listing what may appear in the tray, and §5.3 requires the
same registry to serve the top-strip mode of S1431 so the two placements cannot drift apart in what they offer.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTrayIndicator.kt` exists.
- `Grep` - `enum class LauncherTrayIndicator` matches exactly once.
- `Grep` - `CLOCK`, `BLUETOOTH`, `SIM1`, `SIM2`, `NETWORK`, `BATTERY` all present in that order.
- `Grep` - `data class LauncherTrayComposition` matches exactly once.
- `Grep` - `Log\.d\(` returns zero hits in every file this step modified.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Step Log

- 2026-08-06 - Step 01.1 Verification 2/2 PASS. Files: `AppSettings.kt` (+10 LOC).
- 2026-08-06 - Step 01.2 Verification 2/2 PASS. Files: `SettingsRepositoryImpl.kt` (+18 LOC). Backup at `temp/S1415/`.
- 2026-08-06 - Step 01.3 Verification 2/2 PASS. Files: `DeviceProfilePresetApplier.kt` (+18 LOC). Backup at `temp/S1415/`.
- 2026-08-06 - Step 01.4 Verification 2/2 PASS. Files: `ResetLauncherToDefaultsUseCase.kt` (+6 LOC).
- 2026-08-06 - Step 01.5 Verification 5/5 PASS. Files: `LauncherTrayIndicator.kt` (New, 51 LOC).
- 2026-08-06 - Phase close: `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL, 28s). `post-change.ps1 -ScopeToFile` PASS WITH ADVISORIES (1). The single advisory was attributable and fixed inside the phase: `assert-device-profile-matrix` named the six new `AppSettings` fields as missing CSV rows, so six all-empty rows were added to `app_v2/src/main/assets/device_profile_presets.csv` (no profile overrides a tray indicator) and the gate re-run exits 0. Catalog regenerated with `catalog_sync.ps1 -Force` - the timestamp heuristic had reported the index up to date while the new file was newer.
- 2026-08-06 - Phase-boundary audit (Layer 1 only; the phase touches no lifecycle, coroutine, listener or Room surface): no findings. The registry sits in `ui/launcher/tray` and depends on `domain/model` in the allowed direction; the six settings fields follow the neighbouring `launcherTaskbarShow*` shape exactly.

---

## Handoff Notes to Next Phase

Six persisted booleans exist and default to `true`; nothing reads them yet. `LauncherTrayIndicator` fixes the
render order for every later phase - no phase re-decides it.

---

## Rollback Plan

Revert phase commit(s) - the new keys are additive with defaults, so a revert leaves stored preferences
readable and no data migration is involved.
