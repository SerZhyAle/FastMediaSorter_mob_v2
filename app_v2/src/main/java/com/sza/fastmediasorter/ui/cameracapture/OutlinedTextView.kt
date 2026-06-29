package com.sza.fastmediasorter.ui.cameracapture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R

/**
 * TextView that paints a dark outline around its (white) text so camera-overlay labels stay legible
 * on both bright and dark scenes (S0753). A plain drop shadow was too faint over a white viewfinder,
 * so each label is drawn twice: a dark stroke pass, then the normal fill on top.
 */
class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val outlineColor = ContextCompat.getColor(context, R.color.camera_capture_text_shadow)
    private val outlineWidthPx = OUTLINE_WIDTH_DP * resources.displayMetrics.density
    private var drawingOutline = false

    // The stroke pass swaps the text colour, which would re-trigger a draw; swallow it mid-draw.
    override fun invalidate() {
        if (drawingOutline) return
        super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
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

    private companion object {
        const val OUTLINE_WIDTH_DP = 2f
    }
}
