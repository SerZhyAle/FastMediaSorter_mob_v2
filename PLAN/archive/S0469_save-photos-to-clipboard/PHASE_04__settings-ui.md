# Phase 04 - settings-ui

**Goal:** Add the user-facing toggle on the operations/destinations settings screen and the trilingual strings, mirroring the `cameraCaptureOpenForEditing` row.

**Depends on:** 01

---

## Steps

- [ ] **04.1 - Layout rows (portrait + landscape).**
  - In `app_v2/src/main/res/layout/fragment_settings_destinations.xml`: add a `SettingsToggleRow` with id `rowCameraCopyToClipboard` directly under `rowCameraOpenForEditing`, label `@string/settings_camera_copy_to_clipboard_title`, supporting text `@string/settings_camera_copy_to_clipboard_summary`. Copy the focusable / nextFocus attributes from the neighbouring row.
  - Apply the identical edit to `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` (Rule 11).
  - **Verification:** both layout files contain `rowCameraCopyToClipboard`; `.\a.ps1 fr` PASS.

- [ ] **04.2 - Strings EN/RU/UK.**
  - Add via `scripts/utils/set-android-string.ps1 -Action add` (lockstep EN/RU/UK):
    - `settings_camera_copy_to_clipboard_title`
    - `settings_camera_copy_to_clipboard_summary`
    - `camera_capture_copied_to_clipboard` (the confirmation from Phase 03)
  - Tone must follow `docs/COMMUNICATION_POLICY.md`.
  - **Verification:** `scripts/check_strings_localized.ps1 -KeyPrefix "settings_camera_copy_to_clipboard"` and `-KeyPrefix "camera_capture_copied_to_clipboard"` exit 0.

- [ ] **04.3 - Fragment binding + listener.**
  - In `ui/settings/fragments/OperationsSettingsFragment.kt`: in the settings-observer block, sync `rowCameraCopyToClipboard.setCheckedSilently(settings.cameraCaptureCopyToClipboard)` (guarded by an `isChecked != ..` check like the neighbour); wire its change listener to `viewModel.updateSettings(current.copy(cameraCaptureCopyToClipboard = isChecked))`.
  - **Verification:** fragment references `rowCameraCopyToClipboard` + `cameraCaptureCopyToClipboard`; `.\a.ps1 fc` PASS.

---

## Phase Done Criteria

- The toggle is visible, default-off, keyboard/D-pad focusable, persists across restarts, and is present in EN/RU/UK.
