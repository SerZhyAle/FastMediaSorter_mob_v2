package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * A [SwipeRefreshLayout] that is as tall as its [RecyclerView] when the parent does not fix its height.
 *
 * The stock layout always measures its target `EXACTLY` at its own measured size and takes the whole size the
 * parent offers, so a `wrap_content` list inside it never shrinks to its rows. Here the target is measured
 * first with the offered bound, and the layout then lets the stock measure run at that wrapped height; with an
 * exact height, or without a [RecyclerView] child, it is the stock layout untouched.
 */
class WrapContentSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val target = children.firstOrNull { it is RecyclerView }
        if (target == null || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom
        target.measure(
            ViewGroup.getChildMeasureSpec(widthMeasureSpec, horizontalPadding, ViewGroup.LayoutParams.MATCH_PARENT),
            ViewGroup.getChildMeasureSpec(heightMeasureSpec, verticalPadding, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val wrappedHeight = MeasureSpec.makeMeasureSpec(target.measuredHeight + verticalPadding, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, wrappedHeight)
    }
}
