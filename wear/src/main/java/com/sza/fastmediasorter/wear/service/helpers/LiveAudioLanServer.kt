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
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.inject.Inject

/** Port 0 asks the platform for a free port, which is then reported back over the control channel. */
private const val EPHEMERAL_PORT = 0

/** Falls back to loopback only when no interface is up, which is itself the "no Wi-Fi" answer. */
private const val LOOPBACK_HOST = "127.0.0.1"

/** A request head longer than this is not a browser being verbose, it is something to hang up on. */
private const val MAX_REQUEST_HEAD_BYTES = 8_192

/**
 * S2509: what a watch is willing to spend on being heard by strangers.
 *
 * The owner chose broadcast semantics over S2550's single known listener (strategic §6 question 5) and
 * required explicit resource limits in the same ruling, because the serving device is the weakest one
 * in the exchange. Both numbers are small on purpose and both are enforced rather than documented:
 * the server refuses a connection past [MAX_LISTENERS] outright instead of queueing it, and each
 * admitted listener buffers at most [LISTENER_BUFFER_FRAMES] frames before its oldest are discarded.
 *
 * Worst case is therefore bounded and computable rather than a function of who connects: four
 * listeners times thirty-two frames of about four kilobytes is well under a megabyte held at once.
 */
object LiveAudioLimits {

    /** A refused fifth listener is a clear answer; a queued one is a feature that appears broken. */
    const val MAX_LISTENERS = 4

    /** Roughly a second of speech-grade audio. Deep enough for a hiccup, shallow enough to stay live. */
    const val LISTENER_BUFFER_FRAMES = 32
}

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

/** Past the limit a listener is refused outright rather than queued - a stall reads as broken. */
private const val BUSY_HEADER = "HTTP/1.0 503 Service Unavailable\r\n" +
    "Content-Type: text/plain\r\n" +
    "Connection: close\r\n" +
    "\r\n"

/**
 * What the server needs from the capture side, declared here rather than imported from it.
 *
 * The server is the consumer, so it owns the shape of its dependency: that keeps the socket half
 * testable on the JVM, where `ParcelFileDescriptor` does not exist and a real pipe cannot be opened.
 *
 * S2509 split what used to be one `detach()` in two. With several listeners on one capture, "end this
 * connection" and "end them all" stopped being the same sentence, and a single name for both is how a
 * departing listener would have taken the broadcast down with it.
 */
interface LiveAudioSource {

    /** Attaches [sink] and suspends for that listener's whole session. False = the limit is reached. */
    suspend fun readInto(sink: OutputStream): Boolean

    /** Ends the session of [sink] alone. Other listeners and the capture are untouched. */
    fun detach(sink: OutputStream)

    /** Ends every listener at once, still without touching capture. */
    fun detachAll()
}

/**
 * The watch's own responder for its live microphone, hand-rolled over [ServerSocket].
 *
 * The whole job is one endpoint, a fixed response header and byte pumping. There is no request
 * parsing, no routing and no ranges, so a library would land an amount of code on a watch of which a
 * fraction of a percent is used.
 *
 * S2550 built it for exactly one listener - the paired phone, known in advance. S2509 broadened it to
 * a bounded set, because a broadcast whose descriptor anyone may open cannot know its audience. The
 * limit lives in [LiveAudioLimits] and is refused here rather than deep in the capture side, so the
 * fifth listener gets an HTTP answer instead of a socket that opens and then goes quiet.
 *
 * The invariant it inherits from [LiveAudioPipeSink]: this class closes client sockets and its own
 * listening socket, never the pipe. A listener that walks away must not reach the recorder.
 */
class LiveAudioLanServer @Inject constructor() {

    private val lock = Any()
    private val clients = mutableListOf<Socket>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    /** True between [start] and [stop]. */
    val isRunning: Boolean
        get() = serverSocket != null

    /** How many listeners are connected right now, for the screen that says the broadcast is heard. */
    val listenerCount: Int
        get() = synchronized(lock) { clients.size }

