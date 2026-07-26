package com.sza.fastmediasorter.ui.settings.helpers

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.sza.fastmediasorter.ui.common.widget.SettingsGroupsGridLayout

/**
 * Installs the multi-column group layout into a settings tab (S1161).
 *
 * Settings tabs all share one shape - a scroll container wrapping a vertical stack of group cards -
 * but the cards are hand-written XML, not adapter items. Rather than editing every tab layout in two
 * orientations, this moves the existing stack children into a [SettingsGroupsGridLayout] once, from
 * a single call site in `BaseSettingsFragment`. Flavor-only tabs are covered for free.
 */
object SettingsGroupColumnsManager {

    /** Below this a tab is not a card stack, and rearranging it would be guesswork. */
    private const val MIN_GROUP_CARDS = 2

    /**
     * Wraps the group stack found under [root] and returns the installed grid, or `null` when the tab
     * does not have the expected shape and was therefore left untouched.
     *
     * Deliberately not gated on orientation: portrait correctness comes from the column count being 1,
     * so the grid is present in both orientations and rotation is a re-measure rather than a re-inflate.
     */
    fun install(root: ViewGroup): SettingsGroupsGridLayout? {
        val stack = findGroupStack(root) ?: return null
        if (countGroupCards(stack) < MIN_GROUP_CARDS) return null

        val children = ArrayList<View>(stack.childCount)
        for (i in 0 until stack.childCount) children.add(stack.getChildAt(i))

        val grid = SettingsGroupsGridLayout(stack.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            // Animates the surrounding cards into their new slots while a body expands, so the
            // re-flow and the collapse animation read as one motion instead of two jumps.
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
            }
        }

        stack.removeAllViews()
        // Original LayoutParams carry the card margins; the grid consumes them as MarginLayoutParams.
        children.forEach { child -> grid.addView(child, child.layoutParams) }
        stack.addView(grid)
        return grid
    }

    private fun findGroupStack(root: ViewGroup): ViewGroup? {
        if (isVerticalStack(root)) return root
        if (root.childCount != 1) return null
        return (root.getChildAt(0) as? ViewGroup)?.takeIf(::isVerticalStack)
    }

    private fun isVerticalStack(view: ViewGroup): Boolean =
        view is LinearLayout && view.orientation == LinearLayout.VERTICAL

    private fun countGroupCards(stack: ViewGroup): Int {
        var count = 0
        for (i in 0 until stack.childCount) {
            if (SettingsGroupsGridLayout.findGroupHeader(stack.getChildAt(i)) != null) count++
        }
        return count
    }
}
