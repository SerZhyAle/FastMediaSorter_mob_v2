package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeSession
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ExchangeRelayUploaderGenerationTest {

    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = 8080,
        login = "broadcaster",
        deviceId = "dev1",
        pinnedCertificate = null,
        features = listOf(DeviceExchangeAccountEntry.FEATURE_SEALED)
    )

    @Test
    fun `late session after stop closes immediately and leaves accepted null`() = runBlocking {
        val authStartedLatch = CountDownLatch(1)
        val authReleaseLatch = CountDownLatch(1)
        val sessionClosed = AtomicBoolean(false)

        val session = mockk<DeviceExchangeSession>(relaxed = true)
        every { session.close() } answers { sessionClosed.set(true) }

        val client = mockk<DeviceExchangeAccountClient>()
        coEvery { client.openAuthenticated(entry) } coAnswers {
            authStartedLatch.countDown()
            authReleaseLatch.await(5, TimeUnit.SECONDS)
            session
        }

        val keys = mockk<ExchangeRelayKeys>(relaxed = true)
        val credentials = mockk<BroadcastCredentialRegistry>(relaxed = true)
        val testScope = CoroutineScope(Dispatchers.Default)

        val uploader = ExchangeRelayUploader(client, keys, testScope, Dispatchers.Default, credentials)

        uploader.start(entry, "broadcast_gen1", "http://127.0.0.1:8080/stream.aac")
        assertTrue("Auth should have started", authStartedLatch.await(3, TimeUnit.SECONDS))

        uploader.stop()
        authReleaseLatch.countDown()

        var waited = 0
        while (!sessionClosed.get() && waited < 50) {
            delay(50)
            waited++
        }

        assertTrue("Late session must be closed by generation owner", sessionClosed.get())
        assertNull("Accepted must remain null after stop", uploader.accepted.value)
    }

    @Test
    fun `stop and restart preserves new generation accepted and closes late old session`() = runBlocking {
        val gen1AuthStarted = CountDownLatch(1)
        val gen1AuthRelease = CountDownLatch(1)
        val gen1SessionClosed = AtomicBoolean(false)
        val gen2SessionClosed = AtomicBoolean(false)

        val session1 = mockk<DeviceExchangeSession>(relaxed = true)
        every { session1.close() } answers { gen1SessionClosed.set(true) }

        val connection2 = mockk<ExchangeConnection>(relaxed = true)
        every { connection2.output } returns ByteArrayOutputStream()

        val welcome2 = mockk<DeviceExchangeWelcome>(relaxed = true)
        every { welcome2.features } returns listOf(DeviceExchangeAccountEntry.FEATURE_SEALED)

        val session2 = mockk<DeviceExchangeSession>(relaxed = true)
        every { session2.connection } returns connection2
        every { session2.welcome } returns welcome2
        every { session2.close() } answers { gen2SessionClosed.set(true) }

        val acceptEnvelope = mockk<DeviceExchangeEnvelope>()
        every { acceptEnvelope.type } returns DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED
        every { session2.receive() } returns acceptEnvelope

        val client = mockk<DeviceExchangeAccountClient>()
        coEvery { client.openAuthenticated(entry) } coAnswers {
            if (gen1AuthStarted.count > 0) {
                gen1AuthStarted.countDown()
                gen1AuthRelease.await(5, TimeUnit.SECONDS)
                session1
            } else {
                session2
            }
        }

        val ring = ExchangeRelayRing(
            epoch = 1L,
            epochKey = ByteArray(32),
            groupKeys = listOf(ByteArray(32))
        )
        val keys = mockk<ExchangeRelayKeys>(relaxed = true)
        every { keys.newEpoch("gen2") } returns ring
        every { keys.isCurrent("gen2", ring) } returns true

        val credentials = mockk<BroadcastCredentialRegistry>(relaxed = true)
        val testScope = CoroutineScope(Dispatchers.Default)

        val uploader = ExchangeRelayUploader(client, keys, testScope, Dispatchers.Default, credentials)

        // Start Gen 1
        uploader.start(entry, "gen1", "http://127.0.0.1:8080/stream1.aac")
        assertTrue(gen1AuthStarted.await(3, TimeUnit.SECONDS))

        // Start Gen 2 before Gen 1 auth finishes
        val feed = ExchangeVideoRelayFeed()
        feed.open(ByteArray(10))
        uploader.startVideo(entry, "gen2", feed)

        // Release Gen 1 late auth
        gen1AuthRelease.countDown()

        var waited = 0
        while (!gen1SessionClosed.get() && waited < 50) {
            delay(50)
            waited++
        }

        assertTrue("Gen 1 late session must be closed", gen1SessionClosed.get())

        // Check that accepted published gen2 and was not overwritten by gen1
        var acceptedWaited = 0
        while (uploader.accepted.value != "gen2" && acceptedWaited < 50) {
            delay(50)
            acceptedWaited++
        }

        assertEquals("Accepted must remain gen2", "gen2", uploader.accepted.value)
        uploader.stop()
    }

    @Test
    fun `finish ends the upload with the zero-length chunk and does not reconnect`() = runBlocking {
        val output = ByteArrayOutputStream()
        val closed = AtomicBoolean(false)
        val connection = mockk<ExchangeConnection>(relaxed = true)
        every { connection.output } returns output
        val welcome = mockk<DeviceExchangeWelcome>(relaxed = true)
        every { welcome.features } returns listOf(DeviceExchangeAccountEntry.FEATURE_SEALED)
        val session = mockk<DeviceExchangeSession>(relaxed = true)
        every { session.connection } returns connection
        every { session.welcome } returns welcome
        every { session.close() } answers { closed.set(true) }
        val acceptEnvelope = mockk<DeviceExchangeEnvelope>()
        every { acceptEnvelope.type } returns DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED
        every { session.receive() } returns acceptEnvelope
        val opened = AtomicInteger()
        val client = mockk<DeviceExchangeAccountClient>()
        coEvery { client.openAuthenticated(entry) } coAnswers {
            opened.incrementAndGet()
            session
        }
        val ring = ExchangeRelayRing(
            epoch = 1L,
            epochKey = ByteArray(KEY_BYTES),
            groupKeys = listOf(ByteArray(KEY_BYTES)),
        )
        val keys = mockk<ExchangeRelayKeys>(relaxed = true)
        every { keys.newEpoch(FINISHED_ID) } returns ring
        every { keys.isCurrent(FINISHED_ID, ring) } returns true
        val uploadScope = CoroutineScope(Dispatchers.Default)
        val uploader = ExchangeRelayUploader(
            client,
            keys,
            uploadScope,
            Dispatchers.Default,
            mockk<BroadcastCredentialRegistry>(relaxed = true),
        )
        val feed = ExchangeVideoRelayFeed()
        feed.open(ByteArray(HEADER_BYTES))
        uploader.startVideo(entry, FINISHED_ID, feed)
        var waited = 0
        while (uploader.accepted.value != FINISHED_ID && waited < POLL_ROUNDS) {
            feed.offer(ExchangeRelayChunk(ByteArray(CHUNK_BYTES), joinable = true))
            delay(POLL_MS)
            waited++
        }
        val source = launch(Dispatchers.Default) {
            while (isActive) {
                feed.offer(ExchangeRelayChunk(ByteArray(CHUNK_BYTES), joinable = true))
                delay(POLL_MS)
            }
        }

        uploader.finish(FINISH_TIMEOUT_MS)
        source.cancel()

        val bytes = output.toByteArray()
        val ended = bytes.takeLast(END_BYTES).all { it == 0.toByte() }
        assertTrue("the upload must end with the zero-length chunk", ended)
        assertTrue("the session must be closed", closed.get())
        assertEquals("a finished upload must not reconnect", 1, opened.get())
        assertNull(uploader.accepted.value)
        uploadScope.coroutineContext.job.cancelAndJoin()
    }

    private companion object {
        const val FINISHED_ID = "finished"
        const val KEY_BYTES = 32
        const val HEADER_BYTES = 10
        const val CHUNK_BYTES = 188
        const val END_BYTES = 4
        const val POLL_ROUNDS = 50
        const val POLL_MS = 50L
        const val FINISH_TIMEOUT_MS = 3_000L
    }
}
