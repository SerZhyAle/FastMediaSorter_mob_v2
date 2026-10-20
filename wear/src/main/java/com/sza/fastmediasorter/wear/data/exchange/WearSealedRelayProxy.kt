package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRelayEnd
import com.sza.fastmediasorter.data.security.e2e.E2eRelayHttp
import com.sza.fastmediasorter.data.security.e2e.E2eRelayListener
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A local port a player is pointed at; closing it releases the port and its threads. */
interface WearLoopbackPort : Closeable {
    val port: Int
}

/**
 * The loopback proxy a sealed `RELAY` endpoint plays through on the watch (contract DEVICE-EXCHANGE 27 item DL),
 * mirrored from the phone's `SealedRelayProxy`: every accepted player connection gets its own listen stream to the
 * relay, opened with [groupKey], and is answered with clear media under its true media type. The shared
 * [E2eRelayListener] does the record work. Certificate checking stays in [opener].
 *
 * Bound to the loopback address only, so nothing on the network can read the opened broadcast through it.
 * [requestHeader] is one complete header line added to the relay request; like the key, it is never logged.
 */
class WearSealedRelayProxy(
    private val label: String,
    private val path: String,
    private val host: String,
    private val broadcastId: String,
    private val groupKey: ByteArray,
    private val requestHeader: String? = null,
    private val opener: () -> WearExchangeConnection,
) : WearLoopbackPort {

    private val server =
        ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getByName(WearExchangeRelayEndpoint.LOOPBACK_HOST))
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    private val activeConnections = LinkedHashSet<Closeable>()
    private val workers: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fms-wear-sealed-relay").apply { isDaemon = true }
    }

    override val port: Int get() = server.localPort

    init {
        submit(::acceptLoop)
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                if (!closed.get()) Timber.w(e, "WearSealedRelayProxy %s: accept failed, proxy stops", label)
                return
            }
            if (!track(client)) return
            if (!submit { serve(client) }) release(client)
        }
    }

    // The player's request is read and dropped: the proxy serves one stream, whatever the player asked.
    private fun serve(client: Socket) {
        try {
            E2eRelayHttp.skipRequest(client.getInputStream())
            Timber.d("S4240: watch plays a sealed relay through the opening proxy")
            val player = client.getOutputStream()
            val listener = E2eRelayListener(broadcastId, groupKey)
            var end = listen(listener, player)
            // Item DL: a record of an unknown epoch makes the listener reconnect once for the current ring.
            if (end == E2eRelayEnd.UNKNOWN_EPOCH && !closed.get()) end = listen(listener, player)
            if (end != E2eRelayEnd.ENDED && !closed.get()) {
                Timber.w("WearSealedRelayProxy %s: stopped, code=sealed-failed", label)
            }
        } catch (e: IOException) {
            Timber.d("WearSealedRelayProxy %s: stream ended - %s", label, e.javaClass.simpleName)
        } finally {
            release(client)
        }
    }

    private fun listen(listener: E2eRelayListener, player: OutputStream): E2eRelayEnd {
        val upstream = opener()
        if (!track(upstream)) throw IOException("proxy closed")
        return try {
            E2eRelayHttp.get(path, host, upstream.output, requestHeader)
            val body = E2eRelayHttp.body(upstream.input)
            listener.pump(body, player) { mediaType -> answerHead(player, mediaType) }
        } finally {
            release(upstream)
        }
    }

    private fun answerHead(player: OutputStream, mediaType: String) {
        val head = "HTTP/1.1 200 OK\r\nContent-Type: $mediaType\r\nCache-Control: no-cache\r\n" +
            "Connection: close\r\n\r\n"
        player.write(head.toByteArray(Charsets.US_ASCII))
        player.flush()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeQuietly(server)
        closeActiveConnections()
        workers.shutdownNow()
    }

    private fun track(closeable: Closeable): Boolean {
        val admitted = synchronized(lock) {
            if (closed.get()) {
                false
            } else {
                activeConnections.add(closeable)
                true
            }
        }
        if (!admitted) closeQuietly(closeable)
        return admitted
    }

    private fun release(closeable: Closeable) {
        synchronized(lock) {
            activeConnections.remove(closeable)
        }
        closeQuietly(closeable)
    }

    private fun closeActiveConnections() {
        val snapshot = synchronized(lock) {
            closed.set(true)
            activeConnections.toList().also { activeConnections.clear() }
        }
        snapshot.forEach(::closeQuietly)
    }

    private fun submit(task: () -> Unit): Boolean = try {
        workers.execute(task)
        true
    } catch (e: java.util.concurrent.RejectedExecutionException) {
        Timber.v("WearSealedRelayProxy %s: task rejected after close - %s", label, e.javaClass.simpleName)
        false
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Timber.v("WearSealedRelayProxy %s: close failed - %s", label, e.javaClass.simpleName)
        }
    }

    private companion object {
        const val ACCEPT_BACKLOG = 8
    }
}
