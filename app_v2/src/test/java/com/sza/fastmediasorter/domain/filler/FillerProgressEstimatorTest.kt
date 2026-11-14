package com.sza.fastmediasorter.domain.filler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FillerProgressEstimatorTest {
    private val estimator = FillerProgressEstimator(windowMillis = WINDOW)

    @Test
    fun `no answer before any sample`() {
        assertNull(estimator.remainingMillis(PLANNED))
    }

    @Test
    fun `no answer while the observed span is too short`() {
        estimator.record(0, 0)
        estimator.record(100, 1_000)

        assertNull(estimator.remainingMillis(PLANNED))
    }

    @Test
    fun `no answer while nothing has been written`() {
        estimator.record(0, 0)
        estimator.record(0, 5_000)

        assertNull(estimator.remainingMillis(PLANNED))
    }

    @Test
    fun `steady speed gives the time for the rest`() {
        estimator.record(0, 0)
        estimator.record(300, 3_000)

        assertEquals(7_000L, estimator.remainingMillis(PLANNED))
    }

    @Test
    fun `nothing is left once the plan is written or exceeded`() {
        estimator.record(0, 0)
        estimator.record(PLANNED + 50, 5_000)

        assertEquals(0L, estimator.remainingMillis(PLANNED))
    }

    @Test
    fun `speed follows the recent window and not the whole run`() {
        estimator.record(0, 0)
        estimator.record(10_000, 10_000)
        for (second in 11..20) {
            estimator.record(10_000L + (second - 10) * 100, second * 1_000L)
        }

        assertEquals(10_000L, estimator.remainingMillis(12_000))
    }

    @Test
    fun `reset forgets the earlier run`() {
        estimator.record(0, 0)
        estimator.record(300, 3_000)
        estimator.reset()

        assertNull(estimator.remainingMillis(PLANNED))
    }

    private companion object {
        const val WINDOW = 10_000L
        const val PLANNED = 1_000L
    }
}
