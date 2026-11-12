package com.sza.fastmediasorter.domain.model

/**
 * Result of saving standard application settings to the exchange server.
 */
sealed interface ExchangeSettingsSaveResult {
    data class Success(val version: Int?, val updatedAt: String?) : ExchangeSettingsSaveResult
    data class Refused(val reason: String, val message: String?) : ExchangeSettingsSaveResult
    data class Error(val cause: Throwable) : ExchangeSettingsSaveResult
}

/**
 * Result of retrieving standard application settings from the exchange server.
 */
sealed interface ExchangeSettingsGetResult {
    data class Success(
        val formatVersion: Int,
        val settingsPayload: String,
        val updatedAt: String?
    ) : ExchangeSettingsGetResult
    data class Refused(val reason: String, val message: String?) : ExchangeSettingsGetResult
    data class Error(val cause: Throwable) : ExchangeSettingsGetResult
}
