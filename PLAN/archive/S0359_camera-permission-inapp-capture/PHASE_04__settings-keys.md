# Phase 04 - Settings key for "open captured photo for editing"

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Add one new boolean setting `cameraCaptureOpenForEditing` end to end (domain model + DataStore persistence + settings backup). "Enabled" and "ask filename" reuse existing flags (`disableCameraCapture`, `skipCameraFilenameDialog`) - no new keys for those.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ +2 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ +4 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ +2 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ +2 |

---

## Steps

### Step 04.1 - Add field to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val cameraCaptureOpenForEditing: Boolean = false` next to `skipCameraFilenameDialog` (line ~138). Default `false` (capture saves and stays in browse, as today).

**Verification:**

- `Grep` - `cameraCaptureOpenForEditing` matches once in `AppSettings.kt`.

**Status:** `[x] done`

---

### Step 04.2 - DataStore key + read + write

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `private val KEY_CAMERA_OPEN_FOR_EDITING = booleanPreferencesKey("camera_open_for_editing")` next to `KEY_SKIP_CAMERA_FILENAME_DIALOG` (line ~140). In the settings `map` (read region ~line 383) add `cameraCaptureOpenForEditing = prefs[KEY_CAMERA_OPEN_FOR_EDITING] ?: false`. In `updateSettings` (write region ~line 595) add `prefs[KEY_CAMERA_OPEN_FOR_EDITING] = settings.cameraCaptureOpenForEditing`. Do NOT add the key to `SettingsManager.kt` (legacy store; live persistence is `SettingsRepositoryImpl`).

**Verification:**

- `Grep` - `camera_open_for_editing` matches once in `SettingsRepositoryImpl.kt`.
- `Grep` - `KEY_CAMERA_OPEN_FOR_EDITING` matches exactly 3 times (decl + read + write).

**Status:** `[x] done`

---

### Step 04.3 - Include in settings backup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `cameraCaptureOpenForEditing` to `BackupData` next to `skipCameraFilenameDialog` (line ~132), and map it both directions in `BackupMapper` (settings->backup region ~line 204, backup->settings region ~line 360). Consistent with the existing camera flags (`disableCameraCapture`, `skipCameraFilenameDialog`) which are backed up.

**Verification:**

- `Grep` - `cameraCaptureOpenForEditing` in `BackupData.kt` (1 hit) and `BackupMapper.kt` (2 hits).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Affected unit tests pass (settings mapper / backup mapper tests if present).
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`cameraCaptureOpenForEditing` is readable from `SettingsRepository`. Phase 05 binds the toggle UI; Phase 06 consumes the flag to route captures into the drawing editor.

---

## Rollback Plan

Revert phase commit(s) - the new DataStore key is additive; absent key reads as `false`. No migration.
