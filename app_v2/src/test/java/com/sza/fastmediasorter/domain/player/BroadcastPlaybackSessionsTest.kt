package com.sza.fastmediasorter.domain.player

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BroadcastPlaybackSessionsTest {

    @Test
    fun aRegisteredAddress_findsItsRowUntilCleared() {
        val source = row("row-a", listOf(lan()))

        BroadcastPlaybackSessions.register(PLAYED, source)

        assertEquals("row-a", BroadcastPlaybackSessions.forUrl(PLAYED)?.source?.id)
        BroadcastPlaybackSessions.clear(PLAYED)
        assertNull(BroadcastPlaybackSessions.forUrl(PLAYED))
    }

    @Test
    fun theBroadcastId_comesFromTheRelayTheTunnelOrALiveRowId() {
        BroadcastPlaybackSessions.register("$PLAYED/relay", row("r", listOf(lan(), relay())))
        BroadcastPlaybackSessions.register("$PLAYED/tunnel", row("t", listOf(tunnel())))
        BroadcastPlaybackSessions.register("$PLAYED/live", row("live:$LIVE_ID", null))
        BroadcastPlaybackSessions.register("$PLAYED/lan", row("l", listOf(lan())))

        assertEquals(BROADCAST_ID, BroadcastPlaybackSessions.forUrl("$PLAYED/relay")?.broadcastId)
        assertEquals(BROADCAST_ID, BroadcastPlaybackSessions.forUrl("$PLAYED/tunnel")?.broadcastId)
        assertEquals(LIVE_ID, BroadcastPlaybackSessions.forUrl("$PLAYED/live")?.broadcastId)
        assertNull(BroadcastPlaybackSessions.forUrl("$PLAYED/lan")?.broadcastId)
    }

    @Test
    fun theOldestSession_isEvictedPastTheBound() {
        repeat(EVICTION_PROBE) { BroadcastPlaybackSessions.register("$PLAYED/$it", row("row-$it", null)) }

        assertNull(BroadcastPlaybackSessions.forUrl("$PLAYED/0"))
        assertNotNull(BroadcastPlaybackSessions.forUrl("$PLAYED/${EVICTION_PROBE - 1}"))
    }

    private fun lan() = BroadcastEndpointDto(url = "rtsp://192.168.1.20:8554/", transport = "RTSP")

    private fun relay() = BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY")

    private fun tunnel() = BroadcastEndpointDto(
        url = "fmsx://exchange.example.org:8443/b/$BROADCAST_ID/rtsp",
        transport = "TUNNEL",
        inner = "rtsp://192.168.1.20:8554/",
    )

    private fun row(id: String, endpoints: List<BroadcastEndpointDto>?) = StreamSourceEntity(
        id = id,
        url = "rtsp://192.168.1.20:8554/",
        title = "Broadcast",
        mediaKind = "RTSP",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        addedAt = 0,
        broadcastEndpoints = BroadcastEndpointCodec.encode(endpoints),
    )

    private companion object {
        const val PLAYED = "http://127.0.0.1:40001/v2/b/x/stream"
        const val BROADCAST_ID = "Zm9vYmFyYmF6cXV4MTIzNA"
        const val LIVE_ID = "bGl2ZWlk"
        const val RELAY_URL = "https://exchange.example.org:8443/v2/b/$BROADCAST_ID/stream"
        const val EVICTION_PROBE = 9
    }
}
