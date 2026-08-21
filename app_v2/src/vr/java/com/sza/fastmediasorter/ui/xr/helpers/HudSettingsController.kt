package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import java.nio.ByteBuffer

/**
 * S1271: owns the HUD texture channel while the immersive settings panel is on screen.
 *
 * Same contract as [HudLegendController] (S1223), which is the shipped precedent of this shape:
 * main thread only; buffers are allocated on show and freed on hide, because three ~7 MB buffers
 * are worth holding for the seconds a panel is used, not for the hours a film plays; and
 * [onDismissed] is the single restore point for the media strip - this class never re-asserts the
 * strip's quad geometry itself, since a second restore path is how the strip ends up stuck at
 * panel geometry for the rest of the process.
 *
 * Unlike the legend the panel is interactive: value changes repaint it. Repaints are debounced
 * (~100 ms, S0290/S0964 rule) because a slider drag fires per ray-tick and must never be wired
 * straight to queueHud.
 */
class HudSettingsController(
    private val runtime: DiagnosticXrRuntime,
    private val renderer: HudSettingsRenderer,
    private val onDismissed: () -> Unit,
) {

    var isVisible: Boolean = false
        private set

    // Allocated on show and freed on dismiss/release - see class KDoc.
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var buffer: ByteBuffer? = null
    private var bytes: ByteArray? = null

    private val handler = Handler(Looper.getMainLooper())
    private var repaintPending = false
    private val repaintRunnable = Runnable {
        repaintPending = false
        if (isVisible) paintAndQueue()
    }

    fun show() {
        if (isVisible) return
        isVisible = true
        paintAndQueue()
        // Ownership transition: the panel takes the quad after its content is queued, exactly as
        // the legend does. Geometry matches the renderer's 1600x1120 aspect.
        runtime.setHudQuadSize(QUAD_WIDTH_M, QUAD_HEIGHT_M, QUAD_OFFSET_Y_M)
        runtime.setHudVisible(true)
    }

    /**
     * Returns true when a panel was actually taken down, so the caller can tell a consumed press
     * from one that should fall through to its normal handler.
     */
    fun dismiss(): Boolean {
        if (!isVisible) return false
        isVisible = false
        handler.removeCallbacks(repaintRunnable)
        repaintPending = false
        releaseBuffers()
        onDismissed()
        return true
    }

    /** Frees the buffers without touching native state - for host teardown. */
    fun release() {
        isVisible = false
        handler.removeCallbacks(repaintRunnable)
        repaintPending = false
        releaseBuffers()
    }

    /**
     * Coalesced repaint for value changes. A burst of ray-ticks lands as one texture upload at
     * most every [REPAINT_DEBOUNCE_MS].
     */
    fun requestRepaint() {
        if (!isVisible || repaintPending) return
        repaintPending = true
        handler.postDelayed(repaintRunnable, REPAINT_DEBOUNCE_MS)
    }

    private fun paintAndQueue() {
        val target = bitmap ?: Bitmap.createBitmap(
            HudSettingsRenderer.WIDTH,
            HudSettingsRenderer.HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).also { bitmap = it }
        val targetCanvas = canvas ?: Canvas(target).also { canvas = it }
        renderer.render(targetCanvas)

        val size = HudSettingsRenderer.WIDTH * HudSettingsRenderer.HEIGHT * RGBA_BYTES_PER_PIXEL
        // A heap-backed ByteBuffer.wrap silently yields an all-zero texture here (S0290 round 3).
        val targetBuffer = buffer ?: ByteBuffer.allocateDirect(size).also { buffer = it }
        targetBuffer.clear()
        target.copyPixelsToBuffer(targetBuffer)
        targetBuffer.rewind()
        val targetBytes = bytes?.takeIf { it.size == targetBuffer.remaining() }
            ?: ByteArray(targetBuffer.remaining()).also { bytes = it }
        targetBuffer.get(targetBytes)

        runtime.queueHud(targetBytes, HudSettingsRenderer.WIDTH, HudSettingsRenderer.HEIGHT)
    }

    private fun releaseBuffers() {
        bitmap?.recycle()
        bitmap = null
        canvas = null
        buffer = null
        bytes = null
    }

    companion object {
        // Matches HudSettingsRenderer's 1600x1120 aspect; same read-distance geometry the legend
        // proved in-headset (S1223). Public: the host scales this base live for the size setting.
        const val QUAD_WIDTH_M = 1.00f
        const val QUAD_HEIGHT_M = 0.70f
        const val QUAD_OFFSET_Y_M = -0.05f

        private const val RGBA_BYTES_PER_PIXEL = 4
        private const val REPAINT_DEBOUNCE_MS = 100L
    }
}
