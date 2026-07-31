// XR input module header.
// Manages OpenXR controllers, actions, haptic feedback, and hand tracking lifecycles.

#pragma once

#include <jni.h>
#include <android/native_window.h>

#ifndef XR_USE_PLATFORM_ANDROID
#define XR_USE_PLATFORM_ANDROID
#endif
#ifndef XR_USE_GRAPHICS_API_OPENGL_ES
#define XR_USE_GRAPHICS_API_OPENGL_ES
#endif

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include "xr_session.h"

namespace fms::xr {

struct HandInputState {
    bool active{false};           // True if the controller or hand is tracked and active
    bool isHand{false};           // True if using hand tracking, false if controller
    XrPosef pointerPose;          // Ray pointer origin and orientation
    XrPosef gripPose;             // Physical controller/hand grip pose used as visible ray origin
    bool gripPoseActive{false};   // True when gripPose is valid for the current frame
    bool triggerClicked{false};   // Click event (edge triggered transition to pressed)
    bool triggerDown{false};      // Current state of trigger/pinch select
    float triggerValue{0.0f};     // Analog click value
    bool gripDown{false};         // Grip button state (used for panel dragging)
    // S1240: set when a thumbstick deflection fires while this hand's grip is held, which means
    // the grip was meant as a media-navigation modifier rather than a drag. Latched for the rest
    // of the hold and cleared on release, so the HUD quad does not follow a hand that was only
    // reaching for the next file.
    bool gripIsModifier{false};
    float thumbstickX{0.0f};      // Thumbstick X axis value
    float thumbstickY{0.0f};      // Thumbstick Y axis value
};

// Symmetrically tracks left (0) and right (1) input sources.
extern HandInputState g_handInputStates[2];

// Initialize actions, load dynamic hand tracking pointers, and create hand trackers.
NativeResult xr_input_init(XrInstance instance, XrSession session);

// Synchronize action states and update hand tracking joint locations and aim poses.
void xr_input_poll(XrSpace baseSpace, XrTime predictedTime);

// Apply tactile haptic vibration to the specified controller (0 = left, 1 = right).
void xr_input_apply_haptic(int hand, float durationSeconds, float frequency, float amplitude);

// Clean up hand trackers and action sets.
void xr_input_shutdown();

} // namespace fms::xr
