package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeBroadcastRecords
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlEvent
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlStreams
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeE2e
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEnvelope
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSession
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSessionState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4339: the coordinator's endpoint ownership. Every terminal answer - a `cast-stop` on a pending offer, the
 * answer window's expiry, a decline, a refused start - must release the offer's relay endpoint, while the
 * accepted answer's dismiss must keep it into play. Driven by the real control envelopes on a virtual-time
 * scheduler; the playback service start and the notification are the seams.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearExchangeCastCoordinatorTest {

    private val controlEvents = MutableSharedFlow<WearExchangeControlEvent>(extraBufferCapacity = 8)
    private val playbackSessionFlow = MutableStateFlow<WearBackgroundSession?>(null)
    private val notifier = mockk<WearExchangeCastNotifier>()
    private val serviceStarter = mockk<WearCastServiceStarter>()

    private fun coordinator(scope: CoroutineScope, startsService: Boolean = true): WearExchangeCastCoordinator {
        val streams = mockk<WearExchangeControlStreams> {
            every { events } returns controlEvents
            every { send(any(), any()) } returns true
        }
        val entries = mockk<WearExchangeEntryStore> {
            every { entries } returns flowOf(emptyList())
        }
        every { notifier.ask(any(), any()) } returns true
        every { notifier.cancel() } just runs
        every { serviceStarter.start(any(), any()) } returns startsService
        return WearExchangeCastCoordinator(
            context = mockk<Context>(relaxed = true),
            streams = streams,
            connector = mockk<WearExchangeConnector>(),
            entries = entries,
            e2e = mockk<WearExchangeE2e>(),
            notifier = notifier,
            serviceStarter = serviceStarter,
            playbackSession = mockk<WearBackgroundSessionState> { every { session } returns playbackSessionFlow },
            scope = scope,
        )
    }

    @Test
    fun `repeated pending offers withdrawn by cast-stop retain nothing`() = runTest {
        val coordinator = coordinator(backgroundScope)
        val follow = launch { coordinator.follow() }
        runCurrent()

        repeat(3) { round ->
            val broadcastId = "b$round"
            assertTrue(controlEvents.tryEmit(offerEvent(broadcastId)))
            runCurrent()
            assertEquals(1, coordinator.retainedEndpointCount)

            assertTrue(controlEvents.tryEmit(stopEvent(broadcastId)))
            runCurrent()
            assertEquals(0, coordinator.retainedEndpointCount)
        }
        follow.cancel()
    }

    @Test
    fun `an accepted offer keeps its endpoint into play and releases it at the stop`() = runTest {
        val coordinator = coordinator(backgroundScope)
        val follow = launch { coordinator.follow() }
        runCurrent()

        assertTrue(controlEvents.tryEmit(offerEvent("b1")))
        runCurrent()
        assertEquals(1, coordinator.retainedEndpointCount)

        coordinator.onUserAnswer("b1", accepted = true)
        runCurrent()

        verify { serviceStarter.start(match { it.contains("127.0.0.1") }, "Phone") }
        assertEquals(1, coordinator.retainedEndpointCount)
        assertTrue(coordinator.playing.value)

        assertTrue(controlEvents.tryEmit(stopEvent("b1")))
        runCurrent()
        assertEquals(0, coordinator.retainedEndpointCount)
        assertFalse(coordinator.playing.value)
        follow.cancel()
    }

    @Test
    fun `a refused start releases the endpoint and unmarks playing`() = runTest {
        val coordinator = coordinator(backgroundScope, startsService = false)
        val follow = launch { coordinator.follow() }
        runCurrent()

        assertTrue(controlEvents.tryEmit(offerEvent("b1")))
        runCurrent()
        coordinator.onUserAnswer("b1", accepted = true)
        runCurrent()

        assertEquals(0, coordinator.retainedEndpointCount)
        assertFalse(coordinator.playing.value)
        verify { notifier.cancel() }
        follow.cancel()
    }

    @Test
    fun `an expired question releases its endpoint`() = runTest {
        val coordinator = coordinator(backgroundScope)
        val follow = launch { coordinator.follow() }
        runCurrent()

        assertTrue(controlEvents.tryEmit(offerEvent("b1")))
        runCurrent()
        assertEquals(1, coordinator.retainedEndpointCount)

        advanceTimeBy(WearExchangeCastNotifier.ANSWER_WINDOW_MS)
        runCurrent()

        assertEquals(0, coordinator.retainedEndpointCount)
        verify { notifier.cancel() }
        follow.cancel()
    }

    @Test
    fun `a declined offer releases its endpoint`() = runTest {
        val coordinator = coordinator(backgroundScope)
        val follow = launch { coordinator.follow() }
        runCurrent()

        assertTrue(controlEvents.tryEmit(offerEvent("b1")))
        runCurrent()
        assertEquals(1, coordinator.retainedEndpointCount)

        coordinator.onUserAnswer("b1", accepted = false)
        runCurrent()

        assertEquals(0, coordinator.retainedEndpointCount)
        follow.cancel()
    }

    private fun offerEvent(broadcastId: String): WearExchangeControlEvent {
        val endpoint = JsonObject().apply {
            addProperty("transport", WearExchangeBroadcastRecords.TRANSPORT_RELAY)
            addProperty("mode", "AUDIO_ONLY")
            addProperty("url", "https://phone.example/v2/b/$broadcastId/stream")
        }
        val descriptor = JsonObject().apply {
            addProperty("mode", "AUDIO_ONLY")
            add("endpoints", JsonArray().apply { add(endpoint) })
        }
        val broadcast = JsonObject().apply {
            addProperty("broadcastId", broadcastId)
            addProperty("title", "Phone")
            add("descriptor", descriptor)
        }
        return WearExchangeControlEvent(
            identity = "ex:7443/owner",
            envelope = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CAST_OFFER) {
                addProperty("broadcastId", broadcastId)
                add("broadcast", broadcast)
            },
        )
    }

    private fun stopEvent(broadcastId: String): WearExchangeControlEvent = WearExchangeControlEvent(
        identity = "ex:7443/owner",
        envelope = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CAST_STOP) {
            addProperty("broadcastId", broadcastId)
        },
    )
}
