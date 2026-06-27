package com.sza.fastmediasorter.screencapture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class ScreenGestureOverlayManager(
    context: Context,
    private val overlayWindowType: Int,
    private val onGestureMatched: (direction: ScreenshotGestureDirection) -> Unit = {}
) {
    // Shared by both overlay hosts: OverlayHostService (TYPE_APPLICATION_OVERLAY, standard + noLegal)
    // and the noLegal accessibility service (TYPE_ACCESSIBILITY_OVERLAY). Use the provided context
    // (service) directly, not the application context: a TYPE_ACCESSIBILITY_OVERLAY window must be
    // added through the accessibility service's own WindowManager so the system associates the overlay
    // token with that service.
    private val overlayContext = context
    private val windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var stripView: View? = null
    private var stripWidthPx = 0
    private var stripVisible = false
    private var gestureTriggered = false
    private var downX = 0f
    private var downY = 0f

    fun show(stripVisible: Boolean = false) {
        if (stripView != null) return
        this.stripVisible = stripVisible
        val spec = computeStripSpec()
        stripWidthPx = spec.width
        val view = View(overlayContext).apply {
            isClickable = false
            isFocusable = false
            applyStripBackground(this)
            setOnTouchListener(::handleTouch)
        }
        val params = WindowManager.LayoutParams(
            spec.width,
            spec.height,
            overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = spec.top
            title = "screen_gesture_overlay_strip"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        stripView = view
    }

    fun hide() {
        val view = stripView ?: return
        windowManager.removeViewImmediate(view)
        stripView = null
        stripWidthPx = 0
    }

    /** S0724: recolour a live strip (grey when visible, transparent when hidden) without re-adding the
     *  window; if no strip is shown yet, the value is applied by the next [show]. */
    fun setStripVisible(visible: Boolean) {
        if (stripVisible == visible) return
        stripVisible = visible
        stripView?.let { applyStripBackground(it) }
    }

    private fun applyStripBackground(view: View) {
        if (!stripVisible) {
            view.setBackgroundColor(Color.TRANSPARENT)
            return
        }

        val currentWidthPx = stripWidthPx.takeIf { it > 0 } ?: view.width.coerceAtLeast(EDGE_VISIBLE_WIDTH_PX)
        val visibleEdgeWidthPx = EDGE_VISIBLE_WIDTH_PX.coerceAtMost(currentWidthPx)
        view.background = EdgeGuideDrawable(
            color = STRIP_VISIBLE_EDGE_COLOR,
            visibleWidthPx = visibleEdgeWidthPx
        )
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                gestureTriggered = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureTriggered) return true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                // Rightward drag from the left-edge strip; dy may be negative (upward gesture).
                if (dx <= 0f) {
                    return false
                }
                if (hypot(dx.toDouble(), dy.toDouble()) < GESTURE_DISTANCE_PX) {
                    return true
                }
                val angle = Math.toDegrees(atan2(dy, dx).toDouble())
                val direction = directionForAngle(angle) ?: return false
                gestureTriggered = true
                onGestureMatched(direction)
                view.performClick()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val consumed = gestureTriggered
                gestureTriggered = false
                return consumed
            }
        }
        return false
    }

    // Classify the rightward drag angle into one of three non-overlapping windows. Negative angle =
    // upward drag, ~0 = horizontal, positive = downward. Bounds are device-test tuning candidates.
    private fun directionForAngle(angle: Double): ScreenshotGestureDirection? = when (angle) {
        in UP_MIN_ANGLE_DEGREES..UP_MAX_ANGLE_DEGREES -> ScreenshotGestureDirection.UP
        in RIGHT_MIN_ANGLE_DEGREES..RIGHT_MAX_ANGLE_DEGREES -> ScreenshotGestureDirection.RIGHT
        in DOWN_MIN_ANGLE_DEGREES..DOWN_MAX_ANGLE_DEGREES -> ScreenshotGestureDirection.DOWN
        else -> null
    }

    private fun computeStripSpec(): StripSpec {
        val density = overlayContext.resources.displayMetrics.density
        val width = (STRIP_WIDTH_DP * density).roundToInt().coerceAtLeast(1)
        val metrics = overlayContext.resources.displayMetrics

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val safeTop = insets.top
            val safeBottom = insets.bottom
            val safeHeight = (windowMetrics.bounds.height() - safeTop - safeBottom).coerceAtLeast(1)
            // First iteration tunes only the portrait left-edge strip. Landscape keeps the same
            // safe-bounds math but may need different width/height ratios after device testing.
            val top = safeTop + (safeHeight * STRIP_START_FRACTION).roundToInt()
            val height = (safeHeight * STRIP_HEIGHT_FRACTION).roundToInt().coerceAtLeast(width * 4)
            return StripSpec(top = top, width = width, height = height)
        }

        val top = (metrics.heightPixels * STRIP_START_FRACTION).roundToInt()
        val height = (metrics.heightPixels * STRIP_HEIGHT_FRACTION).roundToInt().coerceAtLeast(width * 4)
        return StripSpec(top = top, width = width, height = height)
    }

    private data class StripSpec(
        val top: Int,
        val width: Int,
        val height: Int
    )

    companion object {
        // S0724 follow-up: only the first 4 px stay visibly guided; the rest of the gesture zone
        // remains transparent so the user can discover the edge without seeing the full hit area.
        private const val STRIP_VISIBLE_EDGE_COLOR = 0x80808080.toInt()
        private const val EDGE_VISIBLE_WIDTH_PX = 4
        private const val STRIP_WIDTH_DP = 18
        // Strip spans the left edge from 10% to 75% of the safe height: the 10% top offset keeps
        // the corner free so the gesture is not triggered there; the strip ends at 75% downward.
        private const val STRIP_START_FRACTION = 0.10f
        private const val STRIP_HEIGHT_FRACTION = 0.65f
        private const val GESTURE_DISTANCE_PX = 120.0
        // Three non-overlapping angle windows (degrees) for the rightward drag from the left strip.
        private const val UP_MIN_ANGLE_DEGREES = -70.0
        private const val UP_MAX_ANGLE_DEGREES = -20.0
        private const val RIGHT_MIN_ANGLE_DEGREES = -20.0
        private const val RIGHT_MAX_ANGLE_DEGREES = 20.0
        private const val DOWN_MIN_ANGLE_DEGREES = 20.0
        private const val DOWN_MAX_ANGLE_DEGREES = 70.0
    }

    private class EdgeGuideDrawable(
        color: Int,
        private val visibleWidthPx: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        override fun draw(canvas: Canvas) {
            val width = visibleWidthPx.coerceAtMost(bounds.width()).coerceAtLeast(0)
            if (width <= 0 || bounds.height() <= 0) return
            canvas.drawRect(0f, 0f, width.toFloat(), bounds.height().toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
