package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * Semi-transparent always-on touch-zone grid.
 *
 * Lines and labels are derived from [TouchZoneConfig] for the active [zoneMap], so each cell
 * names the action a tap there really runs, in the app locale. The grid-off fullscreen fallback
 * (REG-3100 / REG-375) draws its three columns plus the command-panel edge band instead of 3x3.
 */
class TouchZoneOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class ZoneCell(
        val xCenter: Float,
        val yCenter: Float,
        val widthFraction: Float,
        val lines: List<String>
    )

    /** All positions are fractions of the view width and of the zones' height. */
    private data class ZoneGrid(
        val heightPercent: Float,
        val verticalLines: List<Float>,
        val horizontalLines: List<Float>,
        val cells: List<ZoneCell>
    )

    var zoneMap: TouchZoneMap = TouchZoneMap.REG_9100
        set(value) {
            if (field == value) return
            field = value
            grid = buildGrid(value)
            invalidate()
        }

    private var grid: ZoneGrid = buildGrid(zoneMap)

    private val density = context.resources.displayMetrics.density

    private val linePaint = Paint().apply {
        color = Color.WHITE
        alpha = LINE_ALPHA
        strokeWidth = LINE_WIDTH_DP * density
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        alpha = TEXT_ALPHA
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            TEXT_SIZE_SP,
            context.resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = BACKGROUND_ALPHA
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val current = grid
        val zonesHeight = height * current.heightPercent

        canvas.drawRect(0f, 0f, width, height, backgroundPaint)
        for (x in current.verticalLines) {
            canvas.drawLine(width * x, 0f, width * x, zonesHeight, linePaint)
        }
        for (y in current.horizontalLines) {
            canvas.drawLine(0f, zonesHeight * y, width, zonesHeight * y, linePaint)
        }
        for (cell in current.cells) {
            drawLabel(canvas, cell, width, zonesHeight)
        }
    }

    private fun drawLabel(canvas: Canvas, cell: ZoneCell, width: Float, zonesHeight: Float) {
        var widest = 0f
        for (line in cell.lines) widest = maxOf(widest, textPaint.measureText(line))
        val scale = minOf(1f, width * cell.widthFraction * LABEL_WIDTH_SHARE / widest)
        // A band too narrow for a readable label (the command-panel edge band) is shown by its lines alone.
        if (scale < MIN_LABEL_SCALE) return

        val baseTextSize = textPaint.textSize
        textPaint.textSize = baseTextSize * scale
        val lineHeight = textPaint.fontSpacing
        val centerX = width * cell.xCenter
        var baseline = zonesHeight * cell.yCenter - lineHeight * (cell.lines.size - 1) / 2 +
            textPaint.textSize / BASELINE_DIVISOR
        for (line in cell.lines) {
            canvas.drawText(line, centerX, baseline, textPaint)
            baseline += lineHeight
        }
        textPaint.textSize = baseTextSize
    }

    private fun buildGrid(map: TouchZoneMap): ZoneGrid {
        val config = TouchZoneConfig.getConfiguration(map)
        val mapColumns = config.widthRatios.runningFold(0f) { edge, ratio -> edge + ratio }
        // The fallback splits its left column: the edge band opens the command panel, the rest is PREVIOUS.
        val columnEdges = if (isThreeZoneFallback(map)) {
            (mapColumns + TouchZoneConfig.COMMAND_PANEL_EDGE_FRACTION).sorted()
        } else {
            mapColumns
        }
        val rowEdges = config.heightRatios.runningFold(0f) { edge, ratio -> edge + ratio }
        val cells = mutableListOf<ZoneCell>()
        for (row in 0 until rowEdges.size - 1) {
            for (column in 0 until columnEdges.size - 1) {
                val xCenter = (columnEdges[column] + columnEdges[column + 1]) / 2
                val labelRes = labelFor(cellAction(map, row, column, xCenter)) ?: continue
                val yCenter = (rowEdges[row] + rowEdges[row + 1]) / 2
                val widthFraction = columnEdges[column + 1] - columnEdges[column]
                cells += ZoneCell(xCenter, yCenter, widthFraction, context.getString(labelRes).split('\n'))
            }
        }
        return ZoneGrid(config.heightPercent, columnEdges.drop(1).dropLast(1), rowEdges.drop(1).dropLast(1), cells)
    }

    private fun cellAction(map: TouchZoneMap, row: Int, column: Int, xCenter: Float): TouchZoneAction =
        when (map) {
            TouchZoneMap.REG_9100, TouchZoneMap.REG_975 -> TouchZoneConfig.get9ZoneTapAction(row, column, map)
            TouchZoneMap.REG_3100, TouchZoneMap.REG_375 -> TouchZoneConfig.get3ZoneFullscreenTapAction(xCenter, map)
            TouchZoneMap.REG_DOC -> TouchZoneAction.NONE
        }

    private fun isThreeZoneFallback(map: TouchZoneMap): Boolean =
        map == TouchZoneMap.REG_3100 || map == TouchZoneMap.REG_375

    @StringRes
    private fun labelFor(action: TouchZoneAction): Int? = when (action) {
        TouchZoneAction.BACK -> R.string.touch_zone_back
        TouchZoneAction.COPY -> R.string.touch_zone_copy
        TouchZoneAction.RENAME -> R.string.touch_zone_rename
        TouchZoneAction.PREVIOUS -> R.string.touch_zone_previous
        TouchZoneAction.MOVE -> R.string.touch_zone_move
        TouchZoneAction.NEXT -> R.string.touch_zone_next
        TouchZoneAction.COMMAND_PANEL -> R.string.touch_zone_command_panel
        TouchZoneAction.DELETE -> R.string.touch_zone_delete
        TouchZoneAction.SLIDESHOW -> R.string.touch_zone_slideshow
        else -> null
    }

    private companion object {
        const val LINE_ALPHA = 128
        const val TEXT_ALPHA = 180
        const val BACKGROUND_ALPHA = 40
        const val LINE_WIDTH_DP = 2f
        const val TEXT_SIZE_SP = 16f

        // Shifts the baseline so the glyphs sit visually centred on the cell centre.
        const val BASELINE_DIVISOR = 3

        // Share of a cell's width a label may take before it is shrunk to fit.
        const val LABEL_WIDTH_SHARE = 0.9f
        const val MIN_LABEL_SCALE = 0.6f
    }
}
