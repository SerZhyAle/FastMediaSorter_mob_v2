package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * The contour settings and draw pass shared by [OutlinedTextView] and [OutlinedTextClock]: one
 * styleable, one pair of default resources, one width rule and one two-pass draw - so a launcher
 * gadget and a camera overlay cannot drift into two different-looking contours.
 *
 * Read once per view: [strokeWidth] runs inside onDraw, and a launcher desktop holds dozens of these.
 * [draw] owns the stroke-then-fill pass and [isDrawing] reports the colour swap it makes mid-draw, so
 * a view's `invalidate()` override can swallow the redraw that swap would otherwise trigger.
 */
internal class TextContour(
    val color: Int,
    private val widthPx: Float,
    private val scale: Float,
) {

    var isDrawing = false
        private set

    val isEnabled: Boolean
        get() = widthPx > 0f || scale > 0f

    /**
     * A fixed width unless the view asked for a proportional one. Autosizing text changes its size
     * between frames, so the proportional form is resolved per draw rather than in init, and the
     * fixed width acts as its floor - at the small end a percentage alone rounds down to nothing.
     */
    fun strokeWidth(textSize: Float): Float =
        if (scale > 0f) (textSize * scale).coerceAtLeast(widthPx) else widthPx

    /**
     * Draws [view] twice through [superDraw] - a contour stroke pass, then the normal fill on top -
     * or once when the contour is off. The fill colours are restored before the second pass, so the
     * view keeps whatever colour state it had before the draw.
     */
    fun draw(view: TextView, superDraw: () -> Unit) {
        val stroke = strokeWidth(view.paint.textSize)
        if (!isEnabled || stroke <= 0f) {
            superDraw()
            return
        }
        isDrawing = true
        val fillColors = view.textColors
        view.paint.style = Paint.Style.STROKE
        view.paint.strokeWidth = stroke
        view.setTextColor(color)
        superDraw()
        view.paint.style = Paint.Style.FILL
        view.setTextColor(fillColors)
        superDraw()
        isDrawing = false
    }

    companion object {
        fun read(context: Context, attrs: AttributeSet?, defStyleAttr: Int): TextContour {
            var color = ContextCompat.getColor(context, R.color.outline_text_stroke)
            var widthPx = context.resources.getDimension(R.dimen.outline_text_stroke_width)
            var scale = 0f
            context.obtainStyledAttributes(attrs, R.styleable.OutlinedTextView, defStyleAttr, 0).use { ta ->
                color = ta.getColor(R.styleable.OutlinedTextView_otv_outlineColor, color)
                widthPx = ta.getDimension(R.styleable.OutlinedTextView_otv_outlineWidth, widthPx)
                scale = ta.getFloat(R.styleable.OutlinedTextView_otv_outlineScale, scale)
            }
            Timber.d("S3251: contour read width=$widthPx scale=$scale")
            return TextContour(color, widthPx, scale)
        }
    }
}
