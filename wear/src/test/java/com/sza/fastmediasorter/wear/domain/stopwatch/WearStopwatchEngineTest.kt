package com.sza.fastmediasorter.wear.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearStopwatchEngineTest {

    private val start = 10_000L

    @Test
    fun `a started participant reads the distance from its start`() {
        val state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)

        assertTrue(state.participants[0].isRunning)
        assertEquals(1_500L, state.participants[0].elapsedAt(start + 1_500L))
    }

    @Test
    fun `lap splits are distances, not totals`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)
        state = WearStopwatchEngine.startOrLap(state, 0, start + 1_000L)
        state = WearStopwatchEngine.startOrLap(state, 0, start + 2_500L)

        val laps = state.participants[0].laps
        assertEquals(2, laps.size)
        assertEquals(1_000L, laps[0].totalMillis)
        assertEquals(1_000L, laps[0].splitMillis)
        assertEquals(2_500L, laps[1].totalMillis)
        assertEquals(1_500L, laps[1].splitMillis)
    }

    @Test
    fun `stopping freezes the reading`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 3_000L)

        assertFalse(state.participants[0].isRunning)
        assertNull(state.participants[0].startedAtMillis)
        assertEquals(3_000L, state.participants[0].elapsedAt(start + 60_000L))
    }

    @Test
    fun `a second stop resets`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)
        state = WearStopwatchEngine.startOrLap(state, 0, start + 1_000L)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 3_000L)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 4_000L)

        assertTrue(state.participants[0].isPristine)
        assertEquals(0, state.participants[0].laps.size)
    }

    @Test
    fun `resetting an untouched participant changes nothing`() {
        val initial = WearStopwatchState.initial(2)
        val state = WearStopwatchEngine.stopOrReset(initial, 1, start)

        assertEquals(initial, state)
    }

    @Test
    fun `participants are independent`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(4), 0, start)
        state = WearStopwatchEngine.startOrLap(state, 2, start + 500L)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 2_000L)

        assertEquals(2_000L, state.participants[0].elapsedAt(start + 9_000L))
        assertTrue(state.participants[2].isRunning)
        assertTrue(state.participants[1].isPristine)
        assertTrue(state.participants[3].isPristine)
    }

    @Test
    fun `start all does not restart a running participant`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(2), 0, start)
        state = WearStopwatchEngine.startAll(state, start + 5_000L)

        assertEquals(start, state.participants[0].startedAtMillis)
        assertEquals(start + 5_000L, state.participants[1].startedAtMillis)
    }

    @Test
    fun `stop all leaves a stopped participant alone`() {
        var state = WearStopwatchEngine.startAll(WearStopwatchState.initial(2), start)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 1_000L)
        state = WearStopwatchEngine.stopAll(state, start + 4_000L)

        assertEquals(1_000L, state.participants[0].elapsedAt(start + 9_000L))
        assertEquals(4_000L, state.participants[1].elapsedAt(start + 9_000L))
    }

    @Test
    fun `reset all clears every participant`() {
        var state = WearStopwatchEngine.startAll(WearStopwatchState.initial(4), start)
        state = WearStopwatchEngine.startOrLap(state, 1, start + 1_000L)
        state = WearStopwatchEngine.resetAll(state)

        assertTrue(state.isPristine)
    }

    @Test
    fun `resize grows with fresh participants and shrinks from the tail`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)
        state = WearStopwatchEngine.resize(state, 4)

        assertEquals(4, state.participants.size)
        assertTrue(state.participants[0].isRunning)
        assertTrue(state.participants[3].isPristine)

        state = WearStopwatchEngine.resize(state, 2)
        assertEquals(2, state.participants.size)
        assertTrue(state.participants[0].isRunning)
    }

    @Test
    fun `an unoffered count snaps into the allowed set`() {
        val state = WearStopwatchEngine.resize(WearStopwatchState.initial(1), 3)

        assertTrue(state.participants.size in WearStopwatchState.ALLOWED_COUNTS)
    }
}
