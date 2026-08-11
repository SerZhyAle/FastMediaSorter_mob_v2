package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamStallRecoveryWindowTest {

    @Test
    fun `three recoveries in the window exhaust the budget`() {
        val window = StreamStallRecoveryWindow()

        assertEquals(FIRST_ATTEMPT, window.tryAcquire(FIRST_TIME_MS))
        assertEquals(SECOND_ATTEMPT, window.tryAcquire(SECOND_TIME_MS))
        assertEquals(THIRD_ATTEMPT, window.tryAcquire(THIRD_TIME_MS))

        assertNull(window.tryAcquire(FOURTH_TIME_MS))
    }

    @Test
    fun `attempt at the exact window boundary remains counted`() {
        val window = StreamStallRecoveryWindow(maxAttempts = SECOND_ATTEMPT, windowMs = WINDOW_MS)

        assertEquals(FIRST_ATTEMPT, window.tryAcquire(FIRST_TIME_MS))
        assertEquals(SECOND_ATTEMPT, window.tryAcquire(FIRST_TIME_MS + WINDOW_MS))
        assertNull(window.tryAcquire(FIRST_TIME_MS + WINDOW_MS))
    }

    @Test
    fun `attempt just beyond the window starts a fresh budget`() {
        val window = StreamStallRecoveryWindow()

        assertEquals(FIRST_ATTEMPT, window.tryAcquire(FIRST_TIME_MS))
        assertEquals(SECOND_ATTEMPT, window.tryAcquire(SECOND_TIME_MS))

        assertEquals(FIRST_ATTEMPT, window.tryAcquire(SECOND_TIME_MS + WINDOW_MS + ONE_MILLISECOND))
    }

    @Test
    fun `clear starts a new playback session budget`() {
        val window = StreamStallRecoveryWindow(maxAttempts = FIRST_ATTEMPT)

        assertEquals(FIRST_ATTEMPT, window.tryAcquire(FIRST_TIME_MS))
        assertNull(window.tryAcquire(SECOND_TIME_MS))

        window.clear()

        assertEquals(FIRST_ATTEMPT, window.tryAcquire(THIRD_TIME_MS))
    }

    private companion object {
        const val FIRST_ATTEMPT = 1
        const val SECOND_ATTEMPT = 2
        const val THIRD_ATTEMPT = 3
        const val FIRST_TIME_MS = 1_000L
        const val SECOND_TIME_MS = 2_000L
        const val THIRD_TIME_MS = 3_000L
        const val FOURTH_TIME_MS = 4_000L
        const val WINDOW_MS = 120_000L
        const val ONE_MILLISECOND = 1L
    }
}
