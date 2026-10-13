package com.sza.fastmediasorter.domain.model.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TouristCounterStateTest {

    @Test
    fun `first change stamps the session start and later ones keep it`() {
        val state = TouristCounterState().plus(1, nowMillis = 100L).plus(5, nowMillis = 200L)

        assertEquals(6L, state.value)
        assertEquals(100L, state.sessionStartedAtMillis)
    }

    @Test
    fun `minus one at zero changes nothing`() {
        val zero = TouristCounterState()

        assertSame(zero, zero.plus(-1, nowMillis = 100L))
    }

    @Test
    fun `a step past the floor stops at zero`() {
        val state = TouristCounterState(value = 2L).plus(-5, nowMillis = 100L)

        assertEquals(0L, state.value)
    }

    @Test
    fun `reset archives a counted session newest first and returns to the start value`() {
        val state = TouristCounterState()
            .withStartValue(10L)
            .plus(3, nowMillis = 100L)
            .reset(nowMillis = 500L)
            .plus(1, nowMillis = 600L)
            .reset(nowMillis = 900L)

        assertEquals(10L, state.value)
        assertNull(state.sessionStartedAtMillis)
        assertEquals(
            listOf(TouristCounterRecord(11L, 600L, 900L), TouristCounterRecord(13L, 100L, 500L)),
            state.history,
        )
    }

    @Test
    fun `reset with nothing counted archives nothing`() {
        val state = TouristCounterState(value = 4L, startValue = 4L).reset(nowMillis = 100L)

        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `history keeps only the newest records`() {
        var state = TouristCounterState()
        repeat(TouristCounterState.HISTORY_LIMIT + 5) { index ->
            state = state.plus(1, nowMillis = index.toLong()).reset(nowMillis = index.toLong() + 1)
        }

        assertEquals(TouristCounterState.HISTORY_LIMIT, state.history.size)
        assertEquals(TouristCounterState.HISTORY_LIMIT + 4L, state.history.first().startedAtMillis)
    }

    @Test
    fun `start value sets the count and drops the open session`() {
        val state = TouristCounterState().plus(7, nowMillis = 100L).withStartValue(50L)

        assertEquals(50L, state.value)
        assertEquals(50L, state.startValue)
        assertNull(state.sessionStartedAtMillis)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `clear history keeps the running count`() {
        val state = TouristCounterState().plus(1, 1L).reset(2L).plus(2, 3L).clearHistory()

        assertTrue(state.history.isEmpty())
        assertEquals(2L, state.value)
    }
}
