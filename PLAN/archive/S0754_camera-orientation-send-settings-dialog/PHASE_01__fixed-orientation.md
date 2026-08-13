# Phase 01 - Fixed orientation with rotating icons

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/03__fixed-orientation.md`](research/03__fixed-orientation.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Pin the camera screen to portrait, rotate only control icons/labels by device angle, and drive CameraX `targetRotation` from an `OrientationEventListener` so captures stay correct - removing the landscape relayout, the controls overlapping, and the system-bar intrusion.

---

## Prerequisites

- [ ] Strategic §6.3 research artifact read.
- [ ] `CameraCaptureActivity.kt` > 500 LOC - back it up to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOrientationManager.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 470 |

> `res/layout-land/activity_camera_capture.xml` becomes dead with the portrait lock - retired in Phase 06 (Rule 11 exemption documented there). Do not edit it here.

---

## Steps

### Step 01.1 - Lock the camera activity to portrait and self-manage orientation

**Files:** `AndroidManifest.xml`, `CameraCaptureActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` to `temp/` (Rule 5). In the manifest entry for `CameraCaptureActivity` add `android:screenOrientation="portrait"`. Make `CameraCaptureActivity` implement the `SelfManagedScreenOrientation` marker (see `core/orientation/`) so `AppOrientationManager` does not override it on resume; in `onCreate`/`setupViews` set `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`. The screen must no longer recreate or use `layout-land` on rotation.

**Verification:**

- `Grep` - `android:screenOrientation="portrait"` present on the `CameraCaptureActivity` manifest line.
- `Grep` - `SelfManagedScreenOrientation` referenced in `CameraCaptureActivity.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 01.2 - Add a device-orientation manager

**Files:** `CameraOrientationManager.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `CameraOrientationManager` wrapping an `OrientationEventListener`: bucket the device angle to 0/90/180/270, and on a change emit two callbacks - one with the upright icon rotation in degrees (so a view animated to it stays upright), one with the matching `Surface.ROTATION_*` for CameraX. Expose `enable()`/`disable()` tied to the host lifecycle. No view references inside the manager - it only emits values.

**Verification:**

- `Glob` - `CameraOrientationManager.kt` exists.
- `Grep` - `class CameraOrientationManager` matches once; `OrientationEventListener` referenced.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 01.3 - Drive CameraX targetRotation from the device angle

**Files:** `CameraCaptureSessionManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `fun setTargetRotation(rotation: Int)` that applies the `Surface.ROTATION_*` to the bound `ImageCapture` and `VideoCapture` (`imageCapture?.targetRotation = rotation`, same for video). In `capture()`, stop reading `previewView.display?.rotation` (static under the lock) and use the last device rotation supplied by the manager. The saved photo/video must be oriented per the physical device angle, not the locked window.

**Verification:**

- `Grep` - `fun setTargetRotation(` matches once in `CameraCaptureSessionManager.kt`.
- `Grep` - `previewView.display?.rotation` no longer the rotation source in `capture()` (replaced by the stored device rotation).
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 01.4 - Rotate the control icons in the host

**Files:** `CameraCaptureActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Wire `CameraOrientationManager` in the host: on icon-rotation callback, animate every overlay control to the upright rotation via `view.animate().rotation(degrees)` (top-bar buttons, shutter glyph, lens-switch + label, zoom value, gallery thumbnail, pause - positions stay fixed). On the rotation callback also call `sessionManager.setTargetRotation(...)`. Enable the manager in `onResume`, disable in `onPause`. Do not collect any Flow lifecycle-unsafely.

**Verification:**

- `Grep` - `CameraOrientationManager` instantiated and `animate().rotation` used in `CameraCaptureActivity.kt`.
- `Grep` - `setTargetRotation` called from the host.
- `Glob` - a fresh `temp/CameraCaptureActivity.kt.*.bak` backup exists.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched" (batched in Phase 06).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `CameraOrientationManager`) - may defer to Phase 06.

---

## Handoff Notes to Next Phase

The camera is portrait-locked and self-managed; icons rotate by device angle; captures use device-driven `targetRotation`. The landscape layout is now unused (retired in Phase 06). Later phases add controls that must also be registered for icon rotation.

---

## Rollback Plan

Revert the phase commit, restore `CameraCaptureActivity.kt` from `temp/`, restore the manifest `configChanges`. No data migration.
