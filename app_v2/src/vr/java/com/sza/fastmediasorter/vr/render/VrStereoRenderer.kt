package com.sza.fastmediasorter.vr.render

import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Per-eye stereo renderer for VR.
 *
 * Rendering strategy:
 * - SBS (full/half): left eye = left half of frame, right eye = right half
 * - OU (over-under): left eye = top half, right eye = bottom half
 * - MONO / 2D content: cinema mode — single frame fills a virtual screen
 *
 * Uses a GLSL UV-crop shader (preferred over dual swapchain approach).
 * StereoMode is injected from StereoDetectionFacade — the renderer does NOT detect stereo layout itself.
 *
 * NOTE: This is a scaffold. Actual GLES/Vulkan shader code and xrEndFrame()
 * integration require a Quest device for development and testing.
 */
class VrStereoRenderer {

    private var currentStereoMode: StereoMode = StereoMode.MONO

    /**
     * Set the stereo mode for the current media.
     * Called by VrPlayerActivity when StereoDetectionFacade returns a result.
     */
    fun setStereoMode(mode: StereoMode) {
        Timber.d("VrStereoRenderer: stereo mode set to $mode")
        currentStereoMode = mode
    }

    /**
     * Render a single frame for the given eye.
     * Called from the OpenXR render loop (per xrBeginFrame/xrEndFrame cycle).
     *
     * @param eye LEFT or RIGHT
     * @param swapchainImageIndex The swapchain image to render into.
     */
    fun renderEye(eye: Eye, swapchainImageIndex: Int) {
        // TODO: Bind framebuffer for the target swapchain image,
        //       set UV crop based on currentStereoMode + eye,
        //       draw the video texture quad with the GLSL UV-crop shader.
        when (currentStereoMode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> {
                // Left eye: UV x=[0.0, 0.5], Right eye: UV x=[0.5, 1.0]
                val uOffset = if (eye == Eye.LEFT) 0f else 0.5f
                val uWidth = 0.5f
                renderQuad(uOffset, 0f, uWidth, 1f, swapchainImageIndex)
            }
            StereoMode.OU -> {
                // Left eye: UV y=[0.0, 0.5], Right eye: UV y=[0.5, 1.0]
                val vOffset = if (eye == Eye.LEFT) 0f else 0.5f
                val vHeight = 0.5f
                renderQuad(0f, vOffset, 1f, vHeight, swapchainImageIndex)
            }
            else -> {
                // Cinema mode: full frame for both eyes
                renderQuad(0f, 0f, 1f, 1f, swapchainImageIndex)
            }
        }
    }

    /**
     * Draw a textured quad with the specified UV crop region.
     * Scaffold — actual GLES draw calls will be added when the native layer is ready.
     */
    private fun renderQuad(
        uOffset: Float,
        vOffset: Float,
        uWidth: Float,
        vHeight: Float,
        @Suppress("UNUSED_PARAMETER") swapchainImageIndex: Int
    ) {
        // TODO: GLES calls — bind shader, set UV uniforms, draw quad, unbind
        Timber.v("VrStereoRenderer: renderQuad uv=($uOffset,$vOffset,$uWidth,$vHeight)")
    }

    /** Release GL resources. */
    fun release() {
        Timber.d("VrStereoRenderer: releasing GL resources")
        // TODO: Delete shader program, VAO, VBO
    }

    enum class Eye { LEFT, RIGHT }
}
