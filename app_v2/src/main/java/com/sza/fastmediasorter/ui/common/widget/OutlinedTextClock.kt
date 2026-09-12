package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextClock

/**
 * [TextClock] with the contour [OutlinedTextView] paints, for the launcher clock gadgets: a gadget
 * cell is transparent at rest (S1904 backdrop alpha), so the time is drawn straight onto whatever
 * wallpaper the phone carries and a theme text colour alone is no guarantee it can be read.
 *
 * A subclass rather than a converted TextView because TextClock owns the ticking: it registers its
 * own time and timezone receivers and follows the 12/24h system setting, and reimplementing that on
 * a TextView is exactly the lifecycle wiring the clock gadget was written to avoid.
 *
 * Attributes are the `otv_` set - one contour role, one styleable.
 */
class OutlinedTextClock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : TextClock(context, attrs, defStyleAttr) {

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
