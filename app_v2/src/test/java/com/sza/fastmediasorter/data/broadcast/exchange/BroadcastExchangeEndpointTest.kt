package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSealDto
import com.sza.fastmediasorter.data.broadcast.KeystoreBroadcastSecretBox
import com.sza.fastmediasorter.testing.fakes.FakeBroadcastSecretBox
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

class BroadcastExchangeEndpointTest {

    private val box = FakeBroadcastSecretBox()

    @Before
    fun installBox() {
        BroadcastEndpointCodec.secretBox = box
    }

    @After
    fun restoreBox() {
        BroadcastEndpointCodec.secretBox = KeystoreBroadcastSecretBox
    }

    @Test
    fun relayUrl_parsesServerAndBroadcastId() {
        val relay = BroadcastExchangeEndpoints.parse(
            BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", certFingerprint = PIN, seal = relaySeal())
        ) as BroadcastExchangeEndpoint.Relay

        assertEquals(ExchangeServer("exchange.example.org", 8443), relay.server)
        assertEquals(BROADCAST_ID, relay.broadcastId)
        assertEquals(PIN, relay.certFingerprint)
    }

    @Test
    fun tunnelUrl_parsesSchemeAndKeepsInner() {
        val tunnel = BroadcastExchangeEndpoints.parse(
            BroadcastEndpointDto(url = TUNNEL_URL, transport = "TUNNEL", inner = INNER_RTSP, seal = tunnelSeal())
        ) as BroadcastExchangeEndpoint.Tunnel

        assertEquals(ExchangeServer("exchange.example.org", 8443), tunnel.server)
        assertEquals(BROADCAST_ID, tunnel.broadcastId)
        assertEquals("rtsp", tunnel.scheme)
        assertEquals(INNER_RTSP, tunnel.inner)
        assertNull(tunnel.certFingerprint)
    }

    @Test
    fun tunnelWithoutInner_isSkipped() {
        assertNull(BroadcastExchangeEndpoints.parse(BroadcastEndpointDto(url = TUNNEL_URL, transport = "TUNNEL")))
    }

    @Test
    fun unknownTransportsAndMalformedRelays_areSkipped() {
        assertNull(BroadcastExchangeEndpoints.parse(BroadcastEndpointDto(url = "p2p://x", transport = "P2P")))
        assertNull(
            BroadcastExchangeEndpoints.parse(
                BroadcastEndpointDto(url = "http://exchange.example.org/v2/b/x/stream", transport = "RELAY")
            )
        )
        assertNull(
            BroadcastExchangeEndpoints.parse(
                BroadcastEndpointDto(url = "https://exchange.example.org/other", transport = "RELAY")
            )
        )
    }

    @Test
    fun playOrder_followsTheListedOrderAndSkipsUnknown() {
        val stored = BroadcastEndpointCodec.encode(
            listOf(
                BroadcastEndpointDto(url = LAN_URL, transport = "HTTP"),
                BroadcastEndpointDto(url = "p2p://x", transport = "P2P"),
                BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", seal = relaySeal()),
                BroadcastEndpointDto(url = TUNNEL_URL, transport = "TUNNEL", inner = INNER_RTSP, seal = tunnelSeal()),
            )
        )

        val order = BroadcastExchangeEndpoints.playOrder(stored, LAN_URL)

        assertEquals(3, order.size)
        assertTrue(order[0] is BroadcastExchangeEndpoint.Lan)
        assertTrue(order[1] is BroadcastExchangeEndpoint.Relay)
        assertTrue(order[2] is BroadcastExchangeEndpoint.Tunnel)
        assertEquals("exchange.example.org:8443", BroadcastExchangeEndpoints.exchangeServer(stored)?.label)
    }

    @Test
    fun aRowWithoutAList_isItsOwnUrl() {
        val order = BroadcastExchangeEndpoints.playOrder(null, LAN_URL)
        assertEquals(listOf(BroadcastExchangeEndpoint.Lan(LAN_URL)), order)
        assertNull(BroadcastExchangeEndpoints.exchangeServer(null))
        assertNull(BroadcastEndpointCodec.decode("not json"))
    }

    @Test
    fun innerRewrite_replacesHostAndPortOnly() {
        assertEquals(
            "rtsp://127.0.0.1:40123/live?token=a%2Fb",
            BroadcastExchangeEndpoints.rewriteInner("rtsp://192.168.1.97:8554/live?token=a%2Fb", 40123)
        )
        assertEquals(
            "http://127.0.0.1:40123/live-audio.aac",
            BroadcastExchangeEndpoints.rewriteInner("http://192.0.2.1/live-audio.aac", 40123)
        )
    }

    @Test
    fun loopbackRelayUrl_keepsTheRelayPath() {
        val relay = BroadcastExchangeEndpoints.parse(
            BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", certFingerprint = PIN, seal = relaySeal())
        ) as BroadcastExchangeEndpoint.Relay

        assertEquals(
            "http://127.0.0.1:40999/v2/b/$BROADCAST_ID/stream",
            BroadcastExchangeEndpoints.loopbackRelayUrl(relay, 40999)
        )
    }

