package com.sza.fastmediasorter.data.repository.settings

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DEFAULT_BROADCAST_STREAM_TITLE
import com.sza.fastmediasorter.domain.model.LEGACY_BROADCAST_STREAM_TITLE
import timber.log.Timber

/**
 * Persists settings consumed by the next audio broadcast session. Defaults preserve the pre-S2817
 * hard-coded configuration when an existing installation has no matching DataStore keys.
 */
object BroadcastSettingsStore {

    private const val UNKNOWN_MODEL = "unknown"
    private const val DEFAULT_BIT_RATE_BPS = 128_000
    private const val DEFAULT_PORT = 8768
    private const val DEFAULT_SAMPLE_RATE_HZ = 44_100
    private const val DEFAULT_CHANNEL_COUNT = 1

    private val keyEnableBroadcasting = booleanPreferencesKey("broadcast_enabled")
    private val keyStreamTitle = stringPreferencesKey("broadcast_stream_title")
    private val keyBitRateBps = intPreferencesKey("broadcast_bit_rate_bps")
    private val keyPort = intPreferencesKey("broadcast_port")
    private val keySampleRateHz = intPreferencesKey("broadcast_sample_rate_hz")
    private val keyChannelCount = intPreferencesKey("broadcast_channel_count")
    private val keyAutoOpenShare = booleanPreferencesKey("broadcast_auto_open_share")
    private val keySourceDeviceId = stringPreferencesKey("broadcast_source_device_id")

    // S3038: camera/microphone defaults and video quality settings.
    private val keyCameraEnabled = booleanPreferencesKey("broadcast_camera_enabled")
    private val keyMicrophoneEnabled = booleanPreferencesKey("broadcast_microphone_enabled")
    private val keyVideoWidth = intPreferencesKey("broadcast_video_width")
    private val keyVideoHeight = intPreferencesKey("broadcast_video_height")
    private val keyVideoFps = intPreferencesKey("broadcast_video_fps")
    private val keyVideoBitrateBps = intPreferencesKey("broadcast_video_bitrate_bps")

    // S3049: microphone digital PCM gain percentage (50% - 400%, default 100%).
    private val keyMicGainPercent = intPreferencesKey("broadcast_mic_gain_percent")

    data class Values(
        val enableBroadcasting: Boolean,
        val streamTitle: String,
        val bitRateBps: Int,
        val port: Int,
        val sampleRateHz: Int,
        val channelCount: Int,
        val autoOpenShare: Boolean,
        val sourceDeviceId: String?,
        val cameraEnabled: Boolean,
        val microphoneEnabled: Boolean,
        val videoWidth: Int,
        val videoHeight: Int,
        val videoFps: Int,
        val videoBitrateBps: Int,
        val micGainPercent: Int,
    )

    fun defaultDeviceTitle(context: Context? = null): String {
        val deviceName = if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } catch (e: SecurityException) {
                Timber.w(e, "BroadcastSettingsStore: failed to read device name")
                null
            }
        } else {
            null
        }
        val model = try { Build.MODEL } catch (_: Throwable) { null }
        // S3173: the last fallback is a translated resource wherever a Context exists, so a phone
        // reporting no name and no model still broadcasts under a localized neutral title.
        return deviceName?.takeUnless { it.isBlank() }
            ?: model?.takeUnless { it.isBlank() || it == UNKNOWN_MODEL }
            ?: context?.getString(R.string.broadcast_default_stream_title)
            ?: DEFAULT_BROADCAST_STREAM_TITLE
    }

    fun read(preferences: Preferences, context: Context? = null): Values = Values(
        enableBroadcasting = preferences[keyEnableBroadcasting] ?: false,
        // S3173: the legacy default was persisted verbatim by every settings write, so an
        // installation holding it never chose a title - resolve it like an absent key.
        streamTitle = preferences[keyStreamTitle]
            ?.takeUnless { it == LEGACY_BROADCAST_STREAM_TITLE }
            ?: defaultDeviceTitle(context),
        bitRateBps = preferences[keyBitRateBps] ?: DEFAULT_BIT_RATE_BPS,
        port = preferences[keyPort] ?: DEFAULT_PORT,
        sampleRateHz = preferences[keySampleRateHz] ?: DEFAULT_SAMPLE_RATE_HZ,
        channelCount = preferences[keyChannelCount] ?: DEFAULT_CHANNEL_COUNT,
        autoOpenShare = preferences[keyAutoOpenShare] ?: true,
        sourceDeviceId = preferences[keySourceDeviceId],
        cameraEnabled = preferences[keyCameraEnabled] ?: false,
        microphoneEnabled = preferences[keyMicrophoneEnabled] ?: true,
        videoWidth = preferences[keyVideoWidth] ?: 1280,
        videoHeight = preferences[keyVideoHeight] ?: 720,
        videoFps = preferences[keyVideoFps] ?: 30,
        videoBitrateBps = preferences[keyVideoBitrateBps] ?: 2_000_000,
        micGainPercent = preferences[keyMicGainPercent] ?: 100,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[keyEnableBroadcasting] = settings.enableBroadcasting
        preferences[keyStreamTitle] = settings.broadcastStreamTitle
        preferences[keyBitRateBps] = settings.broadcastBitRateBps
        preferences[keyPort] = settings.broadcastPort
        preferences[keySampleRateHz] = settings.broadcastSampleRateHz
        preferences[keyChannelCount] = settings.broadcastChannelCount
        preferences[keyAutoOpenShare] = settings.broadcastAutoOpenShare
        settings.broadcastSourceDeviceId?.let { preferences[keySourceDeviceId] = it }
        preferences[keyCameraEnabled] = settings.broadcastCameraEnabled
        preferences[keyMicrophoneEnabled] = settings.broadcastMicrophoneEnabled
        preferences[keyVideoWidth] = settings.broadcastVideoWidth
        preferences[keyVideoHeight] = settings.broadcastVideoHeight
        preferences[keyVideoFps] = settings.broadcastVideoFps
        preferences[keyVideoBitrateBps] = settings.broadcastVideoBitrateBps
        preferences[keyMicGainPercent] = settings.broadcastMicGainPercent
    }
}
