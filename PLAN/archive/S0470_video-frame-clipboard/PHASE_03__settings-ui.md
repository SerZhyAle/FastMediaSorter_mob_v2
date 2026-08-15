# Phase 03 - settings-ui

**Goal:** Add the `SettingsToggleRow` for the new flag in the video snapshot section of the Video settings screen (portrait + landscape), wired through `VideoSettingsFragment`, with localized strings.

**Depends on:** 01

---

## Steps

- [ ] 1. Strings (EN/RU/UK, parity-locked via `set-android-string.ps1 -Action add`):
  - `setting_video_frame_copy_to_clipboard_title` - EN "Save video frames to clipboard"
  - `setting_video_frame_copy_to_clipboard_summary` - EN "Also copy each extracted frame to the clipboard, ready to paste"
  - `video_frame_copied_to_clipboard` - EN "Copied to clipboard"
  - **Verification:** `check_strings_localized.ps1 -KeyPrefix setting_video_frame_copy_to_clipboard` exit 0; toast key present EN/RU/UK.

- [ ] 2. `res/layout/fragment_settings_video.xml`: add a `SettingsToggleRow` `rowVideoFrameCopyToClipboard` inside the snapshot block (after the format-selector `LinearLayout`), `app:str_title=@string/setting_video_frame_copy_to_clipboard_title`, `app:str_subtitle=@string/setting_video_frame_copy_to_clipboard_summary`. Mirror `rowPlayerShowFps`.
  - **Verification:** row present with correct title/subtitle attrs.

- [ ] 3. `res/layout-land/fragment_settings_video.xml`: add the identical row in the matching snapshot block (CLAUDE.md Rule 11 - orientation parity).
  - **Verification:** landscape counterpart row present.

- [ ] 4. `VideoSettingsFragment.kt`: `bindSwitch(binding.rowVideoFrameCopyToClipboard) { isChecked -> viewModel.updateSettings(viewModel.settings.value.copy(videoFrameCopyToClipboard = isChecked)) }` in `setupSnapshotResourcePicker()`; reflect state in `observeData()` via `setSwitchChecked(binding.rowVideoFrameCopyToClipboard, settings.videoFrameCopyToClipboard)`.
  - **Verification:** bind + reflect present; compiles.

---

## Phase Done Criteria

- [ ] `.\a.ps1 fc` compiles (code + resources).
- [ ] String locale audit exit 0.
