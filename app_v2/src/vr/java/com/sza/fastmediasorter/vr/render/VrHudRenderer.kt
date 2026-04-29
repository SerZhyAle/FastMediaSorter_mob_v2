package com.sza.fastmediasorter.vr.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import timber.log.Timber

/**
 * GL-side ownership of the OpenXR HUD swapchain and the reusable Bitmap that
 * feeds it. Pure Kotlin — all OpenXR / GL work is delegated to native via
 * [sessionManager]. Phase 03 of spec_vr-immersive-hud-gl.
 *
 * Lifecycle:
 *  - Construct on the GL thread once the XR session is ready.
 *  - Call [ensureSwapchainCreated] before the first [submit] — idempotent.
 *  - Call [submit] from any thread; native serialises uploads via g_ctxMutex.
 *  - Call [setVisible] to toggle inclusion of the HUD layer in xrEndFrame.
 *  - Call [release] when the XR session ends.
 *
 * Resolution: 1024×256 by default (≈ 4:1 strip). Caller may override via the
 * constructor when a different aspect ratio is desired — but resizing requires
 * release + recreate on the native side, so prefer one fixed size per session.
 */
class VrHudRenderer(
    private val sessionManager: OpenXrSessionManager,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
) {

    private var swapchainReady: Boolean = false
    private var bitmap: Bitmap? = null
    private var lastVisible: Boolean? = null
    private var failedSubmitCount: Int = 0
    private var firstSubmitLogged: Boolean = false

    /** Ensure the native HUD swapchain exists. Returns true on success / already ready. */
    fun ensureSwapchainCreated(): Boolean {
        if (swapchainReady) return true
        swapchainReady = sessionManager.createHudSwapchain(width, height)
        if (!swapchainReady) {
            Timber.w("VrHudRenderer: createHudSwapchain($width x $height) returned false")
        }
        return swapchainReady
    }

    /**
     * Lock the internal bitmap, hand its [Canvas] to [producer], then upload to
     * the HUD swapchain. Returns true on a successful native upload.
     *
     * The bitmap is reused — no allocation per call. Producer must clear the
     * canvas itself when it wants a transparent frame (composer does this).
     */
    fun submit(producer: (Canvas) -> Unit): Boolean {
        if (!ensureSwapchainCreated()) return false
        val bmp = bitmap ?: createBitmap().also { bitmap = it }
        val canvas = Canvas(bmp)
        producer(canvas)
        val uploaded = sessionManager.uploadHudBitmap(bmp)
        // WHY: the immersive HUD currently fails silently when the native path rejects
        // uploads, which makes runtime logs insufficient to separate routing vs render bugs.
        if (!uploaded) {
            failedSubmitCount++
            if (failedSubmitCount <= 3 || failedSubmitCount % 120 == 0) {
                Timber.w(
                    "VrHudRenderer: uploadHudBitmap returned false (count=%d size=%dx%d)",
                    failedSubmitCount,
                    width,
                    height,
                )
            }
        } else if (!firstSubmitLogged) {
            firstSubmitLogged = true
            Timber.i("VrHudRenderer: first HUD bitmap upload succeeded (%dx%d)", width, height)
        }
        return uploaded
    }

    /**
     * Toggle inclusion of the HUD quad layer in the next composition frame.
     * [reason] is logged once per transition; pass an explicit tag (e.g. `"explicit-open-controls"`,
     * `"auto-show-startup"`, `"idle-hide"`) so the cause of every visibility change is traceable.
     */
    fun setVisible(visible: Boolean, reason: String = "unspecified") {
        Timber.d("VrHudRenderer: setVisible(%s) reason=%s prev=%s", visible, reason, lastVisible)
        if (lastVisible != visible) {
            lastVisible = visible
        }
        sessionManager.setHudLayerVisible(visible)
    }

    /** Release the native swapchain and the internal bitmap. Idempotent. */
    fun release() {
        if (swapchainReady) {
            sessionManager.destroyHudSwapchain()
            swapchainReady = false
        }
        bitmap?.recycle()
        bitmap = null
    }

    private fun createBitmap(): Bitmap {
        // ARGB_8888 with premultiplied alpha — matches GL_RGBA8 swapchain and
        // the BLEND_TEXTURE_SOURCE_ALPHA composition flag set in native renderFrame.
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        b.setPremultiplied(true)
        return b
    }

    companion object {
        const val DEFAULT_WIDTH = 1024
        const val DEFAULT_HEIGHT = 256
    }
}
