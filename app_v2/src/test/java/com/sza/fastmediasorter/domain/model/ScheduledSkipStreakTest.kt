package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScheduledSkipStreakTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun run(at: Long, outcome: ScheduledRunOutcome) = ScheduledRunRecord(at, at + 1, outcome, 0)

    /** Newest first, as the journal returns them. */
    private fun skipsEvery(step: Long, count: Int, newestAt: Long = 1_000 * day) =
        (0 until count).map { run(newestAt - it * step, ScheduledRunOutcome.SKIPPED) }

    @Test
    fun `newest run not skipped is no streak`() {
        val journal = listOf(run(10 * day, ScheduledRunOutcome.OK)) + skipsEvery(day, 5, newestAt = 9 * day)

        assertNull(ScheduledSkipStreak.since(journal))
        assertFalse(ScheduledSkipStreak.isStalled(journal))
    }

    @Test
    fun `daily skips stall after three days`() {
        assertFalse(ScheduledSkipStreak.isStalled(skipsEvery(day, 3)))
        assertTrue(ScheduledSkipStreak.isStalled(skipsEvery(day, 4)))
    }

    @Test
    fun `short interval stalls by count`() {
        assertFalse(ScheduledSkipStreak.isStalled(skipsEvery(hour, ScheduledSkipStreak.STALL_AFTER_SKIPS - 1)))
        assertTrue(ScheduledSkipStreak.isStalled(skipsEvery(hour, ScheduledSkipStreak.STALL_AFTER_SKIPS)))
    }

    @Test
    fun `streak stops at the last run that was not skipped`() {
        val skips = skipsEvery(day, 2, newestAt = 20 * day)
        val journal = skips + run(18 * day, ScheduledRunOutcome.ERROR) + skipsEvery(day, 10, newestAt = 17 * day)

        assertEquals(19 * day, ScheduledSkipStreak.since(journal))
        assertFalse(ScheduledSkipStreak.isStalled(journal))
    }
}
