package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
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

    // Nullable because the TextClock constructor invalidates before this field is assigned.
    private val contour: TextContour? = TextContour.read(context, attrs, defStyleAttr)

    // The stroke pass swaps the text colour, which would re-trigger a draw; swallow it mid-draw.
    override fun invalidate() {
        if (contour?.isDrawing == true) return
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val contour = this.contour
        if (contour == null) {
            super.onDraw(canvas)
            return
        }
        contour.draw(this) { super.onDraw(canvas) }
    }
}
