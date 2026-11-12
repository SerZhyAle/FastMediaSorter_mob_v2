package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import com.sza.fastmediasorter.R

/**
 * S4476: the text column of a gadget tile - a headline value (the one child with a `layout_weight`) and
 * the detail lines under it.
 *
 * A vertical `LinearLayout` measures its `wrap_content` lines first and hands the weighted child what
 * is left, so on a one-row tile of a dense desktop grid the detail lines took the whole height and the
 * headline value was drawn in a box with none of it. Here the value keeps at least
 * `launcher_gadget_value_min_height` and the trailing detail lines that would take it away are measured
 * and laid out at zero size, so the tile shows a whole value rather than a value and half a caption.
 * Lines are kept in child order and visibility stays the gadget's own: a line the gadget hid is not
 * counted, and nothing here writes `visibility`.
 *
 * The desktop grid always measures a gadget exactly (ADR-9), so the height budget is read from the
 * measure spec as given.
 */
class GadgetValueColumn @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val valueMinHeightPx = resources.getDimensionPixelSize(R.dimen.launcher_gadget_value_min_height)

    /** The lines this pass left out, so layout gives them no height and no margins. */
    private val dropped = mutableSetOf<View>()

    private val zeroSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)

    private var lastDroppedCount = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val shown = children.filter { it.visibility != GONE }.toList()
        val value = shown.firstOrNull { paramsOf(it).weight > 0f }
        val details = shown.filter { it !== value }

        val natural = IntArray(details.size) { measureNatural(details[it], widthMeasureSpec, available) }
        val kept = GadgetColumnBudget.keptLines(available, if (value == null) 0 else valueMinHeightPx, natural)

        dropped.clear()
        var used = 0
        details.forEachIndexed { index, child ->
            if (index < kept) {
                used += natural[index]
            } else {
                dropped += child
                child.measure(zeroSpec, zeroSpec)
            }
        }
        if (value != null) {
            measureValue(value, widthMeasureSpec, available - used)
        }
        if (dropped.size != lastDroppedCount) {
            lastDroppedCount = dropped.size
        }

        val widest = shown.maxOfOrNull { it.measuredWidth + paramsOf(it).leftMargin + paramsOf(it).rightMargin } ?: 0
        val contentHeight = if (value != null) available else used
        setMeasuredDimension(
            resolveSize(widest + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    private fun measureNatural(child: View, widthMeasureSpec: Int, available: Int): Int {
        val params = paramsOf(child)
        val childWidth = getChildMeasureSpec(
            widthMeasureSpec,
            paddingLeft + paddingRight + params.leftMargin + params.rightMargin,
            params.width,
        )
        val childHeight = getChildMeasureSpec(
            MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
            params.topMargin + params.bottomMargin,
            params.height,
        )
        child.measure(childWidth, childHeight)
        return child.measuredHeight + params.topMargin + params.bottomMargin
    }

    private fun measureValue(value: View, widthMeasureSpec: Int, height: Int) {
        val params = paramsOf(value)
        val childWidth = getChildMeasureSpec(
            widthMeasureSpec,
            paddingLeft + paddingRight + params.leftMargin + params.rightMargin,
            params.width,
        )
        val childHeight = (height - params.topMargin - params.bottomMargin).coerceAtLeast(0)
        value.measure(childWidth, MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var top = paddingTop
        for (child in children) {
            if (child.visibility != GONE) {
                top = layoutChild(child, top)
            }
        }
    }

    /** Places [child] under [top] and returns where the next one starts. */
    private fun layoutChild(child: View, top: Int): Int {
        if (child in dropped) {
            child.layout(paddingLeft, top, paddingLeft, top)
            return top
        }
        val params = paramsOf(child)
        val left = paddingLeft + params.leftMargin
        val childTop = top + params.topMargin
        child.layout(left, childTop, left + child.measuredWidth, childTop + child.measuredHeight)
        return childTop + child.measuredHeight + params.bottomMargin
    }

    private fun paramsOf(child: View): LinearLayout.LayoutParams = child.layoutParams as LinearLayout.LayoutParams

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams =
        LinearLayout.LayoutParams(context, attrs)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams =
        LinearLayout.LayoutParams(p)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LinearLayout.LayoutParams
}
