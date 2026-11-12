package com.sza.fastmediasorter.core.systeminfo

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Collects Widevine DRM level, hardware video codec capabilities, and audio output / spatial /
 * low-latency capabilities for the System info `Media & Audio` section.
 */
class MediaAudioDiagnosticsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun collectSection(): SystemInfoSection {
        val fields = listOf(
            label(R.string.sysinfo_field_drm_widevine) to safe { widevineInfo() },
            label(R.string.sysinfo_field_hw_video_codecs) to safe { hardwareVideoCodecs() },
            label(R.string.sysinfo_field_audio_outputs) to safe { audioOutputs() },
            label(R.string.sysinfo_field_audio_ringer) to safe { ringerMode() },
            label(R.string.sysinfo_field_audio_capabilities) to safe { audioCapabilities() },
            label(R.string.sysinfo_field_audio_sample_rate) to safe { nativeAudioRateAndBuffer() },
        )
        return SystemInfoSection(
            title = label(R.string.sysinfo_section_media_audio),
            fields = fields,
            group = SystemInfoGroup.MEDIA_AUDIO,
        )
    }

    private fun widevineInfo(): String {
        if (!MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) return "not supported"
        var drm: MediaDrm? = null
        return try {
            drm = MediaDrm(WIDEVINE_UUID)
            val level = runCatching { drm.getPropertyString("securityLevel") }.getOrDefault(UNKNOWN)
            val version = runCatching { drm.getPropertyString(MediaDrm.PROPERTY_VERSION) }.getOrDefault(UNKNOWN)
            val vendor = runCatching { drm.getPropertyString(MediaDrm.PROPERTY_VENDOR) }.getOrDefault(UNKNOWN)
            "$level (v$version, $vendor)"
        } finally {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drm?.close()
                } else {
                    @Suppress("DEPRECATION")
                    drm?.release()
                }
            }
        }
    }

    private fun hardwareVideoCodecs(): String {
        val infos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        val mimeTargets = listOf(
            "AVC/H.264" to "video/avc",
            "HEVC/H.265" to "video/hevc",
            "VP9" to "video/x-vnd.on2.vp9",
            "AV1" to "video/av01",
            "Dolby Vision" to "video/dolby-vision",
        )
        val summary = mimeTargets.mapNotNull { (label, mime) ->
            val matching = infos.filter { codec ->
                codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            if (matching.isEmpty()) {
                null
            } else {
                val hw = matching.any { isHardwareCodec(it.name) }
                "$label (${if (hw) "HW" else "SW"})"
            }
        }
        return if (summary.isEmpty()) UNKNOWN else summary.joinToString(", ")
    }

    private fun isHardwareCodec(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        val swPrefixes = listOf("omx.google.", "c2.android.", "ffmpeg", "avcodec")
        return swPrefixes.none { lower.startsWith(it) }
    }

    private fun audioOutputs(): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return UNKNOWN
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val types = devices.map { deviceTypeName(it.type) }.distinct()
        return if (types.isEmpty()) UNKNOWN else types.joinToString(", ")
    }

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Audio"
        else -> "Other($type)"
    }

    private fun ringerMode(): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return UNKNOWN
        return when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            else -> UNKNOWN
        }
    }

    private fun audioCapabilities(): String {
        val pm = context.packageManager
        val features = mutableListOf<String>()
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)) features += "Low-latency"
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)) features += "Pro audio"
        if (pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) features += "MIDI"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val spatial = runCatching { am?.spatializer?.isAvailable == true }.getOrDefault(false)
            if (spatial) features += "Spatial audio"
        }
        return if (features.isEmpty()) "standard" else features.joinToString(", ")
    }

    private fun nativeAudioRateAndBuffer(): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return UNKNOWN
        val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.takeIf { it.isNotBlank() } ?: UNKNOWN
        val frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.takeIf { it.isNotBlank() } ?: UNKNOWN
        return "$rate Hz / $frames frames"
    }

    private fun label(@StringRes resId: Int): String = context.getString(resId)

    @Suppress("TooGenericExceptionCaught")
    private inline fun safe(block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        if (SystemInfoAccessClassifier.isExpectedAccessDenial(e)) {
            Timber.i("System info: media/audio field unavailable (${e.javaClass.simpleName})")
        } else {
            Timber.w(e, "System info: failed to read media/audio field")
        }
        UNKNOWN
    }

    private companion object {
        const val UNKNOWN = "unknown"
        val WIDEVINE_UUID: UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
    }
}
