package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RtspAuthGateTest {

    private val listener = BroadcastCredentialPair("listener", "secret-pass")
    private val inner = BroadcastCredentialPair("fms-inner", "inner-pass")
    private val received = LinkedBlockingQueue<String>()
    private val innerServer = ServerSocket(0)
    private var gate: RtspAuthGate? = null

    @After
    fun tearDown() {
        gate?.stop()
        innerServer.close()
    }

    // The fake library server records each request head and answers 200 with its CSeq.
    private fun startInner() = thread(isDaemon = true) {
        val socket = innerServer.accept()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val out = socket.getOutputStream()
        while (true) {
            val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
            if (head.isEmpty()) break
            received.put(head.joinToString("\n"))
            val cseq = head.first { it.startsWith("CSeq") }.substringAfter(':').trim()
            out.write("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private fun startGate(protected: Boolean): Int {
        val port = ServerSocket(0).use { it.localPort }
        gate = RtspAuthGate(port, innerServer.localPort, { if (protected) listOf(listener) else null }, inner)
        assertTrue(gate!!.start())
        return port
    }

    private fun exchange(socket: Socket, request: String): String {
        socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
        socket.getOutputStream().flush()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        return generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.joinToString("\n")
    }

    @Test
    fun `a protected gate challenges a request without credentials`() {
        startInner()
        val port = startGate(protected = true)
        Socket("127.0.0.1", port).use { socket ->
            val reply = exchange(socket, "DESCRIBE rtsp://h:$port/live RTSP/1.0\r\nCSeq: 2\r\n\r\n")
            assertTrue(reply, reply.startsWith("RTSP/1.0 401"))
            assertTrue(reply.contains("CSeq: 2"))
            assertTrue(reply.contains("WWW-Authenticate: Digest"))
        }
        assertEquals(null, received.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a right Digest answer reaches the library with the inner Basic pair`() {
        startInner()
        val port = startGate(protected = true)
        Socket("127.0.0.1", port).use { socket ->
            val uri = "rtsp://h:$port/live"
            val challenge = exchange(socket, "DESCRIBE $uri RTSP/1.0\r\nCSeq: 1\r\n\r\n")
                .lines().first { it.startsWith("WWW-Authenticate") }.substringAfter(':').trim()
            val answer = BroadcastDigest.answer(challenge, "DESCRIBE", uri, listener)
            val reply = exchange(socket, "DESCRIBE $uri RTSP/1.0\r\nCSeq: 2\r\nAuthorization: $answer\r\n\r\n")
            assertTrue(reply, reply.startsWith("RTSP/1.0 200"))
        }
        val forwarded = received.poll(2, TimeUnit.SECONDS)!!
        assertTrue(forwarded.contains("Authorization: ${BroadcastDigest.basic(inner)}"))
        assertTrue(!forwarded.contains("Digest"))
    }

    @Test
    fun `a SETUP asking for UDP is refused with 461`() {
        startInner()
        val port = startGate(protected = false)
        Socket("127.0.0.1", port).use { socket ->
            val reply = exchange(
                socket,
                "SETUP rtsp://h:$port/live/trackID=0 RTSP/1.0\r\nCSeq: 3\r\n" +
                    "Transport: RTP/AVP;unicast;client_port=5000-5001\r\n\r\n",
            )
            assertTrue(reply, reply.startsWith("RTSP/1.0 461"))
        }
    }

    @Test
    fun `an unprotected gate forwards a TCP SETUP`() {
        startInner()
        val port = startGate(protected = false)
        Socket("127.0.0.1", port).use { socket ->
            val reply = exchange(
                socket,
                "SETUP rtsp://h:$port/live/trackID=0 RTSP/1.0\r\nCSeq: 4\r\n" +
                    "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n\r\n",
            )
            assertTrue(reply, reply.startsWith("RTSP/1.0 200"))
        }
    }
}
