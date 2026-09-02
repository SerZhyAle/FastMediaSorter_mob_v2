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
import com.sza.fastmediasorter.domain.model.EdgeGestureAxis
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.ui.settings.helpers.ScreenshotGestureActionCatalog
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * S0847: hosts up to four independent edge-band overlay views (2 per edge, at 10-40% / 60-90% of the
 * safe extent). Each band classifies an inward drag into DOWN/RIGHT/UP and reports the originating
 * [ScreenshotGestureZone]. The far edge of each pair drags inward in the opposite sense; the classifier
 * mirrors that delta so the same angle windows apply to all four bands.
 *
 * S1188: which pair of edges the bands occupy comes from [EdgeGestureAxis] - the edges Android's own
 * bars leave free - so a rotation that does not move those bars does not move the bands either.
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

    // S1188: the edge pair the live bands currently sit on. Kept beside stripWidthPx because the touch
    // handler and setStripVisible run without a Geometry in hand and must not re-derive it per event.
    private var bandAxis = EdgeGestureAxis.VERTICAL

    // S1008: the subset of shown bands whose grey guide is visible; the rest render transparent.
    private var stripVisibleZones: Set<ScreenshotGestureZone> = emptySet()

    // A guide is useful only when it touches the physical display edge. A visible system bar can
    // reserve that edge and move the overlay inward, where the guide incorrectly looks detached.
    private var edgeAlignedGuideZones: Set<ScreenshotGestureZone> = emptySet()

    // S2257: the geometry the live bands were last laid out against. An inset dispatch arrives for
    // every window change, most of which move nothing, so a relayout is posted only when the geometry
    // it would produce differs from the one already applied.
    private var appliedGeometry: Geometry? = null

    // S2257: one window change reaches every live band separately, so without this the four dispatches
    // would each post the same relayout.
    private var insetRelayoutPending = false

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
    //
    // S1210: what lifting the finger right now would run - null is the cancel target, which is also the
    // starting value: a touch that never travels inward ends on cancel rather than in a special case.
    private var selection: ScreenshotGestureDirection? = null
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
        bandAxis = geom.axis
        edgeAlignedGuideZones = requestedZones.filterTo(linkedSetOf()) { zone ->
            geom.isBandOnPhysicalEdge(zone)
        }
        for (zone in requestedZones) {
            addBand(zone, geom)
        }
        appliedGeometry = geom
    }

    private fun removeBands() {
        hideHint()
        bandViews.values.forEach { windowManager.removeViewImmediate(it) }
        bandViews.clear()
        stripWidthPx = 0
        appliedGeometry = null
        insetRelayoutPending = false
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

        val geom = computeGeometry()
        val view = ScreenGestureHintView(overlayContext).apply { bind(rows, geom.axis, zone.isRightEdge) }
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
            y = hintY(zone, frame, geom, hintHeight)
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

    /**
     * Places the hint against the band's inner side and keeps it inside the safe bounds (Rule 17).
     *
     * S1188: which of the two coordinates carries that offset follows the axis - across the band's own
     * edge the hint sits beside it, along the edge it stays centred on it.
     */
    private fun hintX(zone: ScreenshotGestureZone, frame: BandFrame, geom: Geometry, width: Int): Int {
        val maxX = (geom.screenWidth - geom.safeRight - width).coerceAtLeast(geom.safeLeft)
        val preferred = when (geom.axis) {
            EdgeGestureAxis.VERTICAL -> if (zone.isRightEdge) frame.x - width else frame.x + frame.width
            EdgeGestureAxis.HORIZONTAL -> frame.x + frame.width / 2 - width / 2
        }
        return preferred.coerceIn(geom.safeLeft, maxX)
    }

    /** Centres the hint on the band, not on the finger - a hint that follows the touch is harder to read. */
    private fun hintY(zone: ScreenshotGestureZone, frame: BandFrame, geom: Geometry, height: Int): Int {
        val maxY = (geom.screenHeight - geom.safeBottom - height).coerceAtLeast(geom.safeTop)
        val preferred = when (geom.axis) {
            EdgeGestureAxis.VERTICAL -> frame.y + frame.height / 2 - height / 2
            EdgeGestureAxis.HORIZONTAL -> if (zone.isRightEdge) frame.y - height else frame.y + frame.height
        }
        return preferred.coerceIn(geom.safeTop, maxY)
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
     *  coordinate space, so bands drift off the physical edge (observed: one landing near center).
     *  S1188: the recomputed geometry also re-picks the edge pair, so the bands follow where the system
     *  bars ended up rather than what the new orientation calls left and right. */
    fun relayout() {
        if (bandViews.isEmpty()) return
        val geom = computeGeometry()
        stripWidthPx = geom.stripWidth
        bandAxis = geom.axis
        edgeAlignedGuideZones = bandViews.keys.filterTo(linkedSetOf()) { zone ->
            geom.isBandOnPhysicalEdge(zone)
        }
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
        appliedGeometry = geom
    }

    /**
     * S2257: a live band drifts off its physical edge when the visible insets change without a host
     * configuration callback - a system bar hiding, or the window entering another mode. The dispatch
     * carrying those insets is the invalidation signal; placement itself stays in [relayout], so the
     * geometry still has exactly one formula and no window mode needs a route of its own.
     */
    private fun onBandInsetsChanged(view: View) {
        if (insetRelayoutPending || bandViews.isEmpty()) return
        if (computeGeometry() == appliedGeometry) return
        insetRelayoutPending = true
        Timber.d("S2257: band insets changed, posting relayout")
        // The insets are still being dispatched: laying the window out inside that pass would re-enter
        // the traversal that delivered them.
        view.post {
            insetRelayoutPending = false
            relayout()
        }
    }

    /** S0724/S0847/S1008: recolour every live band (grey for zones in [zones], transparent otherwise)
     *  without re-adding the windows; if nothing is shown yet, the set is applied by the next [show]. */
    fun setStripVisible(zones: Set<ScreenshotGestureZone>) {
        if (stripVisibleZones == zones) return
        stripVisibleZones = zones
        bandViews.forEach { (zone, view) -> applyBandBackground(zone, view) }
    }

    /**
     * S1048: shared by addBand and relayout so the initial placement and the post-rotation
     * reposition can never drift apart into two competing geometry formulas.
     *
     * S1188: the two axes are a straight transpose of one another - [ScreenshotGestureZone.isRightEdge]
     * picks the far edge of the active pair (right, or bottom) and [ScreenshotGestureZone.isBottomBand]
     * picks the offset along it, measured down the safe height or across the safe width.
     */
    private fun bandFrame(zone: ScreenshotGestureZone, geom: Geometry): BandFrame {
        val startFraction = if (zone.isBottomBand) BAND_BOTTOM_START else BAND_TOP_START
        val minLength = geom.stripWidth * MIN_BAND_LENGTH_STRIPS
        return when (geom.axis) {
            EdgeGestureAxis.VERTICAL -> {
                val length = (geom.safeHeight * BAND_HEIGHT).roundToInt().coerceAtLeast(minLength)
                val x = if (zone.isRightEdge) {
                    (geom.screenWidth - geom.safeRight - geom.stripWidth).coerceAtLeast(0)
                } else {
                    geom.safeLeft
                }
                val y = geom.safeTop + (geom.safeHeight * startFraction).roundToInt()
                BandFrame(x = x, y = y, width = geom.stripWidth, height = length)
            }

            EdgeGestureAxis.HORIZONTAL -> {
                val length = (geom.safeWidth * BAND_HEIGHT).roundToInt().coerceAtLeast(minLength)
                val y = if (zone.isRightEdge) {
                    (geom.screenHeight - geom.safeBottom - geom.stripWidth).coerceAtLeast(0)
                } else {
                    geom.safeTop
                }
                val x = geom.safeLeft + (geom.safeWidth * startFraction).roundToInt()
                BandFrame(x = x, y = y, width = length, height = geom.stripWidth)
            }
        }
    }

    private fun addBand(zone: ScreenshotGestureZone, geom: Geometry) {
        val frame = bandFrame(zone, geom)
        val view = View(overlayContext).apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener { v, event -> handleTouch(zone, v, event) }
            setOnApplyWindowInsetsListener { insetTarget, insets ->
                onBandInsetsChanged(insetTarget)
                // Returned unmodified: the band consumes nothing and normal window processing stands.
                insets
            }
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
        runCatching { windowManager.addView(view, params) }
            .onFailure { error ->
                return
            }
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
        view.systemGestureExclusionRects = listOf(Rect(0, 0, frame.width, frame.height))
    }

    private fun applyBandBackground(zone: ScreenshotGestureZone, view: View) {
        if (zone !in stripVisibleZones || zone !in edgeAlignedGuideZones) {
            view.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        // S1008 device-test: the right edge needed a wider guide to stay perceptible on some panels;
        // the left edge already worked at the original width, so the two sides are no longer tied
        // to one shared constant.
        val edgeVisibleWidthPx = if (zone.isRightEdge) EDGE_VISIBLE_WIDTH_RIGHT_PX else EDGE_VISIBLE_WIDTH_LEFT_PX
        // S1188: the strip's thickness runs across the band's own edge, so it is the view's height
        // once the bands lie along the top and bottom.
        val measuredThickness =
            if (bandAxis == EdgeGestureAxis.VERTICAL) view.width else view.height
        val currentWidthPx = stripWidthPx.takeIf { it > 0 } ?: measuredThickness.coerceAtLeast(edgeVisibleWidthPx)
        val visibleEdgeWidthPx = edgeVisibleWidthPx.coerceAtMost(currentWidthPx)
        view.background = EdgeGuideDrawable(
            color = STRIP_VISIBLE_EDGE_COLOR,
            visibleWidthPx = visibleEdgeWidthPx,
            // Draw the guide on the physical screen edge - the outer side of the band, which is the far
            // edge of the active pair for right/bottom zones and the near one for left/top zones.
            alignEnd = zone.isRightEdge,
            axis = bandAxis
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
                selection = null
                showHint(zone)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val components = dragComponents(zone, event.rawX - downX, event.rawY - downY)
                // S1210: one resolver feeds both the preview and the outcome, so the hint cannot promise
                // a target other than the one the lift runs.
                selection = resolveSelection(components)
                hintView?.highlight(selection)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val chosen = selection
                hideHint()
                selection = null
                if (chosen == null) return false
                // Let WindowManager commit the hint removal before an immediate accessibility capture.
                view.postOnAnimation {
                    onGestureMatched(zone, chosen)
                }
                view.performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // ADR-3: the user never confirmed by lifting, so an interrupted touch runs nothing -
                // a silent action on top of somebody else's app is worse than a gesture that did not fire.
                hideHint()
                selection = null
                return false
            }
        }
        return false
    }

    /**
     * S1210: maps the finger onto the cancel target or one direction. Everything shallower than the
     * drawn cancel target - measured from the touch-down point, so the band's own width counts - is
     * cancel, and so is an angle that falls outside every direction window.
     */
    private fun resolveSelection(components: DragComponents): ScreenshotGestureDirection? {
        val cancelDepth = (hintView?.cancelTargetExtentPx ?: 0) + stripWidthPx
        if (components.inward <= cancelDepth) return null
        val angle = Math.toDegrees(atan2(components.lateral, components.inward).toDouble())
        return directionForAngle(angle)
    }

    /**
     * S1188: restates the drag in band-local terms - [DragComponents.inward] crosses the band's own
     * edge, [DragComponents.lateral] runs along it - so one classifier serves all four bands on either
     * axis. Mirroring the far edge of each pair is what lets the same angle windows apply to both.
     */
    private fun dragComponents(zone: ScreenshotGestureZone, dx: Float, dy: Float): DragComponents =
        when (bandAxis) {
            EdgeGestureAxis.VERTICAL ->
                DragComponents(inward = if (zone.isRightEdge) -dx else dx, lateral = dy)

            EdgeGestureAxis.HORIZONTAL ->
                DragComponents(inward = if (zone.isRightEdge) -dy else dy, lateral = dx)
        }

    private data class DragComponents(val inward: Float, val lateral: Float)

    // Classify the inward drag angle into one of three non-overlapping windows. Negative angle =
    // lateral-negative drag, ~0 = straight inward, positive = lateral-positive. Under the vertical axis
    // that reads as up/inward/down; transposed, as left/inward/right. Bounds are device-test candidates.
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
            val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            // S1358: two questions, two inset sources. WHICH edge pair the bands take must not
            // flip when a bar is briefly revealed, so the axis keeps reading the reserved inset
            // (S1188's reason). HOW FAR from that edge they sit must follow the bar that is
            // actually on screen: under a fullscreen video the status bar is hidden and the
            // reserved variant still claims its height, which parked the band a bar-height below
            // an empty physical edge.
            val visible = windowMetrics.windowInsets.getInsets(types)
            val reserved = windowMetrics.windowInsets.getInsetsIgnoringVisibility(types)
            return Geometry(
                safeTop = visible.top,
                safeBottom = visible.bottom,
                safeLeft = visible.left,
                safeRight = visible.right,
                axisLeft = reserved.left,
                axisRight = reserved.right,
                screenWidth = windowMetrics.bounds.width(),
                screenHeight = windowMetrics.bounds.height(),
                stripWidth = stripWidth
            )
        }

        // Pre-R reports no per-edge inset breakdown, so the free edge pair cannot be established and
        // the zeroed insets keep [Geometry.axis] on the historical left/right placement.
        return Geometry(
            safeTop = 0,
            safeBottom = 0,
            safeLeft = 0,
            safeRight = 0,
            axisLeft = 0,
            axisRight = 0,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            stripWidth = stripWidth
        )
    }

    private data class Geometry(
        // Live insets: what a bar currently occupies. These place the band.
        val safeTop: Int,
        val safeBottom: Int,
        val safeLeft: Int,
        val safeRight: Int,
        // S1358: reserved insets, kept separate because they answer a different question - which
        // edge pair is free - and must stay stable while a bar is hidden and shown again.
        val axisLeft: Int,
        val axisRight: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val stripWidth: Int
    ) {
        val safeHeight: Int get() = (screenHeight - safeTop - safeBottom).coerceAtLeast(1)
        val safeWidth: Int get() = (screenWidth - safeLeft - safeRight).coerceAtLeast(1)

        // S1188: derived rather than passed in, so the placement rule has exactly one definition and
        // the pre-R zeroed-inset fallback resolves through it too.
        val axis: EdgeGestureAxis get() = EdgeGestureAxis.forInsets(axisLeft, axisRight)

        /** A guide must not be drawn after a live system inset has moved its band inward. */
        fun isBandOnPhysicalEdge(zone: ScreenshotGestureZone): Boolean = when (axis) {
            EdgeGestureAxis.VERTICAL -> if (zone.isRightEdge) safeRight == 0 else safeLeft == 0
            EdgeGestureAxis.HORIZONTAL -> if (zone.isRightEdge) safeBottom == 0 else safeTop == 0
        }
    }

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

        // A band shorter than this many strip widths is not reliably hittable, so the percentage
        // length is floored at it on small safe areas.
        private const val MIN_BAND_LENGTH_STRIPS = 4

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
        private val alignEnd: Boolean,
        private val axis: EdgeGestureAxis
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        override fun draw(canvas: Canvas) {
            // The guide's thickness always runs across the band's own edge, so the only thing the axis
            // changes is which bounds dimension it is measured against and which way the rect is laid.
            val vertical = axis == EdgeGestureAxis.VERTICAL
            val span = if (vertical) bounds.width() else bounds.height()
            val thickness = visibleWidthPx.coerceAtMost(span)
            if (thickness <= 0 || bounds.width() <= 0 || bounds.height() <= 0) return
            val offset = if (alignEnd) (span - thickness).toFloat() else 0f
            if (vertical) {
                canvas.drawRect(offset, 0f, offset + thickness, bounds.height().toFloat(), paint)
            } else {
                canvas.drawRect(0f, offset, bounds.width().toFloat(), offset + thickness, paint)
            }
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
