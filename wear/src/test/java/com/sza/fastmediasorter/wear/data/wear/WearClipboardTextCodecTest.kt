package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearClipboardTextCodecTest {

    private val gson = Gson()

    @Test
    fun `a clipboard survives serialise and parse unchanged`() {
        val original = payload(text = "https://example.org/a?b=c")

        val parsed = WearClipboardTextCodec.parse(WearClipboardTextCodec.serialize(original, gson), gson)

        assertTrue("well-formed payload was not parsed", parsed is WearClipboardTextParseResult.Parsed)
        assertEquals(original, (parsed as WearClipboardTextParseResult.Parsed).payload)
    }

    @Test
    fun `a clipboard from an unknown format version is rejected by name`() {
        val future = WearClipboardTextPayload.FORMAT_VERSION + 1
        val json = gson.toJson(payload().copy(formatVersion = future))

        val parsed = WearClipboardTextCodec.parse(json.toByteArray(), gson)

        assertTrue(
            "unknown version was not named as such",
            parsed is WearClipboardTextParseResult.UnsupportedVersion
        )
        assertEquals(future, (parsed as WearClipboardTextParseResult.UnsupportedVersion).version)
    }

    @Test
    fun `a payload missing its text is malformed`() {
        // Written as raw JSON rather than as a copy of the data class: Gson fills fields by
        // reflection and produces an instance whose non-null field is null, which is the case the
        // parser has to survive.
        val json = """{"requestId":"r","formatVersion":1,"sourceDeviceModel":"m","capturedAtEpochMillis":1}"""

        val parsed = WearClipboardTextCodec.parse(json.toByteArray(), gson)

        assertTrue("a payload without text was accepted", parsed is WearClipboardTextParseResult.Malformed)
    }

    @Test
    fun `a clipboard one character over the ceiling is refused`() {
        val tooLong = payload(text = "y".repeat(WearClipboardTextPayload.MAX_TEXT_LENGTH + 1))

        val parsed = WearClipboardTextCodec.parse(WearClipboardTextCodec.serialize(tooLong, gson), gson)

        assertTrue("an oversized clipboard was accepted", parsed is WearClipboardTextParseResult.TooLong)
    }

    @Test
    fun `a clipboard at the ceiling stays under the message limit`() {
        val atCeiling = payload(text = "=".repeat(WearClipboardTextPayload.MAX_TEXT_LENGTH))

        val size = WearClipboardTextCodec.serialize(atCeiling, gson).size

        assertTrue("serialised $size bytes, limit is $MESSAGE_LIMIT_BYTES", size < MESSAGE_LIMIT_BYTES)
    }

    private fun payload(text: String = "line") = WearClipboardTextPayload(
        requestId = "11111111-2222-3333-4444-555555555555",
        sourceDeviceModel = "sdk_gwear_x86_64",
        capturedAtEpochMillis = 1_787_000_000_000L,
        text = text
    )

    private companion object {
        /** The Data Layer per-message ceiling recorded in temp/S1802/message-limit.txt. */
        const val MESSAGE_LIMIT_BYTES = 102_400
    }
}
