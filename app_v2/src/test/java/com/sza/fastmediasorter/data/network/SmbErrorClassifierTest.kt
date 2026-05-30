package com.sza.fastmediasorter.data.network

import com.hierynomus.protocol.transport.TransportException
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

/** Unit tests for [SmbErrorClassifier] - exception bucket classification and message mapping. */
class SmbErrorClassifierTest {

    // ── isNonRetriableConnectionError ────────────────────────────────────────

    @Test
    fun `auth failure message is non-retriable`() {
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("STATUS_LOGON_FAILURE")))
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("Authentication failed")))
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("wrong password")))
    }

    @Test
    fun `access denied message is non-retriable`() {
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("STATUS_ACCESS_DENIED")))
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("Access denied")))
    }

    @Test
    fun `share not found message is non-retriable`() {
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("STATUS_BAD_NETWORK_NAME")))
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("STATUS_OBJECT_NAME_NOT_FOUND")))
    }

    @Test
    fun `config error message is non-retriable`() {
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("Unknown host")))
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(Exception("Connection refused")))
    }

    @Test
    fun `non-retriable signal is found in nested cause chain`() {
        val root = Exception("STATUS_LOGON_FAILURE")
        val mid = Exception("wrapper", root)
        val top = Exception("outer", mid)
        assertTrue(SmbErrorClassifier.isNonRetriableConnectionError(top))
    }

    @Test
    fun `socket timeout is retriable`() {
        assertFalse(SmbErrorClassifier.isNonRetriableConnectionError(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `generic message is retriable`() {
        assertFalse(SmbErrorClassifier.isNonRetriableConnectionError(Exception("transient blip")))
    }

    // ── isTransportOrBrokenPipe ──────────────────────────────────────────────

    @Test
    fun `socket exception is transport failure`() {
        assertTrue(SmbErrorClassifier.isTransportOrBrokenPipe(SocketException("reset")))
    }

    @Test
    fun `smbj transport exception is transport failure`() {
        assertTrue(SmbErrorClassifier.isTransportOrBrokenPipe(TransportException("boom")))
    }

    @Test
    fun `broken pipe message is transport failure`() {
        assertTrue(SmbErrorClassifier.isTransportOrBrokenPipe(IOException("Broken pipe")))
    }

    @Test
    fun `connection reset message is transport failure`() {
        assertTrue(SmbErrorClassifier.isTransportOrBrokenPipe(IOException("Connection reset")))
    }

    @Test
    fun `transport socket combo message is transport failure`() {
        assertTrue(SmbErrorClassifier.isTransportOrBrokenPipe(Exception("transport-level socket error")))
    }

    @Test
    fun `transport signal is found in nested cause chain`() {
        val top = Exception("outer", Exception("mid", SocketException("dead")))
        assertTrue(SmbErrorClassifier.isTransportOrBrokenPipe(top))
    }

    @Test
    fun `unrelated exception is not transport failure`() {
        assertFalse(SmbErrorClassifier.isTransportOrBrokenPipe(Exception("auth failed")))
    }

    @Test
    fun `transport scan stops after depth limit`() {
        // Build a chain longer than 5 with the socket exception at the very bottom (depth 6).
        var current: Throwable = SocketException("deep")
        repeat(6) { current = Exception("layer", current) }
        assertFalse(SmbErrorClassifier.isTransportOrBrokenPipe(Exception("root", current)))
    }

    // ── getUserFriendlyMessage ───────────────────────────────────────────────

    @Test
    fun `server unreachable maps to not-responding message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(Exception("Server unreachable"))
        assertTrue(msg.contains("not responding"))
    }

    @Test
    fun `socket timeout cause maps to not-responding message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(Exception("x", SocketTimeoutException("t")))
        assertTrue(msg.contains("not responding"))
    }

    @Test
    fun `unknown host maps to resolve message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(Exception("Unknown host nas"))
        assertTrue(msg.contains("resolve"))
    }

    @Test
    fun `connection refused maps to service message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(Exception("Connection refused"))
        assertTrue(msg.contains("refused"))
    }

    @Test
    fun `authentication failed maps to credentials message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(Exception("Authentication failed"))
        assertTrue(msg.contains("username and password"))
    }

    @Test
    fun `timeout cancellation maps to overloaded message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(
            TimeoutCancellationExceptionFactory.create()
        )
        assertTrue(msg.contains("timed out"))
    }

    @Test
    fun `nested TimeoutException maps to slow-listing message`() {
        val msg = SmbErrorClassifier.getUserFriendlyMessage(
            Exception("wrap", java.util.concurrent.TimeoutException("QUERY_DIRECTORY"))
        )
        assertTrue(msg.contains("slowly") || msg.contains("timed out"))
    }

    @Test
    fun `unknown error falls back to raw message`() {
        assertEquals("weird thing", SmbErrorClassifier.getUserFriendlyMessage(Exception("weird thing")))
    }

    @Test
    fun `null message falls back to Unknown error`() {
        assertEquals("Unknown error", SmbErrorClassifier.getUserFriendlyMessage(Exception()))
    }
}

/**
 * [TimeoutCancellationException] has no public constructor; obtain a real instance by tripping a
 * coroutine timeout deterministically.
 */
private object TimeoutCancellationExceptionFactory {
    fun create(): TimeoutCancellationException {
        var captured: TimeoutCancellationException? = null
        kotlinx.coroutines.runBlocking {
            try {
                kotlinx.coroutines.withTimeout(1) {
                    kotlinx.coroutines.delay(1_000)
                }
            } catch (e: TimeoutCancellationException) {
                captured = e
            }
        }
        return captured!!
    }
}
