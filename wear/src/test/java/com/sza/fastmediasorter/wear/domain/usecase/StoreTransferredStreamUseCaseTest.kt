package com.sza.fastmediasorter.wear.domain.usecase

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPassStore
import com.sza.fastmediasorter.wear.data.exchange.WearStoredBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastHandoffPayload
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPass
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1799: outcome mapping of the watch-side store - added vs updated vs error - and the
 * classify-on-blank-kind rule.
 */
class StoreTransferredStreamUseCaseTest {

    private class FakeRepository(
        initial: List<WearStreamChannel> = emptyList(),
        var failOnUpsert: Boolean = false,
        var throwCancellation: Boolean = false
    ) : WearStreamChannelRepository {
        val channels = initial.toMutableList()

        override suspend fun getAllChannels(): List<WearStreamChannel> = channels.toList()
        override fun observeChannels(): Flow<List<WearStreamChannel>> = MutableStateFlow(channels.toList())
        override suspend fun saveChannels(channels: List<WearStreamChannel>) {
            this.channels.clear()
            this.channels.addAll(channels)
        }

        override suspend fun clear() = channels.clear()

        override suspend fun upsertChannel(channel: WearStreamChannel): Boolean {
            if (throwCancellation) throw kotlinx.coroutines.CancellationException("Job cancelled")
            check(!failOnUpsert) { "store broken" }
            val index = channels.indexOfFirst { it.url == channel.url }
            return if (index < 0) {
                channels.add(channel)
                true
            } else {
                channels[index] = channel
                false
            }
        }
    }

    private class FakePassStore(var refuseWrites: Boolean = false) : WearBroadcastPassStore {
        val stored = mutableMapOf<String, WearStoredBroadcast>()

        override suspend fun get(url: String): WearStoredBroadcast? = stored[url]

        override suspend fun save(url: String, value: WearStoredBroadcast): Boolean {
            if (!refuseWrites) stored[url] = value
            return !refuseWrites
        }

        override suspend fun forget(url: String) {
            stored.remove(url)
        }

        override suspend fun retainOnly(urls: Set<String>) {
            stored.keys.retainAll(urls)
        }
    }

    private val passStore = FakePassStore()

    private fun useCase(repository: FakeRepository) =
        StoreTransferredStreamUseCase(
            repository = repository,
            classifier = ClassifyWearStreamMediaKindUseCase(),
            requestWearTileRefreshUseCase = mockk(relaxed = true),
            passStore = passStore
        )

    private fun endpoint(transport: String, url: String) = JsonObject().apply {
        addProperty("transport", transport)
        addProperty("url", url)
    }

    private fun descriptor(vararg endpoints: JsonObject, protected: Boolean = true) = JsonObject().apply {
        addProperty("mode", "AUDIO_ONLY")
        add("endpoints", JsonArray().apply { endpoints.forEach(::add) })
        if (protected) {
            add("auth", JsonObject().apply { addProperty("scheme", "password-v1") })
        }
    }

    private val relay = endpoint("RELAY", "https://relay.example/v2/b/b1/stream")

    private fun broadcast(
        descriptor: JsonObject,
        pass: WearBroadcastPass? = WearBroadcastPass("own", "secret")
    ) = WearBroadcastHandoffPayload(
        requestId = "req-2",
        name = "Phone live",
        url = "http://192.168.1.20:8080/live",
        mediaKind = "AUDIO",
        broadcastId = "b1",
        descriptor = descriptor,
        pass = pass
    )

    private fun payload(
        url: String = "https://radio.example/stream",
        mediaKind: String = "AUDIO",
        requestId: String = "req-1"
    ) = WearStreamTransferPayload(requestId = requestId, name = "My stream", url = url, mediaKind = mediaKind)

    @Test
    fun `new url is stored with phone origin`() = runBlocking {
        val repository = FakeRepository()
        val ack = useCase(repository)(payload()).ack
        assertEquals(WearStreamTransferAck.OUTCOME_STORED, ack.outcome)
        assertEquals("req-1", ack.requestId)
        assertEquals(WearStreamChannel.ORIGIN_PHONE, repository.channels.single().origin)
    }

    @Test
    fun `existing url is updated, not duplicated`() = runBlocking {
        val existing = WearStreamChannel(
            id = "old",
            name = "Old name",
            url = "https://radio.example/stream",
            mediaKind = "AUDIO"
        )
        val repository = FakeRepository(initial = listOf(existing))
        val ack = useCase(repository)(payload()).ack
        assertEquals(WearStreamTransferAck.OUTCOME_UPDATED, ack.outcome)
        assertEquals(1, repository.channels.size)
        assertEquals("My stream", repository.channels.single().name)
    }

    @Test
    fun `blank media kind is classified from the url`() = runBlocking {
        val repository = FakeRepository()
        useCase(repository)(payload(url = "https://tv.example/live.m3u8", mediaKind = ""))
        assertEquals("VIDEO", repository.channels.single().mediaKind)
    }

