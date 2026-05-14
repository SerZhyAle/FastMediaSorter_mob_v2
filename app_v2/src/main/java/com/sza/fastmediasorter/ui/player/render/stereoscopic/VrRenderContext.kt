package com.sza.fastmediasorter.ui.player.render.stereoscopic

import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * Eye currently being rendered by the XR callback.
 */
enum class VrEye {
    LEFT,
    RIGHT;

    companion object {
        fun fromNativeIndex(eyeIndex: Int): VrEye = if (eyeIndex == 0) LEFT else RIGHT
    }
}

/**
 * Immutable frame context passed from the XR host into the GL renderer.
 *
 * The current native callback exposes the already-bound FBO rather than the raw
 * swapchain image slot, so [swapchainImageIndex] is treated as the opaque render-target
 * identifier used by the existing pipeline.
 */
data class VrRenderContext(
    val layerType: VrLayerType,
    val stereoMode: StereoMode,
    val eye: VrEye,
    val swapchainImageIndex: Int,
    val renderingMode: VrRenderingMode,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val sourceAspectRatio: Float = DEFAULT_SOURCE_ASPECT_RATIO,
) {
    companion object {
        const val DEFAULT_SOURCE_ASPECT_RATIO = 16f / 9f
    }
}