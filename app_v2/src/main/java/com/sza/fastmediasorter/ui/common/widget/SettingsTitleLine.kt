package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import timber.log.Timber

/**
 * Horizontal line holding a settings row caption and the fixed-size glyphs beside it - leading icon,
 * help icon (S4711).
 *
 * The caption is `wrap_content` and measured first, so a caption longer than its line used to take
 * the whole line and lay the help icon out past the row's end, where it was clipped. Here the caption
 * is the one child that yields: its width is capped to what the other children leave, so it wraps (or
 * ellipsizes, in a label column) and the icon stays visible. The caption never gets `layout_weight` -
 * the icon must keep sitting right after it ("Caption and Value Proximity", docs/ARCHITECTURE.md).
 *
 * The cap is written into the caption's `LayoutParams.width` field before `super.onMeasure`, never
 * through `TextView.maxWidth` or `setLayoutParams`: both request another layout pass from inside
 * `onMeasure`, and resetting them on every pass loops the traversal.
 */
class SettingsTitleLine @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val caption = firstCaption()
        if (caption != null && orientation == HORIZONTAL) {
            capCaption(caption, widthMeasureSpec)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun firstCaption(): TextView? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView && child.visibility != View.GONE) return child
        }
        return null
    }

    private fun capCaption(caption: TextView, widthMeasureSpec: Int) {
        val params = caption.layoutParams as LayoutParams
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            params.width = LayoutParams.WRAP_CONTENT
            return
        }
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var available = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd -
            params.marginStart - params.marginEnd
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val childParams = child.layoutParams as LayoutParams
            val skip = child === caption || child.visibility == View.GONE || childParams.weight > 0f
            if (!skip) available -= fixedWidth(child, childParams, unbounded)
        }
        caption.measure(unbounded, unbounded)
        val natural = caption.measuredWidth
        params.width = LayoutParams.WRAP_CONTENT
        if (natural > available) {
            params.width = available.coerceAtLeast(0)
            Timber.d("S4711: SettingsTitleLine caps caption %d -> %d px", natural, params.width)
        }
    }

    private fun fixedWidth(child: View, params: LayoutParams, unbounded: Int): Int {
        val width = if (params.width >= 0) {
            params.width
        } else {
            child.measure(unbounded, unbounded)
            child.measuredWidth
        }
        return width + params.marginStart + params.marginEnd
    }
}
