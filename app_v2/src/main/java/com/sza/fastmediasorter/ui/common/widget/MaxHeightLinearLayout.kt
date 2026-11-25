package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * A [LinearLayout] that caps its measured height at [maxHeightPx] and/or at [maxHeightFraction] of the
 * height its parent offers. Below the cap it shrinks to content (no empty gap); above it, the layout is
 * bounded so scrollable children scroll instead of pushing the dialog off-screen. With both caps set the
 * lower one wins; with neither set the layout measures like a plain [LinearLayout].
 */
class MaxHeightLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var maxHeightPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** Share of the offered height the layout may take, `0f` = no fraction cap. Needs a bounded parent spec. */
    var maxHeightFraction: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var heightSpec = heightMeasureSpec
        if (maxHeightPx > 0 || maxHeightFraction > 0f) {
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val size = MeasureSpec.getSize(heightMeasureSpec)
            var cap = if (mode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else size
            if (maxHeightPx > 0) cap = minOf(cap, maxHeightPx)
            // An unbounded offer has no height to take a share of, so the fraction waits for a bounded one.
            if (maxHeightFraction > 0f && mode != MeasureSpec.UNSPECIFIED) {
                cap = minOf(cap, (size * maxHeightFraction).toInt())
            }
            // AT_MOST (not EXACTLY) so the layout still shrinks to content when it fits under the cap.
            if (cap != Int.MAX_VALUE) {
                heightSpec = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
            }
        }
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
