# Phase 04 - Camera settings capabilities and apply seams

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/01__settings-dialog-capabilities.md`](research/01__settings-dialog-capabilities.md)
**Status:** ⬜ Not started
**Depends on:** none
**Blocks:** Phase 05
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Probe the device-available pro settings into the capability snapshot and add session apply seams (white balance, manual sensor ISO/shutter, HDR extension, aspect ratio/resolution), so the dialog (Phase 05) only renders what the device supports.

---

## Prerequisites

- [ ] `research/01__settings-dialog-capabilities.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 360 |

---

## Steps

### Step 04.1 - Extend the capability snapshot

**Files:** `CameraRuntimeCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add capability fields: `supportsManualSensor: Boolean` (hardware-level FULL+), `isoRange`/`shutterRange` (nullable), `awbModes: List<Int>` (white-balance modes), `supportsHdrExtension: Boolean`, `availableAspectRatios`/`photoResolutions` summary, and `supportsExposureCompensation` (already present). Defaults make every pro control hidden until probed.

**Verification:**

- `Grep` - `supportsManualSensor: Boolean`, `awbModes`, `supportsHdrExtension` each defined once in `CameraRuntimeCapabilities.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 04.2 - Probe the new characteristics

**Files:** `CameraCapabilityProbe.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `probe()` read (defensively, via `Camera2CameraInfo`): `INFO_SUPPORTED_HARDWARE_LEVEL` -> `supportsManualSensor = level FULL or higher`; `SENSOR_INFO_SENSITIVITY_RANGE`, `SENSOR_INFO_EXPOSURE_TIME_RANGE`; `CONTROL_AWB_AVAILABLE_MODES`; aspect/resolution from `SCALER_STREAM_CONFIGURATION_MAP`. Each read in `runCatching` degrading to "unsupported". HDR availability is read in the session (needs the ExtensionsManager + selector) and copied onto the snapshot like NIGHT.

**Verification:**

- `Grep` - `INFO_SUPPORTED_HARDWARE_LEVEL` and `CONTROL_AWB_AVAILABLE_MODES` referenced in `CameraCapabilityProbe.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 04.3 - Session apply seams (sensor, WB, HDR, format)

**Files:** `CameraCaptureSessionManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add apply methods on the session, each capability-gated and best-effort: `setWhiteBalance(mode)` (Camera2CameraControl `CONTROL_AWB_MODE`, live); `setManualSensor(iso, exposureNs)` and `clearManualSensor()` (Camera2 `CONTROL_AE_MODE_OFF` + `SENSOR_SENSITIVITY` + `SENSOR_EXPOSURE_TIME`, live; restore `AE_MODE_ON` on clear); `applyHdr(enabled)` (rebind with `ExtensionMode.HDR` enabled selector when available, mutually exclusive with NIGHT); `setAspectRatioAndResolution(...)` (rebuild `ImageCapture`/`Preview` with a `ResolutionSelector` + `AspectRatioStrategy`, one rebind on dialog dismiss). Reuse the existing exposure-compensation apply. Copy `supportsHdrExtension` onto the probed snapshot in `bindToLifecycle` (like NIGHT availability).

**Verification:**

- `Grep` - `fun setWhiteBalance(`, `fun setManualSensor(`, `fun applyHdr(`, `fun setAspectRatioAndResolution(` each match once in `CameraCaptureSessionManager.kt`.
- `Grep` - `.copy(supportsHdrExtension` present in `bindToLifecycle`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

### Step 04.4 - UI-only settings state in the flow manager

**Files:** `CameraCaptureFlowManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add UI-only camera settings state (no Camera2): `selfTimerSeconds: Int`, `gridEnabled: Boolean`, with setters. These are consumed by the host (grid overlay, countdown) and persisted by the cleanup phase's preferences hook; do not couple them to the session.

**Verification:**

- `Grep` - `selfTimerSeconds` and `gridEnabled` defined in `CameraCaptureFlowManager.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries (batched in Phase 06).

---

## Handoff Notes to Next Phase

Capabilities snapshot now carries the pro-setting availability flags and ranges; the session exposes apply seams; UI-only settings live in the flow manager. Phase 05 builds the dialog that reads these and calls the seams.

---

## Rollback Plan

Revert the phase commit. Pure additive capability/apply code; no migration.
