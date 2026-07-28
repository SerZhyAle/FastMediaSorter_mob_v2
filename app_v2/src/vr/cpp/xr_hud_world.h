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

    // S0964: caller-requested quad size in meters; 0 = keep the banner defaults from
    // xr_hud_init. Persisted across sessions (xr_hud_init re-runs per entry and must not
    // reset an explicit panel size back to the banner strip).
    float overrideWidth{0.0f};
    float overrideHeight{0.0f};

    // S1228: caller-requested vertical offset in meters from the gaze ray; 0 = centred in view
    // (the S0290 banner placement). The player panel asks for a negative value so the strip sits
    // below the film instead of on top of it. Persisted across sessions like the size above.
    float overrideVerticalOffset{0.0f};

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

// S0964: request a HUD quad size in meters; takes effect immediately and survives re-entry.
// S1228: verticalOffsetMeters places the quad relative to the gaze ray (0 = centred, negative =
// below). Size and placement travel together so a caller cannot set one and forget the other.
void xr_hud_set_quad_size(float widthMeters, float heightMeters, float verticalOffsetMeters);

// Smoothly interpolate HUD position using exponential gaze lazy-follow
void xr_hud_update(const XrPosef& headPose, float deltaTime);

// Process left and right pointer ray intersections with the HUD Quad
void xr_hud_process_rays(const XrSpace localSpace, XrTime predictedTime);

// Draw the textured HUD Quad, physical laser lines, and zero-latency cursor dots
void xr_hud_render(const float* proj, const float* viewMat, size_t eyeIdx, GLuint shaderProgram, GLuint quadVao, GLuint hudTex, GLint locViewProj, GLint locTex, GLint locEye, GLint locStereo);

// Teardown any local HUD graphics resources
void xr_hud_shutdown();

// S0986: dedicated subtitle-cue quad. Anchored to the lower third of view, head-locked and
// independent of the HUD quad's size/visibility/drag (owner decision 2026-07-14). No ray/gaze
// interaction - it only displays the current cue texture uploaded via xr_session's subtitle path.
struct SubtitleQuadState {
    QuadHUD quad;
    bool visible{false};
};

extern SubtitleQuadState g_subtitleState;

// Initialize subtitle quad geometry (size + lower-third offset). Re-runs per session start.
void xr_subtitle_init();

// Head-lock the subtitle quad to the lower third of view; snaps to head pose (no lazy-follow).
void xr_subtitle_update(const XrPosef& headPose);

// Draw the textured subtitle quad. subTex==0 or !visible early-returns (keeps the quad hidden).
void xr_subtitle_render(const float* proj, const float* viewMat, size_t eyeIdx, GLuint shaderProgram, GLuint quadVao, GLuint subTex, GLint locViewProj, GLint locTex, GLint locEye, GLint locStereo, GLint locZoom);

} // namespace fms::xr
