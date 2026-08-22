package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1944: the phone-watch stream contract, asserted rather than described.
 *
 * These two classes exist twice - once per module - and the JSON names are the only thing binding the
 * copies together. A mismatch does not fail a build; it fails silently on a watch, which is the most
 * expensive place in this repository to discover anything. So the wire is pinned here, on the JVM.
 */
class WearStreamTransferWireTest {

    private val gson = Gson()

    @Test
    fun `the open intent travels under its wire name`() {
        val json = gson.toJson(payload(openNow = true))
        assertTrue("the field must be named open_now on the wire: $json", json.contains("\"open_now\":true"))
    }

    @Test
    fun `a payload written before the intent existed still reads`() {
        // The exact shape S1799 put on the wire - four fields, no open_now. This is the compatibility
        // claim of the field's default stated as an assertion: the watch must read it as "just store".
        val legacy = """
            {"request_id":"r-1","name":"Jazz FM","url":"https://example.invalid/1","media_kind":"AUDIO"}
        """.trimIndent()

        val parsed = gson.fromJson(legacy, WearStreamTransferPayload::class.java)

        assertEquals("r-1", parsed.requestId)
        assertEquals("AUDIO", parsed.mediaKind)
        assertFalse("an absent intent means today's behaviour, never an open", parsed.openNow)
    }

    @Test
    fun `the four original field names are unchanged`() {
        val json = gson.toJson(payload(openNow = false))
        listOf("request_id", "name", "url", "media_kind").forEach { field ->
            assertTrue("$field is part of the contract and must stay: $json", json.contains("\"$field\""))
        }
    }

    @Test
    fun `every ack outcome keeps its exact wire value`() {
        // Values, not names: the watch writes these strings and the phone compares against them, so a
        // rename that compiles on both sides would still break the pair.
        assertEquals("STORED", WearStreamTransferAck.OUTCOME_STORED)
        assertEquals("UPDATED", WearStreamTransferAck.OUTCOME_UPDATED)
        assertEquals("ERROR", WearStreamTransferAck.OUTCOME_ERROR)
        assertEquals("OPENED", WearStreamTransferAck.OUTCOME_OPENED)
        assertEquals("NOT_FOREGROUND", WearStreamTransferAck.OUTCOME_NOT_FOREGROUND)
    }

    @Test
    fun `an ack round trips with its optional message absent`() {
        val ack = WearStreamTransferAck(requestId = "r-2", outcome = WearStreamTransferAck.OUTCOME_OPENED)
        val parsed = gson.fromJson(gson.toJson(ack), WearStreamTransferAck::class.java)

        assertEquals("r-2", parsed.requestId)
        assertEquals(WearStreamTransferAck.OUTCOME_OPENED, parsed.outcome)
        assertEquals(null, parsed.message)
    }

    private fun payload(openNow: Boolean) = WearStreamTransferPayload(
        requestId = "r-1",
        name = "Jazz FM",
        url = "https://example.invalid/1",
        mediaKind = "AUDIO",
        openNow = openNow,
    )
}
