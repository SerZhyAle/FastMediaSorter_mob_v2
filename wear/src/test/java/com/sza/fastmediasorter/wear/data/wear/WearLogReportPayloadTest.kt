package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.wear.core.logging.WearLogBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearLogReportPayloadTest {

    private val gson = Gson()

    @Test
    fun `a payload survives serialise and parse unchanged`() {
        val original = payload(logText = "08-18 21:29:24.946 D App started")

        val parsed = WearLogReportCodec.parse(WearLogReportCodec.serialize(original, gson), gson)

        assertTrue("well-formed payload was not parsed", parsed is WearLogReportParseResult.Parsed)
        assertEquals(original, (parsed as WearLogReportParseResult.Parsed).payload)
    }

    @Test
    fun `a payload from an unknown format version is rejected by name`() {
        val future = WearLogReportPayload.FORMAT_VERSION + 1
        val json = gson.toJson(payload().copy(formatVersion = future))

        val parsed = WearLogReportCodec.parse(json.toByteArray(), gson)

        assertTrue(
            "unknown version was not named as such",
            parsed is WearLogReportParseResult.UnsupportedVersion
        )
        assertEquals(future, (parsed as WearLogReportParseResult.UnsupportedVersion).version)
    }

    @Test
    fun `a payload at the buffer ceiling stays under the message limit`() {
        val atCeiling = payload(logText = "y".repeat(WearLogBuffer.MAX_BYTES))

        val size = WearLogReportCodec.serialize(atCeiling, gson).size

        assertTrue("serialised $size bytes, limit is $MESSAGE_LIMIT_BYTES", size < MESSAGE_LIMIT_BYTES)
    }

    @Test
    fun `a ceiling payload of key-value lines stays under the message limit`() {
        // The characters that matter: a default Gson escapes '=' and '&' to a six-byte unicode
        // sequence, and every masked credential the log tree writes ends in '='. Serialising a full
        // buffer of them through an HTML-escaping Gson lands far over the limit, so this is the case
        // that fixes the codec's escaping setting in place.
        val atCeiling = payload(logText = "=".repeat(WearLogBuffer.MAX_BYTES))

        val size = WearLogReportCodec.serialize(atCeiling, gson).size

        assertTrue("serialised $size bytes, limit is $MESSAGE_LIMIT_BYTES", size < MESSAGE_LIMIT_BYTES)
    }

    private fun payload(logText: String = "line") = WearLogReportPayload(
        requestId = "11111111-2222-3333-4444-555555555555",
        appVersionName = "1.2.3",
        appVersionCode = 42L,
        deviceModel = "sdk_gwear_x86_64",
        androidRelease = "17",
        capturedAtEpochMillis = 1_787_000_000_000L,
        logText = logText
    )

    private companion object {
        /** The Data Layer per-message ceiling recorded in temp/S1802/message-limit.txt. */
        const val MESSAGE_LIMIT_BYTES = 102_400
    }
}
