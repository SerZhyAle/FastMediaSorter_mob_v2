# Phase 03 - aspect-ratio-persistence

**Goal:** The selected aspect ratio is remembered between capture sessions (spec §3.1.4); default is native 4:3.

## Context

- Today `selectedAspectRatio` starts `null` on every `CameraCaptureSessionManager` and is only set when the user opens the settings dialog and picks a ratio. Nothing persists.
- Camera capture settings persist through `CaptureSettingsStore` (DataStore) -> `AppSettings` -> `SettingsRepository`. Follow the `cameraGeotagEnabled` pattern.

## Steps

- [x] **3.1** Add a persisted key `KEY_CAMERA_ASPECT_RATIO` (int preference, CameraX `AspectRatio` value) to `CaptureSettingsStore`; default 4:3 (`AspectRatio.RATIO_4_3`). Thread it through `CaptureSettingsStore.Values`, `read`/`write`, `AppSettings.cameraAspectRatio`, and `SettingsRepositoryImpl`.
  - Verify: `a.ps1 fk` PASS.
- [x] **3.2** Load on open: `CameraCaptureActivity.setupViews` (or `bindCamera`) reads the persisted ratio and seeds `sessionManager.setAspectRatioAndResolution(persistedRatio, null)` before/at bind so the first frame already honours it. Guard against a lens that does not report the ratio (fall back to 4:3 via `availableAspectRatios`).
  - Verify: `a.ps1 fk` PASS.
- [x] **3.3** Save on change: when the settings dialog applies a new ratio (`CameraSettingsCallbackHandler.onCameraSettingsApplied` -> `setAspectRatioAndResolution`), persist it via `SettingsRepository`. Keep the write off the main thread (existing settings write pattern).
  - Verify: `a.ps1 fk` PASS.
- [x] **3.4** Settings-doc sync (Rule 22): this is an in-capture setting persisted in DataStore, not a Settings-screen row. Confirm whether it needs a `settings-manifest.json` entry (only if it surfaces on the Settings screen). If not, note it and skip the manifest regen; if yes, run `assert-settings-doc-sync`.
  - Verify: reasoning note + gate result if applicable.

## Done criteria
- Pick 16:9, close the camera, reopen -> starts at 16:9.
- Fresh install -> starts at 4:3.
- A lens without 16:9 falls back cleanly to 4:3.
