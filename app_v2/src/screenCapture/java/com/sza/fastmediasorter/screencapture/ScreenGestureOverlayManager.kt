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
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * S0847: hosts up to four independent edge-band overlay views (2 left, 2 right at 10-40% / 60-90% of
 * the safe height). Each band classifies an inward drag into DOWN/RIGHT/UP and reports the originating
 * [ScreenshotGestureZone]. Right-edge bands drag inward leftward; the classifier mirrors the horizontal
 * delta so the same angle windows apply on both edges.
 */
class ScreenGestureOverlayManager(
    context: Context,
    private val overlayWindowType: Int,
    private val onGestureMatched: (
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection
    ) -> Unit = { _, _ -> }
) {
    // Shared by both overlay hosts: OverlayHostService (TYPE_APPLICATION_OVERLAY, standard + noLegal)
    // and the noLegal accessibility service (TYPE_ACCESSIBILITY_OVERLAY). Use the provided context
    // (service) directly, not the application context: a TYPE_ACCESSIBILITY_OVERLAY window must be
    // added through the accessibility service's own WindowManager so the system associates the overlay
    // token with that service.
    private val overlayContext = context
    private val windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val bandViews = LinkedHashMap<ScreenshotGestureZone, View>()
    private var stripWidthPx = 0

    // S1008: the subset of shown bands whose grey guide is visible; the rest render transparent.
    private var stripVisibleZones: Set<ScreenshotGestureZone> = emptySet()

    // Single-touch state: Android routes a whole gesture (down..up) to the view that received DOWN, so
    // one band owns a gesture at a time and sharing these across bands is safe.
    private var gestureTriggered = false
    private var downX = 0f
    private var downY = 0f

    fun show(stripVisibleZones: Set<ScreenshotGestureZone> = emptySet(), enabledZones: Set<ScreenshotGestureZone>) {
        if (bandViews.isNotEmpty()) return
        this.stripVisibleZones = stripVisibleZones
        val geom = computeGeometry()
        stripWidthPx = geom.stripWidth
        for (zone in enabledZones) {
            addBand(zone, geom)
        }
    }

    fun hide() {
        bandViews.values.forEach { windowManager.removeViewImmediate(it) }
        bandViews.clear()
        stripWidthPx = 0
    }

    /** S0724/S0847/S1008: recolour every live band (grey for zones in [zones], transparent otherwise)
     *  without re-adding the windows; if nothing is shown yet, the set is applied by the next [show]. */
    fun setStripVisible(zones: Set<ScreenshotGestureZone>) {
        if (stripVisibleZones == zones) return
        stripVisibleZones = zones
        bandViews.forEach { (zone, view) -> applyBandBackground(zone, view) }
    }

    private fun addBand(zone: ScreenshotGestureZone, geom: Geometry) {
        val startFraction = if (zone.isBottomBand) BAND_BOTTOM_START else BAND_TOP_START
        val top = geom.safeTop + (geom.safeHeight * startFraction).roundToInt()
        val height = (geom.safeHeight * BAND_HEIGHT).roundToInt().coerceAtLeast(geom.stripWidth * 4)
        val x = if (zone.isRightEdge) {
            (geom.screenWidth - geom.safeRight - geom.stripWidth).coerceAtLeast(0)
        } else {
            geom.safeLeft
        }
        val view = View(overlayContext).apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener { v, event -> handleTouch(zone, v, event) }
        }
        applyBandBackground(zone, view)
        val params = WindowManager.LayoutParams(
            geom.stripWidth,
            height,
            overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            this.x = x
            this.y = top
            title = "screen_gesture_overlay_${zone.name.lowercase()}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        bandViews[zone] = view
    }

    private fun applyBandBackground(zone: ScreenshotGestureZone, view: View) {
        if (zone !in stripVisibleZones) {
            view.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        // S1008 device-test: the right edge needed a wider guide to stay perceptible on some panels;
        // the left edge already worked at the original width, so the two sides are no longer tied
        // to one shared constant.
        val edgeVisibleWidthPx = if (zone.isRightEdge) EDGE_VISIBLE_WIDTH_RIGHT_PX else EDGE_VISIBLE_WIDTH_LEFT_PX
        val currentWidthPx = stripWidthPx.takeIf { it > 0 } ?: view.width.coerceAtLeast(edgeVisibleWidthPx)
        val visibleEdgeWidthPx = edgeVisibleWidthPx.coerceAtMost(currentWidthPx)
        view.background = EdgeGuideDrawable(
            color = STRIP_VISIBLE_EDGE_COLOR,
            visibleWidthPx = visibleEdgeWidthPx,
            // Draw the guide on the physical screen edge: outer-left for left bands, outer-right for right bands.
            alignEnd = zone.isRightEdge
        )
    }

    // Guard-clause touch handler: the several early returns are the clearest shape for the down/move/up
    // dispatch and the inward-drag gating, so ReturnCount is suppressed rather than nested into a flag.
    @Suppress("ReturnCount")
    private fun handleTouch(zone: ScreenshotGestureZone, view: View, event: MotionEvent): Boolean {
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
                // Inward drag: rightward (dx>0) from a left band, leftward (dx<0) from a right band.
                // Mirror the right edge by negating dx so the shared UP/RIGHT/DOWN classifier applies.
                val inwardDx = if (zone.isRightEdge) -dx else dx
                if (inwardDx <= 0f) {
                    return false
                }
                if (hypot(inwardDx.toDouble(), dy.toDouble()) < GESTURE_DISTANCE_PX) {
                    return true
                }
                val angle = Math.toDegrees(atan2(dy, inwardDx).toDouble())
                val direction = directionForAngle(angle) ?: return false
                gestureTriggered = true
                Timber.d("S0847: band $zone matched $direction")
                onGestureMatched(zone, direction)
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

    // Classify the inward drag angle into one of three non-overlapping windows. Negative angle =
    // upward drag, ~0 = horizontal (inward), positive = downward. Bounds are device-test tuning candidates.
    private fun directionForAngle(angle: Double): ScreenshotGestureDirection? = when (angle) {
        in UP_MIN_ANGLE_DEGREES..UP_MAX_ANGLE_DEGREES -> ScreenshotGestureDirection.UP
        in RIGHT_MIN_ANGLE_DEGREES..RIGHT_MAX_ANGLE_DEGREES -> ScreenshotGestureDirection.RIGHT
        in DOWN_MIN_ANGLE_DEGREES..DOWN_MAX_ANGLE_DEGREES -> ScreenshotGestureDirection.DOWN
        else -> null
    }

    private fun computeGeometry(): Geometry {
        val metrics = overlayContext.resources.displayMetrics
        val stripWidth = (STRIP_WIDTH_DP * metrics.density).roundToInt().coerceAtLeast(1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val safeHeight =
                (windowMetrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
            return Geometry(
                safeTop = insets.top,
                safeHeight = safeHeight,
                safeLeft = insets.left,
                safeRight = insets.right,
                screenWidth = windowMetrics.bounds.width(),
                stripWidth = stripWidth
            )
        }

        return Geometry(
            safeTop = 0,
            safeHeight = metrics.heightPixels.coerceAtLeast(1),
            safeLeft = 0,
            safeRight = 0,
            screenWidth = metrics.widthPixels,
            stripWidth = stripWidth
        )
    }

    private data class Geometry(
        val safeTop: Int,
        val safeHeight: Int,
        val safeLeft: Int,
        val safeRight: Int,
        val screenWidth: Int,
        val stripWidth: Int
    )

    companion object {
        // S0724 follow-up: only the first few px stay visibly guided; the rest of the gesture zone
        // remains transparent so the user can discover the edge without seeing the full hit area.
        // S1008 device-test (2026-07-12/13): the right-edge guide was imperceptible at the true
        // physical edge on some panels even though it was present in the composited framebuffer
        // (screenshot showed it, the eye did not); widening it 1px off the absolute edge fixed that
        // on the test device. The left edge worked fine at the original width, so the two are kept
        // independent rather than forced to match.
        private const val STRIP_VISIBLE_EDGE_COLOR = 0x80808080.toInt()
        private const val EDGE_VISIBLE_WIDTH_LEFT_PX = 4
        private const val EDGE_VISIBLE_WIDTH_RIGHT_PX = 5
        private const val STRIP_WIDTH_DP = 18

        // S0847 band geometry over the safe height: TOP band 10%..40%, BOTTOM band 60%..90%; the middle
        // 40%..60% is intentionally left free so the two same-edge bands never touch.
        private const val BAND_TOP_START = 0.10f
        private const val BAND_BOTTOM_START = 0.60f
        private const val BAND_HEIGHT = 0.30f
        private const val GESTURE_DISTANCE_PX = 120.0

        // Three non-overlapping angle windows (degrees) for the inward drag.
        private const val UP_MIN_ANGLE_DEGREES = -70.0
        private const val UP_MAX_ANGLE_DEGREES = -20.0
        private const val RIGHT_MIN_ANGLE_DEGREES = -20.0
        private const val RIGHT_MAX_ANGLE_DEGREES = 20.0
        private const val DOWN_MIN_ANGLE_DEGREES = 20.0
        private const val DOWN_MAX_ANGLE_DEGREES = 70.0
    }

    private class EdgeGuideDrawable(
        color: Int,
        private val visibleWidthPx: Int,
        private val alignEnd: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        override fun draw(canvas: Canvas) {
            val width = visibleWidthPx.coerceAtMost(bounds.width()).coerceAtLeast(0)
            if (width <= 0 || bounds.height() <= 0) return
            val left = if (alignEnd) (bounds.width() - width).toFloat() else 0f
            canvas.drawRect(left, 0f, left + width, bounds.height().toFloat(), paint)
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
