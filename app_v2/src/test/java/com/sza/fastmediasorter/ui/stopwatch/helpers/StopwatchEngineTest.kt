package com.sza.fastmediasorter.ui.stopwatch.helpers

import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1411 phase 01 - the timing arithmetic and the independence between participants. */
class StopwatchEngineTest {

    private val initial = StopwatchScreenState()

    @Test
    fun `start then stop accumulates the measured segment`() {
        var state = StopwatchEngine.start(
            initial,
            participantId = 0,
            nowMillis = 1_000L,
            startedAtEpochMillis = 10_000L,
        )
        state = StopwatchEngine.stop(state, participantId = 0, nowMillis = 3_500L)

        assertEquals(2_500L, state.participants[0].accumulatedMillis)
        assertFalse(state.participants[0].running)
    }

    @Test
    fun `a pause between two runs is not counted`() {
        var state = StopwatchEngine.start(
            initial,
            participantId = 0,
            nowMillis = 0L,
            startedAtEpochMillis = 10_000L,
        )
        state = StopwatchEngine.stop(state, participantId = 0, nowMillis = 1_000L)
        // An hour passes while stopped.
        state = StopwatchEngine.start(
            state,
            participantId = 0,
            nowMillis = 3_601_000L,
            startedAtEpochMillis = 3_611_000L,
        )
        state = StopwatchEngine.stop(state, participantId = 0, nowMillis = 3_602_000L)

        assertEquals(2_000L, state.participants[0].accumulatedMillis)
    }

    @Test
    fun `elapsed is a difference of marks and not a sum of ticks`() {
        val state = StopwatchEngine.start(
            initial,
            participantId = 0,
            nowMillis = 0L,
            startedAtEpochMillis = 10_000L,
        )

        // No tick ever happened between the two readings; the hour is still measured.
        assertEquals(3_600_000L, state.participants[0].elapsedAt(3_600_000L))
    }

    @Test
    fun `start stores the wall-clock stamp and a stop keeps it`() {
        var state = StopwatchEngine.start(
            initial,
            participantId = 0,
            nowMillis = 500L,
            startedAtEpochMillis = 1_700_000_000_000L,
        )
        state = StopwatchEngine.stop(state, participantId = 0, nowMillis = 900L)

        // The result text reports when the measurement was performed, not when it was halted (S2792).
        assertEquals(1_700_000_000_000L, state.participants[0].startedAtEpochMillis)
    }

    @Test
    fun `reset clears the wall-clock stamp`() {
        var state = StopwatchEngine.start(
            initial,
            participantId = 0,
            nowMillis = 0L,
            startedAtEpochMillis = 10_000L,
        )
        state = StopwatchEngine.reset(state, participantId = 0)

        assertNull(state.participants[0].startedAtEpochMillis)
    }

    @Test
    fun `lap records the elapsed value and leaves the count running`() {
        var state = StopwatchEngine.start(
            initial,
            participantId = 0,
            nowMillis = 100L,
            startedAtEpochMillis = 10_000L,
        )
        state = StopwatchEngine.lap(state, participantId = 0, nowMillis = 700L)
        state = StopwatchEngine.lap(state, participantId = 0, nowMillis = 1_300L)

        val laps = state.participants[0].laps
        assertEquals(listOf(1, 2), laps.map { it.ordinal })
        assertEquals(listOf(600L, 1_200L), laps.map { it.atElapsedMillis })
        assertTrue(state.participants[0].running)
    }

    @Test
    fun `a stopped participant records no lap`() {
        val state = StopwatchEngine.lap(initial, participantId = 0, nowMillis = 500L)

        assertTrue(state.participants[0].laps.isEmpty())
    }

    @Test
    fun `starting one participant leaves the others byte-identical`() {
        val state = StopwatchEngine.start(
            initial,
            participantId = 1,
            nowMillis = 1_000L,
            startedAtEpochMillis = 10_000L,
        )

        assertEquals(initial.participants[0], state.participants[0])
        assertEquals(initial.participants[2], state.participants[2])
        assertEquals(initial.participants[3], state.participants[3])
        assertTrue(state.participants[1].running)
    }

