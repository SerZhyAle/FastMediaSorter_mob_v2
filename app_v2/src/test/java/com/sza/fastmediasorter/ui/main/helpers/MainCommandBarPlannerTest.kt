package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure main-screen command-bar planner (S1672). */
class MainCommandBarPlannerTest {

    private val allIds = (1..9).toList()

    private fun bar(labelled: Int = 200, iconOnly: Int = 100): List<CommandCandidate> =
        allIds.map { CommandCandidate(viewId = it, labelledWidthPx = labelled, iconOnlyWidthPx = iconOnly) }

    @Test
    fun `labels stay on when the labelled row fits`() {
        val result = MainCommandBarPlanner.plan(
            availableWidthPx = 2000,
            reservedWidthPx = 0,
            labelsPreferred = true,
            candidates = bar()
        )
        assertTrue(result.labelsVisible)
        assertEquals(allIds, result.visibleIds)
        assertTrue(result.overflowIds.isEmpty())
    }

    @Test
    fun `labels come off before any command is evicted`() {
        // Labelled row needs 1800, icon-only needs 900: the icon rollback alone clears the shortfall.
        val result = MainCommandBarPlanner.plan(
            availableWidthPx = 1000,
            reservedWidthPx = 0,
            labelsPreferred = true,
            candidates = bar()
        )
        assertFalse(result.labelsVisible)
        assertEquals(allIds, result.visibleIds)
        assertTrue(result.overflowIds.isEmpty())
    }

    @Test
    fun `commands are evicted from the right edge when icon-only still overflows`() {
        // Budget 450 fits four 100px icons; the fifth would reach 500.
        val result = MainCommandBarPlanner.plan(
            availableWidthPx = 450,
            reservedWidthPx = 0,
            labelsPreferred = true,
            candidates = bar()
        )
        assertFalse(result.labelsVisible)
        assertEquals(listOf(1, 2, 3, 4), result.visibleIds)
        assertEquals(listOf(5, 6, 7, 8, 9), result.overflowIds)
    }

    @Test
    fun `a narrower budget evicts strictly more commands`() {
        val wide = MainCommandBarPlanner.plan(450, 0, true, bar())
        val narrow = MainCommandBarPlanner.plan(250, 0, true, bar())
        assertEquals(listOf(1, 2), narrow.visibleIds)
        assertTrue(narrow.overflowIds.size > wide.overflowIds.size)
    }

    @Test
    fun `narrow layout never turns labels on`() {
        val result = MainCommandBarPlanner.plan(
            availableWidthPx = 5000,
            reservedWidthPx = 0,
            labelsPreferred = false,
            candidates = bar()
        )
        assertFalse(result.labelsVisible)
        assertEquals(allIds, result.visibleIds)
        assertTrue(result.overflowIds.isEmpty())
    }

    @Test
    fun `a reserved anchor that swallows the budget evicts every command`() {
        val result = MainCommandBarPlanner.plan(
            availableWidthPx = 300,
            reservedWidthPx = 300,
            labelsPreferred = true,
            candidates = bar()
        )
        assertFalse(result.labelsVisible)
        assertTrue(result.visibleIds.isEmpty())
        assertEquals(allIds, result.overflowIds)
    }

    @Test
    fun `an empty candidate list yields empty buckets`() {
        val result = MainCommandBarPlanner.plan(
            availableWidthPx = 1000,
            reservedWidthPx = 0,
            labelsPreferred = true,
            candidates = emptyList()
        )
        assertTrue(result.labelsVisible)
        assertTrue(result.visibleIds.isEmpty())
        assertTrue(result.overflowIds.isEmpty())
    }
}
