package com.sza.fastmediasorter.ui.streams.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1169: [backoffDelayMs] is the pure exponential-backoff schedule for repeated stream-capture
 * failures. The capture path itself is device-only (ExoPlayer), so only the delay math is unit-tested.
 */
class StreamFrameSnapshotBackoffTest {

    @Test
    fun `no failures means no delay - a success resets the counter to zero`() {
        assertEquals(0L, backoffDelayMs(0))
        assertEquals(0L, backoffDelayMs(-1))
    }

    @Test
    fun `delay doubles per consecutive failure`() {
        assertEquals(60_000L, backoffDelayMs(1))
        assertEquals(120_000L, backoffDelayMs(2))
        assertEquals(240_000L, backoffDelayMs(3))
    }

    @Test
    fun `delay clamps at the five-minute cap`() {
        // failure 4 would be 480s uncapped; every failure from here stays at the 300s ceiling.
        assertEquals(300_000L, backoffDelayMs(4))
        assertEquals(300_000L, backoffDelayMs(5))
        assertEquals(300_000L, backoffDelayMs(50))
    }
}
