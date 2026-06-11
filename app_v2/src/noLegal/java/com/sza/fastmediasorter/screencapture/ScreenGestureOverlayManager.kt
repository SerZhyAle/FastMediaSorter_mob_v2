package com.sza.fastmediasorter.screencapture

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class ScreenGestureOverlayManager(
    context: Context,
    private val onGestureMatched: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var stripView: View? = null
    private var gestureTriggered = false
    private var downX = 0f
    private var downY = 0f

    fun show() {
        if (stripView != null) return
        val spec = computeStripSpec()
        val view = View(appContext).apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener(::handleTouch)
        }
        val params = WindowManager.LayoutParams(
            spec.width,
            spec.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                if (dx <= 0f || dy <= 0f) {
                    return false
                }
                if (hypot(dx.toDouble(), dy.toDouble()) < GESTURE_DISTANCE_PX) {
                    return true
                }
                val angle = Math.toDegrees(atan2(dy, dx).toDouble())
                if (angle in MIN_MATCH_ANGLE_DEGREES..MAX_MATCH_ANGLE_DEGREES) {
                    gestureTriggered = true
                    launchConsentActivity()
                    onGestureMatched()
                    view.performClick()
                    return true
                }
                return false
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

    private fun launchConsentActivity() {
        val intent = Intent(appContext, ScreenCaptureConsentActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        appContext.startActivity(intent)
    }

    private fun computeStripSpec(): StripSpec {
        val density = appContext.resources.displayMetrics.density
        val width = (STRIP_WIDTH_DP * density).roundToInt().coerceAtLeast(1)
        val metrics = appContext.resources.displayMetrics

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
        private const val STRIP_WIDTH_DP = 18
        private const val STRIP_START_FRACTION = 0.10f
        private const val STRIP_HEIGHT_FRACTION = 0.40f
        private const val GESTURE_DISTANCE_PX = 120.0
        private const val MIN_MATCH_ANGLE_DEGREES = 25.0
        private const val MAX_MATCH_ANGLE_DEGREES = 65.0
    }
}
