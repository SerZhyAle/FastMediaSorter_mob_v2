package com.sza.fastmediasorter.wear.data.exchange

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
 * A local port on `127.0.0.1` whose every accepted connection becomes one upstream stream to an exchange server,
 * spliced byte for byte until either side closes - the pinning proxy contract DEVICE-EXCHANGE 7.7 asks of a
 * consumer whose player cannot pin, mirrored from the phone's `BroadcastLoopbackBridge`. The player's HTTP request
 * travels inside the TLS [opener] makes; certificate checking stays in [opener], this class never sees one.
 *
 * Bound to the loopback address only, so nothing on the network can use the watch as a relay into the server.
 */
class WearExchangeLoopbackBridge(
    private val label: String,
    private val opener: () -> WearExchangeConnection,
) : Closeable {

    private val server =
        ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getByName(WearExchangeRelayEndpoint.LOOPBACK_HOST))
    private val closed = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fms-wear-exchange-bridge").apply { isDaemon = true }
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
                if (!closed.get()) Timber.w(e, "WearExchangeLoopbackBridge %s: accept failed, bridge stops", label)
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
            Timber.w("WearExchangeLoopbackBridge %s: upstream refused - %s", label, e.javaClass.simpleName)
            closeQuietly(client)
            return
        }
        workers.execute { pump(client.getInputStream(), upstream.output, client, upstream) }
        pump(upstream.input, client.getOutputStream(), client, upstream)
    }

    // Either direction ending closes both sockets, which unblocks the opposite pump.
    private fun pump(input: InputStream, output: OutputStream, client: Socket, upstream: WearExchangeConnection) {
        try {
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
            Timber.v("WearExchangeLoopbackBridge %s: close failed - %s", label, e.javaClass.simpleName)
        }
    }

    private companion object {
        const val ACCEPT_BACKLOG = 8
        const val BUFFER_BYTES = 16 * 1024
    }
}
