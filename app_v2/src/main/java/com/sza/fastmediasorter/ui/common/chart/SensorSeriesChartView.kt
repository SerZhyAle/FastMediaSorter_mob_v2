package com.sza.fastmediasorter.ui.common.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.google.android.material.R as MaterialR

/**
 * S1179: draws one or two accumulated sensor series on a Canvas.
 *
 * The two lines differ in stroke width and in dash pattern, not only in colour - strategic §3.2
 * forbids colour as the only differentiator.
 *
 * The horizontal axis is scaled to the first and last timestamp rather than to the point count,
 * because the series decimates as it grows (Phase 02): the gap between neighbouring points doubles
 * at every thinning, so a view that assumed a fixed interval would compress the older half of every
 * long trip.
 *
 * Feature-neutral on purpose - it lives in `ui/common/chart/` so a second consumer adds no copy.
 */
class SensorSeriesChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var points: List<SensorSeriesPoint> = emptyList()

    /** Whether the second value of each point is drawn at all - off for a single-line series. */
    var showSecondary: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** False while there is too little to draw; the host shows its empty state instead. */
    val hasData: Boolean get() = points.size >= MIN_POINTS

    private val density = resources.displayMetrics.density

    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = PRIMARY_STROKE_DP * density
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
        strokeWidth = SECONDARY_STROKE_DP * density
        pathEffect = DashPathEffect(
            floatArrayOf(DASH_ON_DP * density, DASH_OFF_DP * density),
            0f,
        )
    }

    private val path = Path()

    init {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.SensorSeriesChartView)
        primaryPaint.color = typed.getColor(
            R.styleable.SensorSeriesChartView_sensorSeriesPrimaryColor,
            themeColor(MaterialR.attr.colorOnSurface),
        )
        secondaryPaint.color = typed.getColor(
            R.styleable.SensorSeriesChartView_sensorSeriesSecondaryColor,
            themeColor(MaterialR.attr.colorOnSurfaceVariant),
        )
        typed.recycle()
    }

    fun setPoints(value: List<SensorSeriesPoint>) {
        points = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hasData) {
            drawSeries(canvas, primaryPaint) { it.primaryValue }
            if (showSecondary) {
                drawSeries(canvas, secondaryPaint) { it.secondaryValue }
            }
        }
    }

    /**
     * Each series is scaled to its own minimum and maximum: speed and cumulative distance share a
     * tile but not a unit, and a shared vertical scale would flatten one of them into the axis.
     */
    private fun drawSeries(canvas: Canvas, paint: Paint, value: (SensorSeriesPoint) -> Double?) {
        val values = points.map(value)
        val present = values.filterNotNull()
        if (present.size < MIN_POINTS) {
            return
        }
        val lowest = present.min()
        val highest = present.max()
        val range = (highest - lowest).takeIf { it > 0.0 } ?: FLAT_RANGE
        val startedAt = points.first().takenAtMillis
        val span = (points.last().takenAtMillis - startedAt).takeIf { it > 0L } ?: FLAT_SPAN_MS
        val usableWidth = (width - paddingLeft - paddingRight).toFloat()
        val usableHeight = (height - paddingTop - paddingBottom).toFloat()

        path.rewind()
        var started = false
        points.forEachIndexed { index, point ->
            val current = values[index]
            if (current != null) {
                val x = paddingLeft + usableWidth * (point.takenAtMillis - startedAt) / span
                val y = paddingTop + usableHeight * ((highest - current) / range).toFloat()
                if (started) path.lineTo(x, y) else path.moveTo(x, y)
                started = true
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun themeColor(attr: Int): Int {
        val resolved = TypedValue()
        context.theme.resolveAttribute(attr, resolved, true)
        return resolved.data
    }

    private companion object {
        /** One point is a dot, not a line - and a chart of one sample says nothing. */
        const val MIN_POINTS = 2

        /** Stand-ins for a series that has not moved yet, so a flat line divides by something. */
        const val FLAT_RANGE = 1.0
        const val FLAT_SPAN_MS = 1L

        const val PRIMARY_STROKE_DP = 2f
        const val SECONDARY_STROKE_DP = 1f
        const val DASH_ON_DP = 4f
        const val DASH_OFF_DP = 3f
    }
}
