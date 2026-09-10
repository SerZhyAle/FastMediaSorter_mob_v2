package com.sza.fastmediasorter.wear.service

import com.sza.fastmediasorter.wear.service.helpers.LiveAudioLanServer
import com.sza.fastmediasorter.wear.service.helpers.LiveAudioLimits
import com.sza.fastmediasorter.wear.service.helpers.LiveAudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * S2550 Phase 02, widened by S2509 Phase 02. Runs on the JVM against real loopback sockets, which is
 * possible only because the server declares its own [LiveAudioSource] port rather than importing the
 * Android-only pipe.
 *
 * The concurrency cases are the reason this file changed: S2550's 503-to-the-second-listener was
 * correct while the listener was by definition the paired phone, and it is exactly wrong for a
 * broadcast anyone may open. A test that still asserted it would keep that behaviour alive silently.
 */
class LiveAudioLanServerTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val server = LiveAudioLanServer()
    private val source = FakeAudioSource()
    private val opened = mutableListOf<Socket>()

    @After
    fun tearDown() {
        opened.forEach { runCatching { it.close() } }
        server.stop()
        source.release()
        scope.cancel()
    }

    /**
     * The non-loopback assertion is the point of the case, not decoration: an endpoint carrying
     * `127.0.0.1` is unreachable from a listener, so a server that reported one would look healthy
     * here and fail on the LAN. It requires the host running the suite to have a LAN address, which
     * both the developer machine and the watch do - a machine with no network genuinely cannot serve
     * this feature, and that is the network-hold refusal rather than a flaky test.
     */
    @Test
    fun startReportsAnAddressAndABoundPort() {
        val endpoint = server.start(scope, source)

        assertNotEquals("The platform did not assign a port", 0, endpoint.port)
        assertTrue("Port outside the valid range: ${endpoint.port}", endpoint.port in 1..MAX_PORT)
        assertNotEquals(
            "The endpoint reported loopback, which no listener can reach across the LAN",
            LOOPBACK,
            endpoint.host
        )
        assertEquals(
            "The endpoint URL is the contract with every listener and must carry the one served path",
            "http://${endpoint.host}:${endpoint.port}/listen",
            endpoint.url
        )
    }

    @Test
    fun firstClientReceivesTheWholeHeaderBeforeAnyAudio() {
        val endpoint = server.start(scope, source)
        val client = connect(endpoint.port)

        val head = readHead(client)

        assertTrue("Status line missing from '$head'", head.startsWith("HTTP/1.0 200 OK"))
        assertTrue("Content-Type missing from '$head'", head.contains("Content-Type: audio/aac"))
        assertTrue("Cache-Control missing from '$head'", head.contains("Cache-Control: no-cache"))
        assertTrue("Connection missing from '$head'", head.contains("Connection: close"))
        assertTrue(
            "A live stream has no length, and declaring one would tell the listener otherwise: '$head'",
            !head.contains("Content-Length")
        )
        assertTrue(
            "The audio arrived before the header was complete, so the listener cannot parse it",
            head.endsWith("\r\n\r\n")
        )
        assertEquals(
            "The payload after the header is not what the source wrote",
            PAYLOAD_MARKER,
            readPayloadMarker(client)
        )
    }

    /** S2509 strategic §6 question 5: the owner chose broadcast semantics over one known listener. */
    @Test
    fun everyListenerUpToTheLimitIsServedAtOnce() {
        val endpoint = server.start(scope, source)

        repeat(LiveAudioLimits.MAX_LISTENERS) { index ->
            val client = connect(endpoint.port)
            val head = readHead(client)
            assertTrue(
                "Listener ${index + 1} of ${LiveAudioLimits.MAX_LISTENERS} was not served: '$head'",
                head.startsWith("HTTP/1.0 200 OK")
            )
            assertEquals(
                "Listener ${index + 1} received a header but no audio",
                PAYLOAD_MARKER,
                readPayloadMarker(client)
            )
        }
        awaitAttachments(LiveAudioLimits.MAX_LISTENERS)
    }

    /**
     * The other half of the same ruling: several listeners, but a stated ceiling. A watch that queued
     * the extra connection instead would look alive and deliver nothing.
     */
    @Test
    fun listenerPastTheLimitIsRefused() {
        val endpoint = server.start(scope, source)
        repeat(LiveAudioLimits.MAX_LISTENERS) { readHead(connect(endpoint.port)) }
        awaitAttachments(LiveAudioLimits.MAX_LISTENERS)

        val head = readHead(connect(endpoint.port))

        assertTrue(
            "A listener past the limit must be refused outright, not queued behind the others: '$head'",
            head.startsWith("HTTP/1.0 503")
        )
        assertEquals(
            "The refused client must not have been attached to the source",
            LiveAudioLimits.MAX_LISTENERS,
            source.attachments.get()
        )
    }

    /**
     * The regression this whole phase risks introducing: one listener leaving is a per-listener event,
     * and a teardown shared with the others would take the broadcast down with the first departure.
     */
    @Test
    fun oneListenerLeavingLeavesTheOthersStreaming() {
        val endpoint = server.start(scope, source)
        val leaving = connect(endpoint.port)
        val staying = connect(endpoint.port)
        readHead(leaving)
        readHead(staying)
        readPayloadMarker(staying)
        awaitAttachments(2)

        leaving.close()
        awaitCondition("the departed listener was never released") { source.attachedCount() == 1 }

        assertTrue(
            "The remaining listener stopped receiving audio when the other one left",
            readSomeBytes(staying) > 0
        )
    }

    @Test
    fun serverKeepsAcceptingAfterAClientDisconnects() {
        val endpoint = server.start(scope, source)

        connect(endpoint.port).use { first -> readHead(first) }
        awaitAttachments(1)
        awaitCondition("the server never released the departed listener") { source.attachedCount() == 0 }

        assertNotNull(
            "The listener left without the source's write ever failing, so this case did not exercise " +
                "a disconnect - it would pass just as well against a server that never attached",
            source.lastWriteFailure.get()
        )
        val head = readHead(connect(endpoint.port))
        assertTrue(
            "A departed listener left the server unable to serve the next one: '$head'",
            head.startsWith("HTTP/1.0 200 OK")
        )
    }

    /**
     * S2813: the port is what makes the repeated broadcast reproduce its QR image, so the value the
     * caller asked for has to be the value the endpoint reports - not merely accepted and ignored.
     */
    @Test
    fun aFreePreferredPortIsTheOneTheEndpointReports() {
        val free = ServerSocket(0).use { it.localPort }

        val endpoint = server.start(scope, source, null, free)

        assertEquals("The preferred port was not honoured", free, endpoint.port)
    }

    /**
     * The fallback branch, which a device walk is least likely to reach: the port can be held by
     * anything on the watch, and refusing the broadcast to protect the barcode would trade the feature
     * for a convenience.
     */
    @Test
    fun aTakenPreferredPortStillStartsTheServerElsewhere() {
        ServerSocket(0).use { squatter ->
            val endpoint = server.start(scope, source, null, squatter.localPort)

            assertNotEquals(
                "The server claimed a port another socket is holding",
                squatter.localPort,
                endpoint.port
            )
            assertTrue("The fallback produced no usable port", endpoint.port in 1..MAX_PORT)
            assertTrue(
                "The fallback left the server down, so a busy port silently kills the broadcast",
                readHead(connect(endpoint.port)).startsWith("HTTP/1.0 200 OK")
            )
        }
    }

    private fun connect(port: Int): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(LOOPBACK, port), CONNECT_TIMEOUT_MS)
        socket.soTimeout = READ_TIMEOUT_MS
        socket.getOutputStream().write("GET /listen HTTP/1.1\r\nHost: watch\r\n\r\n".toByteArray())
        socket.getOutputStream().flush()
        opened.add(socket)
        return socket
    }

    /** Reads exactly to the blank line, so anything left in the socket is audio and nothing else. */
    private fun readHead(socket: Socket): String {
        val input = socket.getInputStream()
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n") && head.length < MAX_HEAD_CHARS) {
            val byte = input.read()
            if (byte < 0) {
                break
            }
            head.append(byte.toChar())
        }
        return head.toString()
    }

    private fun readPayloadMarker(socket: Socket): String {
        val buffer = ByteArray(PAYLOAD_MARKER.length)
        var filled = 0
        while (filled < buffer.size) {
            val read = socket.getInputStream().read(buffer, filled, buffer.size - filled)
            if (read <= 0) {
                break
            }
            filled += read
        }
        return String(buffer, 0, filled)
    }

    /** Reads whatever the stream produces next, which for a live listener is the following tick. */
    private fun readSomeBytes(socket: Socket): Int {
        val buffer = ByteArray(READ_CHUNK_BYTES)
        val read = socket.getInputStream().read(buffer)
        return if (read < 0) 0 else read
    }

    private fun awaitAttachments(expected: Int) {
        awaitCondition("source never saw $expected attachment(s)") {
            source.attachments.get() >= expected
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(POLL_MS)
        }
        assertTrue(message, condition())
    }

    /**
     * Stands in for the capture pipe. It keeps writing to each attached listener and returns for that
     * listener when its write fails, which is exactly how the real sink notices one that walked away.
     */
    private class FakeAudioSource : LiveAudioSource {

        val attachments = AtomicInteger(0)
        val lastWriteFailure = AtomicReference<IOException?>(null)
        private val attached: MutableSet<OutputStream> = ConcurrentHashMap.newKeySet()
        private val ended: MutableSet<OutputStream> = ConcurrentHashMap.newKeySet()
        private val released = AtomicBoolean(false)

        fun attachedCount(): Int = attached.size

        fun release() {
            released.set(true)
        }

        override suspend fun readInto(sink: OutputStream): Boolean {
            attachments.incrementAndGet()
            attached.add(sink)
            try {
                var alive = write(sink, PAYLOAD_MARKER)
                while (alive && !released.get() && !ended.contains(sink)) {
                    delay(TICK_MS)
                    alive = write(sink, TICK)
                }
            } finally {
                attached.remove(sink)
            }
            return true
        }

        override fun detach(sink: OutputStream) {
            ended.add(sink)
        }

        override fun detachAll() {
            ended.addAll(attached)
        }

        private fun write(sink: OutputStream, text: String): Boolean = try {
            sink.write(text.toByteArray())
            sink.flush()
            true
        } catch (e: IOException) {
            // The listener closed its end. That is the normal end of a session, not a failure - but
            // it is kept rather than dropped, because a write failing for any OTHER reason would
            // otherwise look identical to a clean disconnect and quietly pass the disconnect case.
            lastWriteFailure.set(e)
            false
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val PAYLOAD_MARKER = "ADTSFRAME"
        const val TICK = "."
        const val TICK_MS = 20L
        const val POLL_MS = 20L
        const val AWAIT_TIMEOUT_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 2_000
        const val READ_TIMEOUT_MS = 5_000
        const val MAX_HEAD_CHARS = 1_024
        const val MAX_PORT = 65_535
        const val READ_CHUNK_BYTES = 64
    }
}
