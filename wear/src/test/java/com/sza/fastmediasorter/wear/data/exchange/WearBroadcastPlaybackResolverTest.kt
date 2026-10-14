package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.google.gson.JsonParser
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback.Reason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** What a tap on a listed broadcast resolves to (contract DEVICE-EXCHANGE 7.7, items CH and CI; LIVE-BROADCAST M). */
class WearBroadcastPlaybackResolverTest {

    private val bridges = WearExchangeRelayBridges(
        object : WearExchangeConnector {
            override fun connect(
                host: String,
                port: Int,
                pinned: String?,
                readTimeoutMs: Int,
                network: Network?,
            ): WearExchangeConnection =
                throw IOException("no server in a unit test")
        },
    )
    private var lanAnswers = true

    private val resolver = WearBroadcastPlaybackResolver(
        bridges,
        object : WearLanEndpointProbe {
            override fun answers(host: String, port: Int): Boolean = lanAnswers
        },
    )

    @After
    fun tearDown() {
        bridges.closeAll()
    }

    @Test
    fun `a reachable LAN endpoint wins over the relay and keeps its address`() {
        val playback = resolve(record(descriptor = LAN_AND_RELAY, mode = "AUDIO_ONLY"))

        val playable = playback as WearBroadcastPlayback.Playable
        assertEquals("http://10.0.0.2:8080/", playable.url)
        assertFalse(playable.isVideo)
    }

    @Test
    fun `a LAN endpoint that does not answer falls to the pinned relay on loopback`() {
        lanAnswers = false

        val playable = resolve(record(descriptor = LAN_AND_RELAY, mode = "VIDEO_AUDIO")) as
            WearBroadcastPlayback.Playable

        assertTrue(playable.url.startsWith("http://127.0.0.1:"))
        assertTrue(playable.url.endsWith("/v2/b/b1/stream"))
        assertTrue(playable.isVideo)
    }

    @Test
    fun `a mode the watch does not decode is unavailable and never played`() {
        val playback = resolve(record(descriptor = LAN_AND_RELAY, mode = "HOLOGRAM"))

        assertEquals(WearBroadcastPlayback.Unavailable(Reason.MODE), playback)
    }

    @Test
    fun `a record with no endpoint the watch implements is unavailable`() {
        val tunnelOnly = """{"endpoints":[{"url":"fmsx://ex.example/b/b1/rtsp","transport":"TUNNEL"}]}"""

        assertEquals(
            WearBroadcastPlayback.Unavailable(Reason.NO_ENDPOINT),
            resolve(record(descriptor = tunnelOnly, mode = "AUDIO_ONLY")),
        )
    }

    @Test
    fun `no endpoint answering is unreachable`() {
        lanAnswers = false
        val lanOnly = """{"endpoints":[{"url":"http://10.0.0.2:8080/","transport":"HTTP"}]}"""

        assertEquals(
            WearBroadcastPlayback.Unavailable(Reason.UNREACHABLE),
            resolve(record(descriptor = lanOnly, mode = "AUDIO_ONLY")),
        )
    }

    @Test
    fun `a protected broadcast is never played from the LAN even when it answers`() {
        val protectedLanOnly = """{"auth":{"scheme":"password-v1","user":"u"},""" +
            """"endpoints":[{"url":"http://10.0.0.2:8080/","transport":"HTTP"}]}"""

        val credentials = WearBroadcastCredentials("u", "p")

        val playback = resolve(record(protectedLanOnly, mode = "AUDIO_ONLY", credentials = credentials))

        assertEquals(WearBroadcastPlayback.Unavailable(Reason.PROTECTED), playback)
    }

    @Test
    fun `a protected broadcast without credentials is unavailable and nothing is asked on the watch`() {
        val protectedRelay = """{"auth":{"scheme":"password-v1","user":"u"},""" +
            """"endpoints":[{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY"}]}"""

        val playback = resolve(record(descriptor = protectedRelay, mode = "AUDIO_ONLY"))

        assertEquals(WearBroadcastPlayback.Unavailable(Reason.PROTECTED), playback)
    }

    @Test
    fun `a protected broadcast with credentials plays on the relay and the url carries no password`() {
        val protectedRelay = """{"auth":{"scheme":"password-v1","user":"alice"},""" +
            """"endpoints":[{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY"}]}"""

        val credentials = WearBroadcastCredentials("alice", "s3cret")

        val playable = resolve(record(protectedRelay, mode = "AUDIO_ONLY", credentials = credentials)) as
            WearBroadcastPlayback.Playable

        assertFalse(playable.url.contains("s3cret"))
        assertFalse(playable.url.contains("alice"))
        assertFalse(playable.url.contains("@"))
    }

    private fun resolve(record: WearDirectoryBroadcast): WearBroadcastPlayback =
        runBlocking { resolver.resolve(record) }

    private fun record(
        descriptor: String,
        mode: String,
        credentials: WearBroadcastCredentials? = null,
    ) = WearDirectoryBroadcast(
        broadcastId = "b1",
        deviceId = "d1",
        title = "Mic",
        mode = mode,
        descriptor = JsonParser.parseString(descriptor).asJsonObject,
        credentials = credentials,
    )

    private companion object {
        const val LAN_AND_RELAY = """{"endpoints":[""" +
            """{"url":"http://10.0.0.2:8080/","transport":"HTTP"},""" +
            """{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY","certFingerprint":"SHA256:p"}]}"""
    }
}
