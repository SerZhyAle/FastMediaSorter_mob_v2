package com.sza.fastmediasorter.ui.cameracapture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S1066: draws the result frame over the full-frame camera preview - a light contour around the
 * region that will be saved, with a semi-transparent scrim over the parts of the shown sensor frame
 * that fall outside it. Decorative only (never intercepts touches).
 *
 * The host shows it only in photo mode when the selected ratio is narrower than the shown frame
 * (16:9 inside the full 4:3), and hides it at 4:3 and in video mode - there the preview already
 * equals the file (spec ADR-1, §6.4), so no frame is needed. Distinguished by contour + dim, not
 * colour alone (spec §3.2 accessibility).
 *
 * Follows the existing lightweight overlay pattern ([GridOverlayView] / [FocusRingOverlayView]):
 * paints and geometry holders are pre-allocated and mutated in place, so [onDraw] - which reruns on
 * every relayout / zoom tick - never allocates (spec §3.2 perf).
 */
class ResultFrameOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.camera_result_frame_scrim)
        style = Paint.Style.FILL
    }
    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.camera_capture_control_stroke)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * CONTOUR_STROKE_DP
    }

    private val imageRect = RectF()
    private val resultRect = RectF()

    /** Full sensor frame aspect shown in the preview, expressed as width / height in display orientation. */
    private var contentRatio = PORTRAIT_4_3

    /** Result (saved file) frame aspect, expressed as width / height in display orientation. */
    private var resultRatio = PORTRAIT_16_9

    init {
        // Decorative overlay: stay out of the touch path so the preview still receives taps.
        isClickable = false
        isFocusable = false
    }

    /**
     * Sets the shown-frame and result-frame aspect ratios (width / height) and repaints. Kept
     * parametric so a future ratio (spec §5.3) reuses this view without a new one.
     */
    fun setRatios(contentWidthOverHeight: Float, resultWidthOverHeight: Float) {
        contentRatio = contentWidthOverHeight
        resultRatio = resultWidthOverHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        // The preview shows the full sensor frame letterboxed (FIT_CENTER), so the shown image occupies
        // a centred sub-rect; the result frame is the selected ratio centred inside that image rect.
        fitCentered(contentRatio, width.toFloat(), height.toFloat(), imageRect)
        fitCentered(resultRatio, imageRect.width(), imageRect.height(), resultRect)
        resultRect.offset(imageRect.left, imageRect.top)
        // Scrim the bands of the shown frame that fall outside the result frame (top/bottom or left/right).
        if (resultRect.top > imageRect.top) {
            canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, resultRect.top, scrimPaint)
        }
        if (resultRect.bottom < imageRect.bottom) {
            canvas.drawRect(imageRect.left, resultRect.bottom, imageRect.right, imageRect.bottom, scrimPaint)
        }
        if (resultRect.left > imageRect.left) {
            canvas.drawRect(imageRect.left, resultRect.top, resultRect.left, resultRect.bottom, scrimPaint)
        }
        if (resultRect.right < imageRect.right) {
            canvas.drawRect(resultRect.right, resultRect.top, imageRect.right, resultRect.bottom, scrimPaint)
        }
        canvas.drawRect(resultRect, contourPaint)
    }

    /** Fills [out] with the largest rect of [ratio] (w/h) centred inside a [w] x [h] box at origin. */
    private fun fitCentered(ratio: Float, w: Float, h: Float, out: RectF) {
        val rectW: Float
        val rectH: Float
        if (w / h > ratio) {
            rectH = h
            rectW = h * ratio
        } else {
            rectW = w
            rectH = w / ratio
        }
        val left = (w - rectW) / 2f
        val top = (h - rectH) / 2f
        out.set(left, top, left + rectW, top + rectH)
    }

    private companion object {
        const val CONTOUR_STROKE_DP = 1.5f

        // Portrait-locked host (S0918): the full 4:3 sensor frame reads 3:4 and the 16:9 result reads 9:16.
        const val PORTRAIT_4_3 = 3f / 4f
        const val PORTRAIT_16_9 = 9f / 16f
    }
}
