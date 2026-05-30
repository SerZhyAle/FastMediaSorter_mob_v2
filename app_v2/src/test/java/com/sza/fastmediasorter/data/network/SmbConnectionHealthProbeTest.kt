package com.sza.fastmediasorter.data.network

import com.hierynomus.protocol.transport.TransportException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeoutException

/**
 * Unit tests for [SmbConnectionHealthProbe.classify] - exception cause-chain to [DeadReason].
 *
 * [SmbConnectionHealthProbe.isAlive] is not covered here: it inspects live SMBJ
 * Connection/Session/DiskShare objects (PooledConnection) and carries no JVM-testable branching
 * beyond delegating to those SMBJ getters.
 */
class SmbConnectionHealthProbeTest {

    private val probe = SmbConnectionHealthProbe()

    @Test
    fun `logon failure classifies as AUTH_FAILED`() {
        assertEquals(DeadReason.AUTH_FAILED, probe.classify(Exception("STATUS_LOGON_FAILURE")))
        assertEquals(DeadReason.AUTH_FAILED, probe.classify(Exception("Authentication failed")))
    }

    @Test
    fun `auth failure wins over socket reason in chain`() {
        // Auth marker is checked first in each frame, before the SocketException branch.
        val ex = Exception("Logon failure", SocketException("connection reset"))
        assertEquals(DeadReason.AUTH_FAILED, probe.classify(ex))
    }

    @Test
    fun `broken pipe socket classifies as BROKEN_PIPE`() {
        assertEquals(DeadReason.BROKEN_PIPE, probe.classify(SocketException("Broken pipe")))
    }

    @Test
    fun `connection reset socket classifies as SERVER_RESET`() {
        assertEquals(DeadReason.SERVER_RESET, probe.classify(SocketException("Connection reset")))
    }

    @Test
    fun `connection abort socket classifies as SERVER_RESET`() {
        assertEquals(
            DeadReason.SERVER_RESET,
            probe.classify(SocketException("Software caused connection abort"))
        )
    }

    @Test
    fun `socket closed socket classifies as SOCKET_CLOSED`() {
        assertEquals(DeadReason.SOCKET_CLOSED, probe.classify(SocketException("Socket closed")))
    }

    @Test
    fun `unrecognised socket message defaults to SERVER_RESET`() {
        assertEquals(DeadReason.SERVER_RESET, probe.classify(SocketException("weird")))
    }

    @Test
    fun `io exception with socket closed classifies as SOCKET_CLOSED`() {
        assertEquals(DeadReason.SOCKET_CLOSED, probe.classify(IOException("Socket closed")))
    }

    @Test
    fun `transport exception wrapping broken pipe classifies as BROKEN_PIPE`() {
        val ex = TransportException(SocketException("Broken pipe"))
        assertEquals(DeadReason.BROKEN_PIPE, probe.classify(ex))
    }

    @Test
    fun `transport exception wrapping reset classifies as SERVER_RESET`() {
        val ex = TransportException(SocketException("Connection reset"))
        assertEquals(DeadReason.SERVER_RESET, probe.classify(ex))
    }

    @Test
    fun `transport exception without cause classifies as SOCKET_CLOSED`() {
        assertEquals(DeadReason.SOCKET_CLOSED, probe.classify(TransportException("no cause")))
    }

    @Test
    fun `timeout cancellation classifies as TIMEOUT`() {
        assertEquals(DeadReason.TIMEOUT, probe.classify(TimeoutException("query dir")))
    }

    @Test
    fun `timeout found deep in chain classifies as TIMEOUT`() {
        val ex = Exception("outer", Exception("mid", TimeoutException("slow")))
        assertEquals(DeadReason.TIMEOUT, probe.classify(ex))
    }

    @Test
    fun `unrelated exception classifies as UNKNOWN`() {
        assertEquals(DeadReason.UNKNOWN, probe.classify(IllegalArgumentException("nope")))
    }

    @Test
    fun `scan stops after depth limit returns UNKNOWN`() {
        var current: Throwable = TimeoutException("deep")
        repeat(9) { current = Exception("layer", current) }
        assertEquals(DeadReason.UNKNOWN, probe.classify(current))
    }
}
