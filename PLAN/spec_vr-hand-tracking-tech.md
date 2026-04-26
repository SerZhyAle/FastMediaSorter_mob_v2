# Specification: VR-HAND-TRACKING-TECH — Technical Implementation of VR Hand Tracking

**Status:** Approved
<!-- auto-approved by /spec-all — 2026-04-26; implementation verified in code -->
**Date:** 2026-04-24
**Tier:** 3 — Feature (4h-8h, medium risk)
**Roadmap entry:** Ad-hoc — Technical implementation of the UX spec `spec_vr-hand-tracking.md`. Integrates `XR_EXT_hand_tracking`, `XR_META_hand_tracking_aim`, and microgestures for controller-free playback management.

---

## 1. Problem Statement

Following the implementation of `spec_vr-immersive-controls-tech.md`, the VR player supports physical Touch controllers, BT keyboards, and BT mice. However, when a user sets aside the controllers, interaction with the UI becomes impossible. Horizon OS provides system-level hand tracking that can be leveraged as an additional input layer (Layer E). Using standard and Meta-specific OpenXR extensions, we can extract an aiming ray and basic gestures (pinch, swipe) natively, avoiding complex hand joint math and directly mapping these actions to the existing `VrControllerInputManager`.

---

## 2. Goals

1. **Layer E (Hand Tracking C++):** Request `XR_EXT_hand_tracking`, `XR_META_hand_tracking_aim`, and `XR_META_hand_tracking_microgestures` during `XrInstance` creation in `OpenXrNative.cpp`.
2. Create `XrHandTrackerEXT` instances for both Left and Right hands.
3. Integrate per-frame polling of `xrLocateHandJointsEXT` and `XrHandTrackingAimStateFB` within `renderFrame()`.
4. Implement automatic **Active Modality Switching** logic to seamlessly transition between controller input and hand tracking based on device activity.
5. **Layer E (Kotlin):** Extend `VrControllerInputManager` and `XrInputCallback` to distinguish between controller and hand input sources.
6. Translate OpenXR pinch events into UI interactions (equivalent to trigger clicks) with spatial and temporal dead-zones (anti-jitter).
7. Translate OpenXR thumb-swipe microgestures into `PlaybackCommand.SeekMicro` and `PlaybackCommand.VolumeStep`.
8. Provide distinct audio feedback (via Android `SoundPool` or `AudioManager`) for hover and click events when using hands, compensating for the lack of haptics.
9. Add the required `<uses-permission android:name="com.oculus.permission.HAND_TRACKING" />` to the `vr` flavor manifest.

**Non-goals:**
- Full hand mesh rendering (system passthrough handles visualization).
- Custom pose recognition (e.g., ASL detection).
- Simultaneous bimanual interactions (e.g., pinch-to-zoom using both hands).

---

## 3. Flavor & API Level Scope

- **Flavor:** Restricted to the `vr` flavor (`SUPPORT_VR_PLAYER = true`). Code changes reside in `app_v2/src/vr/` and `app_v2/src/vr/cpp/`.
- **API Level:** MinSdk 26+ (Quest OS relies on Android 12+, API 32+).
- **Permissions:** `com.oculus.permission.HAND_TRACKING` required in the AndroidManifest.

---

## 4. Current Architecture Gaps

- `OpenXrNative.cpp` does not request hand tracking extensions or instantiate `XrHandTrackerEXT` structures.
- The `XrInputCallback` currently routes `type`, `hand`, and `value` but lacks a `source` identifier. Because hand tracking lacks hardware haptics, the Kotlin layer must know the input source to synthesize audio feedback instead of dispatching haptic commands back to C++.
- UI Raycasting for the HUD overlay currently assumes a fixed ray origin from the controller. A `VrHandRayManager` or an updated `VrControlOverlayManager` needs the dynamic `aimPose` intersected against the UI plane to emulate `MotionEvent` hover/clicks.

---

## 5. Proposed Architecture

### 5.1 C++ OpenXR Hand Tracking (Layer E)

**Extensions in `OpenXrNative.cpp`:**
Add to the `createInstance()` extension list:
- `XR_EXT_hand_tracking`
- `XR_META_hand_tracking_aim` (or `XR_FB_hand_tracking_aim` depending on the available Oculus OpenXR Mobile SDK header)
- `XR_META_hand_tracking_microgestures`

**Data Structure (`HandSystem`)**:
Insert alongside `InputSystem` in `XrCtx`:
```cpp
struct HandSystem {
    XrHandTrackerEXT trackerL = XR_NULL_HANDLE;
    XrHandTrackerEXT trackerR = XR_NULL_HANDLE;

    bool isActiveL = false;
    bool isActiveR = false;

    // Pinch state with hysteresis
    bool isPinchingL = false;
    bool isPinchingR = false;

    // Microgesture state
    bool isSwipingL = false;
    bool isSwipingR = false;

    bool initialized = false;
} hands;
```

**`initHandTracking()`**:
Invoked post-`XrSession` creation. Calls `xrCreateHandTrackerEXT` for `XR_HAND_LEFT_EXT` and `XR_HAND_RIGHT_EXT`. 

**`syncHandTracking(JNIEnv* env)`**:
Invoked per-frame in `renderFrame()` after `syncInputActions()`.
1. **Modality Check:** If controllers are tracked and active (valid poses + recent button/stick state changes), skip hand polling. If controllers are inactive for > 2.0 seconds, enable hand polling.
2. Call `xrLocateHandJointsEXT` for each hand, chaining `XrHandTrackingAimStateFB` to the `next` pointer of `XrHandJointLocationsEXT`.
3. Extract `aimPose` for raycast targeting and `pinchStrengthIndex`.
4. **Pinch Hysteresis:** 
   - `pinchStrengthIndex > 0.9f` -> `isPinching = true` -> trigger `CLICK` down.
   - `pinchStrengthIndex < 0.6f` -> `isPinching = false` -> trigger `CLICK` up.
