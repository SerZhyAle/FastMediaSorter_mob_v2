package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Persists settings consumed by the next audio broadcast session. Defaults preserve the pre-S2817
 * hard-coded configuration when an existing installation has no matching DataStore keys.
 */
object BroadcastSettingsStore {

    private const val DEFAULT_TITLE = "Phone Audio Stream"
    private const val DEFAULT_BIT_RATE_BPS = 128_000
    private const val DEFAULT_PORT = 8768
    private const val DEFAULT_SAMPLE_RATE_HZ = 44_100
    private const val DEFAULT_CHANNEL_COUNT = 1

    private val keyStreamTitle = stringPreferencesKey("broadcast_stream_title")
    private val keyBitRateBps = intPreferencesKey("broadcast_bit_rate_bps")
    private val keyPort = intPreferencesKey("broadcast_port")
    private val keySampleRateHz = intPreferencesKey("broadcast_sample_rate_hz")
    private val keyChannelCount = intPreferencesKey("broadcast_channel_count")
    private val keyAutoOpenShare = booleanPreferencesKey("broadcast_auto_open_share")
    private val keySourceDeviceId = stringPreferencesKey("broadcast_source_device_id")

    data class Values(
        val streamTitle: String,
        val bitRateBps: Int,
        val port: Int,
        val sampleRateHz: Int,
        val channelCount: Int,
        val autoOpenShare: Boolean,
        val sourceDeviceId: String?,
    )

    fun read(preferences: Preferences): Values = Values(
        streamTitle = preferences[keyStreamTitle] ?: DEFAULT_TITLE,
        bitRateBps = preferences[keyBitRateBps] ?: DEFAULT_BIT_RATE_BPS,
        port = preferences[keyPort] ?: DEFAULT_PORT,
        sampleRateHz = preferences[keySampleRateHz] ?: DEFAULT_SAMPLE_RATE_HZ,
        channelCount = preferences[keyChannelCount] ?: DEFAULT_CHANNEL_COUNT,
        autoOpenShare = preferences[keyAutoOpenShare] ?: true,
        sourceDeviceId = preferences[keySourceDeviceId],
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[keyStreamTitle] = settings.broadcastStreamTitle
        preferences[keyBitRateBps] = settings.broadcastBitRateBps
        preferences[keyPort] = settings.broadcastPort
        preferences[keySampleRateHz] = settings.broadcastSampleRateHz
        preferences[keyChannelCount] = settings.broadcastChannelCount
        preferences[keyAutoOpenShare] = settings.broadcastAutoOpenShare
        settings.broadcastSourceDeviceId?.let { preferences[keySourceDeviceId] = it }
    }
}
