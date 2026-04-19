package com.sza.fastmediasorter.vr.render

/**
 * Per-eye UV crop expressed in normalized texture coordinates.
 */
data class VrUvRect(
    val uOffset: Float,
    val vOffset: Float,
    val uScale: Float,
    val vScale: Float,
)

/**
 * Render-layer description shared by the GL renderer and the native OpenXR bridge.
 *
 * The GL side uses the UV rects to crop the decoded frame per eye. The native side uses
 * the layer geometry to choose the correct OpenXR composition layer type at xrEndFrame.
 */
data class VrLayerDescriptor(
    val type: VrLayerType = VrLayerType.QUAD_CINEMA,
    val leftEyeUv: VrUvRect = FULL_FRAME_UV,
    val rightEyeUv: VrUvRect = FULL_FRAME_UV,
    val widthMeters: Float = DEFAULT_WIDTH_METERS,
    val heightMeters: Float = DEFAULT_HEIGHT_METERS,
    val distanceMeters: Float = DEFAULT_DISTANCE_METERS,
    val radiusMeters: Float = DEFAULT_RADIUS_METERS,
    val centralHorizontalAngleRadians: Float = FULL_SPHERE_RADIANS,
    val upperVerticalAngleRadians: Float = HALF_SPHERE_RADIANS,
    val lowerVerticalAngleRadians: Float = -HALF_SPHERE_RADIANS,
) {
    companion object {
        val FULL_FRAME_UV = VrUvRect(
            uOffset = 0f,
            vOffset = 0f,
            uScale = 1f,
            vScale = 1f,
        )

        const val DEFAULT_DISTANCE_METERS = 4f
        const val DEFAULT_WIDTH_METERS = 4f
        const val DEFAULT_HEIGHT_METERS = 2.25f
        const val DEFAULT_RADIUS_METERS = 1f

        const val PI = 3.1415927f
        const val HALF_SPHERE_RADIANS = PI / 2f
        const val FULL_SPHERE_RADIANS = PI * 2f
        const val HALF_DOME_RADIANS = PI
    }
}