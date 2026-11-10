package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject

/**
 * Wire envelopes and keys for exchanging application settings with Media Exchange Server per DEVICE-EXCHANGE
 * PROPOSAL-2026-11-09-app-settings-exchange-standard.
 */
object ExchangeSettingsEnvelopes {

    const val TYPE_SETTINGS_SAVE_STANDARD = "settings_save_standard"
    const val TYPE_SETTINGS_SAVED = "settings_saved"
    const val TYPE_SETTINGS_GET_STANDARD = "settings_get_standard"
    const val TYPE_SETTINGS_STANDARD_PAYLOAD = "settings_standard_payload"

    const val KEY_APP_ID = "appId"
    const val KEY_FORMAT_VERSION = "formatVersion"
    const val KEY_SETTINGS_PAYLOAD = "settingsPayload"
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_VERSION = "version"
    const val KEY_UPDATED_AT = "updatedAt"

    const val APP_ID_FMS_A = "fms_a"
    const val CURRENT_FORMAT_VERSION = 1

    const val REASON_NOT_FOUND = "not-found"
    const val REASON_INVALID_PAYLOAD = "invalid-payload"
    const val REASON_APP_ID_MISMATCH = "app-id-mismatch"
    const val REASON_VERSION_INCOMPATIBLE = "version-incompatible"
    const val REASON_UNAUTHORIZED = "unauthorized"

    data class SettingsSaved(
        val appId: String?,
        val version: Int?,
        val updatedAt: String?
    )

    data class SettingsStandardPayload(
        val appId: String?,
        val formatVersion: Int?,
        val settingsPayload: String?,
        val updatedAt: String?
    )

    fun buildSaveStandardEnvelope(
        appId: String = APP_ID_FMS_A,
        formatVersion: Int = CURRENT_FORMAT_VERSION,
        payloadJson: String,
        timestamp: String
    ): DeviceExchangeEnvelope {
        val members = JsonObject().apply {
            addProperty(KEY_APP_ID, appId)
            addProperty(KEY_FORMAT_VERSION, formatVersion)
            addProperty(KEY_SETTINGS_PAYLOAD, payloadJson)
            addProperty(KEY_TIMESTAMP, timestamp)
        }
        return DeviceExchangeEnvelope(TYPE_SETTINGS_SAVE_STANDARD, members)
    }

    fun buildGetStandardEnvelope(appId: String = APP_ID_FMS_A): DeviceExchangeEnvelope {
        val members = JsonObject().apply {
            addProperty(KEY_APP_ID, appId)
        }
        return DeviceExchangeEnvelope(TYPE_SETTINGS_GET_STANDARD, members)
    }

    fun parseSettingsSaved(envelope: DeviceExchangeEnvelope): SettingsSaved? {
        if (envelope.type != TYPE_SETTINGS_SAVED) return null
        return SettingsSaved(
            appId = envelope.string(KEY_APP_ID),
            version = envelope.int(KEY_VERSION),
            updatedAt = envelope.string(KEY_UPDATED_AT)
        )
    }

    fun parseStandardPayload(envelope: DeviceExchangeEnvelope): SettingsStandardPayload? {
        if (envelope.type != TYPE_SETTINGS_STANDARD_PAYLOAD) return null
        return SettingsStandardPayload(
            appId = envelope.string(KEY_APP_ID),
            formatVersion = envelope.int(KEY_FORMAT_VERSION),
            settingsPayload = envelope.string(KEY_SETTINGS_PAYLOAD),
            updatedAt = envelope.string(KEY_UPDATED_AT)
        )
    }
}
