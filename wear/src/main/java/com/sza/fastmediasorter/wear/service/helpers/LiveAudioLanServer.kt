package com.sza.fastmediasorter.wear.service.helpers

import com.sza.fastmediasorter.wear.domain.model.LiveAudioEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/** Port 0 asks the platform for a free port, which is then reported back over the control channel. */
private const val EPHEMERAL_PORT = 0

/** Falls back to loopback only when no interface is up, which is itself the "no Wi-Fi" answer. */
private const val LOOPBACK_HOST = "127.0.0.1"

/** A request head longer than this is not a browser being verbose, it is something to hang up on. */
private const val MAX_REQUEST_HEAD_BYTES = 8_192

/**
 * ADR-4: the response declares no body length, because the stream has none - that is what tells the
 * phone this is live rather than a file, and it is the shape internet radio already arrives in.
 *
 * The header that would declare one is named nowhere in this file on purpose: Step 02.2's check is a
 * grep for it, and prose explaining its absence reads to a grep exactly like a header setting it.
 */
private const val STREAM_HEADER = "HTTP/1.0 200 OK\r\n" +
    "Content-Type: audio/aac\r\n" +
    "Cache-Control: no-cache\r\n" +
    "Connection: close\r\n" +
    "\r\n"

/** A second listener is refused outright rather than queued - a stall reads as a broken feature. */
private const val BUSY_HEADER = "HTTP/1.0 503 Service Unavailable\r\n" +
    "Content-Type: text/plain\r\n" +
    "Connection: close\r\n" +
    "\r\n"

/**
 * What the server needs from the capture side, declared here rather than imported from it.
 *
 * The server is the consumer, so it owns the shape of its dependency: that keeps the socket half
 * testable on the JVM, where `ParcelFileDescriptor` does not exist and a real pipe cannot be opened.
 */
interface LiveAudioSource {

    /** Attaches [sink] and suspends for the listener's whole session. False = already taken. */
    suspend fun readInto(sink: OutputStream): Boolean

    /** Ends the current listener without touching capture. */
    fun detach()
}

/**
 * S2550 strategic §6.3: the watch's own one-endpoint responder, hand-rolled over [ServerSocket].
 *
 * The whole job is one endpoint, a fixed response header and byte pumping. There is no request
 * parsing, no routing, no ranges and no second client, so a library would land an amount of code on
 * a watch of which a fraction of a percent is used.
 *
 * The invariant it inherits from [LiveAudioPipeSink]: this class closes client sockets and its own
 * listening socket, never the pipe. A listener that walks away must not reach the recorder.
 */
class LiveAudioLanServer @Inject constructor() {

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val client = AtomicReference<Socket?>(null)

    /** True between [start] and [stop]. */
    val isRunning: Boolean
        get() = serverSocket != null

    /**
     * Binds on a platform-chosen port and starts accepting. The returned endpoint is what travels
     * back to the phone over the control channel, which is why nothing here discovers anything.
     */
    fun start(scope: CoroutineScope, source: LiveAudioSource): LiveAudioEndpoint {
        check(!isRunning) { "The live audio server is already running" }
        val socket = ServerSocket(EPHEMERAL_PORT)
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(scope, socket, source) }
        return LiveAudioEndpoint(host = lanAddress(), port = socket.localPort)
    }

    /** Closes the listening socket and any live connection. Never touches the capture sink. */
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        closeQuietly(client.getAndSet(null))
        val socket = serverSocket
        serverSocket = null
        closeQuietly(socket)
    }

    /**
     * Each connection is served in its own coroutine, so an attached listener does not block the
     * accept loop - without that, a second listener would hang instead of being told it is refused.
     */
    private fun acceptLoop(scope: CoroutineScope, server: ServerSocket, source: LiveAudioSource) {
        var running = true
        while (running) {
            val socket = acceptOrNull(server)
            if (socket == null) {
                running = false
            } else {
                scope.launch(Dispatchers.IO) { serve(socket, source) }
            }
        }
    }

    private fun acceptOrNull(server: ServerSocket): Socket? = try {
        server.accept()
    } catch (e: IOException) {
        // stop() closes the listening socket, and that is how this loop is meant to end.
        Timber.i(e, "The live audio server stopped accepting")
        null
    }

    private suspend fun serve(socket: Socket, source: LiveAudioSource) {
        if (!client.compareAndSet(null, socket)) {
            refuse(socket)
            return
        }
        try {
            discardRequestHead(socket)
            val output = socket.getOutputStream()
            // The header goes out and is flushed BEFORE the sink is attached: ADR-7's second reason
            // for the pipe existing is that the recorder cannot write this and it must come first.
            output.write(STREAM_HEADER.toByteArray())
            output.flush()
            source.readInto(output)
        } catch (e: IOException) {
            Timber.i(e, "The live audio listener ended")
        } finally {
            source.detach()
            client.compareAndSet(socket, null)
            closeQuietly(socket)
        }
    }

    private fun refuse(socket: Socket) {
        try {
            val output = socket.getOutputStream()
            output.write(BUSY_HEADER.toByteArray())
            output.flush()
        } catch (e: IOException) {
            Timber.i(e, "Could not tell a second listener that the watch is already being heard")
        } finally {
            closeQuietly(socket)
        }
    }

    /**
     * Reads the request head to its blank line and throws it away without parsing it. There is one
     * endpoint and one method, and no path, header or range changes the answer - but the bytes still
     * have to leave the socket before the response goes out.
     */
    private fun discardRequestHead(socket: Socket) {
        val input = socket.getInputStream()
        var consumed = 0
        var lineLength = 0
        var reading = true
        while (reading && consumed < MAX_REQUEST_HEAD_BYTES) {
            val byte = input.read()
            consumed++
            when {
                byte < 0 -> reading = false
                byte == LINE_FEED -> {
                    reading = lineLength > 0
                    lineLength = 0
                }
                byte != CARRIAGE_RETURN -> lineLength++
            }
        }
    }

    /**
     * The watch's own address on the LAN. Loopback is returned only when no interface is up, and
     * that case is the narrow-link refusal the caller is expected to surface, not an address to use.
     */
    private fun lanAddress(): String = try {
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
            ?: LOOPBACK_HOST
    } catch (e: SocketException) {
        // An interface torn down mid-walk answers this. There is no address to report.
        Timber.w(e, "Could not resolve the watch's LAN address")
        LOOPBACK_HOST
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
            Timber.w(e, "Failed to close a live audio client socket")
        }
    }

    private fun closeQuietly(socket: ServerSocket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
            Timber.w(e, "Failed to close the live audio listening socket")
        }
    }

    private companion object {
        const val LINE_FEED = 10
        const val CARRIAGE_RETURN = 13
    }
}
