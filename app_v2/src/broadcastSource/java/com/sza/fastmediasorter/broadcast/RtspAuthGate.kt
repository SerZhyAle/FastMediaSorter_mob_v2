package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

/**
 * The RTSP door of a broadcast (contract LIVE-BROADCAST item M). The RTSP-Server library knows Basic with one pair
 * only, while a protected broadcast must take an RTSP Digest answer from any of its pairs; so the library listens on
 * an inner port closed by [innerPair], and this gate listens on the advertised one. It checks the first request of a
 * connection against [pairs] (null = unprotected), swaps the answer for the inner Basic pair and passes the bytes
 * through. A connection keeps the pair that opened it and is closed once [pairs] no longer holds that pair (a
 * renewed link password). Tunnelled listeners arrive from loopback like the phone itself, so no address skips the
 * check.
 *
 * RTP is carried inside the RTSP connection only: the library would send UDP to the gate's loopback address, so a
 * `SETUP` asking for UDP gets `461 Unsupported Transport` and the client falls back to TCP.
 *
 * A deliberate end goes through [end] (contract LIVE-BROADCAST item T): the port refuses new connections at once,
 * every open session gets an RTCP BYE on each of its RTCP channels, then the connections close.
 */
class RtspAuthGate(
    private val publicPort: Int,
    private val innerPort: Int,
    private val pairs: () -> List<BroadcastCredentialPair>?,
    private val innerPair: BroadcastCredentialPair,
    private val maxConnections: Int = MAX_CONCURRENT_CONNECTIONS,
    private val firstRequestTimeoutMs: Int = FIRST_REQUEST_TIMEOUT_MS,
) {

    private val nonce = BroadcastDigest.newNonce()
    private val lock = Any()
    private val open = CopyOnWriteArrayList<Closeable>()
    private val streaming = CopyOnWriteArrayList<Session>()
    private val connectionSlots = Semaphore(maxConnections)

    @Volatile
    private var listener: ServerSocket? = null
    private var stopped = false

    /** Binds [publicPort]; false when it is taken, so the caller fails the session as it did for the library. */
    fun start(): Boolean = try {
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(publicPort))
        }
        synchronized(lock) {
            stopped = false
            listener = socket
        }
        thread(name = "rtsp-auth-gate", isDaemon = true) { acceptLoop(socket) }
        true
    } catch (e: IOException) {
        Timber.w("RtspAuthGate: port %d unavailable - %s", publicPort, e.message)
        false
    }

    fun stop() {
        val (server, active) = synchronized(lock) {
            stopped = true
            val currentListener = listener
            listener = null
            val snapshot = open.toList()
            open.clear()
            currentListener to snapshot
        }
        server?.let(::closeQuietly)
        active.forEach(::closeQuietly)
    }

    /**
     * The deliberate end: the listening socket closes first, so a reconnect is refused while the BYEs go out, then
     * every streaming session gets its BYE frames and [stop] closes everything. A client that stopped reading cannot
     * hold the end past [timeoutMs]; its connection is closed with the rest.
     */
    fun end(timeoutMs: Long) {
        val server = synchronized(lock) {
            stopped = true
            listener.also { listener = null }
        }
        server?.let(::closeQuietly)
        val sender = thread(name = "rtsp-auth-gate-bye", isDaemon = true) { streaming.forEach(::sayBye) }
        sender.join(timeoutMs)
        stop()
    }

    // Under the client stream's lock, so the BYE lands between two whole frames and nothing follows it.
    private fun sayBye(session: Session) {
        val out = session.clientOut ?: return
        val frames = session.framer?.byeFrames().orEmpty()
        try {
            synchronized(out) {
                session.ended = true
                frames.forEach(out::write)
                out.flush()
            }
        } catch (e: IOException) {
            Timber.d("RtspAuthGate: BYE not delivered - %s", e.message)
        }
    }

    private fun track(closeable: Closeable): Boolean {
        val admitted = synchronized(lock) {
            if (stopped || listener == null) {
                false
            } else {
                open.add(closeable)
                true
            }
        }
        if (!admitted) closeQuietly(closeable)
        return admitted
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                Timber.d("RtspAuthGate: accept ended - %s", e.message)
                return
            }
            if (!connectionSlots.tryAcquire()) {
                Timber.w("RtspAuthGate: connection limit (%d) reached, dropping peer", maxConnections)
                closeQuietly(client)
            } else if (!track(client)) {
                connectionSlots.release()
                return
            } else {
                thread(name = "rtsp-auth-gate-client", isDaemon = true) {
                    try {
                        serve(client)
                    } finally {
                        connectionSlots.release()
                    }
                }
            }
        }
    }

    // Every stream is taken before a worker thread starts and the whole body is guarded: a client that connects and
    // closes at once must end its own connection, never throw out of a daemon thread and take the process down.
    private fun serve(client: Socket) {
        var inner: Socket? = null
        try {
            client.soTimeout = firstRequestTimeoutMs
            val clientOut = client.getOutputStream()
            val clientIn = BufferedInputStream(client.getInputStream())
            val session = Session().also { it.clientOut = clientOut }
            var innerOut: OutputStream? = null
            fun ensureUpstream(): OutputStream {
                innerOut?.let { return it }
                client.soTimeout = 0
                val upstream = Socket(LOOPBACK, innerPort)
                if (!track(upstream)) throw IOException("gate stopped")
                inner = upstream
                val framer = RtspDownstreamFramer(BufferedInputStream(upstream.getInputStream()))
                val out = upstream.getOutputStream().also { innerOut = it }
                session.framer = framer
                streaming.add(session)
                thread(name = "rtsp-auth-gate-down", isDaemon = true) {
                    pipe(framer, clientOut, client, upstream, session)
                }
                return out
            }
            while (relayNext(clientIn, ::ensureUpstream, clientOut, session)) Unit
        } catch (e: IOException) {
            Timber.d("RtspAuthGate: client connection ended - %s", e.message)
        } finally {
            closeBoth(client, inner)
        }
    }

    /** What one client connection may do: the pair its first accepted request answered with, null until then. */
    private class Session {
        @Volatile
        var pair: BroadcastCredentialPair? = null

        @Volatile
        var clientOut: OutputStream? = null

        @Volatile
        var framer: RtspDownstreamFramer? = null

        // Set under the client stream's lock once the BYE went out; the pipe forwards nothing after it.
        @Volatile
        var ended = false

        // Touched by the downstream pipe thread only.
        private var checkedAtNanos = System.nanoTime()

        /** True once the pair that opened this connection is no longer among [allowed] (item BF revocation). */
        fun revoked(allowed: List<BroadcastCredentialPair>?): Boolean {
            val held = pair ?: return false
            return allowed != null && held !in allowed
        }

        /** [revoked] against a fresh read of [pairs], at most once per interval: the pipe calls it for every packet. */
        fun revokedThrottled(pairs: () -> List<BroadcastCredentialPair>?): Boolean {
            val now = System.nanoTime()
            if (now - checkedAtNanos < REVOCATION_CHECK_NANOS) return false
            checkedAtNanos = now
            return revoked(pairs())
        }
    }

    /** Relays what the client sent next; false once the connection is over. */
    private fun relayNext(
        input: BufferedInputStream,
        innerOut: () -> OutputStream,
        clientOut: OutputStream,
        session: Session,
    ): Boolean {
        input.mark(1)
        val first = input.read()
        input.reset()
        return when {
            first < 0 -> false
            first == INTERLEAVED_MARKER -> {
                if (pairs() != null && session.pair == null) return false
                copyInterleavedFrame(input, innerOut())
                true
            }
            else -> RtspRequest.read(input)?.let { relayRequest(it, innerOut, clientOut, session) } ?: false
        }
    }

    private fun relayRequest(
        request: RtspRequest,
        innerOut: () -> OutputStream,
        clientOut: OutputStream,
        session: Session,
    ): Boolean {
        val verdict = judge(request, session)
        if (verdict.reply == null) {
            val out = innerOut()
            out.write(request.withAuthorization(BroadcastDigest.basic(innerPair)))
            out.flush()
        } else {
            synchronized(clientOut) {
                clientOut.write(verdict.reply.toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()
            }
        }
        return !verdict.closes
    }

    private class Verdict(val reply: String?, val closes: Boolean = false)

    // The pair that opened a connection is judged again by every later request, because a client that never
    // re-sends its Authorization (RTSP clients differ) must still lose the session when the pair is revoked.
    private fun judge(request: RtspRequest, session: Session): Verdict {
        val cseq = request.header("cseq")
        val transport = request.header("transport")
        val wantsUdp = request.method.equals("SETUP", ignoreCase = true) && transport != null &&
            !transport.uppercase(Locale.ROOT).contains(TCP_TRANSPORT)
        val allowed = pairs()
        val challenge = Verdict(
            reply(UNAUTHORIZED, cseq, "${BroadcastDigest.HEADER_CHALLENGE}: " + BroadcastDigest.rtspChallenge(nonce)),
        )
        if (allowed != null && session.pair == null) {
            session.pair = BroadcastDigest.verify(request.header("authorization"), request.method, allowed) {
                it == nonce
            }
            if (session.pair == null) return challenge
        }
        return when {
            session.revoked(allowed) -> {
                Timber.d("RtspAuthGate: the pair of an open connection was revoked - closing it")
                Verdict(challenge.reply, closes = true)
            }
            wantsUdp -> Verdict(reply(UNSUPPORTED_TRANSPORT, cseq, null))
            else -> Verdict(null)
        }
    }

    private fun reply(status: String, cseq: String?, extra: String?): String = buildString {
        append("RTSP/1.0 ").append(status).append(CRLF)
        cseq?.let { append("CSeq: ").append(it).append(CRLF) }
        extra?.let { append(it).append(CRLF) }
        append("Content-Length: 0").append(CRLF).append(CRLF)
    }

    private fun copyInterleavedFrame(input: InputStream, out: OutputStream) {
        val header = input.readNBytesCompat(INTERLEAVED_HEADER_BYTES) ?: throw IOException("truncated frame")
        val length = ((header[LENGTH_HIGH_INDEX].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or
            (header[LENGTH_LOW_INDEX].toInt() and BYTE_MASK)
        val body = input.readNBytesCompat(length) ?: throw IOException("truncated frame")
        out.write(header)
        out.write(body)
        out.flush()
    }

    // A quiet client sends nothing for long stretches while the stream keeps flowing down this pipe, so the pipe
    // is where a revoked pair is noticed without waiting for the client's next request. Whole units only: a BYE
    // written by [end] must never split an RTP frame.
    private fun pipe(from: RtspDownstreamFramer, to: OutputStream, client: Socket, inner: Socket, session: Session) {
        try {
            var unit = from.next()
            while (unit != null && stillAllowed(session) && forward(unit, to, session)) {
                unit = from.next()
            }
        } catch (e: IOException) {
            Timber.d("RtspAuthGate: server side ended - %s", e.message)
        } finally {
            streaming.remove(session)
            closeBoth(client, inner)
        }
    }

    private fun stillAllowed(session: Session): Boolean {
        val revoked = session.revokedThrottled(pairs)
        if (revoked) Timber.d("RtspAuthGate: the pair of a streaming connection was revoked - closing it")
        return !revoked
    }

    /** False once the session's BYE went out: nothing of the stream may follow it. */
    private fun forward(unit: ByteArray, to: OutputStream, session: Session): Boolean = synchronized(to) {
        if (!session.ended) {
            to.write(unit)
            to.flush()
        }
        !session.ended
    }

    private fun closeBoth(client: Socket, inner: Socket?) {
        synchronized(lock) {
            open.remove(client)
            inner?.let(open::remove)
        }
        closeQuietly(client)
        inner?.let(::closeQuietly)
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Timber.d("RtspAuthGate: close failed - %s", e.message)
        }
    }

    /** One RTSP request: the head lines as read and the body its `Content-Length` names. */
    internal class RtspRequest(val method: String, private val lines: List<String>, private val body: ByteArray) {

        fun header(name: String): String? = lines.drop(1).firstNotNullOfOrNull { line ->
            val colon = line.indexOf(':')
            line.substring(colon + 1).trim().takeIf { colon > 0 && line.substring(0, colon).trim().equals(name, true) }
        }

        /** The request with every `Authorization` line replaced by [value]. */
        fun withAuthorization(value: String): ByteArray {
            val kept = lines.filterIndexed { index, line ->
                index == 0 || !line.substringBefore(':').trim().equals(BroadcastDigest.HEADER_AUTHORIZATION, true)
            }
            val head = (kept + "${BroadcastDigest.HEADER_AUTHORIZATION}: $value").joinToString(CRLF) + CRLF + CRLF
            return head.toByteArray(Charsets.ISO_8859_1) + body
        }

        companion object {
            /** Null at the end of the stream; an oversized or malformed head ends the connection. */
            fun read(input: InputStream): RtspRequest? {
                val lines = mutableListOf<String>()
                var size = 0
                while (true) {
                    val line = readLine(input) ?: return null
                    size += line.length
                    if (size > MAX_HEAD_BYTES) malformed("request head too large")
                    if (line.isEmpty()) break
                    lines += line
                }
                val method = lines.firstOrNull()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                    ?: malformed("empty request")
                val request = RtspRequest(method, lines, ByteArray(0))
                val length = request.header("content-length")?.toIntOrNull() ?: 0
                if (length !in 0..MAX_HEAD_BYTES) malformed("bad content length")
                val body = input.readNBytesCompat(length) ?: malformed("truncated body")
                return RtspRequest(method, lines, body)
            }

            private fun malformed(reason: String): Nothing = throw IOException(reason)

            private fun readLine(input: InputStream): String? {
                val line = ByteArrayOutputStream()
                while (true) {
                    val next = input.read()
                    when {
                        next < 0 -> return if (line.size() == 0) null else throw IOException("truncated head")
                        next == LF -> return line.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
                        line.size() > MAX_HEAD_BYTES -> throw IOException("request line too long")
                        else -> line.write(next)
                    }
                }
            }
        }
    }

    internal companion object {
        const val LOOPBACK = "127.0.0.1"
        const val UNAUTHORIZED = "401 Unauthorized"
        const val UNSUPPORTED_TRANSPORT = "461 Unsupported Transport"
        const val TCP_TRANSPORT = "RTP/AVP/TCP"
        const val CRLF = "\r\n"
        const val INTERLEAVED_MARKER = '$'.code
        const val INTERLEAVED_HEADER_BYTES = 4
        const val MAX_HEAD_BYTES = 16 * 1024
        const val MAX_CONCURRENT_CONNECTIONS = 16
        const val FIRST_REQUEST_TIMEOUT_MS = 5_000
        const val REVOCATION_CHECK_NANOS = 250_000_000L
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
        const val LENGTH_HIGH_INDEX = 2
        const val LENGTH_LOW_INDEX = 3
        const val LF = '\n'.code

        /** `InputStream.readNBytes` is API 33; null when the stream ends first. */
        fun InputStream.readNBytesCompat(count: Int): ByteArray? {
            val out = ByteArray(count)
            var filled = 0
            while (filled < count) {
                val read = read(out, filled, count - filled)
                if (read < 0) return null
                filled += read
            }
            return out
        }
    }
}
