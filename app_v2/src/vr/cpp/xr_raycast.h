// S0283 Phase 02: XR Raycasting module header.
// Computes mathematical intersection between 3D input rays and the World Space HUD Quad.

#pragma once

#ifndef XR_USE_PLATFORM_ANDROID
#define XR_USE_PLATFORM_ANDROID
#endif
#ifndef XR_USE_GRAPHICS_API_OPENGL_ES
#define XR_USE_GRAPHICS_API_OPENGL_ES
#endif

#include <openxr/openxr.h>

namespace fms::xr {

// 3D Ray representing the controller/hand aim direction
struct Ray {
    XrVector3f pos;
    XrVector3f dir;
};

// 3D HUD quad description in world space
struct QuadHUD {
    XrVector3f center;
    XrQuaternionf rot;
    float width;
    float height;
};

// Compute intersection of a 3D ray with the HUD Quad plane.
// Returns true if intersection occurs within the quad boundaries, mapping UV coordinates to [0.0, 1.0].
bool ray_quad_intersect(const Ray& ray, const QuadHUD& quad, XrVector2f& outUv);

// Apply an exponential moving average jitter filter (alpha = 0.25f by default).
XrVector2f filter_uv_jitter(const XrVector2f& newUv, const XrVector2f& oldUv, float alpha = 0.25f);

} // namespace fms::xr
