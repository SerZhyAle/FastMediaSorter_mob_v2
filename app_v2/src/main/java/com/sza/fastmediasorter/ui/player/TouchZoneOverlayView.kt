package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Custom View that displays semi-transparent touch zones grid overlay
 * Shows 3x3 grid with labels for each zone
 */
class TouchZoneOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    
    private val linePaint = Paint().apply {
        color = Color.WHITE
        alpha = 128 // 50% transparency
        strokeWidth = 2f * density // Scale with screen density
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        alpha = 180 // 70% transparency
        // Use TypedValue for proper SP to PX conversion
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            16f,
            context.resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 40 // ~15% transparency for subtle background
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Calculate boundaries (30%, 70%)
        val leftBoundary = width * 0.3f
        val rightBoundary = width * 0.7f
        val topBoundary = height * 0.3f
        val bottomBoundary = height * 0.7f

        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, width, height, backgroundPaint)

        // Draw vertical lines
        canvas.drawLine(leftBoundary, 0f, leftBoundary, height, linePaint)
        canvas.drawLine(rightBoundary, 0f, rightBoundary, height, linePaint)

        // Draw horizontal lines
        canvas.drawLine(0f, topBoundary, width, topBoundary, linePaint)
        canvas.drawLine(0f, bottomBoundary, width, bottomBoundary, linePaint)

        // Draw zone labels
        val zones = arrayOf(
            arrayOf("BACK", "COPY", "RENAME"),
            arrayOf("PREV", "MOVE", "NEXT"),
            arrayOf("PANEL", "DELETE", "SHOW")
        )

        val columnCenters = floatArrayOf(
            leftBoundary / 2,
            (leftBoundary + rightBoundary) / 2,
            (rightBoundary + width) / 2
        )

        val rowCenters = floatArrayOf(
            topBoundary / 2,
            (topBoundary + bottomBoundary) / 2,
            (bottomBoundary + height) / 2
        )

        for (row in 0..2) {
            for (col in 0..2) {
                canvas.drawText(
                    zones[row][col],
                    columnCenters[col],
                    rowCenters[row] + textPaint.textSize / 3, // Adjust for text baseline
                    textPaint
                )
            }
        }
    }
}
