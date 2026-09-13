package com.sza.fastmediasorter.broadcast

import android.content.Context
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread

/**
 * S3055: Unit tests holding the phone HTTP broadcast wire format defined by S3050 contract section 2.1.
 */
class BroadcastHttpServerTest {

    private lateinit var context: Context
    private var server: BroadcastHttpServer? = null

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
    }

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    private fun createServer(config: BroadcastSessionConfig): BroadcastHttpServer {
        return BroadcastHttpServer(
            context = context,
            config = config,
            lanAddressProvider = { TEST_SERVER_ADDRESS },
        )
    }

    @Test
    fun `headers for live-audio aac endpoint match S3050 contract`() {
        val port = findFreePort()
        val config = BroadcastSessionConfig(
            streamTitle = "Test Broadcast",
            bitRateBps = 128_000,
            port = port,
            sampleRateHz = 44_100,
            channelCount = 1,
            sourceDeviceId = "device-123",
        )
        val srv = createServer(config)
        server = srv
        assertEquals(port, srv.start())
        assertTrue(srv.isAlive)

        val url = URL(requireNotNull(srv.getBroadcastUrl()))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 2000
        }

        try {
            assertEquals(200, connection.responseCode)
            assertEquals("audio/aac", connection.getHeaderField("Content-Type"))
            assertEquals("Test Broadcast", connection.getHeaderField("icy-name"))
            assertEquals("Live Audio", connection.getHeaderField("icy-genre"))
            assertEquals("1", connection.getHeaderField("icy-pub"))
            assertEquals("128", connection.getHeaderField("icy-br"))
            assertEquals("none", connection.getHeaderField("Accept-Ranges"))
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `headers for live-audio endpoint match live-audio aac`() {
        val port = findFreePort()
        val config = BroadcastSessionConfig(
            streamTitle = "Stream Title",
            bitRateBps = 96_000,
            port = port,
            sampleRateHz = 48_000,
            channelCount = 2,
            sourceDeviceId = null,
        )
        val srv = createServer(config)
        server = srv
        assertEquals(port, srv.start())

        val url = URL("http://127.0.0.1:$port/live-audio")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 2000
        }

        try {
            assertEquals(200, connection.responseCode)
            assertEquals("audio/aac", connection.getHeaderField("Content-Type"))
            assertEquals("Stream Title", connection.getHeaderField("icy-name"))
            assertEquals("96", connection.getHeaderField("icy-br"))
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `invalid endpoint returns 404 not found`() {
        val port = findFreePort()
        val config = BroadcastSessionConfig.DEFAULT.copy(port = port)
        val srv = createServer(config)
        server = srv
        assertEquals(port, srv.start())

        val url = URL("http://127.0.0.1:$port/invalid-endpoint")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 2000
        }

        try {
            assertEquals(404, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `writeFrame streams payload data to connected client`() {
        val port = findFreePort()
        val config = BroadcastSessionConfig.DEFAULT.copy(port = port)
        val srv = createServer(config)
        server = srv
        assertEquals(port, srv.start())

        val testPayload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val receivedBytes = ByteArray(testPayload.size)
        var bytesRead = 0

        val connection = (URL(requireNotNull(srv.getBroadcastUrl())).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2000
            readTimeout = 2000
        }

        val readerThread = thread {
            connection.inputStream.use { input ->
                bytesRead = input.read(receivedBytes, 0, receivedBytes.size)
            }
        }

        // Allow connection to register
        Thread.sleep(100)
        srv.writeFrame(testPayload, 0, testPayload.size)

        readerThread.join(2000)
        connection.disconnect()

        assertEquals(testPayload.size, bytesRead)
        assertTrue(testPayload.contentEquals(receivedBytes))
    }

    @Test
    fun `start returns minus one when port is occupied`() {
        val socket = ServerSocket(0)
        val occupiedPort = socket.localPort
        try {
            val config = BroadcastSessionConfig.DEFAULT.copy(port = occupiedPort)
            val srv = createServer(config)
            server = srv

            assertEquals(-1, srv.start())
            assertFalse(srv.isAlive)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `stop cleans up server and updates isAlive to false`() {
        val port = findFreePort()
        val config = BroadcastSessionConfig.DEFAULT.copy(port = port)
        val srv = createServer(config)
        server = srv
        srv.start()
        assertTrue(srv.isAlive)

        srv.stop()
        assertFalse(srv.isAlive)
    }

    @Test
    fun `broadcast URL is absent when no LAN address is available`() {
        val srv = BroadcastHttpServer(
            context = context,
            config = BroadcastSessionConfig.DEFAULT,
            lanAddressProvider = { null },
        )

        assertNull(srv.getBroadcastUrl())
    }

    companion object {
        private const val TEST_SERVER_ADDRESS = "127.0.0.1"
    }
}
