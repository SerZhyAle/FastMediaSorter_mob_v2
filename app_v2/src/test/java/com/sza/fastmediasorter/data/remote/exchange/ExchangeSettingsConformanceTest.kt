package com.sza.fastmediasorter.data.remote.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Replays the phone against the conformance vectors 01-06 of contract DEVICE-EXCHANGE proposal
 * app-settings-exchange-standard (2026-11-10 set). The vectors are written for `appId` `fms_win`; the codec
 * takes the id as a parameter, so the same bytes prove the `fms_a` wire.
 */
class ExchangeSettingsConformanceTest {

    @Test
    fun `save request encodes byte for byte as vector 01`() {
        val envelope = ExchangeSettingsEnvelopes.buildSaveStandardEnvelope(
            appId = VECTOR_APP_ID,
            formatVersion = FORMAT_VERSION,
            payloadJson = VECTOR_PAYLOAD,
            timestamp = VECTOR_TIMESTAMP
        )

        assertEquals(VECTOR_01_SAVE_REQUEST, envelope.encode())
    }

    @Test
    fun `save request defaults to the fms_a app id and format version 1`() {
        val envelope = ExchangeSettingsEnvelopes.buildSaveStandardEnvelope(
            payloadJson = VECTOR_PAYLOAD,
            timestamp = VECTOR_TIMESTAMP
        )

        assertEquals(ExchangeSettingsEnvelopes.TYPE_SETTINGS_SAVE_STANDARD, envelope.type)
        assertEquals(ExchangeSettingsEnvelopes.APP_ID_FMS_A, envelope.string(ExchangeSettingsEnvelopes.KEY_APP_ID))
        assertEquals(FORMAT_VERSION, envelope.int(ExchangeSettingsEnvelopes.KEY_FORMAT_VERSION))
    }

    @Test
    fun `saved response of vector 02 parses version and timestamp`() {
        val saved = ExchangeSettingsEnvelopes.parseSettingsSaved(DeviceExchangeEnvelope.decode(VECTOR_02_SAVED))

        assertEquals(ExchangeSettingsEnvelopes.SettingsSaved(VECTOR_APP_ID, 1, VECTOR_TIMESTAMP), saved)
    }

    @Test
    fun `get request encodes byte for byte as vector 03`() {
        val envelope = ExchangeSettingsEnvelopes.buildGetStandardEnvelope(VECTOR_APP_ID)

        assertEquals(VECTOR_03_GET_REQUEST, envelope.encode())
    }

    @Test
    fun `payload response of vector 04 parses the stored settings`() {
        val payload = ExchangeSettingsEnvelopes.parseStandardPayload(
            DeviceExchangeEnvelope.decode(VECTOR_04_PAYLOAD)
        )

        assertEquals(
            ExchangeSettingsEnvelopes.SettingsStandardPayload(
                appId = VECTOR_APP_ID,
                formatVersion = FORMAT_VERSION,
                settingsPayload = VECTOR_PAYLOAD,
                updatedAt = VECTOR_TIMESTAMP
            ),
            payload
        )
    }

    @Test
    fun `refused vectors 05 and 06 carry the contract reason codes`() {
        val notFound = DeviceExchangeEnvelope.decode(VECTOR_05_NOT_FOUND)
        val incompatible = DeviceExchangeEnvelope.decode(VECTOR_06_INCOMPATIBLE)

        assertEquals(DeviceExchangeEnvelope.TYPE_REFUSED, notFound.type)
        assertEquals(ExchangeSettingsEnvelopes.REASON_NOT_FOUND, notFound.string(DeviceExchangeEnvelope.KEY_REASON))
        assertEquals(DeviceExchangeEnvelope.TYPE_REFUSED, incompatible.type)
        assertEquals(
            ExchangeSettingsEnvelopes.REASON_VERSION_INCOMPATIBLE,
            incompatible.string(DeviceExchangeEnvelope.KEY_REASON)
        )
    }

    @Test
    fun `parsers reject an envelope of another type`() {
        val refused = DeviceExchangeEnvelope.decode(VECTOR_05_NOT_FOUND)

        assertNull(ExchangeSettingsEnvelopes.parseSettingsSaved(refused))
        assertNull(ExchangeSettingsEnvelopes.parseStandardPayload(refused))
    }

    private companion object {
        const val VECTOR_APP_ID = "fms_win"
        const val FORMAT_VERSION = 1
        const val VECTOR_TIMESTAMP = "2026-11-10T02:00:00Z"
        const val VECTOR_PAYLOAD =
            """{"version":1,"groups":{"appearance":{"theme":"dark","density":"compact"},""" +
                """"general":{"confirmDelete":true}}}"""
        const val ESCAPED_PAYLOAD =
            """{\"version\":1,\"groups\":{\"appearance\":{\"theme\":\"dark\",\"density\":\"compact\"},""" +
                """\"general\":{\"confirmDelete\":true}}}"""

        const val VECTOR_01_SAVE_REQUEST =
            """{"schemaVersion":2,"type":"settings_save_standard","appId":"fms_win","formatVersion":1,""" +
                """"settingsPayload":"$ESCAPED_PAYLOAD","timestamp":"2026-11-10T02:00:00Z"}"""
        const val VECTOR_02_SAVED =
            """{"type":"settings_saved","schemaVersion":2,"appId":"fms_win","version":1,""" +
                """"updatedAt":"2026-11-10T02:00:00Z"}"""
        const val VECTOR_03_GET_REQUEST =
            """{"schemaVersion":2,"type":"settings_get_standard","appId":"fms_win"}"""
        const val VECTOR_04_PAYLOAD =
            """{"type":"settings_standard_payload","schemaVersion":2,"appId":"fms_win","formatVersion":1,""" +
                """"settingsPayload":"$ESCAPED_PAYLOAD","updatedAt":"2026-11-10T02:00:00Z"}"""
        const val VECTOR_05_NOT_FOUND =
            """{"type":"refused","schemaVersion":2,"reason":"not-found",""" +
                """"message":"No standard settings stored for application 'fms_win'."}"""
        const val VECTOR_06_INCOMPATIBLE =
            """{"type":"refused","schemaVersion":2,"reason":"version-incompatible",""" +
                """"message":"Format version 99 is unsupported by application 'fms_win'."}"""
    }
}
