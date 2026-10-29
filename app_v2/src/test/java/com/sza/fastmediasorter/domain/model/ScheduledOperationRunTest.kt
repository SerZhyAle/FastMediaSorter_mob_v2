package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.testing.createScheduledOperation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * S4211: the rules a finished run applies to its schedule - what moves the SINCE_LAST marker, and
 * where the next slot lands. Pure functions, so every case is a value in, a value out.
 */
class ScheduledOperationRunTest {

    private lateinit var originalZone: TimeZone

    @Before
    fun pinZone() {
        originalZone = TimeZone.getDefault()
        // A zone with a DST change inside the dates used below, so "03:00 stays 03:00" is a real check.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun hourOf(ms: Long): Int =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    private fun dayOf(ms: Long): Int =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_MONTH)

    // --- the marker -------------------------------------------------------------------------

    @Test
    fun `a successful run stamps its start as the marker`() {
        val op = createScheduledOperation(lastRunAt = 100L, lastRunStatus = "OK").copy(lastSuccessAt = 100L)

        val updated = op.afterRun(ScheduledRunOutcome.OK, null, startedAt = 5_000L, finishedAt = 9_000L)

        assertEquals(5_000L, updated.lastSuccessAt)
        assertEquals(9_000L, updated.lastRunAt)
        assertEquals("OK", updated.lastRunStatus)
        assertEquals(5_000L, updated.sinceLastMarkerAt)
    }

    @Test
    fun `a failed run moves the attempt but never the marker`() {
        val op = createScheduledOperation(lastRunAt = 1_500L, lastRunStatus = "OK").copy(lastSuccessAt = 1_000L)

        val updated = op.afterRun(ScheduledRunOutcome.ERROR, "disk full", startedAt = 5_000L, finishedAt = 9_000L)

        assertEquals(1_000L, updated.lastSuccessAt)
        assertEquals(9_000L, updated.lastRunAt)
        assertEquals("ERROR: disk full", updated.lastRunStatus)
        assertEquals(1_000L, updated.sinceLastMarkerAt)
    }

    @Test
    fun `a skipped run keeps the marker and says why`() {
        val op = createScheduledOperation().copy(lastSuccessAt = 1_000L)

        val updated = op.afterRun(ScheduledRunOutcome.SKIPPED, "Target 'PC' is unreachable", 5_000L, 9_000L)

        assertEquals(1_000L, updated.lastSuccessAt)
        assertEquals("SKIPPED: Target 'PC' is unreachable", updated.lastRunStatus)
        assertTrue(ScheduledRunStatus.needsAttention(updated.lastRunStatus))
    }

    @Test
    fun `a never successful schedule has no marker and stays that way after a failure`() {
        val op = createScheduledOperation()

        val updated = op.afterRun(ScheduledRunOutcome.ERROR, "boom", startedAt = 5_000L, finishedAt = 9_000L)

        assertNull(updated.lastSuccessAt)
        assertNull(updated.sinceLastMarkerAt)
    }

    @Test
    fun `a row from before last success existed keeps its OK stamp as the marker`() {
        val legacy = createScheduledOperation(lastRunAt = 2_000L, lastRunStatus = "OK")

        assertEquals(2_000L, legacy.sinceLastMarkerAt)
    }

    @Test
    fun `a legacy row seeds its success stamp before a failing run overwrites the status`() {
        val legacy = createScheduledOperation(lastRunAt = 2_000L, lastRunStatus = "OK")

        val updated = legacy.afterRun(ScheduledRunOutcome.ERROR, "boom", startedAt = 5_000L, finishedAt = 9_000L)

        assertEquals(2_000L, updated.lastSuccessAt)
        assertEquals(2_000L, updated.sinceLastMarkerAt)
    }

    @Test
    fun `a failed attempt is never promoted to a marker by its own status`() {
        val failed = createScheduledOperation(lastRunAt = 2_000L, lastRunStatus = "ERROR: x")

        assertNull(failed.sinceLastMarkerAt)
    }

    @Test
    fun `status helper separates the three families`() {
        assertEquals("OK", ScheduledRunStatus.format(ScheduledRunOutcome.OK, "ignored"))
        assertTrue(ScheduledRunStatus.needsAttention("ERROR: x"))
        assertTrue(ScheduledRunStatus.needsAttention("SKIPPED: x"))
        assertTrue(!ScheduledRunStatus.needsAttention("OK"))
        assertTrue(!ScheduledRunStatus.needsAttention(null))
        assertTrue(ScheduledRunStatus.isSkipped("SKIPPED: x"))
        assertTrue(!ScheduledRunStatus.isSkipped("ERROR: x"))
        assertTrue(!ScheduledRunStatus.isSkipped("OK"))
        assertTrue(!ScheduledRunStatus.isSkipped(null))
    }

    // --- the grid ---------------------------------------------------------------------------

    @Test
    fun `daily slot after its own run lands on the same wall clock next day`() {
        val slot = at(2026, 10, 14, 3)
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 0,
            intervalHours = 24,
            intervalMinutes = 0,
            nextRunAt = slot,
        )
        val finished = slot + 90_000L // the run started late and finished at 03:01:30

        val next = computeNextRunAfter(op, finished)

        assertEquals(at(2026, 10, 15, 3), next)
    }