    @Test
    fun redaction_isTheFirstEightHexOfSha256() {
        val expected = MessageDigest.getInstance("SHA-256").digest(BROADCAST_ID.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(8)

        val redacted = BroadcastExchangeEndpoints.redact(BROADCAST_ID)

        assertEquals(expected, redacted)
        assertTrue(!redacted.contains(BROADCAST_ID))
    }

    @Test
    fun fingerprint_matchesTheUnpaddedStandardBase64Form() {
        val random = Random(SEED)
        repeat(SAMPLES) { size ->
            val bytes = random.nextBytes(size)
            assertEquals(
                Base64.getEncoder().withoutPadding().encodeToString(bytes),
                ExchangeCertificateFingerprint.base64Unpadded(bytes)
            )
        }
        val der = random.nextBytes(DER_BYTES)
        val expected = "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(der))
        assertEquals(expected, ExchangeCertificateFingerprint.of(der))
    }

    @Test
    fun unsealedAndUnknownServerEndpointsNeverPlay() {
        val relay = BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY")
        val tunnel = BroadcastEndpointDto(url = TUNNEL_URL, transport = "TUNNEL", inner = INNER_RTSP)
        assertNull(BroadcastExchangeEndpoints.parse(relay))
        assertNull(BroadcastExchangeEndpoints.parse(tunnel))
        assertNull(BroadcastExchangeEndpoints.parse(relay.copy(seal = BroadcastSealDto(v = 99, key = GROUP_KEY))))
        assertNull(BroadcastExchangeEndpoints.parse(relay.copy(seal = BroadcastSealDto(key = "invalid"))))
        assertNull(BroadcastExchangeEndpoints.parse(tunnel.copy(seal = BroadcastSealDto())))
        assertTrue(BroadcastExchangeEndpoints.playOrder("not json", RELAY_URL).isEmpty())
        assertTrue(BroadcastExchangeEndpoints.playOrder(null, RELAY_URL).isEmpty())
    }

    // S4675: a saved list keeps its keys behind the device's box; a lost key never turns a server path clear.

    @Test
    fun aSavedSealedList_playsFromItsOpenedKeysAndNamesItsServerWithoutOpeningAnything() {
        val stored = savedBehindTheBox(listOf(lanEndpoint(), relayEndpoint(), tunnelEndpoint()))

        val order = BroadcastExchangeEndpoints.playOrder(stored, LAN_URL)

        assertEquals(listOf("Lan", "Relay", "Tunnel"), order.map { it.javaClass.simpleName })
        assertEquals(GROUP_KEY, (order[1] as BroadcastExchangeEndpoint.Relay).groupKey)
        assertEquals(PIN, (order[2] as BroadcastExchangeEndpoint.Tunnel).tunnelPin)
        val opened = box.opens
        assertEquals(ExchangeServer("exchange.example.org", 8443), BroadcastExchangeEndpoints.exchangeServer(stored))
        assertEquals(opened, box.opens)
    }

    @Test
    fun aSavedSealedListWhoseKeyIsLost_keepsItsLanEndpointAndPlaysNoServerPath() {
        val stored = savedBehindTheBox(listOf(lanEndpoint(), relayEndpoint(), tunnelEndpoint()))
        box.loseKey()

        val order = BroadcastExchangeEndpoints.playOrder(stored, LAN_URL)

        assertEquals(listOf("Lan"), order.map { it.javaClass.simpleName })
        assertEquals(ExchangeServer("exchange.example.org", 8443), BroadcastExchangeEndpoints.exchangeServer(stored))
    }

    @Test
    fun aRowWithNoListAndAnExchangeAddress_neverPlaysThatAddress() {
        assertTrue(BroadcastExchangeEndpoints.playOrder(null, RELAY_URL).isEmpty())
        assertTrue(BroadcastExchangeEndpoints.playOrder("", TUNNEL_URL).isEmpty())
        val lan = BroadcastExchangeEndpoints.playOrder(null, LAN_URL).single() as BroadcastExchangeEndpoint.Lan
        assertEquals(LAN_URL, lan.url)
    }

    @Test
    fun aSealedEndpointNeverPrintsItsKeyOrPin() {
        val relay = BroadcastExchangeEndpoints.parse(relayEndpoint()) as BroadcastExchangeEndpoint.Relay
        val tunnel = BroadcastExchangeEndpoints.parse(tunnelEndpoint()) as BroadcastExchangeEndpoint.Tunnel

        assertFalse(relay.toString().contains(GROUP_KEY))
        assertFalse(tunnel.toString().contains(PIN))
        assertFalse(tunnelEndpoint().toString().contains(PIN))
    }

    private fun lanEndpoint() = BroadcastEndpointDto(url = LAN_URL, transport = "HTTP")

    private fun relayEndpoint() = BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", seal = relaySeal())

    private fun tunnelEndpoint() =
        BroadcastEndpointDto(url = TUNNEL_URL, transport = "TUNNEL", inner = INNER_RTSP, seal = tunnelSeal())

    private fun savedBehindTheBox(endpoints: List<BroadcastEndpointDto>): String {
        val outcome = BroadcastEndpointCodec.protect(BroadcastEndpointCodec.encode(endpoints))
        return (outcome as BroadcastEndpointCodec.Protection.Protected).value
    }

    private fun relaySeal() = BroadcastSealDto(key = GROUP_KEY)

    private fun tunnelSeal() = BroadcastSealDto(pin = PIN)
    private companion object {
        const val GROUP_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val BROADCAST_ID = "Zm9vYmFyYmF6cXV4MTIzNA"
        const val RELAY_URL = "https://exchange.example.org:8443/v2/b/$BROADCAST_ID/stream"
        const val TUNNEL_URL = "fmsx://exchange.example.org:8443/b/$BROADCAST_ID/rtsp"
        const val INNER_RTSP = "rtsp://192.168.1.97:8554/"
        const val LAN_URL = "http://192.168.1.97:8768/live-audio.aac"
        const val PIN = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
        const val SEED = 4160
        const val SAMPLES = 40
        const val DER_BYTES = 900
    }
}
