package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.sza.fastmediasorter.R

/**
 * S0924: lays out its children in [columnCount] vertical columns, packing each next child into the
 * currently shortest column (height-balanced greedy fill). One column reproduces a plain vertical
 * stack; the camera settings dialog switches to two columns in landscape to halve content height.
 *
 * Honors each child's vertical margins for inter-item spacing and inserts a fixed horizontal gap
 * between columns. Child `layout_width` is ignored - every child is measured at the resolved column
 * width so match_parent rows fill their column.
 *
 * The initial column count may come from `cfl_columnCount`, so a landscape-only layout can ask for
 * two columns without a code hook. Container padding is honored.
 */
class ColumnFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    var columnCount: Int = 1
        set(value) {
            val clamped = value.coerceAtLeast(1)
            if (field != clamped) {
                field = clamped
                requestLayout()
            }
        }

    private val columnGap = resources.getDimensionPixelSize(R.dimen.margin_small)

    // Per-child placement resolved during measure and reused verbatim in layout (column -1 == GONE).
    private var childColumn = IntArray(0)
    private var childLeft = IntArray(0)
    private var childTop = IntArray(0)

    init {
        val array = context.obtainStyledAttributes(attrs, R.styleable.ColumnFlowLayout, defStyleAttr, 0)
        try {
            columnCount = array.getInt(R.styleable.ColumnFlowLayout_cfl_columnCount, 1)
        } finally {
            // TypedArray is AutoCloseable only from API 31, so `use` would crash on minSdk 23/26.
            array.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cols = columnCount
        val totalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = (totalWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        val columnWidth = ((contentWidth - columnGap * (cols - 1)) / cols).coerceAtLeast(0)

        if (childColumn.size != childCount) {
            childColumn = IntArray(childCount)
            childLeft = IntArray(childCount)
            childTop = IntArray(childCount)
        }
        val columnHeights = IntArray(cols)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) {
                childColumn[i] = -1
                continue
            }
            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = (columnWidth - lp.leftMargin - lp.rightMargin).coerceAtLeast(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val target = shortestColumn(columnHeights)
            val left = paddingLeft + target * (columnWidth + columnGap) + lp.leftMargin
            val top = paddingTop + columnHeights[target] + lp.topMargin
            childColumn[i] = target
            childLeft[i] = left
            childTop[i] = top
            columnHeights[target] = columnHeights[target] + lp.topMargin + child.measuredHeight + lp.bottomMargin
        }
        val contentHeight = (columnHeights.maxOrNull() ?: 0) + paddingTop + paddingBottom
        setMeasuredDimension(totalWidth, resolveSize(contentHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            if (childColumn[i] < 0) continue
            val child = getChildAt(i)
            child.layout(
                childLeft[i],
                childTop[i],
                childLeft[i] + child.measuredWidth,
                childTop[i] + child.measuredHeight,
            )
        }
    }

    private fun shortestColumn(heights: IntArray): Int {
        var idx = 0
        for (i in 1 until heights.size) {
            if (heights[i] < heights[idx]) idx = i
        }
        return idx
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
}
