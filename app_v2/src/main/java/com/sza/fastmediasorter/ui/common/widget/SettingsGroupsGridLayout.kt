package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.sza.fastmediasorter.R

/**
 * Lays settings group cards out in [R.integer.settings_group_columns] columns (S1161).
 *
 * A collapsed group card takes one column; an expanded one - or any child that is not a group card -
 * spans the whole row. The span is read from the live view tree during measure instead of being
 * pushed in by a listener, so this layout never competes for
 * [CollapsibleSectionHeader.setOnExpandedChangeListener], which holds a single listener already
 * owned by `CollapsibleSectionsManager`.
 *
 * The column count is re-read on every measure pass, which is what makes rotation free: the settings
 * screen absorbs configuration changes itself, so `layout-land` never re-applies and a plain
 * `requestLayout()` is enough to pick up the new value.
 */
class SettingsGroupsGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    /** Measured placements replayed in [onLayout]; reused across passes to avoid per-frame garbage. */
    private val placements = ArrayList<Placement>()

    private val visibleChildren = ArrayList<View>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = (widthSize - paddingLeft - paddingRight).coerceAtLeast(0)
        val columns = resolveColumns(contentWidth)
        val columnWidths = splitWidth(contentWidth, columns)

        placements.clear()
        collectVisibleChildren()

        var bottom = paddingTop
        var index = 0
        while (index < visibleChildren.size) {
            val child = visibleChildren[index]
            if (columns == 1 || isFullSpan(child)) {
                bottom = placeFullSpan(child, bottom, contentWidth)
                index++
            } else {
                val runEnd = findRunEnd(index)
                bottom = placeRun(index, runEnd, bottom, columnWidths)
                index = runEnd
            }
        }

        val height = resolveSize(bottom + paddingBottom, heightMeasureSpec)
        setMeasuredDimension(resolveSize(widthSize, widthMeasureSpec), height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        placements.forEach { placement ->
            placement.child.layout(
                placement.left,
                placement.top,
                placement.left + placement.child.measuredWidth,
                placement.top + placement.child.measuredHeight,
            )
        }
    }

    private fun collectVisibleChildren() {
        visibleChildren.clear()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) visibleChildren.add(child)
        }
    }

    /**
     * Requested column count, vetoed by width: a column narrower than
     * [R.dimen.settings_group_min_column_width] cannot hold a settings card's own rows, and letting
     * the measured width decide beats enumerating which small landscape phones are safe.
     */
    private fun resolveColumns(contentWidth: Int): Int {
        val requested = resources.getInteger(R.integer.settings_group_columns).coerceAtLeast(1)
        if (requested == 1 || contentWidth == 0) return 1
        val minColumnWidth = resources.getDimensionPixelSize(R.dimen.settings_group_min_column_width)
        return if (contentWidth / requested < minColumnWidth) 1 else requested
    }

    /** Splits [contentWidth] evenly, handing the rounding remainder to the last column. */
    private fun splitWidth(contentWidth: Int, columns: Int): IntArray {
        val base = contentWidth / columns
        return IntArray(columns) { index ->
            if (index == columns - 1) contentWidth - base * (columns - 1) else base
        }
    }

    /** Index one past the last consecutive single-span child starting at [start]. */
    private fun findRunEnd(start: Int): Int {
        var end = start
        while (end < visibleChildren.size && !isFullSpan(visibleChildren[end])) end++
        return end
    }

    private fun placeFullSpan(child: View, top: Int, contentWidth: Int): Int {
        val params = child.layoutParams as MarginLayoutParams
        measureChildToWidth(child, contentWidth - params.leftMargin - params.rightMargin)
        placements.add(Placement(child, paddingLeft + params.leftMargin, top + params.topMargin))
        return top + params.topMargin + child.measuredHeight + params.bottomMargin
    }

    /**
     * Places `visibleChildren[start until end]` column-major: reading straight down the left column
     * follows the original order, and the left column is the longer one when the count is odd - the
     * same balanced split the multi-column row layout inside a group already uses.
     */
    private fun placeRun(start: Int, end: Int, top: Int, columnWidths: IntArray): Int {
        val columns = columnWidths.size
        val columnSizes = balancedColumnSizes(end - start, columns)
        val columnStarts = IntArray(columns)
        var offset = start
        for (column in 0 until columns) {
            columnStarts[column] = offset
            offset += columnSizes[column]
        }

        var rowTop = top
        val rowCount = columnSizes.max()
        for (row in 0 until rowCount) {
            var rowHeight = 0
            var left = paddingLeft
            for (column in 0 until columns) {
                if (row < columnSizes[column]) {
                    val child = visibleChildren[columnStarts[column] + row]
                    val params = child.layoutParams as MarginLayoutParams
                    measureChildToWidth(child, columnWidths[column] - params.leftMargin - params.rightMargin)
                    placements.add(Placement(child, left + params.leftMargin, rowTop + params.topMargin))
                    val outerHeight = params.topMargin + child.measuredHeight + params.bottomMargin
                    if (outerHeight > rowHeight) rowHeight = outerHeight
                }
                left += columnWidths[column]
            }
            rowTop += rowHeight
        }
        return rowTop
    }

    /** Column sizes for [count] items over [columns] columns, front-loaded so column 0 is longest. */
    private fun balancedColumnSizes(count: Int, columns: Int): IntArray {
        val sizes = IntArray(columns)
        var remaining = count
        for (column in 0 until columns) {
            val columnsLeft = columns - column
            val take = (remaining + columnsLeft - 1) / columnsLeft
            sizes[column] = take
            remaining -= take
        }
        return sizes
    }

    private fun measureChildToWidth(child: View, availableWidth: Int) {
        child.measure(
            MeasureSpec.makeMeasureSpec(availableWidth.coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
    }

    /** A child spans every column unless it is a group card whose header is collapsed. */
    private fun isFullSpan(child: View): Boolean {
        val header = findGroupHeader(child) ?: return true
        return header.isExpanded()
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    private class Placement(val child: View, val left: Int, val top: Int)

    companion object {
        /** A card nests its header two levels down; this stops the search entering section content. */
        private const val HEADER_SEARCH_MAX_DEPTH = 3

        /**
         * Returns the group header owned by [view], or `null` when [view] is not a group card.
         *
         * Shared with the installer so "what counts as a group card" has exactly one definition.
         */
        fun findGroupHeader(
            view: View,
            depth: Int = HEADER_SEARCH_MAX_DEPTH,
        ): CollapsibleSectionHeader? {
            if (view is CollapsibleSectionHeader) return view
            if (depth == 0 || view !is ViewGroup) return null
            for (i in 0 until view.childCount) {
                findGroupHeader(view.getChildAt(i), depth - 1)?.let { return it }
            }
            return null
        }
    }
}
