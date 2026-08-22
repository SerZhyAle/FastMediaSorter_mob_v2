package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1799: outcome mapping of the watch-side store - added vs updated vs error - and the
 * classify-on-blank-kind rule.
 */
class StoreTransferredStreamUseCaseTest {

    private class FakeRepository(
        initial: List<WearStreamChannel> = emptyList(),
        var failOnUpsert: Boolean = false
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

    private fun useCase(repository: FakeRepository) =
        StoreTransferredStreamUseCase(repository, ClassifyWearStreamMediaKindUseCase())

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
}
