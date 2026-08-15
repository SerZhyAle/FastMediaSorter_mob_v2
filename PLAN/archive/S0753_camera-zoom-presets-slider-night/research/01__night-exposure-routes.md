# Research 01 - Night mode: routes to "3x exposure length"

**Spec:** S0753
**§6 item:** 1 (night mode semantics)
**Status:** Resolved (owner chose Route 1 - OEM NIGHT extension - on 2026-06-27)
**Date:** 2026-06-27

## Question

Owner wants a "night shooting" control with "exposure length three times longer than normal". Does this mean literally tripling sensor shutter time, or "significantly brighter low-light result"? Which CameraX route delivers it and how robust is each across devices?

## Current state

- Camera capture uses plain CameraX core: `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-video` at 1.5.3. No `androidx.camera:camera-extensions` dependency present.
- No exposure control exists today: zero matches for `exposureCompensation`, `setExposureCompensationIndex`, `SENSOR_EXPOSURE_TIME`, `CONTROL_AE_MODE`, write-side `Camera2Interop` in camera capture code.
- `Camera2CameraInfo` is imported in the capability probe for read-only autofocus-mode probing only. `Camera2Interop` (write side) is in `camera-camera2:1.5.3` on the classpath but unused.
- Session is rebuilt on each bind (new `Preview.Builder` + `ImageCapture.Builder`), so a night-mode toggle that injects capture parameters needs a session rebind, not a live mutation.

## Routes evaluated

### Route 1 - CameraX Extensions NIGHT mode
- Needs new dependency `androidx.camera:camera-extensions` plus `ExtensionsManager` lifecycle (provider-level).
- Availability is per-device, per-lens; must call `isExtensionAvailable(..., NIGHT)` before use.
- Semantics are an opaque OEM pipeline (burst+merge / long exposure / ML). No "3x" control - app cannot dictate the multiplier.
- Not a literal match for "triple exposure length".

### Route 2 - Exposure compensation index
- `cameraInfo.exposureState` exposes `exposureCompensationRange` and `exposureCompensationStep`; `cameraControl.setExposureCompensationIndex(index)` shifts AE target brightness in EV steps.
- Brightens the image but does NOT triple shutter time. AE may raise ISO instead of lengthening shutter; on a dark scene it may lengthen shutter, but that is AE-algorithm-dependent and not controllable.
- Most robust and universal route; works on effectively all devices; no rebind. But semantically it is "brighter", not "3x exposure".

### Route 3 - Camera2Interop manual SENSOR_EXPOSURE_TIME
- `Camera2Interop` can inject `CONTROL_AE_MODE = OFF` + `SENSOR_EXPOSURE_TIME = nanoseconds` at builder time.
- Only route that literally sets exposure duration. To get "3x": read a reference exposure (via a capture callback or `Camera2CameraControl`), multiply by 3.
- Fragile: with AE off there is no auto-gain, so the frame overexposes unless ISO is co-controlled; not all devices report `SENSOR_INFO_EXPOSURE_TIME_RANGE` (fixed-exposure sensors); requires session rebind on toggle.
- API 21+; safe for minSdk 23.

## Recommendation

Route 3 is the only literal match for "exposure length 3x normal". It is device-limited and AE-fragile, so it must be gated: expose the night-mode toggle only when the active lens reports a usable `SENSOR_INFO_EXPOSURE_TIME_RANGE` wide enough for a 3x multiplier, clamp the multiplier to the reported max, and co-manage gain/AE so the frame does not blow out. On unsupported lenses the toggle is hidden - consistent with the project's existing capability-gating of zoom/flash/AF.

If the owner accepts "brighter low-light" instead of literal 3x, Route 2 is far simpler and universal. This is the §6.1 decision: literal-3x-device-gated vs universal-brighten. Route 1 is not recommended (new dep, opaque, no 3x control).

## Owner decision (2026-06-27)

Owner chose **Route 1 - CameraX Extensions NIGHT mode**, scoped to photo capture only. Implications the tactical plan must carry:

- Add the `androidx.camera:camera-extensions` dependency (version aligned with the existing 1.5.3 camera artifacts).
- Introduce an `ExtensionsManager` lifecycle at the camera-session role; probe `isExtensionAvailable(provider, selector, NIGHT)` per bind/lens and surface it as a capability flag on the runtime-capabilities snapshot.
- Bind the photo `ImageCapture`/`Preview` use cases through the NIGHT-enabled `CameraSelector` from `ExtensionsManager` when the toggle is on; rebind on toggle.
- Device-gated: hide the toggle where NIGHT is unavailable. No explicit exposure multiplier - the OEM algorithm owns the result, so the literal "3x" target is dropped.
- Video is out of scope; the toggle is photo-only.

Routes 2 and 3 are recorded above for context but are not the chosen path.

## Sources

- Codebase: `app_v2/build.gradle.kts` camera deps; `ui/cameracapture/helpers/CameraCaptureSessionManager.kt` (bind path, no exposure); `ui/cameracapture/helpers/CameraCapabilityProbe.kt` (read-only Camera2 info).
- developer.android.com CameraX `CameraControl` / `ExposureState` / Camera2 interop docs; `CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE`, `CONTROL_AE_AVAILABLE_MODES`.
