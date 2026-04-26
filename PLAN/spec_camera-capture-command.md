# Spec: Camera Capture Command

**ID:** ad-hoc
**Short name:** camera-capture-command
**Status:** Verified
**Implemented date:** 2026-04-25
**Verified date:** 2026-04-25
**Audit:** see `PLAN/spec_camera-capture-command__audit_2026-04-25.md`
<!-- auto-approved by /spec-all — 2026-04-25; tactical plan created by /spec-all — 2026-04-25 -->
**Created:** 2026-04-25
**Author:** Serhii Zhyhunenko

---

## Problem Statement

Users who are sorting media in BrowseActivity have no way to take a new photo or video without
leaving the app. Adding a low-priority camera-capture button to the Browse top command bar
allows capturing new content and immediately routing it to the current resource root
(including remote SMB/SFTP/FTP/Cloud destinations) — the app's core value proposition applied
to capture, not just sorting.

---

## User Story

> As a user browsing a photo or video resource in BrowseActivity, I want to take a new photo or
> video with the device camera and have the result automatically saved to the current resource
> root (or the camera folder for virtual resources), so I can capture and organize content
> without switching apps.

---

## Functional Requirements

### FR-1 — Command Visibility (Browse top command bar, `layoutControls`)

The **Camera Capture** button is visible when ALL of:

1. Global setting `disableCameraCapture` is `false` (default).
2. The current resource supports at least one of `MediaType.IMAGE` or `MediaType.VIDEO`
   (directly, or via `allFiles = true`). Audio-only and document-only resources hide the button.
3. The resource path is **not** a virtual path — OR the path is one of:
   - `VIRTUAL_PATH_ALL_VIDEO`
   - `VIRTUAL_PATH_ALL_IMAGES`
   - `VIRTUAL_PATH_CAMERA_PHOTOS`

   Hidden for: `VIRTUAL_PATH_ALL_AUDIO`, `VIRTUAL_PATH_ALL_DOCS`, `VIRTUAL_PATH_RECENT`.

### FR-2 — Camera Launch

Tapping the button:

1. Launches the device default camera app via `MediaStore.ACTION_IMAGE_CAPTURE` (default) or
   `MediaStore.ACTION_VIDEO_CAPTURE`. The OS camera app handles mode switching. The app does
   not pre-select a mode and does not show its own chooser.
2. A temp file is prepared in `Context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)` for
   the capture result, ensuring `FileProvider` URI access on API 26+.
3. BrowseActivity awaits the result via `ActivityResultLauncher<Intent>`.

### FR-3 — Filename Dialog

After a successful capture:

1. A dialog is presented with:
   - Title: **"Имя файла"** / **"File name"** / **"Ім'я файлу"** (trilingual).
   - Pre-filled input: timestamp in `yyyyMMdd_HHmmss` format plus extension
     (e.g., `20260425_143025.jpg`).
   - **OK** / **Отмена** buttons.
2. If `skipCameraFilenameDialog` setting is `true`, the dialog is skipped and the timestamp
   name is used directly.

### FR-4 — Save Destination

| Resource path | Save destination |
| ------------- | --------------- |
| `VIRTUAL_PATH_ALL_VIDEO`, `VIRTUAL_PATH_ALL_IMAGES`, `VIRTUAL_PATH_CAMERA_PHOTOS` | `DCIM/Camera` on device storage |
| `ResourceType.LOCAL` (non-virtual) | Resource root path |
| `ResourceType.SMB` / `SFTP` / `FTP` | Resource root via existing upload/transfer infrastructure (`data/transfer/`) |
| `ResourceType.CLOUD` | Resource root via existing cloud SDK upload |

After saving:

- A success toast with filename and destination is shown.
- The Browse file list is **refreshed** (same as manual refresh) so the new file appears at
  its correct sorted position.
- The list **scrolls to the new file** and the item gets a brief highlight (standard
  scroll-to-position, no special animation required).
- Thumbnail generation for the new item follows the normal lazy-load path — no special trigger.

On failure: an error toast with the reason.

### FR-5 — New Settings (Behaviour section, PlaybackSettingsFragment)

| Setting key | Label (EN) | Default | Effect |
|-------------|-----------|---------|--------|
| `disableCameraCapture` | "Disable camera capture button" | `false` | Hides button globally |
| `skipCameraFilenameDialog` | "Don't ask for filename" | `false` | Skips rename dialog |

`skipCameraFilenameDialog` is subordinate: visible in settings only when
`disableCameraCapture = false`.

Both settings are persisted via DataStore in `SettingsRepositoryImpl` and included in
backup export/import.

---

## Out of Scope

- In-app camera mode selection UI (OS handles this).
- EXIF/metadata editing after capture.
- Capture from PlayerActivity command panel.
- VR edition capture.
- Wear OS.

---

## Architecture Notes

- New class: `BrowseCameraCaptureManager` in `ui/browse/managers/` — owns
  `ActivityResultLauncher` registration, temp file creation, filename dialog, save routing.
- Button: `btnCameraCapture` (MaterialButton) added to `layoutControls` in
  `activity_browse.xml`, placed low priority (after `btnPlay`, `visibility="gone"` initially).
- Visibility toggled in `BrowseStateUiUpdater` (or `BrowseObserverManager`) based on
  resource type, supported media types, and `disableCameraCapture` setting.
- `BrowseButtonSetupHelper.ButtonCallbacks` gains `onCameraCaptureClicked()`.
- Existing `BrowseLauncherManager` may be extended, or `BrowseCameraCaptureManager` registers
  its own launcher directly from `BrowseActivity.onCreate()`.

---

## Acceptance Criteria

- [ ] Camera button hidden for audio-only and document-only resources.
- [ ] Camera button hidden for ALL_AUDIO, ALL_DOCS, RECENT virtual paths.
- [ ] Camera button visible for ALL_VIDEO, ALL_IMAGES, CAMERA_PHOTOS virtual paths.
- [ ] Camera button visible for LOCAL, SMB, FTP, SFTP, CLOUD resources supporting image/video.
- [ ] Tapping button opens device camera app.
- [ ] After capture: filename dialog appears (pre-filled with timestamp).
- [ ] `skipCameraFilenameDialog = true` → dialog skipped, timestamp name used.
- [ ] Captured file saved to correct destination per FR-4.
- [ ] Success toast shown; file list refreshed and scrolled to the new item.
- [ ] On failure: error toast shown.
- [ ] `disableCameraCapture = true` → button hidden globally.
- [ ] Both settings present in Behaviour section of Playback settings.
- [ ] Both settings included in backup export/import.
- [ ] Trilingual strings (EN/RU/UK) for all new UI text.
