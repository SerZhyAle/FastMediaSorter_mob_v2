package com.sza.fastmediasorter.ui.settings.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.EdgeGestureAxis
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone

/**
 * S1035: schematic of the four edge bands and their three swipe directions, mirroring the live overlay
 * geometry (upper 12-40% and lower 60-88% of the phone outline). S1188: the outline turns 90° with the
 * bands once the system bars take both side edges, so it shows where they actually are. Colour is not
 * the sole signal - the surrounding tab labels + rows carry the same meaning for accessibility; here grey
 * marks an available-but-off/unassigned target and red marks an enabled zone or an assigned direction.
 * Taps are hit-tested to a (zone, direction) or a bare zone and forwarded to the host manager.
 */
class EdgeGestureSchemaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /**
     * Live snapshot the view renders; per zone: master enable + the set of directions with an action.
     * S1408: [selectedZone] rides the same object rather than a second setter - settings and selection
     * change in the same frame, and two inputs into one view drift apart exactly there.
     */
    data class SchemaState(
        val zones: Map<ScreenshotGestureZone, ZoneState>,
        val selectedZone: ScreenshotGestureZone? = null,
    ) {
        data class ZoneState(val enabled: Boolean, val assigned: Set<ScreenshotGestureDirection>)

        companion object {
            val EMPTY = SchemaState(emptyMap())
        }
    }

    // Vertical order of the three direction cells inside a band (top -> centre -> bottom).
    private val directionOrder = listOf(
        ScreenshotGestureDirection.UP,
        ScreenshotGestureDirection.RIGHT,
        ScreenshotGestureDirection.DOWN,
    )

    private var state: SchemaState = SchemaState.EMPTY

    // S1188: which pair of outline edges carries the bands, read from the same rule the live overlay
    // uses so the diagram cannot promise a layout the overlay does not render.
    private var axis: EdgeGestureAxis = EdgeGestureAxis.VERTICAL
    private val phoneRect = RectF()
    private val bandRects = mutableMapOf<ScreenshotGestureZone, RectF>()
    private val directionRects = mutableMapOf<Pair<ScreenshotGestureZone, ScreenshotGestureDirection>, RectF>()

    private var directionTapListener: ((ScreenshotGestureZone, ScreenshotGestureDirection) -> Unit)? = null
    private var zoneTapListener: ((ScreenshotGestureZone) -> Unit)? = null

    private val redColor = ContextCompat.getColor(context, R.color.error_red)
    private val greyColor = resolveThemeColor(com.google.android.material.R.attr.colorOutline)
    private val accentColor = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary)

    private val phonePaint = strokePaint(dp(STROKE_PHONE_DP))
    private val bandPaint = strokePaint(dp(STROKE_BAND_DP))

    // S1408: dashed, so the marker differs from the red indication by pattern as well as hue, and from
    // the solid accent D-pad focus frame that arrives on the whole view.
    private val selectionPaint = strokePaint(dp(STROKE_SELECTION_DP)).apply {
        color = accentColor
        pathEffect = DashPathEffect(floatArrayOf(dp(DASH_ON_DP), dp(DASH_OFF_DP)), 0f)
    }
    private val selectionRect = RectF()
    private val arrowPaint = strokePaint(dp(STROKE_ARROW_DP)).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowPath = Path()

    // Reused across the twelve arrows of a draw pass rather than allocated per chevron.
    private val arrowDirection = PointF()
    private val phoneCornerPx = dp(PHONE_CORNER_DP)
    private val bandCornerPx = dp(BAND_CORNER_DP)

    init {
        isFocusable = true
        isClickable = true
        contentDescription = context.getString(R.string.edge_gesture_schema_content_description)
        // S1188: bars moving to the side edges need not resize this view, so onSizeChanged alone would
        // leave the diagram drawn against an edge pair the overlay has already stopped using.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            if (width > 0 && height > 0) {
                computeGeometry(width, height)
                invalidate()
            }
            insets
        }
    }

    fun setState(state: SchemaState) {
        // S1408: the host pushes a fresh state on every settings emission, so an unconditional
        // invalidate would repaint the diagram for edits it does not show.
        if (this.state == state) return
        this.state = state
        invalidate()
    }

    fun setOnDirectionTapListener(listener: (ScreenshotGestureZone, ScreenshotGestureDirection) -> Unit) {
        directionTapListener = listener
    }

    fun setOnZoneTapListener(listener: (ScreenshotGestureZone) -> Unit) {
        zoneTapListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSpec(widthMeasureSpec, dp(DEFAULT_WIDTH_DP).toInt())
        val height = resolveSpec(heightMeasureSpec, dp(DEFAULT_HEIGHT_DP).toInt())
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry(w, h)
    }

    /** Resolves the axis from the same insets the overlay reads, defaulting to the portrait layout. */
    private fun resolveAxis(): EdgeGestureAxis {
        val insets = ViewCompat.getRootWindowInsets(this)?.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        ) ?: return EdgeGestureAxis.VERTICAL
        return EdgeGestureAxis.forInsets(insets.left, insets.right)
    }

    private fun computeGeometry(w: Int, h: Int) {
        bandRects.clear()
        directionRects.clear()
        axis = resolveAxis()
        // The transposed outline is the portrait one with its sides swapped, so the diagram keeps a
        // single shape and only changes which pair of edges carries the bands.
        val shortSide = w * PHONE_WIDTH_FRACTION
        val longSide = h * (1f - 2f * PHONE_LONG_AXIS_INSET)
        val vertical = axis == EdgeGestureAxis.VERTICAL
        val phoneW = (if (vertical) shortSide else longSide).coerceAtMost(w.toFloat())
        val phoneH = (if (vertical) longSide else shortSide).coerceAtMost(h.toFloat())
        phoneRect.set((w - phoneW) / 2f, (h - phoneH) / 2f, (w + phoneW) / 2f, (h + phoneH) / 2f)
        val bandW = dp(BAND_WIDTH_DP)
        ScreenshotGestureZone.entries.forEach { zone -> layoutZone(zone, bandW) }
    }

    private fun layoutZone(zone: ScreenshotGestureZone, bandW: Float) {
        val startFraction = if (zone.isBottomBand) BAND_BOTTOM_START else BAND_TOP_START
        val cellHalf = dp(CELL_TOUCH_DP) / 2f
        val cellInset = dp(CELL_INSET_DP)
        when (axis) {
            EdgeGestureAxis.VERTICAL -> {
                val length = phoneRect.height() * BAND_HEIGHT_FRACTION
                val bandTop = phoneRect.top + phoneRect.height() * startFraction
                val bandLeft = if (zone.isRightEdge) phoneRect.right - bandW else phoneRect.left
                bandRects[zone] = RectF(bandLeft, bandTop, bandLeft + bandW, bandTop + length)
                // Direction cells sit just inside the outline from the band so the arrows read against it.
                val cellX = if (zone.isRightEdge) bandLeft - cellInset else bandLeft + bandW + cellInset
                directionOrder.forEachIndexed { index, direction ->
                    val centerY = bandTop + length / DIRECTION_COUNT * (index + HALF)
                    directionRects[zone to direction] = cellRect(cellX, centerY, cellHalf)
                }
            }

            EdgeGestureAxis.HORIZONTAL -> {
                val length = phoneRect.width() * BAND_HEIGHT_FRACTION
                val bandLeft = phoneRect.left + phoneRect.width() * startFraction
                val bandTop = if (zone.isRightEdge) phoneRect.bottom - bandW else phoneRect.top
                bandRects[zone] = RectF(bandLeft, bandTop, bandLeft + length, bandTop + bandW)
                val cellY = if (zone.isRightEdge) bandTop - cellInset else bandTop + bandW + cellInset
                directionOrder.forEachIndexed { index, direction ->
                    val centerX = bandLeft + length / DIRECTION_COUNT * (index + HALF)
                    directionRects[zone to direction] = cellRect(centerX, cellY, cellHalf)
                }
            }
        }
    }

    private fun cellRect(centerX: Float, centerY: Float, half: Float): RectF =
        RectF(centerX - half, centerY - half, centerX + half, centerY + half)

    override fun onDraw(canvas: Canvas) {
        phonePaint.color = greyColor
        canvas.drawRoundRect(phoneRect, phoneCornerPx, phoneCornerPx, phonePaint)
        ScreenshotGestureZone.entries.forEach { zone -> drawZone(canvas, zone) }
        state.selectedZone?.let { zone -> drawSelection(canvas, zone) }
    }

    /**
     * S1408: the marker for the zone the open tab edits. Its rectangle is taken from the live geometry
     * of the band and its three direction cells, so a change of axis carries it along with them instead
     * of needing a second rule keyed on the zone's name.
     */
    private fun drawSelection(canvas: Canvas, zone: ScreenshotGestureZone) {
        val band = bandRects[zone] ?: return
        selectionRect.set(band)
        directionOrder.forEach { direction ->
            directionRects[zone to direction]?.let { cell -> selectionRect.union(cell) }
        }
        val padding = dp(SELECTION_PADDING_DP)
        selectionRect.inset(-padding, -padding)
        val corner = dp(SELECTION_CORNER_DP)
        canvas.drawRoundRect(selectionRect, corner, corner, selectionPaint)
    }

    private fun drawZone(canvas: Canvas, zone: ScreenshotGestureZone) {
        val bandRect = bandRects[zone] ?: return
        val zoneState = state.zones[zone]
        val enabled = zoneState?.enabled == true
        bandPaint.color = if (enabled) redColor else greyColor
        canvas.drawRoundRect(bandRect, bandCornerPx, bandCornerPx, bandPaint)
        directionOrder.forEach { direction ->
            val cell = directionRects[zone to direction] ?: return@forEach
            val assigned = zoneState?.assigned?.contains(direction) == true
            arrowPaint.color = if (assigned) redColor else greyColor
            drawArrow(canvas, cell, direction, zone)
        }
    }

    /**
     * One generic chevron - tip on the gesture's unit vector, arms swept back along its perpendicular -
     * so the portrait and transposed diagrams cannot drift into two competing arrow formulas.
     */
    private fun drawArrow(
        canvas: Canvas,
        cell: RectF,
        direction: ScreenshotGestureDirection,
        zone: ScreenshotGestureZone,
    ) {
        val vector = arrowVector(direction, zone)
        val half = dp(ARROW_HALF_DP)
        val tipX = cell.centerX() + vector.x * half
        val tipY = cell.centerY() + vector.y * half
        val baseX = cell.centerX() - vector.x * half
        val baseY = cell.centerY() - vector.y * half
        arrowPath.reset()
        arrowPath.moveTo(baseX - vector.y * half, baseY + vector.x * half)
        arrowPath.lineTo(tipX, tipY)
        arrowPath.lineTo(baseX + vector.y * half, baseY - vector.x * half)
        canvas.drawPath(arrowPath, arrowPaint)
    }

    /**
     * The unit vector a direction points along, mirroring the overlay classifier exactly: UP is lateral
     * negative, DOWN lateral positive, RIGHT straight inward from the band's own edge.
     *
     * Mutates and returns one shared instance - this runs twelve times per [onDraw].
     */
    private fun arrowVector(direction: ScreenshotGestureDirection, zone: ScreenshotGestureZone): PointF {
        val inward = if (zone.isRightEdge) -1f else 1f
        if (axis == EdgeGestureAxis.VERTICAL) {
            when (direction) {
                ScreenshotGestureDirection.UP -> arrowDirection.set(0f, -1f)
                ScreenshotGestureDirection.DOWN -> arrowDirection.set(0f, 1f)
                ScreenshotGestureDirection.RIGHT -> arrowDirection.set(inward, 0f)
            }
        } else {
            when (direction) {
                ScreenshotGestureDirection.UP -> arrowDirection.set(-1f, 0f)
                ScreenshotGestureDirection.DOWN -> arrowDirection.set(1f, 0f)
                ScreenshotGestureDirection.RIGHT -> arrowDirection.set(0f, inward)
            }
        }
        return arrowDirection
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            handleTap(event.x, event.y)
            performClick()
            return true
        }
        return event.actionMasked == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        directionRects.entries.firstOrNull { it.value.contains(x, y) }?.let { (key, _) ->
            directionTapListener?.invoke(key.first, key.second)
            return
        }
        bandRects.entries.firstOrNull { it.value.contains(x, y) }?.let { (zone, _) ->
            zoneTapListener?.invoke(zone)
        }
    }

    private fun strokePaint(strokeWidth: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }

    private fun resolveThemeColor(attr: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }

    private fun resolveSpec(spec: Int, fallback: Int): Int {
        val size = MeasureSpec.getSize(spec)
        return when (MeasureSpec.getMode(spec)) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> minOf(fallback, size)
            else -> fallback
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val DEFAULT_HEIGHT_DP = 200f
        const val DEFAULT_WIDTH_DP = 280f
        const val PHONE_WIDTH_FRACTION = 0.42f
        const val PHONE_LONG_AXIS_INSET = 0.06f
        const val PHONE_CORNER_DP = 14f
        const val BAND_WIDTH_DP = 14f
        const val BAND_CORNER_DP = 4f
        const val BAND_TOP_START = 0.12f
        const val BAND_BOTTOM_START = 0.60f
        const val BAND_HEIGHT_FRACTION = 0.28f
        const val CELL_INSET_DP = 18f
        const val CELL_TOUCH_DP = 40f
        const val ARROW_HALF_DP = 6f
        const val STROKE_PHONE_DP = 2f
        const val STROKE_BAND_DP = 3f
        const val STROKE_ARROW_DP = 2.5f
        const val STROKE_SELECTION_DP = 1.5f
        const val DASH_ON_DP = 4f
        const val DASH_OFF_DP = 3f
        const val SELECTION_PADDING_DP = 6f
        const val SELECTION_CORNER_DP = 8f
        const val DIRECTION_COUNT = 3
        const val HALF = 0.5f
    }
}
