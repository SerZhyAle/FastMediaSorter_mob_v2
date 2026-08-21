package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1799: pins the wire JSON names of the stream-transfer payload and ack.
 * The wear module deserializes this JSON with its own mirrored classes, so a
 * field-name drift between modules would fail silently at runtime - this test
 * makes the snake_case contract a compile-time-adjacent fact.
 */
class WearStreamTransferPayloadTest {

    private val gson = Gson()

    @Test
    fun `payload round-trips through JSON with snake_case names`() {
        val payload = WearStreamTransferPayload(
            requestId = "req-1",
            name = "My radio",
            url = "https://example.com/live.m3u8",
            mediaKind = "AUDIO"
        )
        val json = gson.toJson(payload)
        assertTrue(json.contains("\"request_id\""))
        assertTrue(json.contains("\"media_kind\""))
        assertEquals(payload, gson.fromJson(json, WearStreamTransferPayload::class.java))
    }

    @Test
    fun `ack round-trips through JSON with snake_case names`() {
        val ack = WearStreamTransferAck(
            requestId = "req-2",
            outcome = WearStreamTransferAck.OUTCOME_UPDATED,
            message = "replaced existing"
        )
        val json = gson.toJson(ack)
        assertTrue(json.contains("\"request_id\""))
        assertTrue(json.contains("\"outcome\""))
        assertEquals(ack, gson.fromJson(json, WearStreamTransferAck::class.java))
    }

    @Test
    fun `ack without message deserializes to null message`() {
        val ack = gson.fromJson(
            """{"request_id":"req-3","outcome":"STORED"}""",
            WearStreamTransferAck::class.java
        )
        assertEquals("req-3", ack.requestId)
        assertEquals(WearStreamTransferAck.OUTCOME_STORED, ack.outcome)
        assertNull(ack.message)
    }
}
