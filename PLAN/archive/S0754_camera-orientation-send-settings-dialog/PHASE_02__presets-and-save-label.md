# Phase 02 - Preset polish and save-destination label

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Drop the "2" zoom step, make the zoom slider + value match the preset-row width, and show the save-destination resource name beside the exit button.

---

## Prerequisites

- [ ] `CameraCaptureActivity.kt` > 500 LOC - back up to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 110 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 700 |

> Portrait-locked (Phase 01) - only `res/layout/activity_camera_capture.xml` exists in use; `layout-land` retired in Phase 06, do not edit it.

---

## Steps

### Step 02.1 - Drop the "2" preset step

**Files:** `CameraRuntimeCapabilities.kt`, `CameraRuntimeCapabilitiesTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `buildZoomPresets` remove `2f` from `desiredEquiv` (now `1, 3, 5, 10, 20, 30`). Update `CameraRuntimeCapabilitiesTest` expectations: the wide-lens and mid-lens cases drop the `2` entry.

**Verification:**

- `Grep` - `desiredEquiv = listOf(1f, 3f, 5f, 10f, 20f, 30f)` matches once.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*CameraRuntimeCapabilitiesTest"` passes.

**Status:** `[ ]` not done

---

### Step 02.2 - Slider + value width equals the preset row

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Constrain `cameraZoomSlider` start to the preset group start and `cameraZoomValue` end to the preset group end (slider end -> value start), so the slider plus the value pinned to its right span exactly the preset-row width. No `="#hex"` colours (Rule 19).

**Verification:**

- `Grep` - `cameraZoomSlider` constrained to `@id/cameraZoomPresetGroup` start; `cameraZoomValue` constrained to `@id/cameraZoomPresetGroup` end.
- `.\a.ps1 fr` passes (exit 0).

**Status:** `[ ]` not done

---

### Step 02.3 - Save-destination label string

**Files:** `values/strings.xml` (+ `values-ru`, `values-uk`)
**Depends on:** - start of phase

**Prompt for developer:**

> Add `camera_save_destination` ("Saves to: %1$s" / RU / UK) via `set-android-string.ps1 -Action add` (one lockstep call). The string is a neutral status line; follow `docs/COMMUNICATION_POLICY.md` §2/§6 (no exclamation, plain wording).

**Verification:**

- `Grep` - `camera_save_destination` in `values/strings.xml`, `values-ru`, `values-uk`.
- `check_strings_localized.ps1 -KeyPrefix "camera_save_destination"` exits 0.

**Status:** `[ ]` not done

---

### Step 02.4 - Show the destination name beside the exit button

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `CameraCaptureActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` (Rule 5). Add a small `OutlinedTextView` `cameraSaveDestination` next to `btnCloseCamera` in the top bar. In the host, set its text from the resolved destination resource name (the output target the flow manager already resolves) via `getString(R.string.camera_save_destination, name)`; hide it when no named destination. Register it for icon rotation (Phase 01) so it stays upright.

**Verification:**

- `Grep` - `@+id/cameraSaveDestination` in the layout; `cameraSaveDestination` set in `CameraCaptureActivity.kt`.
- `Glob` - fresh `temp/CameraCaptureActivity.kt.*.bak`.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build`; preset unit test green.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries (batched in Phase 06).

---

## Handoff Notes to Next Phase

Presets are `1/3/5/10/20/30`; slider+value match the row; the destination name shows by the exit button. Phase 03 adds the Send-to control by the shutter.

---

## Rollback Plan

Revert the phase commit; restore `CameraCaptureActivity.kt` from `temp/`. No migration.
