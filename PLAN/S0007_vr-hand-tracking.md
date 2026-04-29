# Specification: VR Hand Tracking & Gesture Control

**Status:** Tactical
<!-- auto-approved by /spec-all — 2026-04-26; blocker resolved (controller/HUD pipeline implemented via S0008+S0009) -->
<!-- S0008 dependency Implemented 2026-04-29 — ready for /spec-tech -->
<!-- /spec-tech completed 2026-04-30 — tactical plan: PLAN/S0007_vr-hand-tracking/INDEX.md (4 phases) -->
**Date:** 2026-04-24
**Tier:** 3
**Tactical plan:** `PLAN/S0007_vr-hand-tracking/INDEX.md`
**Roadmap Entry:** Ad-hoc (User Request 2026-04-24). Controllers remain the primary input modality; hand tracking serves as a secondary interface for scenarios where controllers are set aside (e.g., reclined media consumption).

---

## 1. Problem Statement

Following the integration of standard VR controller support via OpenXR (`spec_vr-immersive-controls.md`), a critical media-consumption scenario remains unaddressed: managing playback when the user has set aside the physical controllers (e.g., watching a movie while lying down). Currently, playback control necessitates physical device interaction. Horizon OS supports `XR_EXT_hand_tracking` paired with `XR_META_hand_tracking_aim`, providing a system-calculated aiming ray and pinch strength. This is sufficient to implement a hands-free, raycast-based control scheme for the VR media player.

## 2. Objectives & Scope

### 2.1 Core Features

1. **Automatic Modality Switching:** Seamlessly transition between controller and hand-tracking modes based on OpenXR session state and active device presence.
2. **Raycast Targeting:** Utilize `XR_META_hand_tracking_aim` to project an aiming ray from the dominant hand, mirroring the functionality of the controller's laser pointer.
3. **Pinch-to-Click:** Implement pinch gestures (index finger to thumb) using the system-provided pinch strength index to simulate a primary button click on the targeted overlay element.
4. **Microgestures:** Leverage `XR_META_hand_tracking_microgestures` for continuous inputs:
   - **Thumb Swipe (Left/Right):** Seek backward/forward.
   - **Thumb Swipe (Up/Down):** Adjust volume.
5. **Double Pinch:** Toggle play/pause state globally, functioning independently of the UI overlay visibility.
6. **Input Feedback:** Provide visual and auditory feedback (e.g., ray color change, click sounds) to compensate for the lack of haptic feedback in hand tracking.

### 2.2 Non-Goals

- Recognition of arbitrary or custom hand poses (e.g., ASL, custom shapes).
- Rendering of a fully articulated 3D hand mesh (system passthrough is sufficient and preferred).
- Simultaneous two-handed interaction (logic will prioritize the dominant/active hand).
- Bimanual scaling/zooming (pinch-to-zoom with both hands) — deferred to a future specification.
- Hand-tracking support outside the immersive VR player context (e.g., 2D application mode).

## 3. Technical Architecture & Implementation Details

### 3.1 Complexity: MEDIUM

Implementation relies primarily on standard and Meta-specific OpenXR extensions. The abstraction level provided by the OS minimizes complex manual calculations (e.g., skeletal joint math).

### 3.2 OpenXR Integration

- **Extensions Required:**
  - `XR_EXT_hand_tracking`
  - `XR_META_hand_tracking_aim`
  - `XR_META_hand_tracking_microgestures`
- **Initialization (`OpenXrNative.cpp`):**
  - Register the extensions during `XrInstance` creation.
  - Instantiate `XrHandTrackerEXT` for `XR_HAND_LEFT_EXT` and `XR_HAND_RIGHT_EXT`.
- **Per-Frame Processing:**
  - Poll `XrHandTrackingAimStateFB` for both hands to retrieve the `XrPosef` (ray origin and direction) and `pinchStrengthIndex`.
  - Poll microgesture state to detect swiping events.

### 3.3 State Management & Fallback

- **Active Input Modality:** The system must actively track the primary input source. If controller tracking data becomes invalid or static while hand tracking becomes active, seamlessly switch the input processor to the hand-tracking pipeline.
- **Priority:** Controller inputs take strict precedence. Detection of a controller returning to an active state instantly suspends hand-tracking input processing to prevent unintended duplicate events.

### 3.4 Kotlin Integration

- **`VrControllerInputManager` Refactoring:** Extend the existing input manager (or introduce a parallel `VrHandTrackingManager` behind a common `VrInputProvider` interface) to map JNI callbacks from OpenXR hand states into the standard application command stream (e.g., `VrInputAction.CLICK`, `VrInputAction.SEEK`).
- **UI Event Translation:** Map pinch interactions to Android `MotionEvent` or custom UI selection events to interact with the existing HUD overlay architecture.
- **Permissions:** Ensure `<uses-permission android:name="com.oculus.permission.HAND_TRACKING" />` is declared in the VR flavor's manifest.

### 3.5 UI & UX Considerations

- **False Positive Prevention:** Implement spatial and temporal dead-zones for pinch clicks to prevent jitter during the pinch action from shifting the raycast target just before the click registers.
- **Audio Cues:** Since haptic feedback is unavailable, distinct UI sounds must trigger on hover, pinch begin, and pinch complete to confirm interactions.
- **Visual Mapping Guide:** Provide a specialized overlay (cheat sheet) demonstrating the hand-tracking controls, distinct from the controller map.

## 4. Dependencies

- **`spec_vr-immersive-controls.md`:** Must be fully implemented. Hand tracking extends the same input processing pipeline and HUD targeting system established for controllers.
- **OpenXR Action System:** The foundational layer for routing VR inputs must be operational.

## 5. References

- [Meta — Mobile OpenXR Input (XR_META_hand_tracking_aim)](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-input/)
- [Meta — Hand Tracking Microgestures](https://developers.meta.com/horizon/documentation/unity/unity-microgestures/)
- [Godot Engine — OpenXR Hand Tracking Guide](https://docs.godotengine.org/en/stable/tutorials/xr/openxr_hand_tracking.html)
- [Khronos OpenXR Registry — XR_EXT_hand_tracking](https://registry.khronos.org/OpenXR/specs/1.0/html/xrspec.html#XR_EXT_hand_tracking)

---
*End of specification.*
