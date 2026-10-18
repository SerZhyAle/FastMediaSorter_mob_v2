package com.sza.fastmediasorter.data.cloud.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class GoogleDriveResumableUploaderRetryTest {

    private class MockHttpServer : Closeable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        val url: String = "http://127.0.0.1:$port/session"

        private val isRunning = AtomicBoolean(true)
        val recordedRequests = ConcurrentLinkedQueue<String>()
        val responders = ConcurrentLinkedQueue<(Socket, String) -> Unit>()
        val requestCount = AtomicInteger(0)

        private val workerThread = thread(start = true, name = "MockHttpServer") {
            while (isRunning.get()) {
                try {
                    val socket = server.accept()
                    handleSocket(socket)
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                    Timber.d(e, "MockHttpServer accept error")
                }
            }
        }

        private fun handleSocket(socket: Socket) {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                recordedRequests.add(requestLine)
                requestCount.incrementAndGet()

                val headers = mutableListOf<String>()
                var line: String? = reader.readLine()
                var contentLength = 0
                while (!line.isNullOrEmpty()) {
                    headers.add(line)
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                // Consume body
                if (contentLength > 0) {
                    var remaining = contentLength
                    val buffer = CharArray(1024)
                    while (remaining > 0) {
                        val read = reader.read(buffer, 0, minOf(remaining, buffer.size))
                        if (read == -1) break
                        remaining -= read
                    }
                }

                val responder = responders.poll()
                if (responder != null) {
                    responder(s, requestLine)
                } else {
                    // Default 500
                    val response = "HTTP/1.1 500 Internal Server Error\r\nConnection: close\r\n" +
                        "Content-Length: 0\r\n\r\n"
                    s.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    s.getOutputStream().flush()
                }
            }
        }

        override fun close() {
            isRunning.set(false)
            try {
                server.close()
            } catch (e: IOException) {
                Timber.d(e, "MockHttpServer close error")
            }
            workerThread.interrupt()
        }
    }

    private lateinit var server: MockHttpServer

    @Before
    fun setUp() {
        server = MockHttpServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `stalled upload loop with repeated unchanged Range responses exhausts retry budget`() = runBlocking {
        // Enqueue repeated 308 with Range: bytes=0-0 (no progress)
        repeat(10) {
            server.responders.add { socket, _ ->
                val response = "HTTP/1.1 308 Resume Incomplete\r\nConnection: close\r\nRange: bytes=0-0\r\n" +
                    "Content-Length: 0\r\n\r\n"
                socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
        }

        val session = GoogleDriveResumableUploader.Session(
            sessionUri = server.url,
            token = "token",
            size = 1024L,
            backoff = com.sza.fastmediasorter.core.util.JitteredBackoff(initialMs = 50L, maxMs = 100L)
        )

        val buffer = ByteArray(1024) { 0x33.toByte() }

        try {
            session.sendChunk(buffer, 1024, 0L)
            fail("Expected IOException when stalled upload exhausts budget")
        } catch (e: IOException) {
            assertTrue(
                "Exception message should mention stalled: ${e.message}",
                e.message?.contains("stalled", ignoreCase = true) == true
            )
        }

        assertTrue("Should have retried multiple times before giving up", server.requestCount.get() >= 6)
    }

    @Test
    fun `cancellation during stalled loop aborts immediately with CancellationException`() = runBlocking {
        // Enqueue 308 responses
        repeat(10) {
            server.responders.add { socket, _ ->
                val response = "HTTP/1.1 308 Resume Incomplete\r\nConnection: close\r\nRange: bytes=0-0\r\n" +
                    "Content-Length: 0\r\n\r\n"
                socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
        }

        val session = GoogleDriveResumableUploader.Session(
            sessionUri = server.url,
            token = "token",
            size = 1024L
        )

        val buffer = ByteArray(1024)
        val job = async(Dispatchers.IO) {
            session.sendChunk(buffer, 1024, 0L)
        }

        // Wait until at least one request arrives
        while (server.requestCount.get() < 1) {
            delay(10)
        }

        job.cancelAndJoin()
        assertTrue("Job should be cancelled", job.isCancelled)
    }

    private fun fastBackoff() = com.sza.fastmediasorter.core.util.JitteredBackoff(
        initialMs = 1L,
        maxMs = 5L
    )

    @Test
    fun `transient probe failure retries and resumes from confirmed offset`() = runBlocking {
        // 1. Data PUT fails with transient 503
        server.responders.add { socket, _ ->
            val response = "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        }

        // 2. Recovery probe fails with transient 503
        server.responders.add { socket, _ ->
            val response = "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        }

        // 3. Second recovery probe succeeds with 308 and confirmed bytes 0-511
        server.responders.add { socket, _ ->
            val response = "HTTP/1.1 308 Resume Incomplete\r\nConnection: close\r\nRange: bytes=0-511\r\n" +
                "Content-Length: 0\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        }

        // 4. Continued PUT from offset 512 completes successfully with 200 OK
        server.responders.add { socket, _ ->
            val body = "{\"id\":\"file_done\",\"size\":\"1024\"}"
            val response = "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\n\r\n$body"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        }

        val session = GoogleDriveResumableUploader.Session(
            sessionUri = server.url,
            token = "token",
            size = 1024L,
            backoff = fastBackoff()
        )

        val buffer = ByteArray(1024)
        val result = session.sendChunk(buffer, 1024, 0L)

        assertNotNull("Expected completed upload JSON", result)
        assertEquals(4, server.requestCount.get())
    }

    @Test
    fun `non transient status probe failure throws immediately without retry`() = runBlocking {
        // 1. Data PUT fails with 503
        server.responders.add { socket, _ ->
            val response = "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        }

        // 2. Status probe returns 401 Unauthorized (non-transient auth error)
        server.responders.add { socket, _ ->
            val response = "HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
        }

        val session = GoogleDriveResumableUploader.Session(
            sessionUri = server.url,
            token = "token",
            size = 1024L,
            backoff = fastBackoff()
        )

        val buffer = ByteArray(1024)

        try {
            session.sendChunk(buffer, 1024, 0L)
            fail("Expected DriveAppDataFailure.AuthRequired")
        } catch (e: DriveAppDataFailure.AuthRequired) {
            assertTrue("Auth error is not transient", !e.isTransient)
        }

        // Only 2 requests made: data PUT + 1 status probe
        assertEquals(2, server.requestCount.get())
    }
}
