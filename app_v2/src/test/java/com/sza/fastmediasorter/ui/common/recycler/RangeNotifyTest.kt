package com.sza.fastmediasorter.ui.common.recycler

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3319: the run-collapsing algorithm behind every adapter selection notification. It replaced a
 * per-item notifyItemChanged loop that produced the OpReorderer / AdapterHelper ANR frames of S3282,
 * and had no coverage across the three tickets of that family.
 */
class RangeNotifyTest {

    private fun runsOf(changed: List<Boolean>): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        forEachChangedRun(
            size = changed.size,
            isChanged = { index -> changed[index] },
            onRun = { start, length -> runs.add(start to length) }
        )
        return runs
    }

    @Test
    fun `empty list emits no run`() {
        assertEquals(emptyList<Pair<Int, Int>>(), runsOf(emptyList()))
    }

    @Test
    fun `no changed position emits no run`() {
        assertEquals(emptyList<Pair<Int, Int>>(), runsOf(listOf(false, false, false)))
    }

    @Test
    fun `every position changed collapses into a single run`() {
        assertEquals(listOf(0 to 4), runsOf(listOf(true, true, true, true)))
    }

    @Test
    fun `a run in the middle keeps its start and length`() {
        assertEquals(listOf(1 to 2), runsOf(listOf(false, true, true, false, false)))
    }

    @Test
    fun `a run touching the last index is emitted after the walk`() {
        assertEquals(listOf(2 to 2), runsOf(listOf(false, false, true, true)))
    }

    @Test
    fun `two runs separated by one unchanged position stay separate`() {
        assertEquals(listOf(0 to 2, 3 to 1), runsOf(listOf(true, true, false, true)))
    }

    @Test
    fun `alternating positions emit one run each`() {
        assertEquals(
            listOf(0 to 1, 2 to 1, 4 to 1),
            runsOf(listOf(true, false, true, false, true))
        )
    }

    @Test
    fun `a single changed position emits a run of length one`() {
        assertEquals(listOf(0 to 1), runsOf(listOf(true)))
    }
}
