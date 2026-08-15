# Phase 01 - Settings storage

**Strategic spec:** [`../S0620_optional-nine-zone-grid.md`](../S0620_optional-nine-zone-grid.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Introduce a persisted boolean `nineZoneGridEnabled` (default `true`) end-to-end through the settings layer, mirroring the existing `alwaysShowTouchZonesOverlay` field. No UI and no player behaviour change yet - absent value resolves to `true` (grid on), so current behaviour is preserved.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | Modified | ≤ 500 |

> The new field is positive (`nineZoneGridEnabled`); the settings UI (Phase 04) renders its inverse ("Disable 9-zone tracking"). Store positive so an absent value defaults to grid-on.

---

## Steps

### Step 01.1 - Add the field to AppSettings

**Files:** `domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val nineZoneGridEnabled: Boolean = true` to the `AppSettings` data class next to `alwaysShowTouchZonesOverlay` (line ~136). KDoc one-liner: "When false, the fullscreen player uses the simpler 3-zone tap layout instead of the 9-zone grid (S0620)."

**Verification:**

- `Grep` - `nineZoneGridEnabled: Boolean = true` matches once in `AppSettings.kt`.

**Status:** `[ ]` not done

---

### Step 01.2 - Persist via SettingsManager DataStore

**Files:** `data/local/preferences/SettingsManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Mirror every `alwaysShowTouchZonesOverlay` site in `SettingsManager.kt` for `nineZoneGridEnabled`: declare a `booleanPreferencesKey("nine_zone_grid_enabled")`, read it (default `true`) in the `AppSettings` mapping flow, and write it in the save path. Keep the read default `true` so a pre-existing install with no key keeps the grid.

**Verification:**

- `Grep` - `nine_zone_grid_enabled` matches in `SettingsManager.kt`.
- `Grep` - `nineZoneGridEnabled` matches at least twice (read + write) in `SettingsManager.kt`.

**Status:** `[ ]` not done

---

### Step 01.3 - Thread through the repository mapping

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> If `SettingsRepositoryImpl` constructs/copies `AppSettings` field-by-field (as it does for `alwaysShowTouchZonesOverlay`), add `nineZoneGridEnabled` to the same mapping(s) so the value survives the round-trip. If it copies the whole object, no edit is needed - verify and skip.

**Verification:**

- `Grep` - `nineZoneGridEnabled` present in `SettingsRepositoryImpl.kt`, OR a comment/inspection confirms whole-object copy (note in step body which applies).

**Status:** `[ ]` not done

---

### Step 01.4 - Include in backup / import

**Files:** `domain/usecase/BackupData.kt`, `domain/usecase/BackupMapper.kt`, `domain/usecase/ImportSettingsUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Mirror `alwaysShowTouchZonesOverlay` for `nineZoneGridEnabled` in the backup DTO (`BackupData.kt`), the to/from mapping (`BackupMapper.kt`), and the import merge (`ImportSettingsUseCase.kt`). Absent in an older backup -> default `true`.

**Verification:**

- `Grep` - `nineZoneGridEnabled` matches in each of `BackupData.kt`, `BackupMapper.kt`, `ImportSettingsUseCase.kt`.

**Status:** `[ ]` not done

---

### Step 01.5 - Build gate

**Files:** (none - validation only)
**Depends on:** Steps 01.1-01.4

**Prompt for developer:**

> Run `/build` -> `standard debug`. The new field must compile through model, DataStore, repo, and backup.

**Verification:**

- `/build` standard debug PASS.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`AppSettings.nineZoneGridEnabled` is now readable from the settings repository/VM. Phase 02 consumes it in the touch-zone resolver; Phase 04 binds the inverse to a settings toggle.

---

## Rollback Plan

Revert phase commit(s) - additive field with a safe default, no migration, no user-facing surface.
