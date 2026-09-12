package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.listen.ListenRequestRegistry
import com.sza.fastmediasorter.wear.domain.listen.ListenRequester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2550: the listening session's wire contract, which crosses a process and a version boundary.
 *
 * These are the cases where a wrong answer is worse than no answer: a refusal read as an invitation,
 * and one request answered twice.
 */
class ListenSessionPayloadCodecTest {

    private val codec = ListenSessionPayloadCodec(Gson())

    @Test
    fun `a served address survives the round trip`() {
        val encoded = codec.encodeAck(ListenAckPayload.serving("req-1", "192.168.1.42", 45123))

        val decoded = codec.decodeAck(encoded)

        assertEquals("192.168.1.42", decoded?.host)
        assertEquals(45123, decoded?.port)
        assertNull(decoded?.refusal)
    }

    @Test
    fun `a refusal survives the round trip and carries no address`() {
        val encoded = codec.encodeAck(ListenAckPayload.refused("req-1", ListenRefusal.DECLINED))

        val decoded = codec.decodeAck(encoded)

        assertEquals(ListenRefusal.DECLINED, decoded?.refusal)
        assertEquals(0, decoded?.port)
    }

    @Test
    fun `not on wifi survives the round trip`() {
        val encoded = codec.encodeAck(ListenAckPayload.refused("req-1", ListenRefusal.NOT_ON_WIFI))

        val decoded = codec.decodeAck(encoded)

        assertEquals(ListenRefusal.NOT_ON_WIFI, decoded?.refusal)
    }

    /**
     * The case an older build meets after a newer watch ships a reason it has no name for: Gson maps
     * the unknown constant to null, and null is how this payload says "serving".
     */
    @Test
    fun `a refusal this build cannot name does not read as an invitation to play`() {
        val fromFutureBuild =
            """{"requestId":"req-1","host":"","port":0,"refusal":"MICROPHONE_BUSY"}"""

        val decoded = codec.decodeAck(fromFutureBuild.toByteArray(Charsets.UTF_8))

        assertEquals(ListenRefusal.UNKNOWN, decoded?.refusal)
    }

    @Test
    fun `an undecodable payload is null rather than an exception`() {
        assertNull(codec.decodeCommand("not json at all".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `only one of two racing answers takes the requester`() {
        val registry = ListenRequestRegistry()
        registry.remember(ListenRequester(nodeId = "node-1", requestId = "req-1"))

        val first = registry.take()
        val second = registry.take()

        assertEquals("req-1", first?.requestId)
        assertNull(second)
    }
}