    /**
     * Binds on a platform-chosen port and starts accepting. The returned endpoint is what travels
     * into the published descriptor, which is why nothing here discovers anything.
     *
     * S2509: [bindAddress] is the address of the network a broadcast session is holding. Given one,
     * the listening socket is bound to it and the endpoint reports it, so the address a listener is
     * handed and the network the session keeps alive are the same by construction rather than by
     * coincidence. The paired-phone listening session of S2550 passes `null` and keeps its original
     * behaviour - bind on every interface, then report whichever LAN address is up.
     */
    fun start(
        scope: CoroutineScope,
        source: LiveAudioSource,
        bindAddress: InetAddress? = null,
        preferredPort: Int? = null
    ): LiveAudioEndpoint {
        check(!isRunning) { "The live audio server is already running" }
        val socket = bind(preferredPort, bindAddress)
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(scope, socket, source) }
        val host = bindAddress?.hostAddress ?: lanAddress()
        return LiveAudioEndpoint(host = host, port = socket.localPort)
    }

    /**
     * S2813: takes the port the previous session used when it is still free, so a repeated broadcast
     * hands out the same address and the same QR image.
     *
     * A refused port falls back to an ephemeral one rather than failing the start: the stable barcode
     * is a convenience and being heard at all is the feature, and the port can be held by anything on
     * the watch, including this server's own socket still in TIME_WAIT.
     */
    private fun bind(preferredPort: Int?, bindAddress: InetAddress?): ServerSocket {
        if (preferredPort == null || preferredPort <= EPHEMERAL_PORT) {
            return ServerSocket(EPHEMERAL_PORT, ACCEPT_BACKLOG, bindAddress)
        }
        return try {
            ServerSocket(preferredPort, ACCEPT_BACKLOG, bindAddress)
        } catch (e: IOException) {
            Timber.i(e, "Preferred broadcast port %d is taken; falling back to an ephemeral one", preferredPort)
            ServerSocket(EPHEMERAL_PORT, ACCEPT_BACKLOG, bindAddress)
        }
    }

    /** Closes the listening socket and every live connection. Never touches the capture sink. */
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        val open = synchronized(lock) { clients.toList().also { clients.clear() } }
        open.forEach { closeQuietly(it) }
        val socket = serverSocket
        serverSocket = null
        closeQuietly(socket)
    }

    /**
     * Each connection is served in its own coroutine, so an attached listener does not block the
     * accept loop - without that, a listener past the limit would hang instead of being refused, and
     * the second admitted listener could not be served at all.
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
        if (!admit(socket)) {
            refuse(socket)
            return
        }
        var attached: OutputStream? = null
        try {
            discardRequestHead(socket)
            val output = socket.getOutputStream()
            attached = output
            // The header goes out and is flushed BEFORE the sink is attached: ADR-7's second reason
            // for the pipe existing is that the recorder cannot write this and it must come first.
            output.write(STREAM_HEADER.toByteArray())
            output.flush()
            source.readInto(output)
        } catch (e: IOException) {
            Timber.i(e, "The live audio listener ended")
        } finally {
            // This listener only. Ending them all here is what would let one departing listener take
            // the whole broadcast down, which is the failure S2509 exists to avoid.
            attached?.let { source.detach(it) }
            release(socket)
            closeQuietly(socket)
        }
    }

    /** Answers whether there is room, and takes the room in the same step so two racing accepts cannot both win. */
    private fun admit(socket: Socket): Boolean = synchronized(lock) {
        if (clients.size >= LiveAudioLimits.MAX_LISTENERS) {
            false
        } else {
            clients.add(socket)
            true
        }
    }

    private fun release(socket: Socket) {
        synchronized(lock) { clients.remove(socket) }
    }

    private fun refuse(socket: Socket) {
        try {
            val output = socket.getOutputStream()
            output.write(BUSY_HEADER.toByteArray())
            output.flush()
        } catch (e: IOException) {
            Timber.i(e, "Could not tell a listener that the watch is already serving its limit")
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
        /** Small on purpose: past MAX_LISTENERS a connection is refused, never held pending. */
        const val ACCEPT_BACKLOG = 8
        const val LINE_FEED = 10
        const val CARRIAGE_RETURN = 13
    }
}
