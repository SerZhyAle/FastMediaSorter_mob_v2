package com.sza.fastmediasorter.core.playback.resilience

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamBackoffTest {

    @Test
    fun `audio ladder doubles from the base and flattens at the cap`() {
        assertEquals(BASE_MS, delayForAttempt(FIRST_ATTEMPT))
        assertEquals(SECOND_DELAY_MS, delayForAttempt(SECOND_ATTEMPT))
        assertEquals(CAP_MS, delayForAttempt(THIRD_ATTEMPT))
    }

    @Test
    fun `attempt beyond the shift ceiling stays on the plateau`() {
        assertEquals(CAP_MS, delayForAttempt(FOURTH_ATTEMPT))
        assertEquals(CAP_MS, delayForAttempt(RUNAWAY_ATTEMPT))
    }

    @Test
    fun `negative attempt falls back to the base delay`() {
        assertEquals(BASE_MS, delayForAttempt(NEGATIVE_ATTEMPT))
    }

    @Test
    fun `a wider shift ceiling still obeys the cap`() {
        val delay = StreamBackoff.exponentialDelayMs(
            attempt = FOURTH_ATTEMPT,
            baseMs = BASE_MS,
            capMs = CAP_MS,
            maxShift = WIDE_SHIFT,
        )

        assertEquals(CAP_MS, delay)
    }

    @Test
    fun `behind live window ladder climbs linearly and flattens at its cap`() {
        assertEquals(NO_DELAY_MS, linearDelayForAttempt(FIRST_ATTEMPT))
        assertEquals(LINEAR_STEP_MS, linearDelayForAttempt(SECOND_ATTEMPT))
        assertEquals(LINEAR_SECOND_MS, linearDelayForAttempt(THIRD_ATTEMPT))
        assertEquals(LINEAR_THIRD_MS, linearDelayForAttempt(FOURTH_ATTEMPT))
        assertEquals(LINEAR_FOURTH_MS, linearDelayForAttempt(FIFTH_ATTEMPT))
        assertEquals(LINEAR_CAP_MS, linearDelayForAttempt(SIXTH_ATTEMPT))
        assertEquals(LINEAR_CAP_MS, linearDelayForAttempt(SEVENTH_ATTEMPT))
    }

    private fun delayForAttempt(attempt: Int): Long =
        StreamBackoff.exponentialDelayMs(attempt = attempt, baseMs = BASE_MS, capMs = CAP_MS)

    private fun linearDelayForAttempt(attempt: Int): Long = StreamBackoff.linearDelayMs(
        attempt = attempt,
        stepMs = LINEAR_STEP_MS,
        capMs = LINEAR_CAP_MS,
    )

    private companion object {
        // RadioStreamBufferConfig.BASE_RETRY_DELAY_MS / MAX_RETRY_DELAY_MS - the values both audio
        // sites feed the ladder today.
        const val BASE_MS = 2_000L
        const val SECOND_DELAY_MS = 4_000L
        const val CAP_MS = 8_000L

        // StreamPlaybackHelper's behind-live-window branch: attempt * 1000ms, capped at 5000ms.
        const val NO_DELAY_MS = 0L
        const val LINEAR_STEP_MS = 1_000L
        const val LINEAR_SECOND_MS = 2_000L
        const val LINEAR_THIRD_MS = 3_000L
        const val LINEAR_FOURTH_MS = 4_000L
        const val LINEAR_CAP_MS = 5_000L

        const val NEGATIVE_ATTEMPT = -1
        const val FIRST_ATTEMPT = 0
        const val SECOND_ATTEMPT = 1
        const val THIRD_ATTEMPT = 2
        const val FOURTH_ATTEMPT = 3
        const val FIFTH_ATTEMPT = 4
        const val SIXTH_ATTEMPT = 5
        const val SEVENTH_ATTEMPT = 6
        const val RUNAWAY_ATTEMPT = 64
        const val WIDE_SHIFT = 8
    }
}
