package com.sza.fastmediasorter.broadcast

import android.content.Context
import com.sza.fastmediasorter.core.network.LanAddressResolver
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

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
    }

    private var server: InternalServer? = null
    private var activePort: Int = config.port

    private val outputStreams = CopyOnWriteArrayList<PipedOutputStream>()

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

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun writeFrame(buffer: ByteArray, offset: Int, length: Int) {
        outputStreams.forEach { pos ->
            try {
                pos.write(buffer, offset, length)
                pos.flush()
            } catch (e: Exception) {
                Timber.d(e, "BroadcastHttpServer: client disconnected")
                if (outputStreams.remove(pos)) {
                    _listenerCount.value = outputStreams.size
                }
                try {
                    pos.close()
                } catch (_: Exception) {}
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun stop() {
        outputStreams.forEach { pos ->
            try {
                pos.close()
            } catch (_: Exception) {}
        }
        outputStreams.clear()
        _listenerCount.value = 0
        server?.stop()
        server = null
        Timber.d("BroadcastHttpServer: stopped")
    }

    val isAlive: Boolean get() = server?.isAlive == true

    private fun createClientStream(): InputStream {
        val pos = PipedOutputStream()
        val pis = PipedInputStream(pos, BUFFER_SIZE_BYTES)
        outputStreams.add(pos)
        _listenerCount.value = outputStreams.size
        return pis
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
