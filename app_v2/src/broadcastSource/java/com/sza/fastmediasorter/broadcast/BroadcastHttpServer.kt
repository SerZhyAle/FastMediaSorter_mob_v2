package com.sza.fastmediasorter.broadcast

import android.content.Context
import com.sza.fastmediasorter.core.network.LanAddressResolver
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local HTTP server broadcasting live audio with ICY metadata headers over LAN.
 */
@Suppress("MagicNumber")
class BroadcastHttpServer(
    private val context: Context,
    private val config: BroadcastSessionConfig,
    private val lanAddressProvider: () -> String? = { LanAddressResolver(context).resolve() },
    /** S4258: the pairs a protected broadcast accepts; null while it is not protected. */
    private val credentials: () -> List<BroadcastCredentialPair>? = { null },
) {

    companion object {
        const val ENDPOINT = "/live-audio.aac"
        private const val BUFFER_SIZE_BYTES = 65536

        /**
         * Frames a single listener may fall behind by before the oldest one is dropped. One ADTS frame
         * is 1024 samples, so this is roughly a second and a half of audio at 44.1 kHz - enough to ride
         * out a network hiccup, short enough that a listener resuming from pause hears live audio.
         */
        private const val CLIENT_QUEUE_FRAMES = 64

        /** How often [end] looks whether the open responses have finished their bodies. */
        private const val END_POLL_MS = 10L
    }

    private var server: InternalServer? = null
    private val nonce = BroadcastDigest.newNonce()
    private var activePort: Int = config.port

    private val clients = CopyOnWriteArrayList<ClientSink>()

    // Set for the length of [end]: a listener arriving then gets 404 instead of a body that never starts.
    @Volatile
    private var ending = false

    // Live responses whose body NanoHTTPD has not closed yet; it closes the body after the terminating chunk.
    private val openResponses = AtomicInteger(0)

    private val _listenerCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val listenerCount: kotlinx.coroutines.flow.StateFlow<Int> = _listenerCount.asStateFlow()

    fun getBroadcastUrl(): String? = lanAddressProvider()?.let { address ->
        "http://$address:$activePort$ENDPOINT"
    }

    /**
     * Binds the single configured port. Returns the port on success or -1 when the port is occupied,
     * so the caller can report a typed failure instead of silently trying another port.
     */
    @Suppress("TooGenericExceptionCaught")
    fun start(): Int {
        ending = false
        return try {
            val srv = InternalServer(config.port)
            srv.start()
            server = srv
            activePort = config.port
            Timber.d("BroadcastHttpServer: started on port ${config.port}")
            config.port
        } catch (e: Exception) {
            Timber.w("BroadcastHttpServer: port ${config.port} unavailable - ${e.message}")
            -1
        }
    }

    /**
     * Hands the frame to every listener without ever blocking the caller. The caller is the capture
     * thread, and writing straight into a listener's pipe used to park it whenever that listener
     * stopped reading - a paused player on one device then stalled the encoder, the microphone and
     * every other listener (S3218).
     *
     * A slice spanning the whole [buffer] is queued as is, so the caller hands over ownership and must
     * not reuse that array; the encoder allocates one per frame, and a second copy per AAC frame on
     * the capture thread bought nothing.
     */
    fun writeFrame(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) {
            return
        }
        val wholeBuffer = offset == 0 && length == buffer.size
        val frame = if (wholeBuffer) buffer else buffer.copyOfRange(offset, offset + length)
        var removed = false
        clients.forEach { client ->
            if (!client.offer(frame) && clients.remove(client)) {
                client.close()
                removed = true
            }
        }
        if (removed) {
            _listenerCount.value = clients.size
        }
    }

    /**
     * The deliberate end (contract LIVE-BROADCAST item T): new listeners get `404`, every open chunked body ends with
     * its zero-length chunk, and only then is the port closed. A listener that stopped reading cannot hold the end
     * past [timeoutMs]; [stop] closes it with the rest.
     */
    fun end(timeoutMs: Long) {
        Timber.d("S4442: http audio clean end")
        ending = true
        clients.forEach { it.close() }
        clients.clear()
        _listenerCount.value = 0
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (openResponses.get() > 0 && System.nanoTime() < deadline) Thread.sleep(END_POLL_MS)
        stop()
    }

    fun stop() {
        clients.forEach { it.close() }
        clients.clear()
        _listenerCount.value = 0
        server?.stop()
        server = null
        Timber.d("BroadcastHttpServer: stopped")
    }

    val isAlive: Boolean get() = server?.isAlive == true

    private fun createClientStream(): InputStream {
        val client = ClientSink(CLIENT_QUEUE_FRAMES, BUFFER_SIZE_BYTES)
        clients.add(client)
        _listenerCount.value = clients.size
        openResponses.incrementAndGet()
        return CountedBody(client.input)
    }

    /** A listener's body, counted among [openResponses] until NanoHTTPD closes it after the last chunk. */
    private inner class CountedBody(input: InputStream) : FilterInputStream(input) {
        private val open = AtomicBoolean(true)

        override fun close() {
            try {
                super.close()
            } finally {
                if (open.getAndSet(false)) openResponses.decrementAndGet()
            }
        }
    }

    /**
     * One listener: a bounded frame queue, the pipe NanoHTTPD reads from, and the single thread that
     * moves frames between them. Only that thread may block on the pipe.
     */
    private class ClientSink(queueFrames: Int, pipeBufferBytes: Int) {

        private val output = PipedOutputStream()
        val input: PipedInputStream = PipedInputStream(output, pipeBufferBytes)

        private val frames = ArrayBlockingQueue<ByteArray>(queueFrames)
        private val alive = AtomicBoolean(true)

        private val writer = Thread({ drain() }, "broadcast-client-writer").apply {
            isDaemon = true
            start()
        }

        /**
         * Returns false once the listener is gone, so the server can drop it. A full queue loses its
         * oldest frame rather than the newest: on a live stream the stale frame is the worthless one,
         * and dropping it bounds how far a listener can lag behind whatever the pause lasted.
         */
        fun offer(frame: ByteArray): Boolean {
            if (!alive.get()) {
                return false
            }
            if (!frames.offer(frame)) {
                frames.poll()
                frames.offer(frame)
            }
            return true
        }

        fun close() {
            alive.set(false)
            writer.interrupt()
            closeOutput()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun drain() {
            try {
                while (alive.get()) {
                    val frame = frames.take()
                    output.write(frame)
                    output.flush()
                }
            } catch (e: InterruptedException) {
                Timber.d("BroadcastHttpServer: client writer stopped - ${e.message}")
            } catch (e: Exception) {
                Timber.d(e, "BroadcastHttpServer: client disconnected")
            } finally {
                alive.set(false)
                closeOutput()
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun closeOutput() {
            try {
                output.close()
            } catch (e: Exception) {
                Timber.d("BroadcastHttpServer: client pipe already closed - ${e.message}")
            }
        }
    }

    private inner class InternalServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

        override fun createClientHandler(finalAccept: Socket, inputStream: InputStream): ClientHandler =
            NoLookupClientHandler(finalAccept, inputStream)

        /**
         * The library's handler builds its session from the socket's address, whose host name NanoHTTPD 2.3.1
         * resolves by a reverse DNS query for every non-loopback peer before reading the request. A LAN whose
         * resolver does not answer that query stalled each listener past its timeout with no status line, while
         * the loopback relay uploader was served at once (S4491). Presetting the numeric host skips the query.
         */
        private inner class NoLookupClientHandler(
            private val socket: Socket,
            private val input: InputStream,
        ) : ClientHandler(input, socket) {

            @Suppress("TooGenericExceptionCaught")
            override fun run() {
                Timber.d("S4491: lookup-free client handler for ${socket.inetAddress.hostAddress}")
                var output: OutputStream? = null
                try {
                    output = socket.getOutputStream()
                    val peer = socket.inetAddress.let { InetAddress.getByAddress(it.hostAddress, it.address) }
                    val session = HTTPSession(tempFileManagerFactory.create(), input, output, peer)
                    while (!socket.isClosed) session.execute()
                } catch (e: SocketException) {
                    Timber.v("BroadcastHttpServer: client connection closed - ${e.message}")
                } catch (e: SocketTimeoutException) {
                    Timber.v("BroadcastHttpServer: client idle timeout - ${e.message}")
                } catch (e: Exception) {
                    Timber.w(e, "BroadcastHttpServer: client handler failed")
                } finally {
                    closeQuietly(output)
                    closeQuietly(input)
                    closeQuietly(socket)
                    asyncRunner.closed(this)
                }
            }

            private fun closeQuietly(closeable: Closeable?) {
                try {
                    closeable?.close()
                } catch (e: IOException) {
                    Timber.v("BroadcastHttpServer: client stream already closed - ${e.message}")
                }
            }
        }

        override fun serve(session: IHTTPSession): Response {
            if (ending || (session.uri != ENDPOINT && session.uri != "/live-audio")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }

            return unauthorized(session) ?: liveAudio()
        }

        private fun liveAudio(): Response {
            val clientStream = createClientStream()
            val response = newChunkedResponse(Response.Status.OK, "audio/aac", clientStream)
            response.addHeader("icy-name", config.streamTitle)
            response.addHeader("icy-genre", "Live Audio")
            response.addHeader("icy-pub", "1")
            response.addHeader("icy-br", (config.bitRateBps / 1000).toString())
            response.addHeader("Accept-Ranges", "none")
            return response
        }

        // LIVE-BROADCAST item M: a missing or wrong pair gets the challenge and nothing finer. Tunnelled listeners
        // arrive from loopback too, so no address is exempt.
        private fun unauthorized(session: IHTTPSession): Response? {
            val allowed = credentials() ?: return null
            val answer = session.headers["authorization"]
            val accepted = BroadcastDigest.verify(answer, session.method.name, allowed) { it == nonce } != null
            return if (accepted) {
                null
            } else {
                newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "").apply {
                    // NanoHTTPD keeps one value per header name and writes it verbatim, so the second challenge
                    // rides in the first value behind its own field line; a second map key would print in hash
                    // order and could put MD5 ahead of SHA-256.
                    val fieldBreak = "\r\n${BroadcastDigest.HEADER_CHALLENGE}: "
                    val fields = BroadcastDigest.httpChallenges(nonce).joinToString(fieldBreak)
                    addHeader(BroadcastDigest.HEADER_CHALLENGE, fields)
                }
            }
        }
    }
}
