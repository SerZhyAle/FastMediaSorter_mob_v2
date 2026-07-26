package com.sza.fastmediasorter.screencapture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.ui.settings.helpers.ScreenshotGestureActionCatalog
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

    // S1167: the zone set the host last asked for, replayed when the panel lights up again. Kept apart
    // from bandViews because between screen-off and screen-on there are no windows but the request stands.
    private var requestedZones: Set<ScreenshotGestureZone> = emptySet()
    private var screenStateReceiver: BroadcastReceiver? = null

    // S1162: the configured action of every slot, resolved by the host before the bands went up.
    // Kept beside requestedZones for the same reason: it must outlive a screen-off band teardown.
    private var zoneActions:
        Map<ScreenshotGestureZone, Map<ScreenshotGestureDirection, ScreenshotGestureAction>> = emptyMap()

    // Single-touch state: Android routes a whole gesture (down..up) to the view that received DOWN, so
    // one band owns a gesture at a time and sharing these across bands is safe.
    private var gestureTriggered = false

    // S1162: set once the finger returns to the edge. Without it, hiding the hint was the ONLY effect of
    // that return: a second outward drag inside the same touch still classified and fired, silently,
    // with no hint on screen (showHint runs on ACTION_DOWN only). Cancelling must outlive the move that
    // caused it, so it is a flag rather than a single ignored event.
    private var gestureCancelled = false
    private var downX = 0f
    private var downY = 0f

    // S1162: the hint window lives exactly as long as one touch; null between gestures.
    private var hintView: ScreenGestureHintView? = null

    fun show(
        stripVisibleZones: Set<ScreenshotGestureZone> = emptySet(),
        enabledZones: Set<ScreenshotGestureZone>,
        zoneActions: Map<ScreenshotGestureZone, Map<ScreenshotGestureDirection, ScreenshotGestureAction>> =
            emptyMap(),
    ) {
        if (bandViews.isNotEmpty()) return
        this.stripVisibleZones = stripVisibleZones
        requestedZones = enabledZones
        this.zoneActions = zoneActions
        registerScreenStateReceiver()
        val screenOn = isScreenLit()
        Timber.d("S1167: overlay show requested, zones=${enabledZones.size} screenOn=$screenOn")
        if (screenOn) {
            addBands()
        }
    }

    fun hide() {
        unregisterScreenStateReceiver()
        requestedZones = emptySet()
        zoneActions = emptyMap()
        removeBands()
    }

    private fun addBands() {
        if (bandViews.isNotEmpty()) return
        val geom = computeGeometry()
        stripWidthPx = geom.stripWidth
        for (zone in requestedZones) {
            addBand(zone, geom)
        }
    }

    private fun removeBands() {
        hideHint()
        bandViews.values.forEach { windowManager.removeViewImmediate(it) }
        bandViews.clear()
        stripWidthPx = 0
    }

    /**
     * S1162: the three-row legend for the touched band, added on touch-down and taken down with the
     * touch (ADR-2 - no threshold, no delay).
     *
     * The window is FLAG_NOT_TOUCHABLE: it sits exactly where the finger is heading, and without that
     * flag it would swallow the very gesture it exists to explain.
     */
    private fun showHint(zone: ScreenshotGestureZone) {
        val actions = zoneActions[zone]
        if (hintView != null || actions == null) return
        // The action catalog is presentation metadata for these same actions (label + icon, S1166);
        // reading it here keeps the overlay and the settings picker naming one action identically.
        val rows = HINT_DIRECTION_ORDER.mapNotNull { direction ->
            val action = actions[direction] ?: return@mapNotNull null
            val meta = ScreenshotGestureActionCatalog.metaFor(action)
            ScreenGestureHintView.HintRow(direction, meta.iconRes, overlayContext.getText(meta.labelRes))
        }
        if (rows.isEmpty()) return

        val view = ScreenGestureHintView(overlayContext).apply { bind(rows) }
        val geom = computeGeometry()
        // S1048: reuse the band frame rather than re-deriving band coordinates - two geometry
        // formulas for the same band drifted apart once already.
        val frame = bandFrame(zone, geom)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(geom.screenWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val hintWidth = view.measuredWidth
        val hintHeight = view.measuredHeight
        val params = WindowManager.LayoutParams(
            hintWidth,
            hintHeight,
            overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = hintX(zone, frame, geom, hintWidth)
            y = hintY(frame, geom, hintHeight)
            title = "screen_gesture_hint_${zone.name.lowercase()}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        hintView = view
    }

    private fun hideHint() {
        val view = hintView ?: return
        hintView = null
        windowManager.removeViewImmediate(view)
    }

    /** Places the hint against the band's inner side and keeps it inside the safe bounds (Rule 17). */
    private fun hintX(zone: ScreenshotGestureZone, frame: BandFrame, geom: Geometry, width: Int): Int {
        val maxX = (geom.screenWidth - geom.safeRight - width).coerceAtLeast(geom.safeLeft)
        val preferred = if (zone.isRightEdge) frame.x - width else frame.x + frame.width
        return preferred.coerceIn(geom.safeLeft, maxX)
    }

    /** Centres the hint on the band, not on the finger - a hint that follows the touch is harder to read. */
    private fun hintY(frame: BandFrame, geom: Geometry, height: Int): Int {
        val maxY = (geom.safeTop + geom.safeHeight - height).coerceAtLeast(geom.safeTop)
        return (frame.y + frame.height / 2 - height / 2).coerceIn(geom.safeTop, maxY)
    }

    /**
     * S1167: under a full lock - panel dark, or lit only by always-on display - no gesture action can
     * run, so the bands are taken down instead of sitting on the edges unusable. On the unlock prompt
     * the panel is fully lit and part of the actions do work, so the bands come straight back.
     *
     * Removing the windows rather than making them transparent is deliberate: a transparent band still
     * swallows the touch, which would eat an unlock swipe started at the edge.
     */
    private fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                when (action) {
                    Intent.ACTION_SCREEN_ON -> if (isScreenLit()) addBands()
                    Intent.ACTION_SCREEN_OFF -> removeBands()
                    else -> return
                }
                Timber.d("S1167: $action handled, bands=${bandViews.size}")
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(overlayContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenStateReceiver = receiver
    }

    private fun unregisterScreenStateReceiver() {
        val receiver = screenStateReceiver ?: return
        screenStateReceiver = null
        overlayContext.unregisterReceiver(receiver)
    }

    /**
     * Always-on display reports STATE_DOZE, and ACTION_SCREEN_OFF fires on the way into it, so the
     * broadcast alone cannot tell the two lock states apart - the display state can.
     */
    private fun isScreenLit(): Boolean {
        val displayManager = overlayContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val state = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state
        if (state != null) return state == Display.STATE_ON
        val powerManager = overlayContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: true
    }

    /** S1048: recompute geometry against current display metrics and reposition every live band in
     *  place. Call from the host's onConfigurationChanged - a rotation (sensor auto-rotate, or an
     *  app forcing orientation, e.g. YouTube's "expand to fullscreen" button) otherwise leaves each
     *  band's WindowManager x/y/width/height baked in [show] re-interpreted in the new orientation's
     *  coordinate space, so bands drift off the physical edge (observed: one landing near center). */
    fun relayout() {
        if (bandViews.isEmpty()) return
        val geom = computeGeometry()
        stripWidthPx = geom.stripWidth
        bandViews.forEach { (zone, view) ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            val frame = bandFrame(zone, geom)
            params.x = frame.x
            params.y = frame.y
            params.width = frame.width
            params.height = frame.height
            windowManager.updateViewLayout(view, params)
            applyGestureExclusion(view, frame)
            applyBandBackground(zone, view)
        }
    }

    /** S0724/S0847/S1008: recolour every live band (grey for zones in [zones], transparent otherwise)
     *  without re-adding the windows; if nothing is shown yet, the set is applied by the next [show]. */
    fun setStripVisible(zones: Set<ScreenshotGestureZone>) {
        if (stripVisibleZones == zones) return
        stripVisibleZones = zones
        bandViews.forEach { (zone, view) -> applyBandBackground(zone, view) }
    }

    // S1048: shared by addBand and relayout so the initial placement and the post-rotation
    // reposition can never drift apart into two competing geometry formulas.
    private fun bandFrame(zone: ScreenshotGestureZone, geom: Geometry): BandFrame {
        val startFraction = if (zone.isBottomBand) BAND_BOTTOM_START else BAND_TOP_START
        val top = geom.safeTop + (geom.safeHeight * startFraction).roundToInt()
        val height = (geom.safeHeight * BAND_HEIGHT).roundToInt().coerceAtLeast(geom.stripWidth * 4)
        val x = if (zone.isRightEdge) {
            (geom.screenWidth - geom.safeRight - geom.stripWidth).coerceAtLeast(0)
        } else {
            geom.safeLeft
        }
        return BandFrame(x = x, y = top, width = geom.stripWidth, height = height)
    }

    private fun addBand(zone: ScreenshotGestureZone, geom: Geometry) {
        val frame = bandFrame(zone, geom)
        val view = View(overlayContext).apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener { v, event -> handleTouch(zone, v, event) }
        }
        applyBandBackground(zone, view)
        val params = WindowManager.LayoutParams(
            frame.width,
            frame.height,
            overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            this.x = frame.x
            this.y = frame.y
            title = "screen_gesture_overlay_${zone.name.lowercase()}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        applyGestureExclusion(view, frame)
        bandViews[zone] = view
    }

    /**
     * S1171: on gesture navigation the system back-gesture monitor steals a band's touch ~80 ms after
     * DOWN, so no edge gesture ever completes. Opting the band's own rectangle out of system gestures
     * lets the inward drag reach [handleTouch]. The platform caps the honored exclusion per edge
     * (~200dp), so a tall band is only partially protected - a system limit, not a defect here.
     */
    private fun applyGestureExclusion(view: View, frame: BandFrame) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        Timber.d("S1171: exclude band from system gestures ${frame.width}x${frame.height}")
        view.systemGestureExclusionRects = listOf(Rect(0, 0, frame.width, frame.height))
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
                gestureCancelled = false
                showHint(zone)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureTriggered) return true
                if (gestureCancelled) return false
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                // Inward drag: rightward (dx>0) from a left band, leftward (dx<0) from a right band.
                // Mirror the right edge by negating dx so the shared UP/RIGHT/DOWN classifier applies.
                val inwardDx = if (zone.isRightEdge) -dx else dx
                if (inwardDx <= 0f) {
                    // S1162: returning to the edge cancels the gesture for the rest of this touch, and
                    // taking the hint down is how that cancellation becomes visible before the lift.
                    gestureCancelled = true
                    hideHint()
                    return false
                }
                val angle = Math.toDegrees(atan2(dy, inwardDx).toDouble())
                // S1162: one classifier call feeds both the preview and the outcome, so the hint cannot
                // promise a direction other than the one that fires.
                val direction = directionForAngle(angle)
                hintView?.highlight(direction)
                if (hypot(inwardDx.toDouble(), dy.toDouble()) < GESTURE_DISTANCE_PX) {
                    return true
                }
                if (direction == null) return false
                gestureTriggered = true
                hideHint()
                onGestureMatched(zone, direction)
                view.performClick()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                hideHint()
                val consumed = gestureTriggered
                gestureTriggered = false
                gestureCancelled = false
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

    private data class BandFrame(val x: Int, val y: Int, val width: Int, val height: Int)

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

        // S1162: hint row order - up, straight ahead, down, matching the settings direction diagram.
        private val HINT_DIRECTION_ORDER = listOf(
            ScreenshotGestureDirection.UP,
            ScreenshotGestureDirection.RIGHT,
            ScreenshotGestureDirection.DOWN,
        )

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
