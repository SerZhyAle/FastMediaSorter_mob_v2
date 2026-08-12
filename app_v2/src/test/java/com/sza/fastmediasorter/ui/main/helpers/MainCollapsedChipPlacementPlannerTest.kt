package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MainCollapsedChipPlacementPlanner.plan] - the pure take-while-fits decision behind
 * S1443. The planner is the only place that decides which collapsed-panel chips climb into the
 * command bar, so the order invariant and the stop-at-first-miss rule are proven here rather than on
 * a device.
 */
class MainCollapsedChipPlacementPlannerTest {

    private val programs = ChipCandidate(viewId = 1, widthPx = 100)
    private val streams = ChipCandidate(viewId = 2, widthPx = 300)
    private val filter = ChipCandidate(viewId = 3, widthPx = 50)

    private fun plan(freeWidthPx: Int, candidates: List<ChipCandidate>): ChipPlacement =
        MainCollapsedChipPlacementPlanner.plan(
            freeWidthPx = freeWidthPx,
            gapPx = GAP,
            reservePx = RESERVE,
            candidates = candidates
        )

    @Test
    fun `zero free width keeps every candidate in the row`() {
        val placement = plan(0, listOf(programs, streams, filter))
        assertEquals(emptyList<Int>(), placement.inlineIds)
        assertEquals(listOf(1, 2, 3), placement.rowIds)
    }

    @Test
    fun `negative free width keeps every candidate in the row`() {
        val placement = plan(-40, listOf(programs, streams, filter))
        assertEquals(emptyList<Int>(), placement.inlineIds)
        assertEquals(listOf(1, 2, 3), placement.rowIds)
    }

    @Test
    fun `empty candidate list returns two empty lists`() {
        val placement = plan(1000, emptyList())
        assertEquals(emptyList<Int>(), placement.inlineIds)
        assertEquals(emptyList<Int>(), placement.rowIds)
    }

    @Test
    fun `a budget that fits all three keeps the input order inline`() {
        val placement = plan(500, listOf(programs, streams, filter))
        assertEquals(listOf(1, 2, 3), placement.inlineIds)
        assertEquals(emptyList<Int>(), placement.rowIds)
    }

    @Test
    fun `a budget that fits only the first leaves the rest in the row in order`() {
        val wideFilter = ChipCandidate(viewId = 3, widthPx = 400)
        val placement = plan(200, listOf(programs, streams, wideFilter))
        assertEquals(listOf(1), placement.inlineIds)
        assertEquals(listOf(2, 3), placement.rowIds)
    }

    @Test
    fun `the walk stops at the first miss even when a later candidate would fit alone`() {
        // filter costs 50+2+16=68 on top of the 102 programs consumed, so it fits in 200 by itself;
        // streams does not, and the stop-at-first-miss rule must keep filter out anyway.
        val placement = plan(200, listOf(programs, streams, filter))
        assertEquals(listOf(1), placement.inlineIds)
        assertEquals(listOf(2, 3), placement.rowIds)
    }

    @Test
    fun `the two-candidate lite shape follows the same rule`() {
        val placement = plan(200, listOf(programs, streams))
        assertEquals(listOf(1), placement.inlineIds)
        assertEquals(listOf(2), placement.rowIds)
    }

    @Test
    fun `a budget exactly on the boundary accepts and one pixel below rejects`() {
        val exact = programs.widthPx + GAP + RESERVE
        assertEquals(listOf(1), plan(exact, listOf(programs)).inlineIds)
        assertEquals(listOf(1), plan(exact - 1, listOf(programs)).rowIds)
    }

    private companion object {
        const val GAP = 2
        const val RESERVE = 16
    }
}
