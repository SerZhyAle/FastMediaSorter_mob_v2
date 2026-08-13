# Phase 03 - interactive-canvas-jni

**Strategic spec:** [`../S0283_vr-immersive-interactive-controls-hud.md`](../S0283_vr-immersive-interactive-controls-hud.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Establish JNI pipelines for UV coordinates, Hover, Click and Pinch event dispatching; build dynamic Canvas-based HUD controls in specialized Kotlin architectural helpers (`HudCanvasRenderer`, `HudInteractionDispatcher`, `HudPlaybackController`, `HudHapticBridge`) adhering to Strict Rule §3; integrate ExoPlayer playback control via Main Looper thread dispatching, volume/stereo-depth shader uniform adjustments; implement physical haptic feedback using the canonical `xrApplyHapticFeedback` API.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Strategic §6.1 research item (Canvas performance) and §6.3 (Haptic actions) are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/xr_hud_world.cpp` | Modified | ≤ 450 |
| `app_v2/src/vr/cpp/xr_input.cpp` | Modified | ≤ 500 |
| `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` | Modified | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt` | Modified | ≤ 250 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/DiagnosticXrRuntime.kt` | Modified | ≤ 250 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 650 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt` | New | ≤ 250 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudInteractionDispatcher.kt` | New | ≤ 250 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudPlaybackController.kt` | New | ≤ 350 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudHapticBridge.kt` | New | ≤ 150 |

---

## Steps

### Step 03.1 - Define JNI Interface for Ray Interaction Events

**Files:** `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/DiagnosticXrRuntime.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Declare and implement the JNI pathway `onNativeRayInteraction(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean)` to stream interaction data from C++ render loop up to JVM. Ensure thread-safe forwarding to `DiagnosticXrActivity` without coroutine delays.

**Verification:**

- `Grep` - `onNativeRayInteraction` method signature exists in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `onNativeRayInteraction` JNI implementation exists in `diagnostic_xr_runtime.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: diagnostic_xr_runtime.cpp, NativeDiagnosticXrRuntime.kt, DiagnosticXrRuntime.kt. Evidence: `onNativeRayInteraction` signatures exist in Kotlin runtime and JNI bridge.

---

### Step 03.2 - Build Dynamic Interactive Canvas HUD UI with Architectural Helpers

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudInteractionDispatcher.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Design an interactive `1024x512` UI using Android Canvas containing Play/Pause, Next/Prev, Volume/Depth slider controls, and real-time FPS readout. Isolate the Canvas-drawing logic inside `HudCanvasRenderer.kt` and the UV-to-pixels MotionEvent emulation inside `HudInteractionDispatcher.kt` to comply with Strict Rule §3. `DiagnosticXrActivity` must only coordinate these components.

**Verification:**

- `Grep` - `HudCanvasRenderer` class is declared inside `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt`.
- `Grep` - Conversion of UV coordinates to virtual Canvas pixels is handled in `HudInteractionDispatcher.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: HudCanvasRenderer.kt, HudInteractionDispatcher.kt, DiagnosticXrActivity.kt. Evidence: Canvas renderer class exists and UV-to-pixel dispatch is isolated in helper.

---

### Step 03.3 - Implement Volume and 3D Stereo-depth Adjusters with Main-Thread Dispatching

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudPlaybackController.kt`, `app_v2/src/vr/cpp/xr_hud_world.cpp`
**Depends on:** Step 03.2

**Prompt for developer:**

> Connect volume adjustment requests from the HUD to ExoPlayer inside `HudPlaybackController.kt`. Because C++ callbacks come on a background render thread, you must explicitly marshal these requests to the UI main thread using `Handler(Looper.getMainLooper()).post { ... }` before modifying ExoPlayer settings (complying with ExoPlayer requirements). Wire 3D Depth changes by passing a horizontal parallax offset value down to the C++ stereo shader as a uniform float variable (`u_parallaxShift`) in `xr_hud_world.cpp`.

**Verification:**

- `Grep` - `u_parallaxShift` uniform setting exists in `app_v2/src/vr/cpp/xr_hud_world.cpp`.
- `Grep` - `volume\s*=` or `setVolume` property/method manipulation is present inside `HudPlaybackController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: HudPlaybackController.kt, xr_hud_world.cpp, DiagnosticXrRuntime.kt, NativeDiagnosticXrRuntime.kt, DiagnosticXrRenderThread.kt, xr_session.*. Evidence: ExoPlayer volume is main-thread marshalled; `u_parallaxShift` bridge reaches native shader uniform; `pwsh -NoProfile -File ./a.ps1 nd` exit code 0.

---

### Step 03.4 - Integrate Controller Haptic Feedback via Canonical OpenXR API

**Files:** `app_v2/src/vr/cpp/xr_input.cpp`
**Depends on:** Step 03.3

**Prompt for developer:**

> Implement haptic feedback actions. Whenever a hover state transition occurs (ray enters or exits interactive boundary) or a click/pinch gesture is registered, trigger vibration on the active controller. You MUST use the canonical OpenXR function `xrApplyHapticFeedback(session, &XrHapticActionInfo, (XrHapticBaseHeader*)&XrHapticVibration)` (Khronos canonical chain via `XrHapticBaseHeader`, not `XrBaseHeader`). Action paths are `/user/hand/left/output/haptic` and `/user/hand/right/output/haptic` bound under `/interaction_profiles/oculus/touch_controller`.

**Verification:**

- `Grep` - `xrApplyHapticFeedback` is called in `app_v2/src/vr/cpp/xr_input.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: xr_input.cpp. Evidence: haptic output uses `xrApplyHapticFeedback` through the canonical `XrHapticBaseHeader` path.

---

### Step 03.5 - Support HUD Drag-Positioning and Centering

**Files:** `app_v2/src/vr/cpp/xr_hud_world.cpp`
**Depends on:** Step 03.4

**Prompt for developer:**

> Monitor the status of `gripAction` inside `xr_hud_world.cpp`. When squeezed, dynamically update the HUD's world transformation matrix relative to the controller's motion (drag-positioning). Detect double-squeeze triggers or recenter actions to instantly reposition the HUD directly 1.5m in front of the viewer's current gaze.

**Verification:**

- `Grep` - Squeeze value checks or recenter commands exist inside `app_v2/src/vr/cpp/xr_hud_world.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: xr_hud_world.cpp, xr_hud_world.h. Evidence: grip-driven HUD drag and double-grip recenter logic are present.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles successfully - `pwsh -NoProfile -File ./a.ps1 nd` exit code 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entries added for all changed Kotlin helpers and C++ files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Complete interactive VR HUD system is operational: isolated Canvas rendering and dispatcher helpers, seekbar drag-seeking, thread-safe volume/depth sliders, double-squeeze repositioning, and tactile feedback using canonical OpenXR APIs.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
