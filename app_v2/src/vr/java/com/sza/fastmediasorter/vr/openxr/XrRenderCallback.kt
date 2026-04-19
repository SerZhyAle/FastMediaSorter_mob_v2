package com.sza.fastmediasorter.vr.openxr

/**
 * Per-eye render callback invoked by the native XR render loop.
 *
 * Contract (guaranteed by native before this call):
 *  - The swapchain image's FBO is already bound as GL_FRAMEBUFFER.
 *  - Viewport is set to the swapchain image dimensions.
 *  - The call is on the render thread with a valid EGL context.
 *
 * Implementations should draw the current video/image frame for [eye] and return
 * quickly; they must NOT rebind another framebuffer before drawing.
 *
 * Called twice per frame — eye=0 (left), eye=1 (right).
 */
fun interface XrRenderCallback {
    fun onRenderEye(eye: Int, fbo: Int, width: Int, height: Int)
}
