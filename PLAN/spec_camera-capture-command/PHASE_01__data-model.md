# Phase 01 — Data Model

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-04-25
**Completed:** 2026-04-25
**Depends on:** none
**Blocks:** Phase 02, 03, 04, 05

---

## Objective

Add `disableCameraCapture` and `skipCameraFilenameDialog` to `AppSettings`, wire them into
`SettingsRepositoryImpl` DataStore, and include both in backup export/import.

No changes to `PlayerState` — this feature lives in Browse, not the player.

---

## Files Touched

| File | New/Mod | Budget |
| ---- | :-----: | -----: |
| `domain/model/AppSettings.kt` | Mod | ≤ 250 |
| `data/repository/SettingsRepositoryImpl.kt` | Mod | ≤ 1000 |
| `domain/usecase/BackupData.kt` | Mod | ≤ 200 |
| `domain/usecase/BackupMapper.kt` | Mod | ≤ 500 |
| `domain/usecase/ExportSettingsUseCase.kt` | Mod | ≤ 600 |
| `domain/usecase/ImportSettingsUseCase.kt` | Mod | ≤ 700 |

---

## Steps

### Step 01.1 — Add fields to AppSettings

**File:** `domain/model/AppSettings.kt`

After `val enableFavorites: Boolean = true`:

```kotlin
val disableCameraCapture: Boolean = false,   // Hide camera-capture button in Browse globally
val skipCameraFilenameDialog: Boolean = false, // Skip rename dialog after capture; use timestamp name
```

**Verification:** `Grep "disableCameraCapture" domain/model/AppSettings.kt` → 1 hit.

---

### Step 01.2 — DataStore keys in SettingsRepositoryImpl

**File:** `data/repository/SettingsRepositoryImpl.kt`

1. In companion object, after `KEY_ENABLE_FAVORITES`:

   ```kotlin
   private val KEY_DISABLE_CAMERA_CAPTURE = booleanPreferencesKey("disable_camera_capture")
   private val KEY_SKIP_CAMERA_FILENAME_DIALOG = booleanPreferencesKey("skip_camera_filename_dialog")
   ```

2. In `getSettings()` mapping, after `enableFavorites`:

   ```kotlin
   disableCameraCapture = preferences[KEY_DISABLE_CAMERA_CAPTURE] ?: false,
   skipCameraFilenameDialog = preferences[KEY_SKIP_CAMERA_FILENAME_DIALOG] ?: false,
   ```

3. In `saveSettings()`, after `KEY_ENABLE_FAVORITES`:

   ```kotlin
   preferences[KEY_DISABLE_CAMERA_CAPTURE] = settings.disableCameraCapture
   preferences[KEY_SKIP_CAMERA_FILENAME_DIALOG] = settings.skipCameraFilenameDialog
   ```

**Verification:** `Grep "KEY_DISABLE_CAMERA_CAPTURE" data/repository/SettingsRepositoryImpl.kt` → 3 hits.

---

### Step 01.3 — BackupData

**File:** `domain/usecase/BackupData.kt`

After `enableFavorites`:

```kotlin
val disableCameraCapture: Boolean = false,
val skipCameraFilenameDialog: Boolean = false,
```

**Verification:** `Grep "disableCameraCapture" domain/usecase/BackupData.kt` → 1 hit.

---

### Step 01.4 — BackupMapper

**File:** `domain/usecase/BackupMapper.kt`

`settingsToBackup()`, after `enableFavorites`:

```kotlin
disableCameraCapture = settings.disableCameraCapture,
skipCameraFilenameDialog = settings.skipCameraFilenameDialog,
```

`backupToSettings()`, after `enableFavorites`:

```kotlin
disableCameraCapture = backup.disableCameraCapture,
skipCameraFilenameDialog = backup.skipCameraFilenameDialog,
```

**Verification:** `Grep "disableCameraCapture" domain/usecase/BackupMapper.kt` → 2 hits.

---

### Step 01.5 — ExportSettingsUseCase

**File:** `domain/usecase/ExportSettingsUseCase.kt`

After the `enableFavorites` export line:

```kotlin
appendLine("    <disableCameraCapture>${settings.disableCameraCapture}</disableCameraCapture>")
appendLine("    <skipCameraFilenameDialog>${settings.skipCameraFilenameDialog}</skipCameraFilenameDialog>")
```

**Verification:** `Grep "disableCameraCapture" domain/usecase/ExportSettingsUseCase.kt` → 1 hit.

---

### Step 01.6 — ImportSettingsUseCase

**File:** `domain/usecase/ImportSettingsUseCase.kt`

In the XML tag parsing block, alongside `enableFavorites`:

```kotlin
"disableCameraCapture" -> copy(disableCameraCapture = value.toBoolean())
"skipCameraFilenameDialog" -> copy(skipCameraFilenameDialog = value.toBoolean())
```

**Verification:** `Grep "disableCameraCapture" domain/usecase/ImportSettingsUseCase.kt` → 1 hit.

---

## Phase Done Criteria

- [x] `Grep "disableCameraCapture" domain/model/AppSettings.kt` → 1 hit
- [x] `Grep "KEY_DISABLE_CAMERA_CAPTURE" data/repository/SettingsRepositoryImpl.kt` → 3 hits
- [x] `Grep "disableCameraCapture" domain/usecase/BackupData.kt` → 1 hit
- [x] `Grep "disableCameraCapture" domain/usecase/BackupMapper.kt` → 2 hits
- [x] `Grep "disableCameraCapture" domain/usecase/ExportSettingsUseCase.kt` → 1 hit
- [x] `Grep "disableCameraCapture" domain/usecase/ImportSettingsUseCase.kt` → 1 hit

**Phase Step Log:**

- 2026-04-25 — Steps 01.1-01.6 verified by audit 2026-04-25; all predicates PASS. Phase status corrected from ⬜ Todo to ✅ Done (bookkeeping gap — phase file was not updated during original spec-dev run).
