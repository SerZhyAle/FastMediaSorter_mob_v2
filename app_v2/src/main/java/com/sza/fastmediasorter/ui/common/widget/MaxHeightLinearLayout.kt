package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * A [LinearLayout] that caps its measured height at [maxHeightPx]. Below the cap it shrinks to
 * content (no empty gap); above it, the layout is bounded so scrollable children scroll instead of
 * pushing the dialog off-screen.
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var heightSpec = heightMeasureSpec
        if (maxHeightPx > 0) {
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val size = MeasureSpec.getSize(heightMeasureSpec)
            // AT_MOST (not EXACTLY) so the layout still shrinks to content when it fits under the cap.
            val cap = if (mode == MeasureSpec.UNSPECIFIED) maxHeightPx else minOf(size, maxHeightPx)
            heightSpec = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
