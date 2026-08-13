# Phase 06 - Docs & catalog cleanup

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Retire the dead landscape layout, record the capability, regenerate the catalog, run the gates, insert the device-test tag, and hand to on-device verification.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done and the project builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Deleted | - |
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified (debug tag) | ≤ 785 |

---

## Steps

### Step 06.1 - Retire the dead landscape layout

**Files:** `app_v2/src/main/res/layout-land/activity_camera_capture.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> The camera is portrait-locked (Phase 01), so `res/layout-land/activity_camera_capture.xml` is never inflated. Delete it (Rule 21 dead-weight). This is the documented exemption to Rule 11 (landscape parity) - the camera intentionally has no landscape layout; orientation is handled by icon rotation. Confirm no code references a landscape-only id.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout-land/activity_camera_capture.xml` no longer exists.
- `.\a.ps1 fr` passes (exit 0) - no missing-resource error.

**Status:** `[ ]` not done

---

### Step 06.2 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (area Camera): "In-app camera keeps controls fixed when rotating (icons rotate), shows the save destination, adds a Send-to button and a device-available pro settings dialog (timer, grid, aspect, resolution, exposure, white balance, ISO, shutter, HDR)." Do NOT edit `docs/FEATURES*.md` (Rule 11, `/skill-release`-owned).

**Verification:**

- `Grep` - a new `docs/ALL_FEATURES.jsonl` line mentions `camera` and `Send-to`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 06.3 - Regenerate catalog, run gates, dev log

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md`, `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new helpers: `CameraOrientationManager`, `CameraSettingsDialogFragment`, `GridOverlayView`). Run `check_strings_localized.ps1` for the new key prefixes (`camera_save_destination`, `camera_control_send_to`, `camera_setting`). Run `assert-neuroslop.ps1`, `assert-dialog-cancel-style.ps1`, and `assert-settings-doc-sync.ps1` (if a persistent setting was added). Batch dev-log entries for all modified source files (one per logical change) via `close-and-log.ps1 -DevLogs`.

**Verification:**

- `Grep` - `CameraSettingsDialogFragment` present in `dev/CATALOG/app_v2.jsonl`.
- All gate scripts exit 0; `dev/CHANGELOG.md` has S0754 entries.

**Status:** `[ ]` not done

---

### Step 06.4 - Device-test tag and transition

**Files:** `CameraCaptureActivity.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` (Rule 5). The ticket enters `BlockNeedUserTest`, so add exactly one `Timber.d("S0754: <entry>")` at a representative changed-flow entry (e.g. the settings-dialog apply or the orientation-change handler). One tag only; `S0754:` prefix reserved for this probe. After building, transition: `update.ps1 -Id S0754 -Status BlockNeedUserTest -StatusNote 'Verify on a real device (AVD insufficient for camera): rotate the phone - shutter/controls stay physically put, icons rotate, captures are correctly oriented, no overlap / no system-bar intrusion; save-destination name shows by exit; Send-to opens the recipient list for the last shot; three-dots opens a settings dialog with only the device-available options (timer, grid, aspect, resolution, exposure, white balance, ISO, shutter, HDR) and they apply; presets are 1/3/5/10/20/30 with slider = preset-row width.'`

**Verification:**

- `Grep` - exactly one `Timber.d("S0754:` in `app_v2/src/**`.
- `select.ps1 -Id S0754 -Format json` shows `BlockNeedUserTest`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates; catalog regenerated; string + neuroslop + dialog-cancel + settings-doc gates pass.
- [ ] Exactly one `Timber.d("S0754:` probe present.
- [ ] Ticket status is `BlockNeedUserTest` with a device-test `-StatusNote`.

---

## Handoff Notes to Next Phase

Final phase. On-device verification follows via `/spec-test-device S0754` or `/spec-sweep`; `/spec-check` flips to `Verified` and removes the `S0754:` probe.

---

## Rollback Plan

Revert the phase commit; restore the deleted `layout-land` from VCS if the portrait lock is reverted. Catalog indexes are regenerated, not committed.