    @Test
    fun `stopping one of four does not disturb the three still running`() {
        var state = initial
        for (id in 0 until StopwatchScreenState.MAX_PARTICIPANTS) {
            state = StopwatchEngine.start(state, participantId = id, nowMillis = 0L, startedAtEpochMillis = 10_000L)
        }
        state = StopwatchEngine.stop(state, participantId = 2, nowMillis = 5_000L)

        assertEquals(listOf(true, true, false, true), state.participants.map { it.running })
        assertEquals(5_000L, state.participants[2].accumulatedMillis)
        assertEquals(9_000L, state.participants[3].elapsedAt(9_000L))
    }

    @Test
    fun `reset clears one participant and resetAll clears every one`() {
        var state = StopwatchEngine.start(initial, participantId = 0, nowMillis = 0L, startedAtEpochMillis = 10_000L)
        state = StopwatchEngine.start(state, participantId = 1, nowMillis = 0L, startedAtEpochMillis = 10_000L)
        state = StopwatchEngine.stop(state, participantId = 0, nowMillis = 4_000L)

        val afterOne = StopwatchEngine.reset(state, participantId = 0)
        assertEquals(0L, afterOne.participants[0].accumulatedMillis)
        assertTrue(afterOne.participants[1].running)

        val afterAll = StopwatchEngine.resetAll(state)
        assertTrue(afterAll.participants.none { it.hasProgress })
    }

    @Test
    fun `startAll starts only the not-running visible participants`() {
        var state = StopwatchEngine.withParticipantCount(initial, 2)
        state = StopwatchEngine.start(state, participantId = 0, nowMillis = 0L, startedAtEpochMillis = 5L)

        val after = StopwatchEngine.startAll(state, nowMillis = 1_000L, startedAtEpochMillis = 2_000L)

        assertEquals(listOf(true, true, false, false), after.participants.map { it.running })
        // Participant 0 was already running, so a repeated Start all keeps its original marks.
        assertEquals(0L, after.participants[0].startMark)
        assertEquals(5L, after.participants[0].startedAtEpochMillis)
        assertEquals(1_000L, after.participants[1].startMark)
        assertEquals(2_000L, after.participants[1].startedAtEpochMillis)
    }

    @Test
    fun `stopAll stops only the running visible participants`() {
        var state = StopwatchEngine.withParticipantCount(initial, 2)
        state = StopwatchEngine.start(state, participantId = 0, nowMillis = 0L, startedAtEpochMillis = 5L)
        state = StopwatchEngine.start(state, participantId = 2, nowMillis = 0L, startedAtEpochMillis = 6L)

        val after = StopwatchEngine.stopAll(state, nowMillis = 4_000L)

        assertEquals(listOf(false, false, true, false), after.participants.map { it.running })
        assertEquals(4_000L, after.participants[0].accumulatedMillis)
        assertEquals(5L, after.participants[0].startedAtEpochMillis)
        // Hidden participant 2 runs on untouched.
        assertEquals(0L, after.participants[2].accumulatedMillis)
    }

    @Test
    fun `an unsupported participant count is coerced to a supported one`() {
        assertEquals(1, StopwatchEngine.withParticipantCount(initial, 0).participantCount)
        assertEquals(1, StopwatchEngine.withParticipantCount(initial, 1).participantCount)
        assertEquals(2, StopwatchEngine.withParticipantCount(initial, 3).participantCount)
        assertEquals(4, StopwatchEngine.withParticipantCount(initial, 5).participantCount)
        assertEquals(4, StopwatchEngine.withParticipantCount(initial, 99).participantCount)
    }

    @Test
    fun `changing the count keeps the hidden measurements`() {
        var state = StopwatchEngine.withParticipantCount(initial, 4)
        state = StopwatchEngine.start(state, participantId = 3, nowMillis = 0L, startedAtEpochMillis = 10_000L)
        state = StopwatchEngine.stop(state, participantId = 3, nowMillis = 8_000L)

        val narrowed = StopwatchEngine.withParticipantCount(state, 1)
        assertEquals(1, narrowed.visibleParticipants.size)
        assertEquals(8_000L, narrowed.participants[3].accumulatedMillis)
    }
}
