package com.sza.fastmediasorter.ui.main.helpers

/** One command-bar button offered to the planner, with the width it measures to in each label mode. */
data class CommandCandidate(
    val viewId: Int,
    val labelledWidthPx: Int,
    val iconOnlyWidthPx: Int
)

/** The planner's decision: the bar's label mode plus the split into kept and evicted commands. */
data class CommandBarPlan(
    val labelsVisible: Boolean,
    val visibleIds: List<Int>,
    val overflowIds: List<Int>
)

/**
 * S1672: decides how the main-screen command bar survives a row that is wider than the screen.
 *
 * Two stages, in this order. Labels come off the whole row first: they are the largest share of the
 * required width, so the wide layout usually fits again with icons alone and no command is lost.
 * Only when icon-only still does not fit are commands evicted, and they are evicted from the RIGHT
 * EDGE in layout order - owner decision 2026-08-15, which retired the 2026-07-06 rule of always
 * sacrificing one named button. No button carries a rank: an evicted command stays reachable in the
 * "⋮" menu, so there is no cost to picking the wrong victim and no nine-way ranking to maintain.
 *
 * The walk stops at the first candidate that does not fit instead of best-fitting the remainder,
 * which keeps the visible set a stable prefix - a best-fit search would let a narrow button overtake
 * a wider neighbour on every relayout and make the bar reshuffle itself.
 *
 * Pure by contract: no Android type enters this file, so the whole decision is unit-testable.
 */
object MainCommandBarPlanner {

    fun plan(
        availableWidthPx: Int,
        reservedWidthPx: Int,
        labelsPreferred: Boolean,
        candidates: List<CommandCandidate>
    ): CommandBarPlan {
        val budgetPx = availableWidthPx - reservedWidthPx
        val labelsFit = labelsPreferred && candidates.sumOf { it.labelledWidthPx } <= budgetPx
        val visibleIds = if (labelsFit) {
            candidates.map { it.viewId }
        } else {
            fittingPrefix(candidates, budgetPx)
        }
        val visible = visibleIds.toSet()
        return CommandBarPlan(
            labelsVisible = labelsFit,
            visibleIds = visibleIds,
            overflowIds = candidates.map { it.viewId }.filterNot { it in visible }
        )
    }

    private fun fittingPrefix(candidates: List<CommandCandidate>, budgetPx: Int): List<Int> {
        val fitted = mutableListOf<Int>()
        var consumedPx = 0
        for (candidate in candidates) {
            val nextPx = consumedPx + candidate.iconOnlyWidthPx
            if (nextPx > budgetPx) break
            consumedPx = nextPx
            fitted.add(candidate.viewId)
        }
        return fitted
    }
}
