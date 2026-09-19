package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearScreenshotRequestCodecTest {

    private val gson = Gson()

    @Test
    fun `a request survives serialise and parse unchanged`() {
        val original = request()

        val parsed = WearScreenshotRequestCodec.parse(
            WearScreenshotRequestCodec.serialize(original, gson),
            gson
        )

        assertTrue("well-formed request was not parsed", parsed is WearScreenshotRequestParseResult.Parsed)
        assertEquals(original, (parsed as WearScreenshotRequestParseResult.Parsed).payload)
    }

    @Test
    fun `a request from an unknown format version is rejected by name`() {
        val future = WearScreenshotRequestPayload.FORMAT_VERSION + 1
        val json = gson.toJson(request().copy(formatVersion = future))

        val parsed = WearScreenshotRequestCodec.parse(json.toByteArray(), gson)

        assertTrue(
            "unknown version was not named as such",
            parsed is WearScreenshotRequestParseResult.UnsupportedVersion
        )
        assertEquals(future, (parsed as WearScreenshotRequestParseResult.UnsupportedVersion).version)
    }

    @Test
    fun `a request missing its id is malformed`() {
        // Written as raw JSON rather than as a copy of the data class: Gson fills fields by
        // reflection and produces an instance whose non-null field is null, which is the case the
        // parser has to survive.
        val json = """{"formatVersion":1,"requestedAtEpochMillis":1}"""

        val parsed = WearScreenshotRequestCodec.parse(json.toByteArray(), gson)

        assertTrue(
            "a request without an id was accepted",
            parsed is WearScreenshotRequestParseResult.Malformed
        )
    }

    @Test
    fun `an ack survives serialise and parse with its outcome intact`() {
        val ack = WearScreenshotRequestAck(
            requestId = REQUEST_ID,
            captured = false,
            fileName = "watch-screen-20260916-120000.png",
            reason = WearScreenshotRefusalReasons.SEND_FAILED
        )

        val parsed = WearScreenshotRequestCodec.parseAck(
            WearScreenshotRequestCodec.serializeAck(ack, gson),
            gson
        )

        assertTrue("well-formed ack was not parsed", parsed is WearScreenshotAckParseResult.Parsed)
        assertEquals(ack, (parsed as WearScreenshotAckParseResult.Parsed).ack)
    }

    @Test
    fun `an ack missing its id is malformed`() {
        val json = """{"captured":true}"""

        val parsed = WearScreenshotRequestCodec.parseAck(json.toByteArray(), gson)

        assertTrue("an ack without an id was accepted", parsed is WearScreenshotAckParseResult.Malformed)
    }

    private fun request() = WearScreenshotRequestPayload(
        requestId = REQUEST_ID,
        requestedAtEpochMillis = 1_787_000_000_000L
    )

    private companion object {
        const val REQUEST_ID = "11111111-2222-3333-4444-555555555555"
    }
}