    @Test
    fun `daily slot keeps 03 00 across the spring DST change`() {
        val slot = at(2026, 3, 7, 3) // the night before New York springs forward
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 0,
            intervalHours = 24,
            intervalMinutes = 0,
            nextRunAt = slot,
        )

        val next = computeNextRunAfter(op, slot + 60_000L)

        assertEquals(8, dayOf(next))
        assertEquals(3, hourOf(next))
    }

    @Test
    fun `a manual run before the slot leaves the slot where it was`() {
        val slot = at(2026, 10, 15, 3)
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 0,
            intervalHours = 24,
            intervalMinutes = 0,
            nextRunAt = slot,
        )

        val next = computeNextRunAfter(op, at(2026, 10, 14, 18))

        assertEquals(slot, next)
    }

    @Test
    fun `a weekly schedule stays on its weekday however often it is run by hand`() {
        val slot = at(2026, 10, 12, 3) // a Monday
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 0,
            intervalHours = 168,
            intervalMinutes = 0,
            nextRunAt = slot,
        )
        // Run on Thursday after the slot has passed: the grid must continue from Monday, not Thursday.
        val next = computeNextRunAfter(op, at(2026, 10, 15, 12))

        assertEquals(at(2026, 10, 19, 3), next)
    }

    @Test
    fun `a slot missed by several days jumps to the first future grid point`() {
        val slot = at(2026, 10, 1, 3)
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 0,
            intervalHours = 24,
            intervalMinutes = 0,
            nextRunAt = slot,
        )

        val next = computeNextRunAfter(op, at(2026, 10, 14, 9))

        assertEquals(at(2026, 10, 15, 3), next)
    }

    @Test
    fun `a schedule with no slot yet takes the configured anchor`() {
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 30,
            intervalHours = 24,
            intervalMinutes = 0,
            nextRunAt = null,
        )

        val next = computeNextRunAfter(op, at(2026, 10, 14, 9))

        assertEquals(at(2026, 10, 15, 3, 30), next)
    }

    @Test
    fun `a sub-day interval follows the editor preview`() {
        val op = createScheduledOperation(
            startTimeHour = 3,
            startTimeMinute = 0,
            intervalHours = 6,
            intervalMinutes = 0,
            nextRunAt = at(2026, 10, 14, 9),
        )
        val now = at(2026, 10, 14, 9, 5)

        val expected = computeNextRunAt(3, 0, 6, 0, now)

        assertEquals(expected, computeNextRunAfter(op, now))
        assertEquals(at(2026, 10, 14, 15), expected)
    }

    @Test
    fun `computeNextRunAt returns the anchor when it is still ahead`() {
        val now = at(2026, 10, 14, 1)

        assertEquals(at(2026, 10, 14, 3), computeNextRunAt(3, 0, 24, 0, now))
    }

    @Test
    fun `computeNextRunAt keeps the wall clock across DST for whole days`() {
        val now = at(2026, 3, 7, 9) // after the 03:00 anchor of that day

        val next = computeNextRunAt(3, 0, 24, 0, now)

        assertEquals(8, dayOf(next))
        assertEquals(3, hourOf(next))
    }
}
