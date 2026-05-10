package com.sza.fastmediasorter.vr.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import timber.log.Timber

/**
 * GL-side ownership of the OpenXR interactive panel swapchain (spec_vr-immersive-controls-panel
 * Phase 03). Mirrors [VrHudRenderer], replacing HUD JNI calls with panel equivalents.
 *
 * Lifecycle identical to [VrHudRenderer]: construct on the GL thread, call
 * [ensureSwapchainCreated] before the first [submit], [release] when the session ends.
 */
class VrInteractivePanelRenderer(
    private val sessionManager: OpenXrSessionManager,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
) {
    private var swapchainReady = false
    private var bitmap: Bitmap? = null
    private var lastVisible: Boolean? = null
    private var failedSubmitCount = 0
    private var firstSubmitLogged = false
    private var lastRefusalLogged = false

    /** Ensure the native panel swapchain exists. Returns true on success / already ready. */
    fun ensureSwapchainCreated(): Boolean {
        if (swapchainReady) {
            lastRefusalLogged = false
            return true
        }
        if (lastRefusalLogged) {
            Timber.d(
                "VrInteractivePanelRenderer: ensureSwapchainCreated suppressed-repeat (size=%dx%d)",
                width, height,
            )
            return false
        }
        swapchainReady = sessionManager.createPanelSwapchain(width, height)
        if (!swapchainReady) {
            Timber.w("VrInteractivePanelRenderer: createPanelSwapchain(%dx%d) returned false", width, height)
            Timber.w("VrInteractivePanelRenderer: panel unavailable — falling back to 2D overlay")
            lastRefusalLogged = true
        } else {
            lastRefusalLogged = false
        }
        return swapchainReady
    }

    /**
     * Lock the internal bitmap, hand its [Canvas] to [producer], then upload to
     * the panel swapchain. Returns true on a successful native upload.
     */
    fun submit(producer: (Canvas) -> Unit): Boolean {
        if (!ensureSwapchainCreated()) return false
        val bmp = bitmap ?: createBitmap().also { bitmap = it }
        val canvas = Canvas(bmp)
        producer(canvas)
        val uploaded = sessionManager.uploadPanelBitmap(bmp)
        if (!uploaded) {
            failedSubmitCount++
            if (failedSubmitCount <= 3 || failedSubmitCount % 120 == 0) {
                Timber.w(
                    "VrInteractivePanelRenderer: uploadPanelBitmap returned false (count=%d size=%dx%d)",
                    failedSubmitCount, width, height,
                )
            }
        } else if (!firstSubmitLogged) {
            firstSubmitLogged = true
            Timber.i("VrInteractivePanelRenderer: first panel bitmap upload succeeded (%dx%d)", width, height)
            Timber.d("VR_AUDIT/2: panel FIRST submit OK size=%dx%d", width, height)
        }
        return uploaded
    }

    /** Toggle inclusion of the panel quad layer in the next composition frame. */
    fun setVisible(visible: Boolean) {
        if (lastVisible != visible) {
            lastVisible = visible
            Timber.d("VrInteractivePanelRenderer: setVisible(%b)", visible)
        }
        sessionManager.setPanelLayerVisible(visible)
    }

    /** Release the native swapchain and the internal bitmap. Idempotent. */
    fun release() {
        if (swapchainReady) {
            sessionManager.destroyPanelSwapchain()
            swapchainReady = false
        }
        bitmap?.recycle()
        bitmap = null
    }

    private fun createBitmap(): Bitmap {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        b.setPremultiplied(true)
        return b
    }

    companion object {
        const val DEFAULT_WIDTH = 1024
        const val DEFAULT_HEIGHT = 512
    }
}
