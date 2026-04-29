package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * A SeekBar that is vertical in portrait and falls back to the standard horizontal behaviour
 * in landscape — so a single layout resource works across orientations.
 *
 * Vertical mode: progress increases bottom→top (swipe UP = more).
 * onMeasure swaps w/h so the view occupies height rather than width.
 * onTouchEvent remaps touchY → seekX so drag direction is correct.
 *
 * Horizontal mode (landscape): all overrides are skipped; the view behaves exactly like
 * a stock SeekBar, which avoids any ClassCastException when landscape layouts use plain
 * SeekBar IDs while the ViewBinding field type is VerticalSeekBar.
 */
class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyle) {

    // Determined once at construction time; re-inflated on orientation change anyway.
    private val isVertical: Boolean =
        context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        if (isVertical) super.onSizeChanged(h, w, oldH, oldW) else super.onSizeChanged(w, h, oldW, oldH)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!isVertical) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // Swap specs so the view is tall instead of wide.
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isVertical) {
            super.onDraw(canvas)
            return
        }
        // Rotate canvas so the track draws bottom-to-top.
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (!isVertical) return super.onTouchEvent(event)
        // Remap: localX = (height - touchY) so bottom=0, top=max; localY = touchX (unused).
        val remapped = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            event.action,
            height - event.y,
            event.x,
            event.metaState
        )
        val handled = super.onTouchEvent(remapped)
        remapped.recycle()
        // Block the parent ScrollView from stealing the vertical drag on every touch event.
        parent?.requestDisallowInterceptTouchEvent(true)
        return handled
    }
}
