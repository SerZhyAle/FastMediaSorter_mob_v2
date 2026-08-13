# Phase 04 - In-app video

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Extend the shared in-app capture host to record video with an explicit microphone policy and no dependency on the external system camera UI.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] UI contract resolved - strategic §3.4 (owner gate 2026-06-20): NO in-screen photo/video switch in S0545. Capture mode is fixed by the launching entry point; the host shows photo controls XOR video controls based on the mode passed in the intent. Mic toggle lives in the top bar next to flash and is visible only in video mode. In-screen mode switching is deferred to `S0563`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | +1 dependency |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 280 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 230 |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Modified | ≤ 230 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +12 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +12 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +12 lines |

> `app_v2/build.gradle.kts` is >500 lines - create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 4.1 - Add CameraX video capture and recording lifecycle support

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the `androidx.camera:camera-video` dependency and extend `CameraCaptureSessionManager` with a `Recorder`/`VideoCapture` pipeline that can start and stop recordings from the existing host. Keep photo capture intact - the phase goal is one session manager that can switch between photo and video instead of two parallel camera stacks.

**Verification:**

- `Grep` - `androidx.camera:camera-video:` is present in `app_v2/build.gradle.kts`.
- `Grep` - `Recorder.Builder()` or `VideoCapture.withOutput` is present in `CameraCaptureSessionManager.kt`.
- `Grep` - `fun startRecording` and `fun stopRecording` are present in `CameraCaptureSessionManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

### Step 4.2 - Add mode-driven control visibility and microphone toggle UI

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/layout-land/activity_camera_capture.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 4.1

> Scope per strategic §3.4: NO in-screen photo/video switch in S0545. Capture mode is fixed by the launching intent; the host renders photo controls XOR video controls based on that fixed mode. This step adds the video-mode surface (record button state + microphone toggle), not a user mode picker.

**Prompt for developer:**

> Add the visible microphone toggle to the shared capture layouts in the top bar next to the flash control (Samsung-familiar placement, strategic §3.4). The mic toggle is only shown when the host is in `VIDEO` mode - photo mode hides it. Add the video record affordance reusing the shutter position (the same bottom shutter doubles as start/stop record in video mode). Do NOT add `PHOTO|VIDEO` mode-switch buttons - mode is fixed by the entry point. Add EN/RU/UK labels and content descriptions for the microphone toggle and the record start/stop states. Before merging the strings, check `docs/COMMUNICATION_POLICY.md` §2 for the message formula and §6 for the tone checklist.

**Verification:**

- `Grep` - `@+id/toggleCameraMicrophone` is present in both layout variants.
- `Grep` - `@+id/btnCaptureModePhoto` and `@+id/btnCaptureModeVideo` return ZERO hits in both layout variants (no in-screen mode switch in S0545).
- `Grep` - `camera_capture_microphone`, `camera_capture_record_start`, and `camera_capture_record_stop` are present in `values/strings.xml`, `values-ru/strings.xml`, and `values-uk/strings.xml`.
- `Verification predicate` - Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[ ]` not done

---

### Step 4.3 - Enforce explicit audio permission and result reporting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt`
**Depends on:** Step 4.2

**Prompt for developer:**

> Request `RECORD_AUDIO` only when the user starts a recording with the microphone toggle enabled, never silently beforehand. Pack the final media kind and microphone-enabled state into the activity result so callers can keep their photo/video save handling explicit. Preserve the current `CAMERA` permission flow for photo mode.

**Verification:**

- `Grep` - `Manifest.permission.RECORD_AUDIO` is referenced from the in-app video path.
- `Grep` - `EXTRA_MICROPHONE_ENABLED` is present in `CameraCaptureContract.kt`.
- `Grep` - `EXTRA_RESULT_MEDIA_KIND` is referenced from `CameraCaptureFlowManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The shared capture host now records video in-app and reports explicit media-kind/audio state to its callers. Phase 05 should remove the old external intent usage and keep every existing save route intact.

---

## Rollback Plan

Revert phase commit(s) - build dependency plus capture-host logic only, no Room or persistent schema change.
