# Research 01 - Pro-mode camera settings (the "three-dots" dialog)

**Spec:** S0754
**§6 item:** 1 (settings dialog composition)
**Status:** Resolved (owner chose full pro-mode 2026-06-28)
**Date:** 2026-06-28

## Seam

All Camera2 reads stay in `CameraCapabilityProbe.probe()` (`ui/cameracapture/helpers/CameraCapabilityProbe.kt:57`) -> add fields to `CameraRuntimeCapabilities`; the dialog reads only the snapshot, the session applies (S0545 §3.4). `CameraCaptureSessionManager` already wires exposure compensation, NIGHT extension, macro (Camera2CameraControl), zoom - the apply seam for new settings.

## Settings, API, availability

| Setting | API / apply | Availability check | Notes |
|---|---|---|---|
| Self-timer | Pure UI: delay loop before `sessionManager.capture()` | always | Full-screen countdown overlay (Samsung style). |
| Grid overlay | Pure UI: draw 3x3 lines over preview | always | New overlay view, toggle. |
| Aspect ratio | `ImageCapture/Preview` `ResolutionSelector` + `AspectRatioStrategy` (rebind) | always (4:3 / 16:9) | Apply on dialog dismiss (one rebind), not live. |
| Photo/video resolution | `ResolutionSelector` / output sizes from `SCALER_STREAM_CONFIGURATION_MAP` | per lens | Apply on dialog dismiss (rebind). |
| Exposure compensation | `CameraControl.setExposureCompensationIndex` + `ExposureState` | `exposureState.isExposureCompensationSupported` | Already wired (night fallback uses it). Live. |
| White balance | Camera2Interop `CONTROL_AWB_MODE` | `CONTROL_AWB_AVAILABLE_MODES` | Live via `Camera2CameraControl`. |
| ISO | Camera2Interop `SENSOR_SENSITIVITY` + AE OFF | `SENSOR_INFO_SENSITIVITY_RANGE` AND `HARDWARE_LEVEL >= FULL` | Gate behind `supportsManualSensor`. |
| Shutter / exposure time | Camera2Interop `SENSOR_EXPOSURE_TIME` + AE OFF | `SENSOR_INFO_EXPOSURE_TIME_RANGE` AND `HARDWARE_LEVEL >= FULL` | Gate behind `supportsManualSensor`. |
| HDR | `ExtensionMode.HDR` via ExtensionsManager | `isExtensionAvailable(selector, HDR)` | Same pattern as NIGHT; mutually exclusive with NIGHT. |

## Key constraints

- ISO + shutter require `CaptureRequest.CONTROL_AE_MODE = OFF`, honoured only on `INFO_SUPPORTED_HARDWARE_LEVEL >= FULL`. On `LIMITED` devices (common mid-range) the option is silently overridden. **Decision:** probe `HARDWARE_LEVEL` and expose `supportsManualSensor: Boolean`; show ISO/shutter only when true. Hidden otherwise (capability-gating, like flash/night).
- ISO/shutter manual mode disables AE; toggling them off restores `CONTROL_AE_MODE_ON`. Tap-to-focus and exposure compensation are AE-dependent - manual sensor and exposure-comp are mutually exclusive in UI.
- Aspect ratio / resolution need a rebind; batch them to dialog-dismiss to avoid per-tap preview flicker.

## Resolution / decisions for the plan

- Owner: full pro-mode. Ship the device-available subset, hide the rest.
- Persistence: UI-level settings (self-timer delay, grid on/off, aspect ratio) persist across launches in app preferences; sensor-level settings (exposure comp, white balance, ISO, shutter, HDR) are session-only and reset each time the camera opens. Keeps DataStore surface small and avoids stale manual-sensor values across devices.
- Dialog is a single scrollable dialog (not dropdown), each row gated by capability; sensor section appears only when `supportsManualSensor`.

## Sources

- `ui/cameracapture/helpers/CameraCapabilityProbe.kt`, `CameraCaptureSessionManager.kt` (exposure/night/macro/zoom apply seams)
- `ui/cameracapture/model/CameraRuntimeCapabilities.kt`
- androidx.camera 1.5.3 + camera-extensions + Camera2Interop (already on the classpath)
- developer.android.com: ResolutionSelector/AspectRatioStrategy, Camera2 CONTROL_AWB_MODE, SENSOR_SENSITIVITY/EXPOSURE_TIME, INFO_SUPPORTED_HARDWARE_LEVEL, ExtensionMode.HDR
