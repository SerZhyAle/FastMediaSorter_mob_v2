package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sza.fastmediasorter.R

/**
 * The contour settings shared by [OutlinedTextView] and [OutlinedTextClock]: one styleable, one pair
 * of default resources, one width rule - so a launcher gadget and a camera overlay cannot drift into
 * two different-looking contours.
 *
 * Read once per view: [strokeWidth] runs inside onDraw, and a launcher desktop holds dozens of these.
 */
internal class TextContour(
    val color: Int,
    private val widthPx: Float,
    private val scale: Float,
) {

    val isEnabled: Boolean
        get() = widthPx > 0f || scale > 0f

    /**
     * A fixed width unless the view asked for a proportional one. Autosizing text changes its size
     * between frames, so the proportional form is resolved per draw rather than in init, and the
     * fixed width acts as its floor - at the small end a percentage alone rounds down to nothing.
     */
    fun strokeWidth(textSize: Float): Float =
        if (scale > 0f) (textSize * scale).coerceAtLeast(widthPx) else widthPx

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
            return TextContour(color, widthPx, scale)
        }
    }
}
