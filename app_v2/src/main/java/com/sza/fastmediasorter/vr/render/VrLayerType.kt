package com.sza.fastmediasorter.vr.render

/**
 * Stable layer IDs mirrored by the native OpenXR bridge.
 */
enum class VrLayerType(val nativeValue: Int) {
    PROJECTION(0),
    QUAD_CINEMA(1),
    EQUIRECT_2(2),
    CYLINDER(3),
}