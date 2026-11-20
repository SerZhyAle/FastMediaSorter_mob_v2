package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.PopupMainDropdownGridBinding
import timber.log.Timber

/**
 * Anchored grid popup whose height never leaves the system-bar/cutout safe area: when the cells are
 * taller than the space below the anchor, the grid scrolls vertically instead of running off-screen.
 * Shared by the top-bar dropdown and the programs-panel overflow so the bound logic has one copy (S3214).
 */
class BoundedGridPopupManager {

    private var activePopup: PopupWindow? = null

    fun show(
        anchor: View,
        columnCount: Int,
        equalColumns: Boolean,
        buildCells: (grid: GridLayout, dismiss: () -> Unit) -> List<View>,
    ): PopupWindow {
        dismiss()
        val context = anchor.context
        val binding = PopupMainDropdownGridBinding.inflate(LayoutInflater.from(context))
        val grid = binding.gridContainer
        grid.columnCount = columnCount
        val cells = buildCells(grid) { dismiss() }

        val popup = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(resolvePopupBackground(context))
            elevation = context.resources.getDimension(R.dimen.card_elevation)
        }

        val safe = ViewCompat.getRootWindowInsets(anchor)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val metrics = context.resources.displayMetrics

        val maxWidth = metrics.widthPixels - (safe?.left ?: 0) - (safe?.right ?: 0) - POPUP_MARGIN_PX
        val gridPadding = grid.paddingLeft + grid.paddingRight
        if (equalColumns) {
            val widest = cells.maxOfOrNull { measureCellWidth(it) } ?: 0
            // Weighted columns only stretch under an EXACTLY width, so the containers must fill the popup.
            binding.scrollView.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            grid.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            cells.forEach { cell ->
                cell.layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            popup.width = resolveGridWidth(widest, columnCount, gridPadding, maxWidth)
        } else {
            capCellsToColumnShare(cells, resolveCellMaxWidth(maxWidth, columnCount, gridPadding))
        }
        cells.forEach { grid.addView(it) }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val maxHeight = resolveMaxHeight(
            screenHeight = metrics.heightPixels,
            anchorBottom = location[1] + anchor.height,
            topInset = safe?.top ?: 0,
            bottomInset = safe?.bottom ?: 0,
        )
        binding.scrollView.post {
            if (binding.root.measuredHeight > maxHeight) {
                binding.scrollView.layoutParams = binding.scrollView.layoutParams.apply { height = maxHeight }
            }
            if (!equalColumns) trimOverflowingCells(grid, maxWidth, columnCount, gridPadding)
            Timber.d("S4796: wrapped=${cells.count { it is android.widget.TextView && it.lineCount > 1 }}")
        }

        activePopup = popup
        popup.showAsDropDown(anchor)
        return popup
    }

    fun dismiss() {
        activePopup?.dismiss()
        activePopup = null
    }

    /**
     * S1736: with unequal columns nothing bounded the row - both the popup and the grid wrap their
     * content - so a cell wider than its share of the screen was sliced by the popup edge mid-word
     * ("Быстрый перевод с камеры" read as "Быстрый перевод с камер"). Pinning the over-wide cell to its
     * share hands the cut back to the cell layout's own `ellipsize`, which says it is cutting.
     */
    private fun capCellsToColumnShare(cells: List<View>, cellMaxWidth: Int) {
        if (cellMaxWidth <= 0) return
        cells.forEach { cell ->
            if (measureCellWidth(cell) <= cellMaxWidth) return@forEach
            pinCellWidth(cell, cellMaxWidth)
        }
    }

    /**
     * S4709: the pre-layout cap above trusts a measure taken before the popup is attached. On a 360dp
     * ru screen it still left the left column wider than its share, so the right column ended past the
     * popup edge and the window cut "Запись с диктофона" mid-word with no ellipsis. The laid-out right
     * edge is the real figure: when it passes the safe width, every cell wider than its column share is
     * pinned to that share, which hands the cut back to the cell's own `ellipsize`.
     */
    private fun trimOverflowingCells(grid: GridLayout, maxWidth: Int, columnCount: Int, gridPadding: Int) {
        val laidOut = grid.children.toList()
        val rightmost = laidOut.maxOfOrNull { it.right } ?: return
        val share = resolveCellMaxWidth(maxWidth, columnCount, gridPadding)
        if (share <= 0 || resolveOverflow(rightmost, grid.paddingRight, maxWidth) == 0) return
        Timber.d("S4709: dropdown overflow trimmed, rightmost=$rightmost max=$maxWidth share=$share")
        laidOut.filter { it.width > share }.forEach { pinCellWidth(it, share) }
    }

    private fun pinCellWidth(cell: View, cellWidth: Int) {
        cell.layoutParams = GridLayout.LayoutParams().apply { width = cellWidth }
    }

    private fun measureCellWidth(cell: View): Int {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        cell.measure(unspecified, unspecified)
        val lp = cell.layoutParams as? ViewGroup.MarginLayoutParams
        return cell.measuredWidth + (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0)
    }

    private fun resolvePopupBackground(context: Context): Drawable {
        val attrs = context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.popupMenuBackground))
        val drawable = attrs.getDrawable(0)
        attrs.recycle()
        if (drawable != null) return drawable
        val surface = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, surface, true)
        return ColorDrawable(surface.data)
    }

    companion object {
        private const val POPUP_MARGIN_PX = 16
        private const val MIN_POPUP_HEIGHT_PX = 300

        /**
         * Space below the anchor, floored so a low anchor still gets a usable list (PopupWindow then
         * shifts it up), but never taller than the whole safe area - otherwise a short landscape screen
         * would get a floor it cannot hold.
         */
        internal fun resolveMaxHeight(screenHeight: Int, anchorBottom: Int, topInset: Int, bottomInset: Int): Int {
            val below = screenHeight - anchorBottom - bottomInset - POPUP_MARGIN_PX
            val safeArea = screenHeight - topInset - bottomInset - POPUP_MARGIN_PX
            return below.coerceAtLeast(MIN_POPUP_HEIGHT_PX).coerceAtMost(safeArea)
        }

        internal fun resolveGridWidth(widestCell: Int, columnCount: Int, horizontalPadding: Int, maxWidth: Int): Int =
            minOf(widestCell * columnCount + horizontalPadding, maxWidth)

        /** How far the laid-out cells reach past the safe width, counting the grid's end padding; 0 when they fit. */
        internal fun resolveOverflow(rightmostCellEdge: Int, gridPaddingEnd: Int, maxWidth: Int): Int =
            (rightmostCellEdge + gridPaddingEnd - maxWidth).coerceAtLeast(0)

        /** The width one column may take before the popup edge, not the cell's own ellipsize, does the cutting. */
        internal fun resolveCellMaxWidth(maxWidth: Int, columnCount: Int, horizontalPadding: Int): Int =
            (maxWidth - horizontalPadding) / columnCount.coerceAtLeast(1)
    }
}