5. **Microgestures:** Poll `XrMicrogesturesStateFB` (if chained). Dispatch `SWIPE_LEFT`/`RIGHT`/`UP`/`DOWN` as respective event types.
6. Dispatch events via `emitInputEvent(type, hand, value, SOURCE_HAND)`.

### 5.2 Kotlin JNI Contract Updates

Update JNI signature for `XrInputCallback` and `nativeSetInputCallback`:
```kotlin
interface XrInputCallback {
    fun onInputEvent(type: Int, hand: Int, value: Float, source: Int)
}

object XrInputSource {
    const val SOURCE_CONTROLLER = 0
    const val SOURCE_HAND = 1
}
```

### 5.3 VrControllerInputManager Refactoring

Modify `dispatchXrEvent` to evaluate the `source`:
- If `source == SOURCE_HAND`:
  - **Audio Feedback:** Ignore `nativeTriggerHaptic`. Trigger Android `SoundPool` UI click/hover sounds to confirm the pinch action.
- Route thumb-swipe events (mapped from C++ constants) to `PlaybackCommand.SeekMicro(forward)` and `VolumeStep(delta)`.
- Route Double Pinch to `PlaybackCommand.TogglePausePlay`.

### 5.4 Raycast and UI Pointer Rendering

When `SOURCE_HAND` is active, the aiming ray originates from the user's pinch point rather than a controller chassis. 
- C++ must compute the ray-plane intersection (assuming the UI overlays reside on a fixed virtual plane in front of the camera) and emit `POINTER_MOVE` events with normalized `X, Y` coordinates.
- A new `VrHandRayManager` translates these `POINTER_MOVE` events into Android `MotionEvent.ACTION_HOVER_MOVE` to highlight UI elements and `ACTION_DOWN/UP` upon pinch.

### 5.5 File Modification Budget

| Class / File | Location | Modification Details |
|-------------|----------|----------------------|
| `AndroidManifest.xml` (vr) | `app_v2/src/vr/` | Add `<uses-permission android:name="com.oculus.permission.HAND_TRACKING" />` |
| `XrInputCallback.kt` | `vr/openxr/` | Add `source: Int` to method signature |
| `OpenXrNative.kt` | `vr/openxr/` | Update `nativeSetInputCallback` |
| `OpenXrNative.cpp` | `vr/cpp/` | Add `HandSystem`, `initHandTracking`, `syncHandTracking`, intersection math |
| `VrControllerInputManager.kt`| `vr/helpers/` | Add audio feedback, handle `SOURCE_HAND` bypasses |
| `VrHandRayManager.kt` | `vr/ui/` | New class (≤ 150 LOC) to manage pointer visuals and `MotionEvent` emulation |

---

## 6. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Khronos/Meta header mismatch for extensions | High | Verify Oculus Mobile SDK version in CMake. Fallback to older `XR_FB_hand_tracking_aim` if `META` namespace is missing. |
| Raycast jitter during pinch closure | Med | Freeze raycast XY coordinates precisely when `pinchStrengthIndex` exceeds 0.5f to prevent the cursor from shifting off-target right before the click registers (0.9f). |
| Input conflict (Hands vs Controllers) | High | Implement a strict priority lock: Controllers unconditionally override hands. Hand tracking output is dropped if controller grip/trigger state changes. |
| Audio feedback latency | Low | Use preloaded `.ogg` files in Android `SoundPool` instead of standard `MediaPlayer` for zero-latency UI clicks. |

---

## 7. Implementation Steps

1. **Manifest Configuration:** Add required permissions and feature flags to the VR flavor manifest.
2. **C++ Header Integration:** Ensure `openxr_meta_hand_tracking_aim.h` or equivalent is included in `OpenXrNative.cpp`.
3. **C++ Initialization:** Update `createInstance` to request the three hand-tracking extensions. Implement `initHandTracking()`.
4. **C++ Synchronization:** Implement `syncHandTracking()`. Add the ray-plane intersection math. Add temporal pinch hysteresis.
5. **JNI Contract:** Update `emitInputEvent` with the `source` parameter in both C++ and Kotlin (`XrInputCallback.kt`).
6. **Kotlin Input Routing:** Update `VrControllerInputManager` to parse `SOURCE_HAND` and trigger `SoundPool` effects instead of haptics.
7. **UI Event Emulation:** Implement `VrHandRayManager` to convert `POINTER_MOVE` and `CLICK` events into injected `MotionEvent` dispatches on the root view of `VrFileOpsOverlayManager` and HUD overlays.
8. **Dev Logging:** Run `.\scripts\add_to_dev_log.ps1` for every modified file.

---

## 8. Data Flow

```
Hand (User Action)
  ↓ OpenXR Runtime
  xrLocateHandJointsEXT + XrHandTrackingAimStateFB
  ↓ C++ syncHandTracking() [xr-render-thread]
  Hysteresis -> XrInputEventType.CLICK / POINTER_MOVE
  ↓ emitInputEvent(type, hand, value, SOURCE_HAND)
  XrInputCallback.onInputEvent [xr-render-thread]
  ↓ mainHandler.post
  VrControllerInputManager
  ↓ if SOURCE_HAND -> Play SoundPool click
  VrHandRayManager -> Inject MotionEvent(ACTION_DOWN/UP/HOVER)
  ↓ Android UI Framework
  VrFileOpsOverlayManager / HUD Buttons
```

---
*End of specification.*
