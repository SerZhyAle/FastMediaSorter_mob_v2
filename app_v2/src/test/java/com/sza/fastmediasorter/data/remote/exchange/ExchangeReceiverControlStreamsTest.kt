package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** S4421: a non-outgoing entry receives casts on its own stream and answers on that same stream. */
class ExchangeReceiverControlStreamsTest {

    private val entry = DeviceExchangeAccountEntry(
        host = "exchange.example",
        port = 8443,
        login = "second",
        deviceId = "AAAAAAAAAAAAAAAAAAAAAA",
        pinnedCertificate = null,
    )

    private val offer = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_OFFER)
    private val answer = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER)

    @Test
    fun `an offer read on an entry's stream is emitted with that entry and answered on it`() = runBlocking {
        val closed = CountDownLatch(1)
        val session = blockingSession(listOf(offer), closed)
        val client = mockk<DeviceExchangeAccountClient>()
        coEvery { client.openAuthenticated(entry, any()) } returns session
        val streams = ExchangeReceiverControlStreams(client, Dispatchers.IO)

        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(TIMEOUT_MS) { streams.events.first() } }
        val run = launch(Dispatchers.IO) { streams.run(MutableStateFlow(listOf(entry))) }

        val received = event.await()
        assertEquals(entry, received.entry)
        assertEquals(DeviceExchangeEnvelope.TYPE_CAST_OFFER, received.envelope.type)
        assertTrue(streams.send(entry.identity, answer))
        verify { session.send(answer) }
        run.cancelAndJoin()
        assertTrue(closed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a lent stream carries the answers and its cast messages`() = runBlocking {
        val streams = ExchangeReceiverControlStreams(mockk(), Dispatchers.IO)
        val lentSession = mockk<DeviceExchangeSession>(relaxed = true)

        streams.lend(entry, lentSession)
        val event = async(start = CoroutineStart.UNDISPATCHED) { streams.events.first() }
        streams.deliver(entry, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY))
        streams.deliver(entry, offer)

        assertEquals(DeviceExchangeEnvelope.TYPE_CAST_OFFER, withTimeout(TIMEOUT_MS) { event.await() }.envelope.type)
        assertTrue(streams.send(entry.identity, answer))
        verify { lentSession.send(answer) }
        streams.giveBack(entry, lentSession)
        assertFalse(streams.send(entry.identity, answer))
    }

    @Test
    fun `an own stream that connects while the entry is lent is closed and never serves`() = runBlocking {
        val connecting = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val closed = CountDownLatch(1)
        val ownSession = blockingSession(emptyList(), closed)
        val client = mockk<DeviceExchangeAccountClient>()
        coEvery { client.openAuthenticated(entry, any()) } coAnswers {
            connecting.complete(Unit)
            proceed.await()
            ownSession
        }
        val streams = ExchangeReceiverControlStreams(client, Dispatchers.IO)
        val lentSession = mockk<DeviceExchangeSession>(relaxed = true)

        val run = launch(Dispatchers.IO) { streams.run(MutableStateFlow(listOf(entry))) }
        withTimeout(TIMEOUT_MS) { connecting.await() }
        streams.lend(entry, lentSession)
        proceed.complete(Unit)

        assertTrue(closed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertTrue(streams.send(entry.identity, answer))
        verify { lentSession.send(answer) }
        verify(exactly = 0) { ownSession.send(any()) }
        run.cancelAndJoin()
    }

    // Hands out [envelopes], then blocks the read the way a socket does until the session is closed.
    private fun blockingSession(
        envelopes: List<DeviceExchangeEnvelope>,
        closed: CountDownLatch,
    ): DeviceExchangeSession {
        val session = mockk<DeviceExchangeSession>(relaxed = true)
        val queue = ArrayDeque(envelopes)
        every { session.welcome.keepaliveSeconds } returns null
        every { session.receive() } answers {
            queue.removeFirstOrNull() ?: run {
                closed.await()
                null
            }
        }
        every { session.close() } answers { closed.countDown() }
        return session
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
