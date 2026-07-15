package com.sza.fastmediasorter.ui.settings.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone

/**
 * S1035: schematic of the four edge bands and their three swipe directions, mirroring the live overlay
 * geometry (left/right edges, upper 12-40% and lower 60-88% of the phone outline). Colour is not the
 * sole signal - the surrounding tab labels + rows carry the same meaning for accessibility; here grey
 * marks an available-but-off/unassigned target and red marks an enabled zone or an assigned direction.
 * Taps are hit-tested to a (zone, direction) or a bare zone and forwarded to the host manager.
 */
class EdgeGestureSchemaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Live snapshot the view renders; per zone: master enable + the set of directions with an action. */
    data class SchemaState(val zones: Map<ScreenshotGestureZone, ZoneState>) {
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
    private val phoneRect = RectF()
    private val bandRects = mutableMapOf<ScreenshotGestureZone, RectF>()
    private val directionRects = mutableMapOf<Pair<ScreenshotGestureZone, ScreenshotGestureDirection>, RectF>()

    private var directionTapListener: ((ScreenshotGestureZone, ScreenshotGestureDirection) -> Unit)? = null
    private var zoneTapListener: ((ScreenshotGestureZone) -> Unit)? = null

    private val redColor = ContextCompat.getColor(context, R.color.error_red)
    private val greyColor = resolveThemeColor(com.google.android.material.R.attr.colorOutline)

    private val phonePaint = strokePaint(dp(STROKE_PHONE_DP))
    private val bandPaint = strokePaint(dp(STROKE_BAND_DP))
    private val arrowPaint = strokePaint(dp(STROKE_ARROW_DP)).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowPath = Path()
    private val phoneCornerPx = dp(PHONE_CORNER_DP)
    private val bandCornerPx = dp(BAND_CORNER_DP)

    init {
        isFocusable = true
        isClickable = true
        contentDescription = context.getString(R.string.edge_gesture_schema_content_description)
    }

    fun setState(state: SchemaState) {
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

    private fun computeGeometry(w: Int, h: Int) {
        bandRects.clear()
        directionRects.clear()
        val phoneW = w * PHONE_WIDTH_FRACTION
        val vInset = h * PHONE_VERTICAL_INSET
        val phoneLeft = (w - phoneW) / 2f
        phoneRect.set(phoneLeft, vInset, phoneLeft + phoneW, h - vInset)
        val bandW = dp(BAND_WIDTH_DP)
        ScreenshotGestureZone.entries.forEach { zone -> layoutZone(zone, bandW) }
    }

    private fun layoutZone(zone: ScreenshotGestureZone, bandW: Float) {
        val phoneH = phoneRect.height()
        val startFraction = if (zone.isBottomBand) BAND_BOTTOM_START else BAND_TOP_START
        val bandTop = phoneRect.top + phoneH * startFraction
        val bandHeight = phoneH * BAND_HEIGHT_FRACTION
        val bandLeft = if (zone.isRightEdge) phoneRect.right - bandW else phoneRect.left
        bandRects[zone] = RectF(bandLeft, bandTop, bandLeft + bandW, bandTop + bandHeight)
        val cellHeight = bandHeight / DIRECTION_COUNT
        // Direction cells sit just inside the screen from the band so the arrows read against the outline.
        val cellCenterX = if (zone.isRightEdge) bandLeft - dp(CELL_INSET_DP) else bandLeft + bandW + dp(CELL_INSET_DP)
        val cellHalf = dp(CELL_TOUCH_DP) / 2f
        directionOrder.forEachIndexed { index, direction ->
            val centerY = bandTop + cellHeight * (index + HALF)
            directionRects[zone to direction] = RectF(
                cellCenterX - cellHalf,
                centerY - cellHalf,
                cellCenterX + cellHalf,
                centerY + cellHalf,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        phonePaint.color = greyColor
        canvas.drawRoundRect(phoneRect, phoneCornerPx, phoneCornerPx, phonePaint)
        ScreenshotGestureZone.entries.forEach { zone -> drawZone(canvas, zone) }
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
            drawArrow(canvas, cell, direction, zone.isRightEdge)
        }
    }

    // Chevron pointing UP / DOWN / inward (RIGHT means "toward screen centre", mirrored on the right edge).
    private fun drawArrow(canvas: Canvas, cell: RectF, direction: ScreenshotGestureDirection, rightEdge: Boolean) {
        val cx = cell.centerX()
        val cy = cell.centerY()
        val half = dp(ARROW_HALF_DP)
        arrowPath.reset()
        when (direction) {
            ScreenshotGestureDirection.UP -> {
                arrowPath.moveTo(cx - half, cy + half)
                arrowPath.lineTo(cx, cy - half)
                arrowPath.lineTo(cx + half, cy + half)
            }
            ScreenshotGestureDirection.DOWN -> {
                arrowPath.moveTo(cx - half, cy - half)
                arrowPath.lineTo(cx, cy + half)
                arrowPath.lineTo(cx + half, cy - half)
            }
            ScreenshotGestureDirection.RIGHT -> {
                val tipX = if (rightEdge) cx - half else cx + half
                val baseX = if (rightEdge) cx + half else cx - half
                arrowPath.moveTo(baseX, cy - half)
                arrowPath.lineTo(tipX, cy)
                arrowPath.lineTo(baseX, cy + half)
            }
        }
        canvas.drawPath(arrowPath, arrowPaint)
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
        const val PHONE_VERTICAL_INSET = 0.06f
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
        const val DIRECTION_COUNT = 3
        const val HALF = 0.5f
    }
}
