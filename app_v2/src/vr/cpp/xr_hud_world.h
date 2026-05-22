// XR world HUD module header.
// Manages World Space 3D Quad rendering, lazy-follow gaze updates, rays, and visual cursors.

#pragma once

#ifndef XR_USE_PLATFORM_ANDROID
#define XR_USE_PLATFORM_ANDROID
#endif
#ifndef XR_USE_GRAPHICS_API_OPENGL_ES
#define XR_USE_GRAPHICS_API_OPENGL_ES
#endif

#include <openxr/openxr.h>
#include <GLES3/gl3.h>
#include "xr_raycast.h"

namespace fms::xr {

// Combined structure for HUD state, gaze-kept lazy follow, and ray/cursor intersections
struct HUDWorldState {
    QuadHUD quad;
    bool visible{true};
    
    // Gaze-kept target states
    XrVector3f targetCenter;
    XrQuaternionf targetRot;
    
    // Low-latency smoothed UV coordinates per-hand (0 = left, 1 = right)
    XrVector2f smoothedUv[2];
    bool hasIntersection[2];
    bool dragging{false};
    bool recenterRequested{false};
};

extern HUDWorldState g_hudState;

// Initialize the 3D HUD to initial position and properties
void xr_hud_init();

// Smoothly interpolate HUD position using exponential gaze lazy-follow
void xr_hud_update(const XrPosef& headPose, float deltaTime);

// Process left and right pointer ray intersections with the HUD Quad
void xr_hud_process_rays(const XrSpace localSpace, XrTime predictedTime);

// Draw the textured HUD Quad, physical laser lines, and zero-latency cursor dots
void xr_hud_render(const float* proj, const float* viewMat, size_t eyeIdx, GLuint shaderProgram, GLuint quadVao, GLuint hudTex, GLint locViewProj, GLint locTex, GLint locEye, GLint locStereo);

// Teardown any local HUD graphics resources
void xr_hud_shutdown();

} // namespace fms::xr
