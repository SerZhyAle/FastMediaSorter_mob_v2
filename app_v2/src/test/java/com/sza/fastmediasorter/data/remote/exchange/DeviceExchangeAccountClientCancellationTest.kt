package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A cancelled [DeviceExchangeAccountClient.openAuthenticated] owns its socket at every blocking stage: a server
 * that withholds `welcome`, a connect still in progress and a connection that only completes after the
 * cancellation all end with the socket closed, the job finished and no stream handed back.
 */
class DeviceExchangeAccountClientCancellationTest {

    private val server = ServerSocket(0)
    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = server.localPort,
        login = "Owner.Home",
        deviceId = DEVICE_ID,
        pinnedCertificate = null,
    )
    private val tokens = object : DeviceExchangeTokenStore {
        override suspend fun token(entry: DeviceExchangeAccountEntry): String = TOKEN
        override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean = true
        override suspend fun forget(entry: DeviceExchangeAccountEntry) = Unit
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `cancelling while welcome is withheld closes the socket`() = runBlocking {
        val accepted = holdAfterHello()
        val client = client(plainConnector())
        var opened: DeviceExchangeSession? = null
        val job = launch(Dispatchers.IO) { opened = client.openAuthenticated(entry) }

        val serverSide = checkNotNull(accepted.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(WAIT_MS) { job.join() }

        assertTrue(job.isCancelled)
        assertNull(opened)
        assertEquals(END_OF_STREAM, serverSide.use { it.getInputStream().read() })
    }

    @Test
    fun `a connection created after the cancellation is closed and never authenticated`() = runBlocking {
        val accepted = acceptOne()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val created = ArrayBlockingQueue<Socket>(1)
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
                entered.countDown()
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                return ExchangeConnection(Socket(host, port).also(created::put), FINGERPRINT)
            }
        }
        val client = client(connector)
        var opened: DeviceExchangeSession? = null
        val job = launch(Dispatchers.IO) { opened = client.openAuthenticated(entry) }

        assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        withTimeout(WAIT_MS) { job.join() }

        assertTrue(job.isCancelled)
        assertNull(opened)
        assertTrue(checkNotNull(created.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isClosed)
        val serverSide = checkNotNull(accepted.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(END_OF_STREAM, serverSide.use { it.getInputStream().read() })
    }

    @Test
    fun `cancelling during the handshake closes the adopted socket at once`() = runBlocking {
        val accepted = acceptOne()
        val adopted = CountDownLatch(1)
        val release = CountDownLatch(1)
        var socket: Socket? = null
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
                error("the client must hand the socket over before blocking")

            override fun connect(
                host: String,
                port: Int,
                trust: ExchangeTrust,
                adopt: (Closeable) -> Unit,
            ): ExchangeConnection {
                val connected = Socket(host, port).also { socket = it }
                adopt(connected)
                adopted.countDown()
                // Stands for a TLS handshake the peer never answers: only the owner closing it ends the wait.
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                return ExchangeConnection(connected, FINGERPRINT)
            }
        }
        val client = client(connector)
        val job = launch(Dispatchers.IO) { client.openAuthenticated(entry) }

        assertTrue(adopted.await(WAIT_SECONDS, TimeUnit.SECONDS))
        job.cancel()
        val serverSide = checkNotNull(accepted.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        // The socket is closed while the connector is still blocked, not when it gives up.
        assertEquals(END_OF_STREAM, serverSide.use { it.getInputStream().read() })
        assertTrue(checkNotNull(socket).isClosed)
        release.countDown()
        withTimeout(WAIT_MS) { job.join() }
        assertTrue(job.isCancelled)
    }

    private fun client(connector: ExchangeConnector) =
        DeviceExchangeAccountClient(connector, tokens, InMemoryE2eKeyStore(), RecordingPinRecorder())

    private fun plainConnector() = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), FINGERPRINT)
    }

    // Reads `hello` and then says nothing, as a server that never welcomes.
    private fun holdAfterHello(): ArrayBlockingQueue<Socket> {
        val accepted = ArrayBlockingQueue<Socket>(1)
        thread(isDaemon = true) {
            val socket = server.accept()
            socket.soTimeout = WAIT_MS.toInt()
            checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream()))
            accepted.put(socket)
        }
        return accepted
    }

    private fun acceptOne(): ArrayBlockingQueue<Socket> {
        val accepted = ArrayBlockingQueue<Socket>(1)
        thread(isDaemon = true) {
            accepted.put(server.accept().apply { soTimeout = WAIT_MS.toInt() })
        }
        return accepted
    }

    private companion object {
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val TOKEN = "tok3n-of-thirty-two-bytes-base64url-xxxxxx"
        const val FINGERPRINT = "SHA256:fake"
        const val END_OF_STREAM = -1
        const val WAIT_SECONDS = 10L
        const val WAIT_MS = 10_000L
    }
}
