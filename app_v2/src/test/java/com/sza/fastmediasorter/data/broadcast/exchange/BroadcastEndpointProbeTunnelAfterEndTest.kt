package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeFrameCodec
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.domain.player.AfterEndAnswer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** DEVICE-EXCHANGE 1.10 item DY against a fake exchange server on loopback speaking plain frames. */
class BroadcastEndpointProbeTunnelAfterEndTest {

    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fake", null)
    }
    private val probe = BroadcastEndpointProbe(connector)
    private val tunnel = BroadcastExchangeEndpoint.Tunnel(
        server = ExchangeServer(LOOPBACK, server.localPort),
        broadcastId = "broadcast-a",
        scheme = "rtsp",
        inner = "rtsp://127.0.0.1:8554/live",
        certFingerprint = null,
    )

    @After
    fun tearDown() = server.close()

    @Test
    fun aRefusalEnded_isAnEnd() {
        answerConnect(DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_ENDED))

        assertEquals(AfterEndAnswer.ENDED, probe.tunnelAfterEnd(tunnel))
    }

    @Test
    fun aRefusalUnavailable_isNoAnswer() {
        answerConnect(DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE))

        assertEquals(AfterEndAnswer.NO_ANSWER, probe.tunnelAfterEnd(tunnel))
    }

    @Test
    fun aConnectedTunnel_isALiveBroadcast() {
        answerConnect(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECTED))

        assertEquals(AfterEndAnswer.ALIVE, probe.tunnelAfterEnd(tunnel))
    }

    private fun answerConnect(answer: DeviceExchangeEnvelope) {
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    DeviceExchangeFrameCodec.read(socket.getInputStream())
                    DeviceExchangeFrameCodec.write(socket.getOutputStream(), answer)
                    socket.getInputStream().read()
                }
            }
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
