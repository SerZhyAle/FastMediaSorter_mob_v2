package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * S0989: always-on HUD banner rendering (filename + error banners) for the diagnostic immersive
 * host, extracted from DiagnosticXrActivity. Owns the reusable HUD byte buffer and pushes banner
 * textures to native via [DiagnosticXrRuntime.queueHud].
 */
class VrHudBannerRenderer(private val runtime: DiagnosticXrRuntime) {

    @Volatile private var reusableHudBuffer: ByteBuffer? = null

    @Synchronized
    private fun getReusableHudBuffer(): ByteBuffer {
        val size = HUD_BANNER_WIDTH * HUD_BANNER_HEIGHT * 4
        val current = reusableHudBuffer
        if (current != null) {
            current.clear()
            return current
        }
        val newBuffer = ByteBuffer.allocateDirect(size)
        reusableHudBuffer = newBuffer
        return newBuffer
    }

    fun queueError(filename: String, errorMsg: String) {
        val bytes = generateErrorHudBytes(filename, errorMsg)
        runtime.queueHud(bytes, HUD_BANNER_WIDTH, HUD_BANNER_HEIGHT)
    }

    private fun generateErrorHudBytes(filename: String, errorMsg: String): ByteArray {
        val w = HUD_BANNER_WIDTH
        val h = HUD_BANNER_HEIGHT
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark red opaque background for warning / error
        canvas.drawColor(Color.argb(255, 139, 0, 0))

        // Rounded panel inside the banner
        val bgPaint = Paint().apply {
            color = Color.argb(255, 60, 0, 0)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rect = RectF(12f, 10f, (w - 12).toFloat(), (h - 10).toFloat())
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)

        // Filename on the left, monospace bold
        val namePaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        // Error msg on the right, bold yellow accent
        val errorPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val truncated = if (filename.length > 32) filename.take(30) + ".." else filename
        val nameBounds = Rect()
        namePaint.getTextBounds(truncated, 0, truncated.length, nameBounds)
        val nameY = (h / 2f) - nameBounds.exactCenterY()
        canvas.drawText(truncated, 36f, nameY, namePaint)

        val errorLabel = "ERROR: $errorMsg"
        val errorBounds = Rect()
        errorPaint.getTextBounds(errorLabel, 0, errorLabel.length, errorBounds)
        val errorY = (h / 2f) - errorBounds.exactCenterY()
        canvas.drawText(errorLabel, (w - 36).toFloat(), errorY, errorPaint)

        val buf = getReusableHudBuffer()
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        bitmap.recycle()
        return bytes
    }

    /**
     * S0290 (owner feedback round 3 2026-05-22): the previous attempt to render the full
     * [HudCanvasRenderer] panel as the always-on HUD produced an invisible result on Quest 3 (user
     * reported "I see no HUD"). The simple 1024x128 strip used pre-refactor was visible. This
     * restores that working path and additionally includes the resolved projection and stereo layout
     * next to the filename so the operator can see what the parser decided.
     */
    fun queueFilename(filename: String, projection: ProjectionType, layout: StereoLayout) {
        val bytes = generateFilenameHudBytes(filename, projection, layout)
        runtime.queueHud(bytes, HUD_BANNER_WIDTH, HUD_BANNER_HEIGHT)
    }

    private fun generateFilenameHudBytes(
        filename: String,
        projection: ProjectionType,
        layout: StereoLayout,
    ): ByteArray {
        val w = HUD_BANNER_WIDTH
        val h = HUD_BANNER_HEIGHT
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark opaque background - solid black w/ slight alpha so any wrong sampling stays diagnosable
        // in the headset. Full-banner fill (not just rounded rect) so the entire 1024x128 texture has
        // known pixels.
        canvas.drawColor(Color.argb(220, 8, 8, 16))

        // Rounded panel inside the banner - gives the HUD a clean edge.
        val bgPaint = Paint().apply {
            color = Color.argb(255, 12, 16, 30)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rect = RectF(12f, 10f, (w - 12).toFloat(), (h - 10).toFloat())
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)

        // Filename on the left, large white monospace bold.
        val namePaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        // Projection/Layout config on the right, smaller cyan-ish accent.
        val configPaint = Paint().apply {
            color = Color.argb(255, 140, 210, 255)
            textSize = 38f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val truncated = if (filename.length > 32) filename.take(30) + ".." else filename
        val nameBounds = Rect()
        namePaint.getTextBounds(truncated, 0, truncated.length, nameBounds)
        val nameY = (h / 2f) - nameBounds.exactCenterY()
        canvas.drawText(truncated, 36f, nameY, namePaint)

        val configLabel = "${projectionLabel(projection)} ${layoutLabel(layout)}"
        val configBounds = Rect()
        configPaint.getTextBounds(configLabel, 0, configLabel.length, configBounds)
        val configY = (h / 2f) - configBounds.exactCenterY()
        canvas.drawText(configLabel, (w - 36).toFloat(), configY, configPaint)

        // Use a DIRECT ByteBuffer (allocateDirect) - Bitmap.copyPixelsToBuffer is reliable with direct
        // buffers; the previous ByteBuffer.wrap(ByteArray) heap-buffer path produced all-zero output
        // (confirmed in logcat 16:29 round 2).
        val buf = getReusableHudBuffer()
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        bitmap.recycle()
        val px = "R=${bytes[0].toInt() and 0xFF} G=${bytes[1].toInt() and 0xFF} " +
            "B=${bytes[2].toInt() and 0xFF} A=${bytes[3].toInt() and 0xFF}"
        Timber.d(
            "HUD bytes ${bytes.size}; first pixel $px; " +
                "filename=$filename layout=${projection.name}/${layout.name}"
        )
        return bytes
    }

    private fun projectionLabel(p: ProjectionType): String = when (p) {
        ProjectionType.SPHERE_360 -> "360"
        ProjectionType.HEMISPHERE_180 -> "180"
        ProjectionType.FLAT -> "FLAT"
    }

    private fun layoutLabel(l: StereoLayout): String = when (l) {
        StereoLayout.MONO -> "MONO"
        StereoLayout.TOP_BOTTOM -> "TB"
        StereoLayout.SIDE_BY_SIDE -> "SBS"
    }

    /** S0989: release the reusable HUD buffer on host teardown. */
    fun releaseBuffers() {
        reusableHudBuffer = null
    }

    companion object {
        // S0290 (owner round 3): always-on HUD banner dimensions. Wide banner with the filename on the
        // left and the resolved projection/stereo layout on the right - visible from the moment a slide
        // loads, no ray pointing required.
        const val HUD_BANNER_WIDTH = 1024
        const val HUD_BANNER_HEIGHT = 128
    }
}
