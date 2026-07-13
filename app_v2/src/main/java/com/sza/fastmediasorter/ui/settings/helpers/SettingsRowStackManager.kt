package com.sza.fastmediasorter.ui.settings.helpers

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow

/**
 * Stacks two-up settings toggle pairs vertically on narrow portrait screens.
 *
 * Portrait settings layouts keep text-heavy toggle pairs side by side; below
 * [MIN_TWO_COLUMN_WIDTH_DP] the two columns degrade into long wrapped label
 * stacks, so a single full-width column reads better. Only rows where every
 * weighted child hosts a [SettingsToggleRow] are converted - rows pairing a
 * toggle with a wrap_content action button intentionally stay horizontal.
 */
object SettingsRowStackManager {

    /** Minimum portrait width (dp) at which two-column toggle pairs remain acceptable. */
    private const val MIN_TWO_COLUMN_WIDTH_DP = 480

    /** Wrappers around a toggle are shallow; bounds the search so section columns never match. */
    private const val TOGGLE_SEARCH_MAX_DEPTH = 3

    fun stackNarrowPortraitRows(root: ViewGroup) {
        val config = root.resources.configuration
        if (config.orientation != Configuration.ORIENTATION_PORTRAIT) return
        if (config.screenWidthDp >= MIN_TWO_COLUMN_WIDTH_DP) return
        stackToggleRowsRecursively(root)
    }

    private fun stackToggleRowsRecursively(group: ViewGroup) {
        if (group is LinearLayout &&
            group.orientation == LinearLayout.HORIZONTAL &&
            isWeightedTogglePairRow(group)
        ) {
            stackRow(group)
            return
        }
        for (i in 0 until group.childCount) {
            (group.getChildAt(i) as? ViewGroup)?.let(::stackToggleRowsRecursively)
        }
    }

    private fun isWeightedTogglePairRow(row: LinearLayout): Boolean {
        if (row.childCount < 2) return false
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            val lp = child.layoutParams as? LinearLayout.LayoutParams ?: return false
            if (lp.weight <= 0f || lp.width != 0) return false
            if (!containsToggleRow(child, TOGGLE_SEARCH_MAX_DEPTH)) return false
        }
        return true
    }

    private fun containsToggleRow(view: View, depth: Int): Boolean {
        if (view is SettingsToggleRow) return true
        if (depth == 0 || view !is ViewGroup) return false
        return (0 until view.childCount).any { containsToggleRow(view.getChildAt(it), depth - 1) }
    }

    private fun stackRow(row: LinearLayout) {
        row.orientation = LinearLayout.VERTICAL
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            val lp = child.layoutParams as LinearLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.weight = 0f
            // Side margins were inter-column gutters; meaningless once stacked.
            lp.marginStart = 0
            lp.marginEnd = 0
            child.layoutParams = lp
        }
        row.requestLayout()
    }
}
