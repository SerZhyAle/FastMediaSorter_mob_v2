package com.sza.fastmediasorter.wear.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract 7.3 presence: the keepalive window, the clamp and the reconnect backoff. */
class WearExchangeKeepaliveTest {

    @Test
    fun `a stream is dead only after three silent intervals`() {
        assertFalse(WearExchangeKeepalive.isDead(silentForMs = 90_000, intervalMs = 30_000))
        assertTrue(WearExchangeKeepalive.isDead(silentForMs = 90_001, intervalMs = 30_000))
    }

    @Test
    fun `the echoed interval is clamped to 10 to 120 seconds`() {
        assertEquals(30_000L, WearExchangeKeepalive.intervalMs(null))
        assertEquals(10_000L, WearExchangeKeepalive.intervalMs(2))
        assertEquals(120_000L, WearExchangeKeepalive.intervalMs(600))
        assertEquals(45_000L, WearExchangeKeepalive.intervalMs(45))
    }

    @Test
    fun `the backoff doubles up to two minutes`() {
        assertEquals(4_000L, WearExchangeKeepalive.nextBackoffMs(2_000))
        assertEquals(120_000L, WearExchangeKeepalive.nextBackoffMs(90_000))
        assertEquals(120_000L, WearExchangeKeepalive.nextBackoffMs(120_000))
    }

    @Test
    fun `only a dead token or a revoked device stops the retries`() {
        assertTrue("device-revoked" in WearExchangeKeepalive.PERMANENT_REFUSALS)
        assertTrue("bad-credentials" in WearExchangeKeepalive.PERMANENT_REFUSALS)
        assertFalse("capacity" in WearExchangeKeepalive.PERMANENT_REFUSALS)
    }
}