    @Test
    fun `S1944 - an unrecognised media kind is re-derived from the url, not stored raw`() = runBlocking {
        // Before this, only a BLANK kind was classified. A non-blank value the watch does not act on -
        // a typo, or a kind a newer phone knows - was stored verbatim and then routed to the audio
        // player, because playback treats "not VIDEO and not RTSP" as audio without ever looking at
        // the URL. An "open on watch" landing in the wrong player is the visible form of that.
        val repository = FakeRepository()
        useCase(repository)(payload(url = "https://tv.example/live.m3u8", mediaKind = "viedo"))
        assertEquals("VIDEO", repository.channels.single().mediaKind)
    }

    @Test
    fun `S1944 - a recognised media kind is trusted as sent`() = runBlocking {
        // The phone stays the authority when it says something the watch understands: a radio station
        // whose URL happens to end in .mp4 must not be re-derived into the video player.
        val repository = FakeRepository()
        useCase(repository)(payload(url = "https://radio.example/stream.mp4", mediaKind = "AUDIO"))
        assertEquals("AUDIO", repository.channels.single().mediaKind)
    }

    @Test
    fun `S1944 - the stored channel comes back beside the ack`() = runBlocking {
        // The listener needs the record the list would have used, not the raw payload, to decide which
        // player to open.
        val repository = FakeRepository()
        val result = useCase(repository)(payload())
        assertEquals(repository.channels.single().url, result.channel?.url)
    }

    @Test
    fun `blank url answers an error ack without touching the store`() = runBlocking {
        val repository = FakeRepository()
        val ack = useCase(repository)(payload(url = " ")).ack
        assertEquals(WearStreamTransferAck.OUTCOME_ERROR, ack.outcome)
        assertEquals(0, repository.channels.size)
    }

    @Test
    fun `persistence failure answers an error ack with the cause`() = runBlocking {
        val repository = FakeRepository(failOnUpsert = true)
        val ack = useCase(repository)(payload()).ack
        assertEquals(WearStreamTransferAck.OUTCOME_ERROR, ack.outcome)
        assertEquals("store broken", ack.message)
    }

    @Test
    fun `success ack carries no message`() = runBlocking {
        val ack = useCase(FakeRepository())(payload()).ack
        assertNull(ack.message)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `cancellation exception is rethrown rather than converted to error ack`() {
        runBlocking {
            val repository = FakeRepository(throwCancellation = true)
            useCase(repository)(payload())
        }
    }

    @Test
    fun `S4261 - a protected broadcast with its pass is stored with the descriptor kept apart`() = runBlocking {
        val repository = FakeRepository()

        val result = useCase(repository).storeBroadcast(broadcast(descriptor(relay)))

        assertEquals(WearStreamTransferAck.OUTCOME_STORED, result.ack.outcome)
        assertEquals(true, repository.channels.single().hasBroadcast)
        assertEquals("secret", passStore.stored.getValue("http://192.168.1.20:8080/live").pass?.password)
    }

    @Test
    fun `S4261 - a protected broadcast without its pass is unplayable and stores nothing`() = runBlocking {
        val repository = FakeRepository()

        val result = useCase(repository).storeBroadcast(broadcast(descriptor(relay), pass = null))

        assertEquals(WearStreamTransferAck.OUTCOME_UNPLAYABLE, result.ack.outcome)
        assertEquals(0, repository.channels.size)
        assertEquals(0, passStore.stored.size)
    }

    @Test
    fun `S4261 - a tunnel-only broadcast is unplayable`() = runBlocking {
        val repository = FakeRepository()
        val tunnel = endpoint("TUNNEL", "https://tunnel.example/t/abc")

        val result = useCase(repository).storeBroadcast(broadcast(descriptor(tunnel)))

        assertEquals(WearStreamTransferAck.OUTCOME_UNPLAYABLE, result.ack.outcome)
        assertNull(result.channel)
        assertEquals(0, repository.channels.size)
    }

    @Test
    fun `S4261 - a refused secret write is an error and stores no channel`() = runBlocking {
        val repository = FakeRepository()
        passStore.refuseWrites = true

        val result = useCase(repository).storeBroadcast(broadcast(descriptor(relay)))

        assertEquals(WearStreamTransferAck.OUTCOME_ERROR, result.ack.outcome)
        assertEquals("secret store refused", result.ack.message)
        assertEquals(0, repository.channels.size)
    }

    @Test
    fun `S4261 - a plain transfer after a broadcast forgets the stored pass`() = runBlocking {
        val repository = FakeRepository()
        val store = useCase(repository)
        store.storeBroadcast(broadcast(descriptor(relay)))
        assertNotNull(passStore.stored["http://192.168.1.20:8080/live"])

        store(payload(url = "http://192.168.1.20:8080/live"))

        assertNull(repository.channels.single().hasBroadcast)
        assertNull(passStore.stored["http://192.168.1.20:8080/live"])
    }
}
