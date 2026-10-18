package com.sza.fastmediasorter.wear.data.exchange

import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A local port on `127.0.0.1` whose every accepted connection becomes one upstream stream to an exchange server,
 * spliced byte for byte until either side closes - the pinning proxy contract DEVICE-EXCHANGE 7.7 asks of a
 * consumer whose player cannot pin, mirrored from the phone's `BroadcastLoopbackBridge`. The player's HTTP request
 * travels inside the TLS [opener] makes; certificate checking stays in [opener], this class never sees one.
 *
 * Bound to the loopback address only, so nothing on the network can use the watch as a relay into the server.
 *
 * [requestHeader] is one complete header line (`Authorization: Basic ..`) added to the head of the HTTP request
 * on every accepted connection: the relay checks a protected broadcast's credentials inside TLS (7.6), and adding
 * the line here keeps the password out of the URL the player, the session and the log would otherwise carry
 * (LIVE-BROADCAST item M). It is never logged.
 */
class WearExchangeLoopbackBridge(
    private val label: String,
    private val requestHeader: String? = null,
    private val opener: () -> WearExchangeConnection,
) : WearLoopbackPort {

    private val server =
        ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getByName(WearExchangeRelayEndpoint.LOOPBACK_HOST))
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    private val activeConnections = LinkedHashSet<Closeable>()
    private val workers: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fms-wear-exchange-bridge").apply { isDaemon = true }
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
                if (!closed.get()) Timber.w(e, "WearExchangeLoopbackBridge %s: accept failed, bridge stops", label)
                return
            }
            if (!track(client)) return
            if (!submit { serve(client) }) release(client)
        }
    }

    private fun serve(client: Socket) {
        val upstream = try {
            opener()
        } catch (e: IOException) {
            // The player sees a closed connection and runs its own retry; the reason is logged without the id.
            Timber.w("WearExchangeLoopbackBridge %s: upstream refused - %s", label, e.javaClass.simpleName)
            release(client)
            return
        }
        val header = requestHeader
        val started = track(upstream) &&
            (header == null || forwardHead(client, upstream, header)) &&
            submit { pump(clientToUpstream = true, client = client, upstream = upstream) }
        if (started) {
            pump(clientToUpstream = false, client = client, upstream = upstream)
        } else {
            release(client)
            release(upstream)
        }
    }

    // The head is read byte by byte so nothing of a body is consumed, and bounded so a peer cannot grow it.
    private fun forwardHead(client: Socket, upstream: WearExchangeConnection, header: String): Boolean = try {
        val head = readHead(client.getInputStream())
        if (head != null) {
            upstream.output.write(head, 0, head.size - LINE_END_BYTES)
            upstream.output.write((header + "\r\n\r\n").toByteArray(Charsets.US_ASCII))
            upstream.output.flush()
        }
        head != null
    } catch (e: IOException) {
        Timber.d("WearExchangeLoopbackBridge %s: request head failed - %s", label, e.javaClass.simpleName)
        false
    }

    private fun readHead(input: InputStream): ByteArray? {
        val head = ByteArrayOutputStream()
        var matched = 0
        while (matched < HEAD_TERMINATOR.size && head.size() < MAX_HEAD_BYTES) {
            val next = input.read()
            if (next < 0) return null
            head.write(next)
            matched = when {
                next == HEAD_TERMINATOR[matched].toInt() -> matched + 1
                next == HEAD_TERMINATOR[0].toInt() -> 1
                else -> 0
            }
        }
        return head.toByteArray().takeIf { matched == HEAD_TERMINATOR.size }
    }

    // Either direction ending closes both sockets, which unblocks the opposite pump.
    private fun pump(clientToUpstream: Boolean, client: Socket, upstream: WearExchangeConnection) {
        try {
            val input: InputStream = if (clientToUpstream) client.getInputStream() else upstream.input
            val output: OutputStream = if (clientToUpstream) upstream.output else client.getOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0) {
                output.write(buffer, 0, read)
                output.flush()
                read = input.read(buffer)
            }
        } catch (e: SocketException) {
            Timber.v("WearExchangeLoopbackBridge %s: stream closed - %s", label, e.javaClass.simpleName)
        } catch (e: IOException) {
            Timber.d("WearExchangeLoopbackBridge %s: stream failed - %s", label, e.javaClass.simpleName)
        } finally {
            release(client)
            release(upstream)
        }
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
        Timber.v("WearExchangeLoopbackBridge %s: task rejected after close - %s", label, e.javaClass.simpleName)
        false
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Timber.v("WearExchangeLoopbackBridge %s: close failed - %s", label, e.javaClass.simpleName)
        }
    }

    private companion object {
        const val ACCEPT_BACKLOG = 8
        const val BUFFER_BYTES = 16 * 1024
        const val MAX_HEAD_BYTES = 8 * 1024
        const val LINE_END_BYTES = 2
        val HEAD_TERMINATOR = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    }
}
