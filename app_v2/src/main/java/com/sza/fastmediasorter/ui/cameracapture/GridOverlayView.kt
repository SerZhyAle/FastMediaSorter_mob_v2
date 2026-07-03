package com.sza.fastmediasorter.ui.cameracapture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R

/**
 * Lightweight rule-of-thirds overlay for the in-app camera (S0754). Visibility is owned by the host.
 */
class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.camera_capture_control_stroke)
        strokeWidth = context.resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val thirdWidth = width / 3f
        val thirdHeight = height / 3f
        for (index in 1..2) {
            val x = thirdWidth * index
            val y = thirdHeight * index
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }
    }
}
