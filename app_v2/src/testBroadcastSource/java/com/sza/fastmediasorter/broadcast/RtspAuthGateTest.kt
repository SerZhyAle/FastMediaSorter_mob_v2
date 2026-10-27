package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RtspAuthGateTest {

    private val listener = BroadcastCredentialPair("listener", "secret-pass")
    private val inner = BroadcastCredentialPair("fms-inner", "inner-pass")
    private val received = LinkedBlockingQueue<String>()
    private val innerServer = ServerSocket(0)
    private val libraryHungUp = CountDownLatch(1)
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

    // The pairs the gate accepts right now; a test changes it to renew or revoke a password mid-connection.
    @Volatile
    private var allowed: List<BroadcastCredentialPair>? = null

    private fun startGate(protected: Boolean): Int {
        allowed = if (protected) listOf(listener) else null
        val port = ServerSocket(0).use { it.localPort }
        gate = RtspAuthGate(port, innerServer.localPort, { allowed }, inner)
        assertTrue(gate!!.start())
        return port
    }

    // Takes the gate's challenge for [uri] and returns the Authorization value that answers it for [method].
    private fun authorize(
        socket: Socket,
        uri: String,
        pair: BroadcastCredentialPair,
        method: String = "DESCRIBE",
    ): String {
        val challenge = exchange(socket, "DESCRIBE $uri RTSP/1.0\r\nCSeq: 1\r\n\r\n")
            .lines().first { it.startsWith("WWW-Authenticate") }.substringAfter(':').trim()
        return requireNotNull(BroadcastDigest.answer(challenge, method, uri, pair))
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

    // A probe that connects and closes at once used to leave the downstream thread reading a closed socket, and an
    // uncaught exception there ended the whole broadcaster process.
    @Test
    fun `a client that connects and closes at once never throws out of a gate thread`() {
        startInner()
        val port = startGate(protected = true)
        val thrown = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> thrown += error }
        try {
            repeat(CONNECT_CLOSE_ROUNDS) { Socket("127.0.0.1", port).close() }
            Thread.sleep(SETTLE_MS)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertEquals(emptyList<Throwable>(), thrown.toList())
    }

    @Test
    fun `a request after its pair was revoked gets 401 and the connection is closed`() {
        startInner()
        val port = startGate(protected = true)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = IO_TIMEOUT_MS
            val uri = "rtsp://h:$port/live"
            val authorization = authorize(socket, uri, listener)
            val accepted = exchange(socket, signedRequest("DESCRIBE", uri, 2, authorization))
            assertTrue(accepted, accepted.startsWith("RTSP/1.0 200"))

            allowed = listOf(BroadcastCredentialPair(listener.user, "renewed-pass"))
            val reply = exchange(socket, signedRequest("OPTIONS", uri, 3, authorization))

            assertTrue(reply, reply.startsWith("RTSP/1.0 401"))
            assertTrue(closedWithin(socket.getInputStream(), IO_TIMEOUT_MS.toLong()))
        }
    }

    @Test
    fun `a streaming connection is closed soon after its pair was revoked`() {
        startStreamingInner()
        val port = startGate(protected = true)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = IO_TIMEOUT_MS
            val uri = "rtsp://h:$port/live"
            val authorization = authorize(socket, uri, listener, method = "PLAY")
            val played = exchange(socket, signedRequest("PLAY", uri, 2, authorization))
            assertTrue(played, played.startsWith("RTSP/1.0 200"))
            assertTrue("no stream data before the revocation", socket.getInputStream().read() >= 0)

            allowed = listOf(BroadcastCredentialPair(listener.user, "renewed-pass"))

            assertTrue(closedWithin(socket.getInputStream(), IO_TIMEOUT_MS.toLong()))
            val hungUp = libraryHungUp.await(IO_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            assertTrue("the gate left the library server connection open", hungUp)
        }
    }

    @Test
    fun `an unauthenticated request never opens an upstream connection`() {
        val upstreamAccepted = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                innerServer.accept().close()
                upstreamAccepted.countDown()
            } catch (_: IOException) {
            }
        }
        val port = startGate(protected = true)
        Socket("127.0.0.1", port).use { socket ->
            val reply = exchange(socket, "DESCRIBE rtsp://h:$port/live RTSP/1.0\r\nCSeq: 1\r\n\r\n")
            assertTrue(reply, reply.startsWith("RTSP/1.0 401"))
        }
        assertTrue(!upstreamAccepted.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a silent peer is timed out and closed without opening upstream`() {
        val upstreamAccepted = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                innerServer.accept().close()
                upstreamAccepted.countDown()
            } catch (_: IOException) {
            }
        }
        allowed = listOf(listener)
        val port = ServerSocket(0).use { it.localPort }
        gate = RtspAuthGate(port, innerServer.localPort, { allowed }, inner, firstRequestTimeoutMs = 200)
        assertTrue(gate!!.start())

        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = IO_TIMEOUT_MS
            assertTrue(closedWithin(socket.getInputStream(), IO_TIMEOUT_MS.toLong()))
        }
        assertTrue(!upstreamAccepted.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `connections beyond the concurrent cap are dropped`() {
        startInner()
        allowed = listOf(listener)
        val port = ServerSocket(0).use { it.localPort }
        gate = RtspAuthGate(
            port,
            innerServer.localPort,
            { allowed },
            inner,
            maxConnections = 2,
            firstRequestTimeoutMs = 2000,
        )
        assertTrue(gate!!.start())

        Socket("127.0.0.1", port).use { first ->
            Socket("127.0.0.1", port).use { second ->
                // Send one byte on each so the accept loop has entered serve() and holds both permits.
                first.getOutputStream().write('D'.code)
                first.getOutputStream().flush()
                second.getOutputStream().write('D'.code)
                second.getOutputStream().flush()
                Thread.sleep(100)

                Socket("127.0.0.1", port).use { third ->
                    third.soTimeout = IO_TIMEOUT_MS
                    assertTrue(closedWithin(third.getInputStream(), IO_TIMEOUT_MS.toLong()))
                }
            }
        }
    }

    // Item T: after the frames already on their way, exactly one BYE on the RTCP channel, then the end of stream;
    // the port refuses a reconnect from the first moment of the end.
    @Test
    fun `end sends a BYE after whole frames, closes, and refuses a reconnect`() {
        startRtpInner()
        val port = startGate(protected = false)
        val units = mutableListOf<ByteArray>()
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = IO_TIMEOUT_MS
            socket.getOutputStream().apply {
                write(
                    (
                        "SETUP rtsp://h:$port/live/trackID=0 RTSP/1.0\r\nCSeq: 3\r\n" +
                            "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n\r\n"
                        ).toByteArray(Charsets.ISO_8859_1),
                )
                flush()
            }
            val framer = RtspDownstreamFramer(socket.getInputStream())
            repeat(FRAMES_BEFORE_END) { units += checkNotNull(framer.next()) }

            thread(isDaemon = true) { gate?.end(IO_TIMEOUT_MS.toLong()) }
            var unit = framer.next()
            while (unit != null) {
                units += unit
                unit = framer.next()
            }
        }

        val bye = units.last()
        assertEquals('$'.code, bye[0].toInt())
        assertEquals(1, bye[1].toInt())
        assertEquals(BYE_TYPE, bye[BYE_TYPE_AT].toInt() and BYTE_MASK)
        assertEquals(1, units.count { it[0] == '$'.code.toByte() && it[1].toInt() == 1 })
        assertTrue(units.dropLast(1).drop(1).all { it.size == RTP_FRAME.size })
        val refused = runCatching { Socket("127.0.0.1", port).close() }.isFailure
        assertTrue("a reconnect after the end must be refused", refused)
    }

    // The fake library server answers SETUP with its interleaved pair, then streams RTP on channel 0.
    private fun startRtpInner() = thread(isDaemon = true) {
        val socket = innerServer.accept()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
        val cseq = head.firstOrNull { it.startsWith("CSeq") }?.substringAfter(':')?.trim() ?: return@thread
        val out = socket.getOutputStream()
        try {
            out.write(
                (
                    "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n" +
                        "Transport: RTP/AVP/TCP;unicast;interleaved=0-1;mode=play\r\n\r\n"
                    ).toByteArray(Charsets.ISO_8859_1),
            )
            while (true) {
                out.write(RTP_FRAME)
                out.flush()
                Thread.sleep(FRAME_INTERVAL_MS)
            }
        } catch (e: IOException) {
            Timber.d(e, "fake library server: the gate hung up")
        }
    }

    private fun signedRequest(method: String, uri: String, cseq: Int, authorization: String) =
        "$method $uri RTSP/1.0\r\nCSeq: $cseq\r\nAuthorization: $authorization\r\n\r\n"

    // The fake library server answers the first request, then keeps pushing interleaved frames until the gate hangs up.
    private fun startStreamingInner() = thread(isDaemon = true) {
        val socket = innerServer.accept()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
        val cseq = head.firstOrNull { it.startsWith("CSeq") }?.substringAfter(':')?.trim()
        if (cseq != null) {
            val out = socket.getOutputStream()
            out.write("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n".toByteArray())
            try {
                while (true) {
                    out.write(STREAM_FRAME)
                    out.flush()
                    Thread.sleep(FRAME_INTERVAL_MS)
                }
            } catch (e: IOException) {
                Timber.d(e, "fake library server: the gate hung up")
                libraryHungUp.countDown()
            }
        }
    }

    private fun closedWithin(input: InputStream, millis: Long): Boolean {
        val deadline = System.currentTimeMillis() + millis
        val buffer = ByteArray(READ_CHUNK_BYTES)
        var closed = false
        try {
            while (!closed && System.currentTimeMillis() < deadline) closed = input.read(buffer) < 0
        } catch (e: SocketTimeoutException) {
            Timber.d(e, "closedWithin: the stream is still open")
            closed = false
        } catch (e: IOException) {
            Timber.d(e, "closedWithin: the connection ended")
            closed = true
        }
        return closed
    }

    private companion object {
        const val CONNECT_CLOSE_ROUNDS = 20
        const val SETTLE_MS = 500L
        const val IO_TIMEOUT_MS = 3000
        const val FRAME_INTERVAL_MS = 10L
        const val READ_CHUNK_BYTES = 1024
        val STREAM_FRAME = byteArrayOf('$'.code.toByte(), 0, 0, 4, 1, 2, 3, 4)
        const val FRAMES_BEFORE_END = 4

        // The interleaved header, then the eight-byte receiver report, then the BYE: its payload type is byte 13.
        const val BYE_TYPE_AT = 13
        const val BYE_TYPE = 203
        const val BYTE_MASK = 0xFF

        // An RTP packet on channel 0: V=2, payload type 96, SSRC 0x0A0B0C0D, four bytes of payload.
        val RTP_FRAME = byteArrayOf(
            '$'.code.toByte(), 0, 0, 16,
            0x80.toByte(), 96, 0, 1, 0, 0, 0, 1, 0x0A, 0x0B, 0x0C, 0x0D, 1, 2, 3, 4,
        )
    }
}
