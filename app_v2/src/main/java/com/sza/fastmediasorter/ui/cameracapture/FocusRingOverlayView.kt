package com.sza.fastmediasorter.ui.cameracapture

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R

/**
 * Lightweight tap-to-focus confirmation affordance: draws a short-lived focus ring at the tapped
 * preview point and auto-fades it. Kept as its own view so focus visuals never leak into the
 * Activity (S0545 §3.4). Purely decorative - it never intercepts touches.
 */
class FocusRingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * RING_STROKE_DP
        color = ContextCompat.getColor(context, R.color.camera_capture_focus_ring)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var ringRadius = 0f
    private var fadeAnimator: ValueAnimator? = null

    init {
        // Decorative overlay: stay out of the touch path so the preview still receives tap events.
        isClickable = false
        isFocusable = false
    }

    /** Shows the focus ring at the given coordinates and starts the auto-fade animation. */
    fun showAt(x: Float, y: Float) {
        centerX = x
        centerY = y
        ringRadius = resources.displayMetrics.density * RING_RADIUS_DP
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofInt(MAX_ALPHA, 0).apply {
            duration = FADE_DURATION_MS
            startDelay = HOLD_DURATION_MS
            addUpdateListener {
                ringPaint.alpha = it.animatedValue as Int
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ringPaint.alpha = 0
                    invalidate()
                }
            })
        }
        ringPaint.alpha = MAX_ALPHA
        invalidate()
        fadeAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ringPaint.alpha == 0) return
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)
        val tick = ringRadius * INNER_TICK_FRACTION
        canvas.drawLine(centerX - tick, centerY, centerX + tick, centerY, ringPaint)
        canvas.drawLine(centerX, centerY - tick, centerX, centerY + tick, ringPaint)
    }

    override fun onDetachedFromWindow() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val RING_RADIUS_DP = 36f
        const val RING_STROKE_DP = 2f
        const val INNER_TICK_FRACTION = 0.28f
        const val MAX_ALPHA = 255
        const val HOLD_DURATION_MS = 700L
        const val FADE_DURATION_MS = 350L
    }
}
