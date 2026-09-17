package com.sza.fastmediasorter.broadcast

import android.content.Context
import com.sza.fastmediasorter.core.network.LanAddressResolver
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local HTTP server broadcasting live audio with ICY metadata headers over LAN.
 */
@Suppress("MagicNumber")
class BroadcastHttpServer(
    private val context: Context,
    private val config: BroadcastSessionConfig,
    private val lanAddressProvider: () -> String? = { LanAddressResolver(context).resolve() },
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
    }

    private var server: InternalServer? = null
    private var activePort: Int = config.port

    private val clients = CopyOnWriteArrayList<ClientSink>()

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
     */
    fun writeFrame(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) {
            return
        }
        val frame = buffer.copyOfRange(offset, offset + length)
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
        Timber.d("S3218: listener attached with its own frame queue, listeners=${clients.size}")
        return client.input
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

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != ENDPOINT && session.uri != "/live-audio") {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }

            val clientStream = createClientStream()
            val response = newChunkedResponse(Response.Status.OK, "audio/aac", clientStream)
            response.addHeader("icy-name", config.streamTitle)
            response.addHeader("icy-genre", "Live Audio")
            response.addHeader("icy-pub", "1")
            response.addHeader("icy-br", (config.bitRateBps / 1000).toString())
            response.addHeader("Accept-Ranges", "none")
            return response
        }
    }
}
