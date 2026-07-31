package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * S1223: owns the HUD texture channel while the one-time controls legend is on screen.
 *
 * Main thread only, like every other HUD helper here - the host calls it from its JNI callbacks
 * after they have already marshalled onto the UI thread, so there is nothing to synchronize.
 *
 * [onDismissed] is the single restore point for the media strip: this class deliberately does not
 * re-assert the strip's quad size or repaint it, because the quad-size override persists in native
 * state for the whole process and a second restore path is how it ends up stuck on legend geometry.
 */
class HudLegendController(
    private val runtime: DiagnosticXrRuntime,
    private val renderer: HudLegendRenderer,
    private val onDismissed: () -> Unit,
) {

    var isVisible: Boolean = false
        private set

    // Allocated on show and freed on dismiss: three buffers of ~7 MB each are worth holding for the
    // seconds a legend is read, not for the hours a film plays, on a memory-tight headset.
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var buffer: ByteBuffer? = null
    private var bytes: ByteArray? = null

    fun show() {
        if (isVisible) return
        val target = bitmap ?: Bitmap.createBitmap(
            HudLegendRenderer.WIDTH,
            HudLegendRenderer.HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).also { bitmap = it }
        val targetCanvas = canvas ?: Canvas(target).also { canvas = it }
        renderer.render(targetCanvas)

        val size = HudLegendRenderer.WIDTH * HudLegendRenderer.HEIGHT * RGBA_BYTES_PER_PIXEL
        // A heap-backed ByteBuffer.wrap silently yields an all-zero texture here (S0290 round 3).
        val targetBuffer = buffer ?: ByteBuffer.allocateDirect(size).also { buffer = it }
        targetBuffer.clear()
        target.copyPixelsToBuffer(targetBuffer)
        targetBuffer.rewind()
        val targetBytes = bytes?.takeIf { it.size == targetBuffer.remaining() }
            ?: ByteArray(targetBuffer.remaining()).also { bytes = it }
        targetBuffer.get(targetBytes)

        runtime.queueHud(targetBytes, HudLegendRenderer.WIDTH, HudLegendRenderer.HEIGHT)
        runtime.setHudQuadSize(QUAD_WIDTH_M, QUAD_HEIGHT_M, QUAD_OFFSET_Y_M)
        runtime.setHudVisible(true)
        isVisible = true
    }

    /**
     * Returns true when a legend was actually taken down, so the caller can tell a consumed press
     * from one that should fall through to its normal handler.
     */
    fun dismiss(): Boolean {
        if (!isVisible) return false
        Timber.d("S1223: legend dismissed by controller input")
        isVisible = false
        releaseBuffers()
        onDismissed()
        return true
    }

    /** Frees the buffers without touching native state - for host teardown. */
    fun release() {
        isVisible = false
        releaseBuffers()
    }

    private fun releaseBuffers() {
        bitmap?.recycle()
        bitmap = null
        canvas = null
        buffer = null
        bytes = null
    }

    private companion object {
        // Matches HudLegendRenderer's 1600x1120 aspect. Nearer the gaze ray than the strip's
        // -0.30 m because the legend is read rather than glanced at, and the strip is not on
        // screen while it is up.
        const val QUAD_WIDTH_M = 1.00f
        const val QUAD_HEIGHT_M = 0.70f
        const val QUAD_OFFSET_Y_M = -0.05f

        const val RGBA_BYTES_PER_PIXEL = 4
    }
}
