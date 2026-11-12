package com.sza.fastmediasorter.data.remote.sftp.anywhere

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

/** S4551: the cause label that tells an ordinary reconnect of the control stream from a systematic one. */
class ControlStreamEndTest {

    @Test
    fun `a clean end of the read is the server closing the stream`() {
        assertEquals(ControlStreamEnd.SERVER_CLOSED, end(failure = null))
    }

    @Test
    fun `the Android abort message is the local network stack`() {
        val failure = SocketException("Software caused connection abort")

        assertEquals(ControlStreamEnd.ABORTED, end(failure))
    }

    @Test
    fun `a reset or a broken pipe is the peer or a middlebox`() {
        assertEquals(ControlStreamEnd.RESET, end(SocketException("Connection reset by peer")))
        assertEquals(ControlStreamEnd.RESET, end(SocketException("Broken pipe")))
    }

    @Test
    fun `a read timeout is its own cause`() {
        assertEquals(ControlStreamEnd.SOCKET_TIMEOUT, end(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `an unrelated failure or one without a message is other`() {
        assertEquals(ControlStreamEnd.OTHER, end(IOException("something else")))
        assertEquals(ControlStreamEnd.OTHER, end(IOException()))
    }

    @Test
    fun `the keepalive close wins over the failure it caused`() {
        val failure = SocketException("Software caused connection abort")

        assertEquals(ControlStreamEnd.KEEPALIVE_MISSED, end(failure, keepaliveClosed = true))
    }

    @Test
    fun `a deliberate detach wins over the keepalive and the failure`() {
        val failure = SocketException("Socket closed")

        assertEquals(ControlStreamEnd.DETACHED, end(failure, keepaliveClosed = true, detached = true))
        assertEquals(ControlStreamEnd.DETACHED, end(null, detached = true))
    }

    private fun end(
        failure: IOException?,
        keepaliveClosed: Boolean = false,
        detached: Boolean = false,
    ) = ControlStreamEnd.of(failure, keepaliveClosed, detached)
}
