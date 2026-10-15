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
import kotlin.concurrent.thread

/**
 * The RTSP door of a broadcast (contract LIVE-BROADCAST item M). The RTSP-Server library knows Basic with one pair
 * only, while a protected broadcast must take an RTSP Digest answer from any of its pairs; so the library listens on
 * an inner port closed by [innerPair], and this gate listens on the advertised one. It checks the first request of a
 * connection against [pairs] (null = unprotected), swaps the answer for the inner Basic pair and passes the bytes
 * through. Tunnelled listeners arrive from loopback like the phone itself, so no address skips the check.
 *
 * RTP is carried inside the RTSP connection only: the library would send UDP to the gate's loopback address, so a
 * `SETUP` asking for UDP gets `461 Unsupported Transport` and the client falls back to TCP.
 */
class RtspAuthGate(
    private val publicPort: Int,
    private val innerPort: Int,
    private val pairs: () -> List<BroadcastCredentialPair>?,
    private val innerPair: BroadcastCredentialPair,
) {

    private val nonce = BroadcastDigest.newNonce()
    private val open = CopyOnWriteArrayList<Closeable>()

    @Volatile
    private var listener: ServerSocket? = null

    /** Binds [publicPort]; false when it is taken, so the caller fails the session as it did for the library. */
    fun start(): Boolean = try {
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(publicPort))
        }
        listener = socket
        thread(name = "rtsp-auth-gate", isDaemon = true) { acceptLoop(socket) }
        true
    } catch (e: IOException) {
        Timber.w("RtspAuthGate: port %d unavailable - %s", publicPort, e.message)
        false
    }

    fun stop() {
        listener?.let(::closeQuietly)
        listener = null
        open.forEach(::closeQuietly)
        open.clear()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                Timber.d("RtspAuthGate: accept ended - %s", e.message)
                return
            }
            thread(name = "rtsp-auth-gate-client", isDaemon = true) { serve(client) }
        }
    }

    private fun serve(client: Socket) {
        open.add(client)
        val inner = try {
            Socket(LOOPBACK, innerPort)
        } catch (e: IOException) {
            Timber.w("RtspAuthGate: inner server unreachable - %s", e.message)
            closeQuietly(client)
            open.remove(client)
            return
        }
        open.add(inner)
        val clientOut = client.getOutputStream()
        thread(name = "rtsp-auth-gate-down", isDaemon = true) { pipe(inner.getInputStream(), clientOut, client, inner) }
        try {
            relayRequests(BufferedInputStream(client.getInputStream()), inner.getOutputStream(), clientOut)
        } catch (e: IOException) {
            Timber.d("RtspAuthGate: client connection ended - %s", e.message)
        } finally {
            closeBoth(client, inner)
        }
    }

    private fun relayRequests(input: BufferedInputStream, innerOut: OutputStream, clientOut: OutputStream) {
        var authenticated = false
        while (true) {
            input.mark(1)
            val first = input.read()
            if (first < 0) return
            input.reset()
            if (first == INTERLEAVED_MARKER) {
                copyInterleavedFrame(input, innerOut)
                continue
            }
            val request = RtspRequest.read(input) ?: return
            val verdict = judge(request, authenticated)
            if (verdict.reply != null) {
                synchronized(clientOut) {
                    clientOut.write(verdict.reply.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                }
            } else {
                authenticated = true
                innerOut.write(request.withAuthorization(BroadcastDigest.basic(innerPair)))
                innerOut.flush()
            }
        }
    }

    private class Verdict(val reply: String?)

    private fun judge(request: RtspRequest, authenticated: Boolean): Verdict {
        Timber.d("S4258: rtsp gate judges %s, authenticated=%b", request.method, authenticated)
        val cseq = request.header("cseq")
        val transport = request.header("transport")
        val wantsUdp = request.method.equals("SETUP", ignoreCase = true) && transport != null &&
            !transport.uppercase(Locale.ROOT).contains(TCP_TRANSPORT)
        val allowed = pairs()
        val refused = !authenticated && allowed != null &&
            BroadcastDigest.verify(request.header("authorization"), request.method, allowed) { it == nonce } == null
        return when {
            refused -> Verdict(
                reply(
                    UNAUTHORIZED,
                    cseq,
                    "${BroadcastDigest.HEADER_CHALLENGE}: " +
                        BroadcastDigest.rtspChallenge(nonce)
                )
            )
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

    private fun pipe(from: InputStream, to: OutputStream, client: Socket, inner: Socket) {
        val buffer = ByteArray(PIPE_BUFFER_BYTES)
        try {
            while (true) {
                val read = from.read(buffer)
                if (read < 0) break
                synchronized(to) {
                    to.write(buffer, 0, read)
                    to.flush()
                }
            }
        } catch (e: IOException) {
            Timber.d("RtspAuthGate: server side ended - %s", e.message)
        } finally {
            closeBoth(client, inner)
        }
    }

    private fun closeBoth(client: Socket, inner: Socket) {
        closeQuietly(client)
        closeQuietly(inner)
        open.remove(client)
        open.remove(inner)
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
        const val PIPE_BUFFER_BYTES = 16 * 1024
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
