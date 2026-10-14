package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearTouristCounterStateTest {

    @Test
    fun `minus one at zero leaves the state untouched`() {
        val state = WearTouristCounterState()

        assertSame(state, state.plus(-1L, NOW))
    }

    @Test
    fun `the first change stamps the session start and later ones keep it`() {
        val state = WearTouristCounterState().plus(1L, NOW).plus(LARGE_STEP, LATER)

        assertEquals(1L + LARGE_STEP, state.value)
        assertEquals(NOW, state.sessionStartedAtMillis)
    }

    @Test
    fun `the count is clamped to its ceiling`() {
        val state = WearTouristCounterState(value = WearTouristCounterState.MAX_VALUE).plus(1L, NOW)

        assertEquals(WearTouristCounterState.MAX_VALUE, state.value)
    }

    @Test
    fun `a reset archives a counted session and returns to the starting number`() {
        val state = WearTouristCounterState().withStartValue(START).plus(LARGE_STEP, NOW).reset(LATER)

        assertEquals(START, state.value)
        assertNull(state.sessionStartedAtMillis)
        assertEquals(listOf(WearTouristCounterRecord(START + LARGE_STEP, NOW, LATER)), state.history)
    }

    @Test
    fun `a reset with nothing counted archives nothing`() {
        val state = WearTouristCounterState().reset(NOW)

        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `history keeps the newest records up to its limit`() {
        var state = WearTouristCounterState()
        repeat(WearTouristCounterState.HISTORY_LIMIT + 1) { index ->
            state = state.plus(1L, index.toLong()).reset(index.toLong())
        }

        assertEquals(WearTouristCounterState.HISTORY_LIMIT, state.history.size)
        assertEquals(WearTouristCounterState.HISTORY_LIMIT.toLong(), state.history.first().endedAtMillis)
    }

    @Test
    fun `clearing history keeps the count`() {
        val state = WearTouristCounterState().plus(1L, NOW).reset(LATER).plus(1L, LATER).clearHistory()

        assertEquals(1L, state.value)
        assertTrue(state.history.isEmpty())
    }

    private companion object {
        const val NOW = 1_000L
        const val LATER = 2_000L
        const val START = 7L
        const val LARGE_STEP = 10L
    }
}
