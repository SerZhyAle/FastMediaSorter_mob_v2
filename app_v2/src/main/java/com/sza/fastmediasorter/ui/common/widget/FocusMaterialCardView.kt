package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.card.MaterialCardView
import com.sza.fastmediasorter.R

/** Draws the required D-pad focus ring after MaterialCardView draws its clickable foreground. */
class FocusMaterialCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = MaterialR.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val focusRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.focus_button_stroke)
        strokeWidth = resources.getDimension(R.dimen.stroke_width_normal)
        style = Paint.Style.STROKE
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (!isFocused || width == 0 || height == 0) return

        val inset = focusRingPaint.strokeWidth / 2f
        val cornerRadius = (radius - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(
            inset,
            inset,
            width - inset,
            height - inset,
            cornerRadius,
            cornerRadius,
            focusRingPaint,
        )
    }
}
