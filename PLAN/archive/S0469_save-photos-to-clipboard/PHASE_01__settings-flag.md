# Phase 01 - settings-flag

**Goal:** Add a global boolean setting `cameraCaptureCopyToClipboard` (default `false`), mirroring the existing `cameraCaptureOpenForEditing` flag across model, store, repository, view-model, backup/import, and device-preset applier.

**Depends on:** -

---

## Steps

- [ ] **01.1 - Domain model + DataStore store.**
  - In `app_v2/.../domain/model/AppSettings.kt`: add `val cameraCaptureCopyToClipboard: Boolean = false` next to `cameraCaptureOpenForEditing`, with a short WHY comment.
  - In `app_v2/.../data/repository/settings/CaptureSettingsStore.kt`: add `KEY_CAMERA_COPY_TO_CLIPBOARD = booleanPreferencesKey("camera_copy_to_clipboard")`, a `cameraCaptureCopyToClipboard: Boolean` field in `Values`, read (`?: false`) and write.
  - **Verification:** `cameraCaptureCopyToClipboard` appears in both files; `compileStandardDebugKotlin` resolves it.

- [ ] **01.2 - Repository read + settings view-model defaults + backup/import + preset.**
  - `data/repository/SettingsRepositoryImpl.kt`: map `cameraCaptureCopyToClipboard = capture.cameraCaptureCopyToClipboard` where the other capture fields are mapped.
  - `ui/settings/SettingsViewModel.kt`: add `cameraCaptureCopyToClipboard = defaults.cameraCaptureCopyToClipboard` in the defaults block.
  - `domain/usecase/BackupData.kt`: add `val cameraCaptureCopyToClipboard: Boolean = false`.
  - `domain/usecase/BackupMapper.kt`: map the field in BOTH directions (settings->backup and backup->settings).
  - `domain/usecase/ImportSettingsUseCase.kt`: add `cameraCaptureCopyToClipboard = data["cameraCaptureCopyToClipboard"]?.toBoolean() ?: false`.
  - `data/preset/DeviceProfilePresetApplier.kt`: add `"cameraCaptureCopyToClipboard" -> settings.copy(cameraCaptureCopyToClipboard = raw.toBool())`.
  - **Verification:** `grep -rn "cameraCaptureCopyToClipboard"` lists all 7 plumbing sites; `.\a.ps1 fk` PASS.

---

## Phase Done Criteria

- The flag is persisted, read back, exported/imported, and preset-applicable - exactly like `cameraCaptureOpenForEditing`.
- No UI yet (Phase 04). No behaviour change yet (Phase 03).
