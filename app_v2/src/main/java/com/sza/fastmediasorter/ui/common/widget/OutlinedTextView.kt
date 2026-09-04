package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView that paints a contour around its text so a label stays legible over a background it knows
 * nothing about - a bright photo, a dark one, or a busy one (S0753 camera overlays, S1173 launcher
 * cells). A plain drop shadow was too faint over a white viewfinder, so each label is drawn twice: a
 * contour stroke pass, then the normal fill on top.
 *
 * The default width suits large overlay labels. A small caption must pass its own `otv_outlineWidth`,
 * or the contour thickens the glyph into a blob; passing zero switches the contour off entirely. Text
 * whose size is not fixed - an autosizing gadget value - passes `otv_outlineScale` instead, so the
 * contour tracks the size the view settled on.
 */
class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val contour = TextContour.read(context, attrs, defStyleAttr)
    private var drawingOutline = false

    // The stroke pass swaps the text colour, which would re-trigger a draw; swallow it mid-draw.
    override fun invalidate() {
        if (drawingOutline) return
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val strokeWidth = contour.strokeWidth(paint.textSize)
        if (!contour.isEnabled || strokeWidth <= 0f) {
            super.onDraw(canvas)
            return
        }
        drawingOutline = true
        val fillColors = textColors
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        setTextColor(contour.color)
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        setTextColor(fillColors)
        super.onDraw(canvas)
        drawingOutline = false
    }
}
