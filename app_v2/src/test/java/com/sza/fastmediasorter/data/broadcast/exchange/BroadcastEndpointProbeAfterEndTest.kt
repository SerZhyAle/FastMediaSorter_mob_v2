package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.domain.player.AfterEndAnswer
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class BroadcastEndpointProbeAfterEndTest {

    private val probe = BroadcastEndpointProbe(mockk(relaxed = true))
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    @After
    fun tearDown() = server.close()

    @Test
    fun aClosedPort_isAnEnd() {
        val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).run { localPort.also { close() } }

        assertEquals(AfterEndAnswer.ENDED, probe.lanAfterEnd("rtsp://127.0.0.1:$closedPort/"))
    }

    @Test
    fun notFoundOrGone_isAnEnd() {
        answerOnce("RTSP/1.0 404 Not Found\r\nCSeq: 1\r\n\r\n")
        assertEquals(AfterEndAnswer.ENDED, probe.lanAfterEnd("rtsp://127.0.0.1:${server.localPort}/"))

        answerOnce("HTTP/1.1 410 Gone\r\n\r\n")
        assertEquals(AfterEndAnswer.ENDED, probe.lanAfterEnd("http://127.0.0.1:${server.localPort}/live-audio.aac"))
    }

    @Test
    fun anyOtherStatus_isALiveBroadcast() {
        answerOnce("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n")

        assertEquals(AfterEndAnswer.ALIVE, probe.lanAfterEnd("http://127.0.0.1:${server.localPort}/live-audio.aac"))
    }

    @Test
    fun anAcceptedButSilentConnection_isNoAnswer() {
        thread(isDaemon = true) { runCatching { server.accept().use { Thread.sleep(SILENCE_MS) } } }

        assertEquals(AfterEndAnswer.NO_ANSWER, probe.lanAfterEnd("rtsp://127.0.0.1:${server.localPort}/"))
    }

    private fun answerOnce(response: String) {
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { client ->
                    client.getInputStream().bufferedReader().readLine()
                    client.getOutputStream().apply {
                        write(response.toByteArray(Charsets.US_ASCII))
                        flush()
                    }
                }
            }
        }
    }

    private companion object {
        const val SILENCE_MS = 3_000L
    }
}
