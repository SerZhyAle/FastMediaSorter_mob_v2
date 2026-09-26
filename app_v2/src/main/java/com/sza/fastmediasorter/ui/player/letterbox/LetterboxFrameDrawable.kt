package com.sza.fastmediasorter.ui.player.letterbox

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath.Axis
import timber.log.Timber

/**
 * One LETTERBOX-BARS frame, optionally with the LETTERBOX-HALO fade over its bars, at eased growth
 * [eased] (1 = the resting frame). [haloAllowed] is false for a video frame, which the contracts'
 * version 0.1 does not cover. While a growth runs, [underlay] is the frame that was on screen
 * before, laid whole over everything at opacity `1 - eased` (halo rule 7).
 *
 * Drawn in frame pixels scaled to the bounds, which the player sizes to the frame itself.
 */
class LetterboxFrameDrawable(
    val frame: LetterboxBarsFrame,
    haloEnabled: Boolean,
    val haloAllowed: Boolean,
) : Drawable() {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val haloPaint = Paint()
    private val solidPaint = Paint().apply { color = frame.background }
    private val stopColors = IntArray(LetterboxFillMath.BLEND_STOPS) { j ->
        (LetterboxFillMath.haloStopAlphas[j] shl ALPHA_SHIFT) or (frame.background and RGB_MASK)
    }
    private val stopPositions = FloatArray(
        LetterboxFillMath.BLEND_STOPS
    ) { LetterboxFillMath.haloStopPositions[it].toFloat() }

    var haloEnabled: Boolean = haloEnabled
        set(value) {
            field = value
            invalidateSelf()
        }

    var eased: Double = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            invalidateSelf()
        }

    var underlay: LetterboxFrameDrawable? = null
        set(value) {
            field = value
            invalidateSelf()
        }

    /** Halo rule 10: set by the growth driver; a frame that throws ends the growth instead of the image. */
    var onDrawFailure: (() -> Unit)? = null

    /** Halo rule 7: only a frame of the same size may dissolve into this one. */
    fun sameSizeAs(other: LetterboxFrameDrawable): Boolean =
        other.frame.surfaceWidth == frame.surfaceWidth && other.frame.surfaceHeight == frame.surfaceHeight

    /** Halo rule 1: the growth ends on exactly the resting frame. */
    fun settle() {
        underlay = null
        eased = 1.0
    }

    // A frame can fail with any RuntimeException the canvas throws (a recycled bitmap, a bad shader);
    // halo rule 10 wants every such failure to end the growth rather than the draw pass.
    @Suppress("TooGenericExceptionCaught")
    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val saved = canvas.save()
        try {
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(
                bounds.width() / frame.surfaceWidth.toFloat(),
                bounds.height() / frame.surfaceHeight.toFloat(),
            )
            drawFrame(canvas, eased)
            val previous = underlay
            val opacity = LetterboxFillMath.underlayOpacity(eased)
            if (previous != null && opacity > 0.0) {
                val alpha = (opacity * ALPHA_MAX).toInt().coerceIn(0, ALPHA_MAX)
                val layer = canvas.saveLayerAlpha(
                    0f,
                    0f,
                    frame.surfaceWidth.toFloat(),
                    frame.surfaceHeight.toFloat(),
                    alpha
                )
                previous.drawFrame(canvas, previous.eased)
                canvas.restoreToCount(layer)
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "LetterboxFrameDrawable: frame failed, the growth ends at rest")
            onDrawFailure?.invoke()
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    /** Bars bitmap, then the halo of each bar - in frame pixel coordinates. */
    private fun drawFrame(canvas: Canvas, growth: Double) {
        canvas.drawBitmap(frame.bitmap, 0f, 0f, bitmapPaint)
        if (!haloEnabled) return
        val rect = frame.rect
        when (frame.axis) {
            Axis.PILLARBOX -> {
                val top = 0f
                val bottom = frame.surfaceHeight.toFloat()
                drawBar(canvas, growth, rect.left, rect.left, -1, top, bottom, horizontal = true)
                val farStart = rect.left + rect.width
                drawBar(canvas, growth, frame.surfaceWidth - farStart, farStart, 1, top, bottom, horizontal = true)
            }
            Axis.LETTERBOX -> {
                val left = 0f
                val right = frame.surfaceWidth.toFloat()
                drawBar(canvas, growth, rect.top, rect.top, -1, left, right, horizontal = false)
                val farStart = rect.top + rect.height
                drawBar(canvas, growth, frame.surfaceHeight - farStart, farStart, 1, left, right, horizontal = false)
            }
        }
    }

    /**
     * Halo rules 2-3 for one bar of length [length] whose image edge is at [edge] and which runs in
     * [direction] (-1 towards 0, +1 towards the surface end). The ramp covers `reach` pixels from the
     * edge with the stop table; past the front the gradient clamps to its last stop, solid `B`.
     * [crossStart]/[crossEnd] span the other axis.
     */
    @Suppress("LongParameterList")
    private fun drawBar(
        canvas: Canvas,
        growth: Double,
        length: Int,
        edge: Int,
        direction: Int,
        crossStart: Float,
        crossEnd: Float,
        horizontal: Boolean,
    ) {
        if (length <= 0) return
        val reach = LetterboxFillMath.haloReach(length, growth)
        val barStart = if (direction < 0) edge - length else edge
        val barEnd = barStart + length
        val paint = if (reach <= 0) {
            solidPaint
        } else {
            val front = (edge + direction * reach).toFloat()
            val from = edge.toFloat()
            haloPaint.shader = if (horizontal) {
                LinearGradient(from, 0f, front, 0f, stopColors, stopPositions, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(0f, from, 0f, front, stopColors, stopPositions, Shader.TileMode.CLAMP)
            }
            haloPaint
        }
        if (horizontal) {
            canvas.drawRect(barStart.toFloat(), crossStart, barEnd.toFloat(), crossEnd, paint)
        } else {
            canvas.drawRect(crossStart, barStart.toFloat(), crossEnd, barEnd.toFloat(), paint)
        }
    }

    override fun getIntrinsicWidth(): Int = frame.surfaceWidth

    override fun getIntrinsicHeight(): Int = frame.surfaceHeight

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE"))
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        const val ALPHA_SHIFT = 24
        const val ALPHA_MAX = 255
        const val RGB_MASK = 0x00FFFFFF
    }
}
