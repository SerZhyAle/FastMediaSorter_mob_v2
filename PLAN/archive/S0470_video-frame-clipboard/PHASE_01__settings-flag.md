# Phase 01 - settings-flag

**Goal:** Add a single boolean setting `videoFrameCopyToClipboard` (default `false`) across the full settings stack, mirroring the existing `videoSnapshotFormat` plumbing.

**Depends on:** -

---

## Steps

- [ ] 1. `AppSettings.kt`: add `val videoFrameCopyToClipboard: Boolean = false` next to `videoSnapshotFormat`, with a `// S0470` WHY comment.
  - **Verification:** field present; default `false`.

- [ ] 2. `SettingsRepositoryImpl.kt`: add `KEY_VIDEO_FRAME_COPY_TO_CLIPBOARD = booleanPreferencesKey("video_frame_copy_to_clipboard")`; read it into the settings map (`?: false`); write it in the persist block. Mirror `KEY_VIDEO_SNAPSHOT_FORMAT`.
  - **Verification:** key declared once; read + write present.

- [ ] 3. `SettingsViewModel.kt`: thread `videoFrameCopyToClipboard = defaults.videoFrameCopyToClipboard` into the defaults builder next to `videoSnapshotFormat`.
  - **Verification:** assignment present.

- [ ] 4. `BackupData.kt` + `BackupMapper.kt`: add backup field (default `false`) and map both ways (`toBackupSettings` + `toAppSettings`).
  - **Verification:** field + both mappings present.

- [ ] 5. `ImportSettingsUseCase.kt`: import `videoFrameCopyToClipboard = data["videoFrameCopyToClipboard"]?.toBoolean() ?: false`.
  - **Verification:** import line present.

- [ ] 6. `device_profile_presets.csv` + `DeviceProfilePresetApplier.kt`: add the `videoFrameCopyToClipboard` preset row (empty cells) and the applier branch `"videoFrameCopyToClipboard" -> settings.copy(videoFrameCopyToClipboard = raw.toBool())`.
  - **Verification:** csv row + applier branch present.

---

## Phase Done Criteria

- [ ] `.\a.ps1 fk` compiles (settings stack symbol changes only).
