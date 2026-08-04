package com.sza.fastmediasorter.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1361: the upload response budget must scale with body size instead of sitting at a constant 60 s. */
class HttpTimeoutsTest {

    @Test
    fun `unknown length falls back to the ceiling`() {
        assertEquals(HttpTimeouts.UPLOAD_READ_MAX_MS, HttpTimeouts.uploadReadTimeoutMs(0L))
        assertEquals(HttpTimeouts.UPLOAD_READ_MAX_MS, HttpTimeouts.uploadReadTimeoutMs(-1L))
    }

    @Test
    fun `reported failing size gets more than the time that upload actually needed`() {
        // The 2 016 006-byte screenshot from the S1361 log needed ~66 s on a ~26 KB/s uplink.
        val budget = HttpTimeouts.uploadReadTimeoutMs(2_016_006L)
        assertTrue("budget was $budget ms", budget > 66_000)
    }

    @Test
    fun `huge body is clamped and never overflows`() {
        val budget = HttpTimeouts.uploadReadTimeoutMs(10L * 1024L * 1024L * 1024L)
        assertEquals(HttpTimeouts.UPLOAD_READ_MAX_MS, budget)
    }

    @Test
    fun `tiny body still gets the streaming floor`() {
        assertTrue(HttpTimeouts.uploadReadTimeoutMs(1L) >= HttpTimeouts.STREAM_READ_MS)
    }
}
