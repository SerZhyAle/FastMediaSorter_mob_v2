package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.R

/**
 * Fullscreen black overlay view with Wear-dimming inspired wake gestures (S3203, S3097, S3200).
 *
 * Single tap does not exit; it draws an animated expanding "water ripple" circle that slowly fades out.
 * Double tap or long press wakes / exits the dim mode, invoking [onExit].
 * Hardware Back or Escape key also exits.
 */
class DimOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Callback invoked when a double-tap, long-press, or Back key dismisses the dim overlay. */
    var onExit: (() -> Unit)? = null

    private var tapX = 0f
    private var tapY = 0f
    private var animStartTime = 0L

    @get:VisibleForTesting
    var isAnimating = false
        private set

    @get:VisibleForTesting
    var animationProgress = 1f
        private set

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * TAP_MARK_STROKE_DP
        color = Color.WHITE
    }

    @VisibleForTesting
    val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            startRippleAnimation(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onExit?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onExit?.invoke()
        }
    }

    @VisibleForTesting
    val gestureDetector = GestureDetector(context, gestureListener)

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        fitsSystemWindows = false
        contentDescription = context.getString(R.string.broadcast_control_blank_screen_cd)
    }

    fun startRippleAnimation(x: Float, y: Float) {
        tapX = x
        tapY = y
        animStartTime = SystemClock.uptimeMillis()
        animationProgress = 0f
        isAnimating = true
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        // Always consume all touch events while the dim overlay is displayed
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onExit?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isAnimating) {
            val elapsed = SystemClock.uptimeMillis() - animStartTime
            if (elapsed >= TAP_MARK_DURATION_MS) {
                isAnimating = false
                animationProgress = 1f
            } else {
                val linearFraction = elapsed.toFloat() / TAP_MARK_DURATION_MS
                val progress = TAP_MARK_INTERPOLATOR.getInterpolation(linearFraction)
                animationProgress = progress
                val density = resources.displayMetrics.density
                val startRadius = density * TAP_MARK_START_RADIUS_DP
                val endRadius = density * TAP_MARK_END_RADIUS_DP
                val radius = startRadius + (endRadius - startRadius) * progress
                val alpha = ((1f - progress) * MAX_ALPHA_FLOAT).toInt().coerceIn(0, MAX_ALPHA_INT)
                ripplePaint.alpha = alpha
                canvas.drawCircle(tapX, tapY, radius, ripplePaint)
                postInvalidateOnAnimation()
            }
        }
    }

    override fun onDetachedFromWindow() {
        isAnimating = false
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_ALPHA_FLOAT = 255f
        private const val MAX_ALPHA_INT = 255
        private const val TAP_MARK_DURATION_MS = 1400L
        private const val TAP_MARK_START_RADIUS_DP = 6f
        private const val TAP_MARK_END_RADIUS_DP = 48f
        private const val TAP_MARK_STROKE_DP = 2f
        private val TAP_MARK_INTERPOLATOR = PathInterpolator(0f, 0f, 0.2f, 1f)
    }
}
