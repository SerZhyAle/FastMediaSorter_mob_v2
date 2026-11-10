package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * Client for standard settings exchange with Media Exchange Server.
 */
@Singleton
class DeviceExchangeSettingsClient @Inject constructor(
    private val accountClient: DeviceExchangeAccountClient
) {

    suspend fun saveStandard(
        entry: DeviceExchangeAccountEntry,
        payloadJson: String,
        formatVersion: Int = ExchangeSettingsEnvelopes.CURRENT_FORMAT_VERSION,
        timestamp: String = currentIsoTimestamp()
    ): ExchangeSettingsSaveResult {
        var session: DeviceExchangeSession? = null
        return try {
            session = accountClient.openAuthenticated(entry)
            val envelope = ExchangeSettingsEnvelopes.buildSaveStandardEnvelope(
                appId = ExchangeSettingsEnvelopes.APP_ID_FMS_A,
                formatVersion = formatVersion,
                payloadJson = payloadJson,
                timestamp = timestamp
            )
            session.send(envelope)
            val answer = session.receive() ?: throw IOException("Server closed connection without answer")
            when (answer.type) {
                ExchangeSettingsEnvelopes.TYPE_SETTINGS_SAVED -> {
                    val saved = ExchangeSettingsEnvelopes.parseSettingsSaved(answer)
                    ExchangeSettingsSaveResult.Success(saved?.version, saved?.updatedAt)
                }
                DeviceExchangeEnvelope.TYPE_REFUSED -> {
                    val reason = answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: "unknown"
                    val message = answer.string(DeviceExchangeEnvelope.KEY_MESSAGE)
                    ExchangeSettingsSaveResult.Refused(reason, message)
                }
                else -> {
                    ExchangeSettingsSaveResult.Refused(
                        "unexpected-response",
                        "Unexpected envelope type: ${answer.type}"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Exchange saveStandard IO error")
            ExchangeSettingsSaveResult.Error(e)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Exchange saveStandard state error")
            ExchangeSettingsSaveResult.Error(e)
        } finally {
            session?.close()
        }
    }

    suspend fun getStandard(
        entry: DeviceExchangeAccountEntry,
        appId: String = ExchangeSettingsEnvelopes.APP_ID_FMS_A
    ): ExchangeSettingsGetResult {
        var session: DeviceExchangeSession? = null
        return try {
            session = accountClient.openAuthenticated(entry)
            val envelope = ExchangeSettingsEnvelopes.buildGetStandardEnvelope(appId)
            session.send(envelope)
            val answer = session.receive() ?: throw IOException("Server closed connection without answer")
            when (answer.type) {
                ExchangeSettingsEnvelopes.TYPE_SETTINGS_STANDARD_PAYLOAD -> {
                    val payload = ExchangeSettingsEnvelopes.parseStandardPayload(answer)
                    if (payload != null && payload.settingsPayload != null) {
                        ExchangeSettingsGetResult.Success(
                            formatVersion = payload.formatVersion ?: ExchangeSettingsEnvelopes.CURRENT_FORMAT_VERSION,
                            settingsPayload = payload.settingsPayload,
                            updatedAt = payload.updatedAt
                        )
                    } else {
                        ExchangeSettingsGetResult.Refused("invalid-payload", "Missing settingsPayload in response")
                    }
                }
                DeviceExchangeEnvelope.TYPE_REFUSED -> {
                    val reason = answer.string(DeviceExchangeEnvelope.KEY_REASON) ?: "unknown"
                    val message = answer.string(DeviceExchangeEnvelope.KEY_MESSAGE)
                    ExchangeSettingsGetResult.Refused(reason, message)
                }
                else -> {
                    ExchangeSettingsGetResult.Refused(
                        "unexpected-response",
                        "Unexpected envelope type: ${answer.type}"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Exchange getStandard IO error")
            ExchangeSettingsGetResult.Error(e)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Exchange getStandard state error")
            ExchangeSettingsGetResult.Error(e)
        } finally {
            session?.close()
        }
    }

    private fun currentIsoTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date())
    }
}
