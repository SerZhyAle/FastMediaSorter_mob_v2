package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import timber.log.Timber
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
 * S4160: a local port on `127.0.0.1` whose every accepted connection becomes one upstream stream to an exchange
 * server, spliced byte for byte until either side closes (`DEVICE-EXCHANGE` 7.7). It serves both shapes the
 * contract asks of a consumer: the `TUNNEL` forwarder, where [opener] sends `connect {broadcastId}`, and the
 * pinning proxy for a `RELAY` whose player cannot pin, where [opener] is plain TLS with the pin and the player's
 * HTTP request travels inside it. Certificate checking stays in [opener] - this class never sees a certificate.
 *
 * Bound to the loopback address only, so nothing on the network can use the device as a relay into the server.
 */
class BroadcastLoopbackBridge(
    private val label: String,
    private val opener: () -> ExchangeConnection,
) : Closeable {

    private val server =
        ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getByName(BroadcastExchangeEndpoints.LOOPBACK_HOST))
    private val closed = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fms-broadcast-bridge").apply { isDaemon = true }
    }

    val port: Int get() = server.localPort

    init {
        workers.execute(::acceptLoop)
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                if (!closed.get()) Timber.w(e, "BroadcastLoopbackBridge %s: accept failed, bridge stops", label)
                return
            }
            workers.execute { serve(client) }
        }
    }

    private fun serve(client: Socket) {
        val upstream = try {
            opener()
        } catch (e: IOException) {
            // The player sees a closed connection and runs its own retry; the reason is logged without the id.
            Timber.w("BroadcastLoopbackBridge %s: upstream refused - %s", label, e.javaClass.simpleName)
            closeQuietly(client)
            return
        }
        workers.execute { pump(client.getInputStream(), upstream.output, client, upstream) }
        pump(upstream.input, client.getOutputStream(), client, upstream)
    }

    // Either direction ending closes both sockets, which unblocks the opposite pump.
    private fun pump(input: InputStream, output: OutputStream, client: Socket, upstream: ExchangeConnection) {
        try {
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: SocketException) {
            Timber.v("BroadcastLoopbackBridge %s: stream closed - %s", label, e.javaClass.simpleName)
        } catch (e: IOException) {
            Timber.d("BroadcastLoopbackBridge %s: stream failed - %s", label, e.javaClass.simpleName)
        } finally {
            closeQuietly(client)
            closeQuietly(upstream)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeQuietly(server)
        workers.shutdownNow()
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Timber.v("BroadcastLoopbackBridge %s: close failed - %s", label, e.javaClass.simpleName)
        }
    }

    private companion object {
        const val ACCEPT_BACKLOG = 8
        const val BUFFER_BYTES = 16 * 1024
    }
}
