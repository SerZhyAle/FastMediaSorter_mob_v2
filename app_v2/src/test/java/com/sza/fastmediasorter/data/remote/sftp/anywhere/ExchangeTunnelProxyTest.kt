package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The consumer leg of contract ANYWHERE-ACCESS section 6.4 against a fake exchange server on loopback. */
class ExchangeTunnelProxyTest {

    private val exchange = ServerSocket(0)
    private val parts = SftpTunnelAddress.Parts(SHARE_ID, "127.0.0.1")
    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fake")
    }

    @After
    fun tearDown() {
        exchange.close()
    }

    @Test
    fun `an accepted connect hands the stream over to raw bytes`() {
        val asked = ArrayBlockingQueue<ExchangeEnvelope>(1)
        thread(isDaemon = true) {
            exchange.accept().use { socket ->
                asked.put(checkNotNull(ExchangeFrameCodec.read(socket.getInputStream())))
                ExchangeFrameCodec.write(socket.getOutputStream(), ExchangeEnvelope(ExchangeEnvelope.TYPE_CONNECTED))
                val banner = ByteArray(BANNER.length)
                var offset = 0
                while (offset < banner.size) {
                    offset += socket.getInputStream().read(banner, offset, banner.size - offset)
                }
                socket.getOutputStream().write(banner)
                socket.getOutputStream().flush()
                socket.getInputStream().read()
            }
        }
        val proxy = ExchangeTunnelProxy(connector, parts)

        proxy.connect(null, "ignored", exchange.localPort, TIMEOUT_MS)
        proxy.outputStream.write(BANNER.toByteArray())
        proxy.outputStream.flush()
        val echoed = ByteArray(BANNER.length)
        var offset = 0
        while (offset < echoed.size) offset += proxy.inputStream.read(echoed, offset, echoed.size - offset)
        proxy.close()

        val request = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(ExchangeEnvelope.TYPE_CONNECT, request.type)
        assertEquals(SHARE_ID, request.shareId)
        assertEquals(BANNER, String(echoed))
    }

    @Test
    fun `an unavailable share is its own failure`() {
        thread(isDaemon = true) {
            exchange.accept().use { socket ->
                ExchangeFrameCodec.read(socket.getInputStream())
                ExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    ExchangeEnvelope(ExchangeEnvelope.TYPE_REFUSED, reason = ExchangeEnvelope.REASON_UNAVAILABLE),
                )
            }
        }
        val proxy = ExchangeTunnelProxy(connector, parts)

        assertThrows(ExchangeTunnelUnavailableException::class.java) {
            proxy.connect(null, "ignored", exchange.localPort, TIMEOUT_MS)
        }
    }

    private companion object {
        const val SHARE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val BANNER = "SSH-2.0-test\r\n"
        const val TIMEOUT_MS = 5_000
        const val WAIT_SECONDS = 10L
    }
}
