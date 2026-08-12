package com.sza.fastmediasorter.ui.main.helpers

/** One collapsed-panel chip offered to the planner, with the width it measured to. */
data class ChipCandidate(val viewId: Int, val widthPx: Int)

/** The planner's split: chips that go into the command bar, chips that stay in the collapsed row. */
data class ChipPlacement(val inlineIds: List<Int>, val rowIds: List<Int>)

/**
 * S1443: decides which collapsed-panel chips fit into the free tail of the main-screen command bar.
 *
 * The rule is take-while-fits over the caller's order: the first candidate that does not fit stops
 * the walk, and every candidate after it stays in the collapsed-panels row even when one of them
 * would have fitted on its own. Stopping at the first miss is what keeps the chips in a stable
 * order - a best-fit search would let a narrow chip overtake a wider neighbour on every relayout.
 *
 * Pure by contract: no Android type enters this file, so the whole decision is unit-testable.
 */
object MainCollapsedChipPlacementPlanner {

    fun plan(
        freeWidthPx: Int,
        gapPx: Int,
        reservePx: Int,
        candidates: List<ChipCandidate>
    ): ChipPlacement {
        if (freeWidthPx <= 0 || candidates.isEmpty()) {
            return ChipPlacement(emptyList(), candidates.map { it.viewId })
        }
        val inlineIds = mutableListOf<Int>()
        var consumed = 0
        for (candidate in candidates) {
            val cost = candidate.widthPx + gapPx
            if (consumed + cost + reservePx > freeWidthPx) break
            consumed += cost
            inlineIds.add(candidate.viewId)
        }
        return ChipPlacement(inlineIds, candidates.drop(inlineIds.size).map { it.viewId })
    }
}
