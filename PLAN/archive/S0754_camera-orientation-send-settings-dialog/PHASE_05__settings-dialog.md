# Phase 05 - Camera settings dialog

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/01__settings-dialog-capabilities.md`](research/01__settings-dialog-capabilities.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Add the "three-dots" control opening a dialog of the device-available camera settings (capability-gated), plus the grid overlay and self-timer countdown that two of those settings drive.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done (capabilities + apply seams exist).
- [ ] `CameraCaptureActivity.kt` > 500 LOC - back up to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_camera_settings.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |
| `app_v2/src/main/res/layout/dialog_camera_settings.xml` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt` | New | ≤ 320 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 780 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/GridOverlayView.kt` | New | ≤ 80 |

> Camera is portrait-locked (Phase 01) - `res/layout-land/activity_camera_capture.xml` is dead (retired in Phase 06); do not edit the landscape variant.

---

## Steps

### Step 05.1 - Settings strings and three-dots icon

**Files:** `ic_camera_settings.xml` (New), strings (EN/RU/UK)
**Depends on:** - start of phase

**Prompt for developer:**

> Create a 24dp three-dots / tune vector `ic_camera_settings`. Add the dialog strings via `set-android-string.ps1 -Action add` (one call covers EN/RU/UK): `camera_settings_title`, `camera_setting_timer`, `camera_setting_grid`, `camera_setting_aspect`, `camera_setting_resolution`, `camera_setting_exposure`, `camera_setting_white_balance`, `camera_setting_iso`, `camera_setting_shutter`, `camera_setting_hdr`. Tone per `docs/COMMUNICATION_POLICY.md` §6.

**Verification:**

- `Glob` - `ic_camera_settings.xml` exists.
- `Grep` - `camera_settings_title` and `camera_setting_iso` in all three `values*/strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "camera_setting"` exits 0.

**Status:** `[ ]` not done

---

### Step 05.2 - Dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_camera_settings.xml` (New)
**Depends on:** Step 05.1

**Prompt for developer:**

> Create a scrollable dialog layout with rows for each setting (toggle/spinner/slider as fits): timer, grid, aspect ratio, resolution, exposure compensation, white balance, ISO, shutter, HDR. Use the unified dialog confirm/cancel pair (`Widget.FastMediaSorter.Button.DialogConfirm`/`DialogCancel` per CLAUDE.md Rule 11 dialog-action-pair). No inline `#hex` (Rule 19).

**Verification:**

- `Glob` - `dialog_camera_settings.xml` exists.
- `Grep` - `Widget.FastMediaSorter.Button.DialogConfirm` and `DialogCancel` referenced; no `="#` in the file.
- `.\a.ps1 fr` passes (exit 0).

**Status:** `[ ]` not done

---

### Step 05.3 - Dialog fragment, capability-gated

**Files:** `CameraSettingsDialogFragment.kt` (New)
**Depends on:** Step 05.2

**Prompt for developer:**

> Create `CameraSettingsDialogFragment` (DialogFragment) that takes the current `CameraRuntimeCapabilities` and current settings, shows ONLY rows the device supports (hide ISO/shutter unless `supportsManualSensor`; hide WB unless `awbModes` non-trivial; hide HDR unless `supportsHdrExtension`; exposure unless supported), and returns the chosen values via a callback. Sensor settings are session-only; timer/grid/aspect persist (preferences hook in Phase 06). Apply sensor/WB/exposure live; apply aspect/resolution on dismiss (single rebind). No lifecycle-unsafe Flow collection.

**Verification:**

- `Glob` - `CameraSettingsDialogFragment.kt` exists.
- `Grep` - `class CameraSettingsDialogFragment` matches once; `supportsManualSensor` gating present.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 05.4 - Grid overlay view

**Files:** `GridOverlayView.kt` (New), `activity_camera_capture.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Create `GridOverlayView` drawing a 3x3 rule-of-thirds grid over the preview, toggled by `gridEnabled`. Add it over `previewViewCamera` in the layout, `visibility="gone"` by default. Lines use `@color/camera_capture_control_stroke` (no inline hex).

**Verification:**

- `Glob` - `GridOverlayView.kt` exists.
- `Grep` - `@+id/cameraGridOverlay` (or the chosen id) in the layout; `class GridOverlayView` matches once.
- `.\a.ps1 fr` passes (exit 0).

**Status:** `[ ]` not done

---

### Step 05.5 - Three-dots button, countdown, and wiring

**Files:** `activity_camera_capture.xml`, `CameraCaptureActivity.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` (Rule 5). Add `@+id/btnCameraSettings` (three-dots) to the top bar (overlay button style, registered for icon rotation). On click open `CameraSettingsDialogFragment` with the current capabilities; route its callbacks to the session apply seams (Phase 04) and to `gridEnabled`/`selfTimerSeconds`. Implement the self-timer: on shutter, if `selfTimerSeconds > 0`, show a full-screen countdown overlay then capture. Toggle the grid overlay from `gridEnabled`.

**Verification:**

- `Grep` - `@+id/btnCameraSettings` in the layout; `CameraSettingsDialogFragment` shown from `CameraCaptureActivity.kt`.
- `Grep` - self-timer countdown path present before `sessionManager.capture(`.
- `Glob` - fresh `temp/CameraCaptureActivity.kt.*.bak`.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `assert-dialog-cancel-style.ps1` passes (dialog action pair); `assert-neuroslop.ps1` no new violations.
- [ ] Settings-doc sync gate if a persistent app setting was added (Rule 22) - run `assert-settings-doc-sync.ps1`.
- [ ] Dev log entries (batched in Phase 06).

---

## Handoff Notes to Next Phase

The pro-settings dialog renders device-available options and applies them; grid + self-timer work. Phase 06 finalizes (catalog, ALL_FEATURES, retire layout-land, device-test).

---

## Rollback Plan

Revert the phase commit; restore `CameraCaptureActivity.kt` from `temp/`. New files (dialog, overlay) deleted on revert. No migration.
