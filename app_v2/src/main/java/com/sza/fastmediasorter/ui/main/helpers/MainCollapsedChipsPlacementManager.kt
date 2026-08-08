package com.sza.fastmediasorter.ui.main.helpers

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S1443: relocates the collapsed-panel chips between the command bar's free tail and the shared
 * collapsed-panels row, so a single collapsed chip no longer costs a whole row of vertical space.
 *
 * The chip itself is never rebuilt or restyled - the very same view is moved between the two hosts
 * and given the layout params that host expects, so nothing about its appearance depends on where
 * it currently lives. [MainCollapsedChipPlacementPlanner] owns the decision; this class only
 * measures, moves and reports.
 */
class MainCollapsedChipsPlacementManager(
    private val controlBar: ViewGroup,
    private val collapsedRow: ViewGroup,
    private val chips: List<View>,
    private val onPlacementChanged: () -> Unit = {}
) {

    // Captured while every chip still sits where it was inflated, so a chip returning to the row
    // lands next to the same neighbours it had before it ever moved.
    private val rowIndexById: Map<Int, Int> = chips.associate { it.id to collapsedRow.indexOfChild(it) }

    private var lastInlineIds: List<Int> = emptyList()

    /**
     * Place every chip for the given free width. Runs from a layout callback, so it returns without
     * touching a single view when the plan has not changed - an unguarded reparent would request
     * another layout pass and loop.
     */
    fun apply(freeWidthPx: Int, wideLayout: Boolean) {
        val resources = collapsedRow.resources
        val gapPx = resources.getDimensionPixelSize(R.dimen.main_panel_item_spacing)
        val reservePx = resources.getDimensionPixelSize(R.dimen.main_collapsed_chip_inline_reserve)
        val visibleChips = chips.filter { it.isVisible }
        val inlineIds = if (wideLayout && visibleChips.isNotEmpty()) {
            planInlineIds(visibleChips, freeWidthPx, gapPx, reservePx)
        } else {
            emptyList()
        }
        val rowNeeded = visibleChips.any { it.id !in inlineIds }
        if (inlineIds == lastInlineIds && collapsedRow.isVisible == rowNeeded) return
        var moved = false
        for (chip in chips) {
            if (placeChip(chip, chip.id in inlineIds, gapPx)) {
                moved = true
            }
        }
        collapsedRow.isVisible = rowNeeded
        lastInlineIds = inlineIds
        Timber.d("S1443: placement wide=$wideLayout free=$freeWidthPx inline=$inlineIds row=$rowNeeded")
        if (moved) {
            onPlacementChanged()
        }
    }

    private fun planInlineIds(
        visibleChips: List<View>,
        freeWidthPx: Int,
        gapPx: Int,
        reservePx: Int
    ): List<Int> {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(controlBar.height, View.MeasureSpec.AT_MOST)
        val candidates = visibleChips.map { chip ->
            chip.measure(widthSpec, heightSpec)
            ChipCandidate(chip.id, chip.measuredWidth)
        }
        healProbeMeasure()
        return MainCollapsedChipPlacementPlanner.plan(freeWidthPx, gapPx, reservePx, candidates).inlineIds
    }

    /**
     * S1258: a probe measure overwrites each view's measured size with its preferred one, and a
     * TextView centres its text against that stale value while its compound drawables centre
     * against the real height. Heal after the frame settles, exactly as the control bar does for
     * its own children - an inline request issued from inside a layout pass gets superseded.
     */
    private fun healProbeMeasure() {
        collapsedRow.post {
            chips.forEach { it.forceLayout() }
            collapsedRow.requestLayout()
            controlBar.requestLayout()
        }
    }

    private fun placeChip(chip: View, inline: Boolean, gapPx: Int): Boolean {
        val target: ViewGroup = if (inline) controlBar else collapsedRow
        if (chip.parent === target) return false
        (chip.parent as? ViewGroup)?.removeView(chip)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (inline) {
            params.gravity = Gravity.CENTER_VERTICAL
            params.marginStart = gapPx
            // Standing among full-height command buttons the chip's own padding no longer reaches a
            // touch target, and its label is the only thing naming it in a row of icon buttons.
            chip.minimumHeight = chip.resources.getDimensionPixelSize(R.dimen.control_button_size)
            (chip as? TextView)?.let { it.contentDescription = it.text }
            target.addView(chip, params)
        } else {
            params.marginEnd = gapPx
            chip.minimumHeight = 0
            chip.contentDescription = null
            val index = (rowIndexById[chip.id] ?: target.childCount).coerceIn(0, target.childCount)
            target.addView(chip, index, params)
        }
        return true
    }
}
