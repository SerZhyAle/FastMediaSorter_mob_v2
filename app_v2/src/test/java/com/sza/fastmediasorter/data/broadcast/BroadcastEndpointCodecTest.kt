package com.sza.fastmediasorter.data.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec.Protection
import com.sza.fastmediasorter.testing.fakes.FakeBroadcastSecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4675: relay keys and tunnel pins never sit in a stored endpoint list in clear text. */
class BroadcastEndpointCodecTest {

    private val box = FakeBroadcastSecretBox()

    @Test
    fun protect_movesEveryKeyAndPinBehindTheBox() {
        val protected = protect(clearList())

        assertFalse(protected.contains(GROUP_KEY))
        assertFalse(protected.contains(TUNNEL_PIN))
        assertTrue(protected.contains("\"box\""))
        assertFalse(BroadcastEndpointCodec.holdsClearSecret(protected))
    }

    @Test
    fun decode_opensTheBoxBackIntoTheSameEndpoints() {
        val decoded = BroadcastEndpointCodec.decode(protect(clearList()), box)

        assertEquals(endpoints(), decoded)
        assertNull(decoded?.first { it.transport == "RELAY" }?.seal?.box)
    }

    @Test
    fun decode_stillReadsARowSavedBeforeTheKeysWereSealed() {
        assertEquals(endpoints(), BroadcastEndpointCodec.decode(clearList(), box))
        assertEquals(0, box.opens)
    }

    @Test
    fun protect_keepsMembersThisAppDoesNotModel() {
        val withExtra = clearList().replace("\"transport\":\"RELAY\"", "\"transport\":\"RELAY\",\"hint\":\"kept\"")

        assertTrue(protect(withExtra).contains("\"hint\":\"kept\""))
    }

    @Test
    fun protect_leavesAValueWithNoClearSecretAlone() {
        val lan = BroadcastEndpointCodec.encode(listOf(BroadcastEndpointDto(url = LAN_URL)))
        val alreadyProtected = protect(clearList())
        val sealedBefore = box.seals

        listOf(null, "", "  ", "not json", "{}", lan, alreadyProtected).forEach {
            assertEquals(Protection.Unchanged, BroadcastEndpointCodec.protect(it, box))
        }
        assertEquals(sealedBefore, box.seals)
    }

    @Test
    fun protect_failsWhenTheBoxCannotSeal() {
        box.sealOk = false

        assertEquals(Protection.Failed, BroadcastEndpointCodec.protect(clearList(), box))
    }

    @Test
    fun protect_failsWhenWhatItSealedWouldNotOpenAgain() {
        box.openOk = false

        assertEquals(Protection.Failed, BroadcastEndpointCodec.protect(clearList(), box))
    }

    @Test
    fun decode_aBoxThatWillNotOpenLeavesTheSealWithoutKeysAndTheRestOfTheList() {
        val stored = protect(clearList())
        box.openOk = false

        val decoded = BroadcastEndpointCodec.decode(stored, box)

        assertEquals(endpoints().size, decoded?.size)
        val relay = decoded?.first { it.transport == "RELAY" }?.seal
        assertEquals(BroadcastSealDto.VERSION, relay?.v)
        assertNull(relay?.key)
        assertNull(relay?.pin)
        assertNull(relay?.box)
        assertEquals(LAN_URL, decoded?.first { it.transport == "HTTP" }?.url)
    }

    @Test
    fun decodeMetadata_opensNothing() {
        val stored = protect(clearList())
        // protect() opens what it sealed to prove the round trip, so only opens after it count.
        val opensBeforeDecode = box.opens

        val decoded = BroadcastEndpointCodec.decodeMetadata(stored)

        assertEquals(opensBeforeDecode, box.opens)
        assertNull(decoded?.first { it.transport == "RELAY" }?.seal?.key)
        assertEquals(RELAY_URL, decoded?.first { it.transport == "RELAY" }?.url)
    }

    @Test
    fun sameEndpoints_comparesWhatTheBoxHoldsNotItsText() {
        val first = protect(clearList())
        val second = protect(clearList())

        assertNotEquals(first, second)
        assertTrue(BroadcastEndpointCodec.sameEndpoints(first, second, box))
        assertTrue(BroadcastEndpointCodec.sameEndpoints(clearList(), first, box))
        assertTrue(BroadcastEndpointCodec.sameEndpoints(null, null, box))
        assertFalse(BroadcastEndpointCodec.sameEndpoints(first, null, box))
    }

    @Test
    fun sameEndpoints_aDifferentKeyIsADifferentList() {
        val other = clearList().replace(GROUP_KEY, OTHER_KEY)

        assertFalse(BroadcastEndpointCodec.sameEndpoints(protect(clearList()), other, box))
    }

    @Test
    fun encode_neverWritesABox() {
        val foreign = endpoints().map { it.copy(seal = it.seal?.copy(box = "foreign")) }

        assertFalse(BroadcastEndpointCodec.encode(foreign).orEmpty().contains("box"))
    }

    @Test
    fun printedSealAndRowNameNoSecret() {
        val printed = BroadcastSealDto(key = GROUP_KEY, pin = TUNNEL_PIN, box = "sealed").toString()

        assertFalse(printed.contains(GROUP_KEY))
        assertFalse(printed.contains(TUNNEL_PIN))
        assertFalse(printed.contains("sealed"))
        assertNotNull(printed)
    }

    private fun protect(stored: String): String =
        (BroadcastEndpointCodec.protect(stored, box) as Protection.Protected).value

    private fun clearList(): String = requireNotNull(BroadcastEndpointCodec.encode(endpoints()))

    private fun endpoints() = listOf(
        BroadcastEndpointDto(url = LAN_URL, transport = "HTTP"),
        BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", seal = BroadcastSealDto(key = GROUP_KEY)),
        BroadcastEndpointDto(
            url = TUNNEL_URL,
            transport = "TUNNEL",
            inner = INNER,
            seal = BroadcastSealDto(pin = TUNNEL_PIN),
        ),
    )

    private companion object {
        const val GROUP_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val TUNNEL_PIN = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
        const val LAN_URL = "http://192.168.1.97:8768/live-audio.aac"
        const val RELAY_URL = "https://exchange.example.org:8443/v2/b/Zm9vYmFyYmF6cXV4MTIzNA/stream"
        const val TUNNEL_URL = "fmsx://exchange.example.org:8443/b/Zm9vYmFyYmF6cXV4MTIzNA/rtsp"
        const val INNER = "rtsp://192.168.1.97:8554/"
    }
}
