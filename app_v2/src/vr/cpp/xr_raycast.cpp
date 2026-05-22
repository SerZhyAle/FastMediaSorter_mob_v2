// S0283 Phase 02: XR Raycasting module implementation.
// Handles Ray-to-Quad math, vector projections, and exponential smoothing filters.

#include "xr_raycast.h"
#include <cmath>

namespace fms::xr {

namespace {

// Helper to calculate dot product of two XrVector3f
inline float dot_product(const XrVector3f& a, const XrVector3f& b) {
    return a.x * b.x + a.y * b.y + a.z * b.z;
}

// Helper to rotate a vector using a quaternion
XrVector3f rotate_vector(const XrQuaternionf& q, const XrVector3f& v) {
    XrVector3f q_vec{q.x, q.y, q.z};
    
    // cross(q_vec, v)
    XrVector3f cross1{
        q_vec.y * v.z - q_vec.z * v.y,
        q_vec.z * v.x - q_vec.x * v.z,
        q_vec.x * v.y - q_vec.y * v.x
    };
    
    // cross1 + q.w * v
    XrVector3f temp{
        cross1.x + q.w * v.x,
        cross1.y + q.w * v.y,
        cross1.z + q.w * v.z
    };
    
    // cross(q_vec, temp)
    XrVector3f cross2{
        q_vec.y * temp.z - q_vec.z * temp.y,
        q_vec.z * temp.x - q_vec.x * temp.z,
        q_vec.x * temp.y - q_vec.y * temp.x
    };
    
    return XrVector3f{
        v.x + 2.0f * cross2.x,
        v.y + 2.0f * cross2.y,
        v.z + 2.0f * cross2.z
    };
}

} // namespace

bool ray_quad_intersect(const Ray& ray, const QuadHUD& quad, XrVector2f& outUv) {
    // 1. Get quad orientation vectors (Normal, Right, Up)
    // In OpenXR/OpenGL, the Quad's front/rear normal vector faces towards the viewer.
    // By convention, we rotate the unit Z-vector (0, 0, 1) as Normal,
    // X-vector (1, 0, 0) as Right, and Y-vector (0, 1, 0) as Up.
    XrVector3f normal = rotate_vector(quad.rot, {0.0f, 0.0f, 1.0f});
    XrVector3f right  = rotate_vector(quad.rot, {1.0f, 0.0f, 0.0f});
    XrVector3f up     = rotate_vector(quad.rot, {0.0f, 1.0f, 0.0f});

    // 2. Compute Ray-to-Plane Intersection
    // Plane: (P - C) . Normal = 0
    // Ray: P(t) = O + t * D
    // (O + t * D - C) . Normal = 0 -> t = ((C - O) . Normal) / (D . Normal)
    float denom = dot_product(ray.dir, normal);
    if (std::abs(denom) < 1e-6f) {
        return false; // Ray is parallel to the plane
    }

    XrVector3f center_to_origin{
        quad.center.x - ray.pos.x,
        quad.center.y - ray.pos.y,
        quad.center.z - ray.pos.z
    };

    float t = dot_product(center_to_origin, normal) / denom;
    if (t < 0.0f) {
        return false; // Intersection point is behind the ray origin
    }

    // 3. Find intersection point on the plane
    XrVector3f intersect_point{
        ray.pos.x + t * ray.dir.x,
        ray.pos.y + t * ray.dir.y,
        ray.pos.z + t * ray.dir.z
    };

    // 4. Project intersection point to Local Quad Coordinates
    XrVector3f center_to_intersect{
        intersect_point.x - quad.center.x,
        intersect_point.y - quad.center.y,
        intersect_point.z - quad.center.z
    };

    float local_x = dot_product(center_to_intersect, right);
    float local_y = dot_product(center_to_intersect, up);

    // 5. Boundary check
    float half_w = quad.width * 0.5f;
    float half_h = quad.height * 0.5f;

    if (std::abs(local_x) > half_w || std::abs(local_y) > half_h) {
        return false; // Intersection point is outside the quad boundaries
    }

    // 6. Map to normalized [0.0, 1.0] UV coordinates
    // U is from left to right (0.0 to 1.0)
    // V is from bottom to top (0.0 to 1.0)
    outUv.x = (local_x / quad.width) + 0.5f;
    outUv.y = (local_y / quad.height) + 0.5f;

    return true;
}

XrVector2f filter_uv_jitter(const XrVector2f& newUv, const XrVector2f& oldUv, float alpha) {
    return XrVector2f{
        alpha * newUv.x + (1.0f - alpha) * oldUv.x,
        alpha * newUv.y + (1.0f - alpha) * oldUv.y
    };
}

} // namespace fms::xr
