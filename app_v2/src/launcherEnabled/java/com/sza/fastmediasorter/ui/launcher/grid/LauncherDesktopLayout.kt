package com.sza.fastmediasorter.ui.launcher.grid

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.sza.fastmediasorter.R
import kotlin.math.floor

/**
 * S0404: the desktop canvas - a fixed 2D grid of square cells, addressed by (row, column).
 *
 * Deliberately not a RecyclerView (ADR-9). The persisted model is a canvas: cells carry `rowIndex`,
 * `colIndex`, `spanW` and `spanH`, gaps between them are meaningful, and a gadget claims a rectangle
 * of cells. No stock LayoutManager expresses that - GridLayoutManager understands horizontal spans
 * only and packs items in list order, which drops positions and gaps entirely. A desktop is dozens of
 * cells, not a feed, so recycling buys nothing and costs the 2D model.
 *
 * Height is the scroll axis: this layout lives inside a vertical scroll container and measures itself
 * to `rows * cellSize` (strategic §3.3 - one screen plus downward scroll, no desktop pages).
 *
 * Knows nothing about commands, gadgets or edit mode - it measures and places whatever children it is
 * given, using their [CellLayoutParams].
 */
class LauncherDesktopLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    /**
     * The column count resolved for the current width and density factor. Guarded against no-op
     * writes: this is fed from a Flow collector, and an unconditional requestLayout() in the setter
     * would re-enter layout on every emission.
     */
    var columns: Int = LauncherGridGeometry.MIN_COLUMNS
        set(value) {
            val safe = value.coerceAtLeast(1)
            if (field == safe) return
            field = safe
            requestLayout()
        }

    /** How many rows the canvas spans - drives the measured height. */
    var rows: Int = 1
        set(value) {
            val safe = value.coerceAtLeast(1)
            if (field == safe) return
            field = safe
            requestLayout()
        }

    /** A child's place on the canvas. The binder supplies these; nothing else needs to know a cell. */
    class CellLayoutParams(
        val row: Int,
        val col: Int,
        val spanW: Int,
        val spanH: Int,
    ) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    override fun generateDefaultLayoutParams(): LayoutParams = CellLayoutParams(0, 0, 1, 1)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is CellLayoutParams

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = CellLayoutParams(0, 0, 1, 1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cellSize = cellSize(width)
        forEachCell { child, params ->
            val bounds = boundsOf(params, cellSize)
            child.measure(
                MeasureSpec.makeMeasureSpec(bounds.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bounds.height, MeasureSpec.EXACTLY),
            )
        }
        val gridHeight = paddingTop + rows * cellSize + paddingBottom
        val minHeight = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> minHeight
            MeasureSpec.AT_MOST -> gridHeight.coerceAtMost(minHeight)
            else -> gridHeight
        }
        setMeasuredDimension(width, maxOf(gridHeight, height))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellSize = cellSize(r - l)
        forEachCell { child, params ->
            val bounds = boundsOf(params, cellSize)
            val left = paddingLeft + bounds.left
            val top = paddingTop + bounds.top
            child.layout(left, top, left + bounds.width, top + bounds.height)
        }
    }

    /**
     * Maps a local pixel point to the (row, col) it falls in, clamped to the grid. A drag listener
     * attached to this view gets post-scroll local coordinates, so the caller passes them straight
     * through. The container owns this arithmetic on purpose: [cellSize] is private, and a second copy
     * at the call site is exactly the drift the phase-07 audit caught. Spans are 1x1 - a drop only needs
     * an anchor; the repository decides what happens when it is occupied.
     */
    fun cellAt(x: Float, y: Float): LauncherGridGeometry.CellFootprint {
        val cellSize = cellSize(width).coerceAtLeast(1)
        val col = ((x - paddingLeft) / cellSize).toInt()
        val row = ((y - paddingTop) / cellSize).toInt()
        return LauncherGridGeometry.footprint(row, col, spanW = 1, spanH = 1, columns = columns)
    }

    /**
     * S1466: where the last press went down, in this view's coordinates.
     *
     * Recorded here rather than by a touch listener on the container: a long-press callback is told that a
     * press happened and never where, and this is the one place every touch passes through on its way to a
     * child - including the presses that a cell goes on to consume.
     */
    var lastPressX: Float = 0f
        private set

    var lastPressY: Float = 0f
        private set

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastPressX = ev.x
            lastPressY = ev.y
        }
        // Nothing is intercepted - this only reads the point on the way past.
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * S1466: the square a press landed on, or null when it landed past the grid.
     *
     * The unclamped sibling of [cellAt], and separate from it on purpose: a drop needs an anchor and is
     * right to be pulled back onto the grid, while a press decides where a *new* cell is created, and
     * clamping there would place the item somewhere the user did not point at.
     */
    fun slotAt(x: Float, y: Float): LauncherGridGeometry.Slot? = LauncherGridGeometry.slotAt(
        // floor, not truncation: a press a fraction of a pixel left of the grid must stay negative, or
        // toInt() would round it back to 0 and report a slot for a point that is outside.
        xPx = floor(x - paddingLeft).toInt(),
        yPx = floor(y - paddingTop).toInt(),
        cellSize = cellSize(width).coerceAtLeast(1),
        columns = columns,
        rows = rows,
    )

    /** The current cell edge in px - the one authoritative size the resize gesture reads (S1093). */
    fun currentCellSize(): Int = cellSize(width)

    /** True when a screen coordinate lands on a rendered desktop cell or gadget. */
    fun hasChildAtScreenPosition(screenX: Float, screenY: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val localX = screenX - location[0]
        val localY = screenY - location[1]
        return (0 until childCount).any { index ->
            getChildAt(index).let { child ->
                child.visibility == VISIBLE &&
                    localX >= child.left && localX < child.right &&
                    localY >= child.top && localY < child.bottom
            }
        }
    }

    /** True when a screen coordinate lands on a rendered gadget, whose child controls own gestures. */
    fun hasGadgetAtScreenPosition(screenX: Float, screenY: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val localX = screenX - location[0]
        val localY = screenY - location[1]
        return (0 until childCount).any { index ->
            getChildAt(index).let { child ->
                child.id == R.id.cardLauncherGadget &&
                    child.visibility == VISIBLE &&
                    localX >= child.left && localX < child.right &&
                    localY >= child.top && localY < child.bottom
            }
        }
    }

    private fun cellSize(totalWidth: Int): Int =
        LauncherGridGeometry.cellSizePx(totalWidth - paddingLeft - paddingRight, columns)

    private fun boundsOf(params: CellLayoutParams, cellSize: Int): LauncherGridGeometry.CellBounds =
        LauncherGridGeometry.boundsOf(
            LauncherGridGeometry.footprint(params.row, params.col, params.spanW, params.spanH, columns),
            cellSize,
        )

    private inline fun forEachCell(action: (View, CellLayoutParams) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as? CellLayoutParams
            // A GONE child or a foreign LayoutParams (never expected) is simply skipped.
            if (child.visibility != GONE && params != null) action(child, params)
        }
    }
}
