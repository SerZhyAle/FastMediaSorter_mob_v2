package com.sza.fastmediasorter.wear.service

import com.sza.fastmediasorter.wear.service.helpers.LiveAudioLanServer
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
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * S2550 Phase 02. Runs on the JVM against real loopback sockets, which is possible only because the
 * server declares its own [LiveAudioSource] port rather than importing the Android-only pipe.
 */
class LiveAudioLanServerTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val server = LiveAudioLanServer()
    private val source = FakeAudioSource()

    @After
    fun tearDown() {
        server.stop()
        source.release()
        scope.cancel()
    }

    /**
     * The non-loopback assertion is the point of the case, not decoration: an endpoint carrying
     * `127.0.0.1` is unreachable from the phone, so a server that reported one would look healthy
     * here and fail on the pair. It requires the host running the suite to have a LAN address, which
     * both the developer machine and the watch do - a machine with no network genuinely cannot serve
     * this feature, and that is Pillar E's refusal rather than a flaky test.
     */
    @Test
    fun startReportsAnAddressAndABoundPort() {
        val endpoint = server.start(scope, source)

        assertNotEquals("The platform did not assign a port", 0, endpoint.port)
        assertTrue("Port outside the valid range: ${endpoint.port}", endpoint.port in 1..MAX_PORT)
        assertNotEquals(
            "The endpoint reported loopback, which the phone cannot reach across the LAN",
            LOOPBACK,
            endpoint.host
        )
        assertEquals(
            "The endpoint URL is the contract with the phone and must carry the one served path",
            "http://${endpoint.host}:${endpoint.port}/listen",
            endpoint.url
        )
    }

    @Test
    fun firstClientReceivesTheWholeHeaderBeforeAnyAudio() {
        val endpoint = server.start(scope, source)

        connect(endpoint.port).use { client ->
            val head = readHead(client)

            assertTrue("Status line missing from '$head'", head.startsWith("HTTP/1.0 200 OK"))
            assertTrue("Content-Type missing from '$head'", head.contains("Content-Type: audio/aac"))
            assertTrue("Cache-Control missing from '$head'", head.contains("Cache-Control: no-cache"))
            assertTrue("Connection missing from '$head'", head.contains("Connection: close"))
            assertTrue(
                "A live stream has no length, and declaring one would tell the phone otherwise: '$head'",
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
    }

    @Test
    fun secondClientIsRefusedWhileTheFirstIsAttached() {
        val endpoint = server.start(scope, source)

        connect(endpoint.port).use { first ->
            readHead(first)
            awaitAttachments(1)

            connect(endpoint.port).use { second ->
                val head = readHead(second)
                assertTrue(
                    "A second listener must be refused outright, not queued behind the first: '$head'",
                    head.startsWith("HTTP/1.0 503")
                )
            }
            assertEquals(
                "The refused client must not have been attached to the source",
                1,
                source.attachments.get()
            )
        }
    }

    @Test
    fun serverKeepsAcceptingAfterTheFirstClientDisconnects() {
        val endpoint = server.start(scope, source)

        connect(endpoint.port).use { first -> readHead(first) }
        awaitAttachments(1)
        awaitSlotFree()

        assertNotNull(
            "The slot freed without the source's write ever failing, so this case did not exercise " +
                "a disconnect - it would pass just as well against a server that never attached",
            source.lastWriteFailure.get()
        )
        connect(endpoint.port).use { second ->
            val head = readHead(second)
            assertTrue(
                "A departed listener left the server unable to serve the next one: '$head'",
                head.startsWith("HTTP/1.0 200 OK")
            )
        }
    }

    private fun connect(port: Int): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(LOOPBACK, port), CONNECT_TIMEOUT_MS)
        socket.soTimeout = READ_TIMEOUT_MS
        socket.getOutputStream().write("GET /listen HTTP/1.1\r\nHost: watch\r\n\r\n".toByteArray())
        socket.getOutputStream().flush()
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

    private fun awaitAttachments(expected: Int) {
        awaitCondition("source never saw $expected attachment(s)") {
            source.attachments.get() >= expected
        }
    }

    private fun awaitSlotFree() {
        awaitCondition("the server never released the listener slot") { !source.isAttached() }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(POLL_MS)
        }
        assertTrue(message, condition())
    }

    /**
     * Stands in for the capture pipe. It keeps writing while a listener is attached and returns when
     * the write fails, which is exactly how the real sink notices a listener that walked away.
     */
    private class FakeAudioSource : LiveAudioSource {

        val attachments = AtomicInteger(0)
        val lastWriteFailure = AtomicReference<IOException?>(null)
        private val attached = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        fun isAttached(): Boolean = attached.get()

        fun release() {
            released.set(true)
        }

        override suspend fun readInto(sink: OutputStream): Boolean {
            attachments.incrementAndGet()
            attached.set(true)
            try {
                var alive = write(sink, PAYLOAD_MARKER)
                while (alive && !released.get()) {
                    delay(TICK_MS)
                    alive = write(sink, TICK)
                }
            } finally {
                attached.set(false)
            }
            return true
        }

        override fun detach() {
            attached.set(false)
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
    }
}
