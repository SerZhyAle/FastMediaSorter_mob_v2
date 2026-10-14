package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastLoopbackBridges
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class ResolveBroadcastPlaybackUseCaseTest {

    /** Answers by host: a trusted handshake, a pin mismatch, or no route. Records the pin each call carried. */
    private class FakeConnector(private val answers: Map<String, String>) : ExchangeConnector {
        val pins = mutableListOf<String?>()

        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            pins += (trust as ExchangeTrust.Verified).pinned
            return when (answers[host]) {
                TRUSTED -> ExchangeConnection(Socket(), PRESENTED)
                CHANGED -> throw ExchangeCertificateChangedException(trust.pinned.orEmpty(), PRESENTED)
                else -> throw IOException("no route")
            }
        }
    }

    private val lanServer = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val deadPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).run { localPort.also { close() } }

    @After
    fun tearDown() = lanServer.close()

    private fun useCase(connector: ExchangeConnector) = ResolveBroadcastPlaybackUseCase(
        BroadcastEndpointProbe(connector),
        BroadcastLoopbackBridges(connector),
        Dispatchers.Unconfined,
    )

    @Test
    fun aRowWithoutAList_playsItsUrlUntouched() = runTest {
        val outcome = useCase(FakeConnector(emptyMap()))(row(url = "http://192.0.2.5/x.aac", endpoints = null))

        assertEquals(Playback.Playable("http://192.0.2.5/x.aac"), outcome)
    }

    @Test
    fun anAnsweringLanEndpoint_winsOverTheExchange() = runTest {
        val lan = "http://127.0.0.1:${lanServer.localPort}/live-audio.aac"
        val connector = FakeConnector(mapOf(RELAY_HOST to TRUSTED))

        val outcome = useCase(connector)(row(lan, listOf(lan(lan), relay(pin = null), tunnel())))

        assertEquals(Playback.Playable(lan), outcome)
        assertTrue("the exchange is never contacted while the LAN answers", connector.pins.isEmpty())
    }

    @Test
    fun aSilentLan_fallsToAnUnpinnedRelayPlayedDirectly() = runTest {
        val outcome = useCase(FakeConnector(mapOf(RELAY_HOST to TRUSTED)))(
            row(deadLan(), listOf(lan(deadLan()), relay(pin = null), tunnel()))
        )

        assertEquals(Playback.Playable(RELAY_URL), outcome)
    }

    @Test
    fun aPinnedRelay_playsThroughTheLoopbackProxyWithThePin() = runTest {
        val connector = FakeConnector(mapOf(RELAY_HOST to TRUSTED))

        val outcome = useCase(connector)(row(deadLan(), listOf(lan(deadLan()), relay(pin = PIN))))

        val url = (outcome as Playback.Playable).url
        assertTrue(url, url.matches(Regex("http://127\\.0\\.0\\.1:\\d+/v2/b/$BROADCAST_ID/stream")))
        assertEquals(listOf<String?>(PIN), connector.pins)
    }

    @Test
    fun aTunnel_playsInnerOnTheForwarderPort() = runTest {
        val outcome = useCase(FakeConnector(mapOf(RELAY_HOST to TRUSTED)))(
            row(deadLan(), listOf(lan(deadLan()), tunnel()))
        )

        val url = (outcome as Playback.Playable).url
        assertTrue(url, url.matches(Regex("rtsp://127\\.0\\.0\\.1:\\d+/live")))
    }

    @Test
    fun aPinMismatch_isATrustRefusalNamingTheServer() = runTest {
        val outcome = useCase(FakeConnector(mapOf(RELAY_HOST to CHANGED)))(
            row(deadLan(), listOf(lan(deadLan()), relay(pin = PIN), tunnel()))
        )

        assertEquals(Playback.TrustRefused("exchange.example.org:8443"), outcome)
    }

    @Test
    fun nothingAnswering_isUnreachable() = runTest {
        val outcome = useCase(FakeConnector(emptyMap()))(row(deadLan(), listOf(lan(deadLan()), relay(pin = PIN))))

        assertEquals(Playback.Unreachable, outcome)
    }

    private suspend operator fun ResolveBroadcastPlaybackUseCase.invoke(row: StreamSourceEntity) =
        invoke(row.url, row.broadcastEndpoints)

    @Test
    fun theCaptionNamesTheExchangeServerOnly() {
        val stored = BroadcastEndpointCodec.encode(listOf(lan(deadLan()), relay(pin = null)))

        assertEquals("exchange.example.org:8443", ResolveBroadcastPlaybackUseCase.exchangeServerLabel(stored))
        val lanOnly = BroadcastEndpointCodec.encode(listOf(lan(deadLan())))
        assertEquals(null, ResolveBroadcastPlaybackUseCase.exchangeServerLabel(lanOnly))
    }

    private fun deadLan() = "http://127.0.0.1:$deadPort/live-audio.aac"

    private fun lan(url: String) = BroadcastEndpointDto(url = url, transport = "HTTP")

    private fun relay(pin: String?) = BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", certFingerprint = pin)

    private fun tunnel() = BroadcastEndpointDto(
        url = "fmsx://$RELAY_HOST:8443/b/$BROADCAST_ID/rtsp",
        transport = "TUNNEL",
        inner = "rtsp://192.168.1.97:8554/live",
    )

    private fun row(url: String, endpoints: List<BroadcastEndpointDto>?) = StreamSourceEntity(
        id = "row",
        url = url,
        title = "Broadcast",
        mediaKind = "AUDIO",
        sourceOrigin = "MANUAL",
        sortIndex = 0,
        addedAt = 0,
        broadcastEndpoints = BroadcastEndpointCodec.encode(endpoints),
    )

    private companion object {
        const val RELAY_HOST = "exchange.example.org"
        const val BROADCAST_ID = "Zm9vYmFyYmF6cXV4MTIzNA"
        const val RELAY_URL = "https://$RELAY_HOST:8443/v2/b/$BROADCAST_ID/stream"
        const val PIN = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
        const val PRESENTED = "SHA256:presented"
        const val TRUSTED = "trusted"
        const val CHANGED = "changed"
    }
}
