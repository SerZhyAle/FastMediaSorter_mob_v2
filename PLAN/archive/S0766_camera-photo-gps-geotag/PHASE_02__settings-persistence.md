# Phase 02 - Settings persistence (opt-in flag)

**Strategic spec:** [`../S0766_camera-photo-gps-geotag.md`](../S0766_camera-photo-gps-geotag.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none
**Blocks:** Phase 03, Phase 05
**Steps done:** 0 / 3

---

## Objective

Add a single boolean opt-in setting `cameraGeotagEnabled` (default `false`) end-to-end through the DataStore capture store, the `AppSettings` domain model, and the repository read bridge. Write is already covered by `CaptureSettingsStore.write(..)`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/CaptureSettingsStore.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 800 |

---

## Steps

### Step 02.1 - Persist key in CaptureSettingsStore

**Files:** `data/repository/settings/CaptureSettingsStore.kt`

**Prompt for developer:**

> Add `private val KEY_CAMERA_GEOTAG_ENABLED = booleanPreferencesKey("camera_geotag_enabled")`. Add `val cameraGeotagEnabled: Boolean` to the `Values` data class. In `read(..)` set `cameraGeotagEnabled = preferences[KEY_CAMERA_GEOTAG_ENABLED] ?: false`. In `write(..)` set `preferences[KEY_CAMERA_GEOTAG_ENABLED] = settings.cameraGeotagEnabled`.

**Verification:**

- `Grep` - `camera_geotag_enabled` present in the file.
- `Grep` - `cameraGeotagEnabled` present in `Values`, `read`, and `write`.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 02.2 - Add field to AppSettings

**Files:** `domain/model/AppSettings.kt`

**Prompt for developer:**

> Next to `cameraCaptureCopyToClipboard`, add `val cameraGeotagEnabled: Boolean = false,` with a short EN comment: opt-in GPS geotag of in-app camera photos (S0766).

**Verification:**

- `Grep` - `cameraGeotagEnabled: Boolean = false` present in `AppSettings.kt`.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 02.3 - Bridge read in SettingsRepositoryImpl

**Files:** `data/repository/SettingsRepositoryImpl.kt`

**Prompt for developer:**

> In the `AppSettings(..)` construction, next to `cameraCaptureCopyToClipboard = capture.cameraCaptureCopyToClipboard`, add `cameraGeotagEnabled = capture.cameraGeotagEnabled`. The write path already routes through `CaptureSettingsStore.write(preferences, settings)` - no extra write code needed. Verify `resetToDefaults`/enable-all paths need no special handling (default `false` is the safe reset).

**Verification:**

- `Grep` - `cameraGeotagEnabled = capture.cameraGeotagEnabled` present in `SettingsRepositoryImpl.kt`.
- `.\a.ps1 fk` - Kotlin compiles for `standard`.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Steps 02.1-02.3 are `[x] done`.
- [ ] `.\a.ps1 fk` compiles.
- [ ] No new DataStore migration required (additive boolean, default false).

---

## Rollback Plan

Remove the key, the `Values` field, the `AppSettings` field, and the bridge line. Additive-only; partial revert compiles.
