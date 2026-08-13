# Phase 01 - Settings foundation

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Add three additive boolean settings (`quickVoiceMenuEnabled`, `quickVideoMenuEnabled`, `quickPhotoMenuEnabled`, default off) to the settings model, DataStore persistence, and the device-profile preset matrix. No UI, no menu, no capture.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | +9 lines |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | +3 rows |

> `SettingsRepositoryImpl.kt` is >500 LOC - create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 1.1 - Add three quick-capture settings fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `AppSettings`, next to the existing capture fields (`micRecordingEnabled` .. `cameraPhotosDestinationResourceId`, around line 166-173), add three booleans with default `false`: `quickVoiceMenuEnabled`, `quickVideoMenuEnabled`, `quickPhotoMenuEnabled`. Each is a main-menu quick-capture toggle. Add a one-line WHY comment for the group (these gate the three overflow-menu quick-capture entries; capture writes to the phone's public folders). Do not alter existing fields.

**Verification:**

- `Grep` - `val quickVoiceMenuEnabled: Boolean = false` matches once in `AppSettings.kt`.
- `Grep` - `val quickVideoMenuEnabled: Boolean = false` matches once.
- `Grep` - `val quickPhotoMenuEnabled: Boolean = false` matches once.

**Status:** `[x]` done

---

### Step 1.2 - Persist the three fields in DataStore

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Add three `booleanPreferencesKey` entries (`quick_voice_menu_enabled`, `quick_video_menu_enabled`, `quick_photo_menu_enabled`) in the keys companion alongside `KEY_ENABLE_CALCULATOR` / `KEY_EMBEDDED_GAME_ENABLED`. Read them into the `AppSettings(..)` mapping (default `false`, mirror `enableCalculator = preferences[KEY_ENABLE_CALCULATOR] ?: false`). Write them in the `dataStore.edit { .. }` save block (mirror `preferences[KEY_ENABLE_CALCULATOR] = settings.enableCalculator`). Use the general settings-copy path; do not add a dedicated update function.

**Verification:**

- `Grep` - `quick_voice_menu_enabled` matches once (key declaration).
- `Grep` - `quickVoiceMenuEnabled = preferences\[` present (read mapping).
- `Grep` - `= settings.quickVoiceMenuEnabled` present (write mapping).
- `Grep` - same three checks pass for `quickVideoMenuEnabled` and `quickPhotoMenuEnabled`.

**Status:** `[x]` done

---

### Step 1.3 - Register the three fields in the device-profile preset matrix

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 1.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -AddMissing` to scaffold rows for the three new fields, then confirm a plain run passes. Leave the new rows empty (not profile-applied) - these are user-intent capture toggles, consistent with `enableCalculator` / `enableStatistics`; do not add `DeviceProfilePresetApplier` cases.

**Verification:**

- `Grep` - `quickVoiceMenuEnabled` present in `device_profile_presets.csv`.
- `Grep` - `quickVideoMenuEnabled` and `quickPhotoMenuEnabled` present.
- Script: `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`AppSettings.quick{Voice,Video,Photo}MenuEnabled` exist and persist. Phase 05 (menu) and Phase 06 (settings UI) read/write them.

---

## Step Log

- 2026-06-19 - Step 1.1 Verification 3/3 PASS. `AppSettings.kt` +3 fields (quick{Voice,Video,Photo}MenuEnabled, default false).
- 2026-06-19 - Step 1.2 Verification PASS. `SettingsRepositoryImpl.kt` keys L50-52, read L291-293, write L543-545.
- 2026-06-19 - Step 1.3 Verification PASS. `device_profile_presets.csv` 173/173 (check exit 0); -AddMissing also scaffolded a pre-existing-missing cameraCaptureCopyToClipboard row.

---

## Rollback Plan

Revert phase commit(s) - additive settings only, no migration, no user-facing surface.
