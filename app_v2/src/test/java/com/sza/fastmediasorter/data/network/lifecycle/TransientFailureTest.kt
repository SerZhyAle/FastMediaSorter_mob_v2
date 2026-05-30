package com.sza.fastmediasorter.data.network.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

/** Unit tests for [TransientFailure] cause-chain classification. */
class TransientFailureTest {

    @Test
    fun `socket timeout classifies as TIMEOUT`() {
        assertEquals(TransientFailure.Reason.TIMEOUT, TransientFailure.classify(SocketTimeoutException()))
    }

    @Test
    fun `socket exception classifies as TRANSPORT`() {
        assertEquals(TransientFailure.Reason.TRANSPORT, TransientFailure.classify(SocketException("reset")))
    }

    @Test
    fun `broken pipe message classifies as BROKEN_PIPE`() {
        assertEquals(TransientFailure.Reason.BROKEN_PIPE, TransientFailure.classify(RuntimeException("Broken pipe")))
    }

    @Test
    fun `channel closed message classifies as CHANNEL_CLOSED`() {
        assertEquals(TransientFailure.Reason.CHANNEL_CLOSED, TransientFailure.classify(RuntimeException("Channel is closed")))
        assertEquals(TransientFailure.Reason.CHANNEL_CLOSED, TransientFailure.classify(RuntimeException("session is closed")))
        assertEquals(
            TransientFailure.Reason.CHANNEL_CLOSED,
            TransientFailure.classify(RuntimeException("DiskShare has already been closed"))
        )
    }

    @Test
    fun `token expired markers classify as TOKEN_EXPIRED`() {
        assertEquals(TransientFailure.Reason.TOKEN_EXPIRED, TransientFailure.classify(RuntimeException("HTTP 401")))
        assertEquals(TransientFailure.Reason.TOKEN_EXPIRED, TransientFailure.classify(RuntimeException("token expired")))
        assertEquals(TransientFailure.Reason.TOKEN_EXPIRED, TransientFailure.classify(RuntimeException("invalid_grant")))
    }

    @Test
    fun `rate limit markers classify as RATE_LIMIT`() {
        assertEquals(TransientFailure.Reason.RATE_LIMIT, TransientFailure.classify(RuntimeException("429 Too Many")))
        assertEquals(TransientFailure.Reason.RATE_LIMIT, TransientFailure.classify(RuntimeException("rate limit hit")))
    }

    @Test
    fun `timed out message classifies as TIMEOUT`() {
        assertEquals(TransientFailure.Reason.TIMEOUT, TransientFailure.classify(RuntimeException("operation timed out")))
    }

    @Test
    fun `generic IOException classifies as TRANSPORT`() {
        assertEquals(TransientFailure.Reason.TRANSPORT, TransientFailure.classify(IOException("disk")))
    }

    @Test
    fun `non-transient error returns null`() {
        assertNull(TransientFailure.classify(IllegalArgumentException("bad arg")))
    }

    @Test
    fun `class check beats message heuristic`() {
        // SocketTimeoutException also carries "broken pipe" text - class check wins -> TIMEOUT.
        assertEquals(
            TransientFailure.Reason.TIMEOUT,
            TransientFailure.classify(SocketTimeoutException("broken pipe"))
        )
    }

    @Test
    fun `transient reason found in nested cause`() {
        val top = RuntimeException("outer", RuntimeException("mid", SocketTimeoutException()))
        assertEquals(TransientFailure.Reason.TIMEOUT, TransientFailure.classify(top))
    }

    @Test
    fun `scan stops after depth limit`() {
        var current: Throwable = SocketTimeoutException()
        repeat(6) { current = RuntimeException("layer", current) }
        assertNull(TransientFailure.classify(RuntimeException("root", current)))
    }
}
