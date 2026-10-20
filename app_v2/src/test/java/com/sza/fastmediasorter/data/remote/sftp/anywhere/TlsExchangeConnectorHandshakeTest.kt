package com.sza.fastmediasorter.data.remote.sftp.anywhere

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A peer that accepts TCP and never speaks TLS is given up on at the handshake deadline, its socket closed. */
class TlsExchangeConnectorHandshakeTest {

    private val server = ServerSocket(0)

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a withheld TLS handshake fails at the deadline and closes the socket`() {
        val accepted = ArrayBlockingQueue<Socket>(1)
        thread(isDaemon = true) { accepted.put(server.accept().apply { soTimeout = WAIT_MS }) }
        val adopted = ArrayBlockingQueue<Closeable>(1)
        val connector = TlsExchangeConnector(HANDSHAKE_MS)

        assertThrows(SocketTimeoutException::class.java) {
            connector.connect("127.0.0.1", server.localPort, ExchangeTrust.Verified(null), adopted::put)
        }

        assertTrue((adopted.poll(1, TimeUnit.SECONDS) as Socket).isClosed)
        val serverSide = checkNotNull(accepted.poll(WAIT_MS.toLong(), TimeUnit.MILLISECONDS))
        // The ClientHello arrives, then the close: drain it and the stream ends.
        val input = serverSide.getInputStream()
        val buffer = ByteArray(BUFFER)
        var read = input.read(buffer)
        while (read > 0) read = input.read(buffer)
        assertEquals(END_OF_STREAM, read)
        serverSide.close()
    }

    private companion object {
        const val HANDSHAKE_MS = 300
        const val WAIT_MS = 10_000
        const val BUFFER = 4096
        const val END_OF_STREAM = -1
    }
}
