package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sza.fastmediasorter.R

/**
 * TextView that paints a contour around its text so a label stays legible over a background it knows
 * nothing about - a bright photo, a dark one, or a busy one (S0753 camera overlays, S1173 launcher
 * cells). A plain drop shadow was too faint over a white viewfinder, so each label is drawn twice: a
 * contour stroke pass, then the normal fill on top.
 *
 * The default width suits large overlay labels. A small caption must pass its own `otv_outlineWidth`,
 * or the contour thickens the glyph into a blob; passing zero switches the contour off entirely.
 */
class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val outlineColor: Int
    private val outlineWidthPx: Float
    private var drawingOutline = false

    init {
        // Resolved once: onDraw runs per frame, and a launcher desktop holds dozens of these.
        var color = ContextCompat.getColor(context, R.color.outline_text_stroke)
        var widthPx = resources.getDimension(R.dimen.outline_text_stroke_width)
        context.obtainStyledAttributes(attrs, R.styleable.OutlinedTextView, defStyleAttr, 0).use { ta ->
            color = ta.getColor(R.styleable.OutlinedTextView_otv_outlineColor, color)
            widthPx = ta.getDimension(R.styleable.OutlinedTextView_otv_outlineWidth, widthPx)
        }
        outlineColor = color
        outlineWidthPx = widthPx
    }

    // The stroke pass swaps the text colour, which would re-trigger a draw; swallow it mid-draw.
    override fun invalidate() {
        if (drawingOutline) return
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (outlineWidthPx <= 0f) {
            super.onDraw(canvas)
            return
        }
        drawingOutline = true
        val fillColors = textColors
        val p = paint
        p.style = Paint.Style.STROKE
        p.strokeWidth = outlineWidthPx
        setTextColor(outlineColor)
        super.onDraw(canvas)
        p.style = Paint.Style.FILL
        setTextColor(fillColors)
        super.onDraw(canvas)
        drawingOutline = false
    }
}
