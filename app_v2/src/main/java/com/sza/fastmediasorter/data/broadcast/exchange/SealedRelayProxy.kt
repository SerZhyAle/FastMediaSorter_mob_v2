package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.security.e2e.E2eRelayEnd
import com.sza.fastmediasorter.data.security.e2e.E2eRelayHttp
import com.sza.fastmediasorter.data.security.e2e.E2eRelayListener
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The loopback proxy a sealed `RELAY` endpoint plays through (contract DEVICE-EXCHANGE 27 item DL): a port on
 * `127.0.0.1` whose every accepted player connection gets its own listen stream to the relay, opened with the
 * group key, and is answered with clear media and its true media type. A sealed relay is no ordinary stream for any
 * player, so the player never talks to the relay itself. Certificate checking stays in [opener].
 *
 * Bound to the loopback address only, so nothing on the network can read the opened broadcast through it.
 */
class SealedRelayProxy(
    private val label: String,
    private val path: String,
    private val host: String,
    private val broadcastId: String,
    private val groupKey: ByteArray,
    /** S4258: the relay's complete Authorization header line for a protected broadcast; null sends none. */
    private val authorization: () -> String? = { null },
    private val opener: () -> ExchangeConnection,
) : LoopbackEndpoint {

    private val server =
        ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getByName(BroadcastExchangeEndpoints.LOOPBACK_HOST))
    private val closed = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fms-sealed-relay").apply { isDaemon = true }
    }

    override val port: Int get() = server.localPort

    init {
        workers.execute(::acceptLoop)
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                if (!closed.get()) Timber.w(e, "SealedRelayProxy %s: accept failed, proxy stops", label)
                return
            }
            workers.execute { serve(client) }
        }
    }

    // The player's request is read and dropped: the proxy serves one stream, whatever the player asked.
    private fun serve(client: Socket) {
        try {
            client.use {
                E2eRelayHttp.skipRequest(client.getInputStream())
                Timber.d("S4240: phone plays a sealed relay through the opening proxy")
                val player = client.getOutputStream()
                val listener = E2eRelayListener(broadcastId, groupKey)
                var end = listen(listener, player)
                // Item DL: a record of an unknown epoch makes the listener reconnect once for the current ring.
                if (end == E2eRelayEnd.UNKNOWN_EPOCH) end = listen(listener, player)
                if (end != E2eRelayEnd.ENDED) Timber.w("SealedRelayProxy %s: stopped, code=sealed-failed", label)
            }
        } catch (e: IOException) {
            Timber.d("SealedRelayProxy %s: stream ended - %s", label, e.javaClass.simpleName)
        }
    }

    private fun listen(listener: E2eRelayListener, player: OutputStream): E2eRelayEnd =
        opener().use { upstream ->
            E2eRelayHttp.get(path, host, upstream.output, authorization())
            val body = E2eRelayHttp.body(upstream.input)
            listener.pump(body, player) { mediaType -> answerHead(player, mediaType) }
        }

    private fun answerHead(player: OutputStream, mediaType: String) {
        val head = "HTTP/1.1 200 OK\r\nContent-Type: $mediaType\r\nCache-Control: no-cache\r\n" +
            "Connection: close\r\n\r\n"
        player.write(head.toByteArray(Charsets.US_ASCII))
        player.flush()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            server.close()
        } catch (e: IOException) {
            Timber.v("SealedRelayProxy %s: close failed - %s", label, e.javaClass.simpleName)
        }
        workers.shutdownNow()
    }

    private companion object {
        const val ACCEPT_BACKLOG = 8
    }
}
