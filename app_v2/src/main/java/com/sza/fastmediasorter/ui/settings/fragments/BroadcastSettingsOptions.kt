package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import java.util.Locale

/**
 * Option lists and value captions of the Broadcast settings group.
 *
 * S3060 gave these preferences a second editor - the panel on the unified broadcast screen - and two
 * editors reading their own copy of the option lists drift silently: a gain added on one side is
 * simply missing on the other, and an index written by one editor then means a different value.
 */
object BroadcastSettingsOptions {

    const val PORT_MIN = 1
    const val PORT_MAX = 65_535

    val bitRatesBps: List<Int> = listOf(BIT_RATE_128, BIT_RATE_192, BIT_RATE_256, BIT_RATE_320)

    /** S3049: microphone digital gain percent options. */
    val micGainPercents: List<Int> =
        listOf(GAIN_50, GAIN_100, GAIN_150, GAIN_200, GAIN_300, GAIN_400)

    /** (sampleRateHz, channelCount) - the index is the dropdown position. */
    val audioFormats: List<Pair<Int, Int>> = listOf(
        SAMPLE_RATE_44_1K to 1,
        SAMPLE_RATE_44_1K to 2,
        SAMPLE_RATE_48K to 1,
        SAMPLE_RATE_48K to 2,
    )

    fun bitRateLabels(context: Context): List<String> = bitRatesBps.map { bps ->
        context.getString(R.string.unit_bitrate_kbps, (bps / BPS_PER_KBPS).toString())
    }

    fun micGainLabels(): List<String> = micGainPercents.map { "$it%" }

    fun audioFormatLabels(context: Context): List<String> = audioFormats.map { (sampleRate, channels) ->
        val khz = String.format(Locale.getDefault(), "%.1f", sampleRate / HZ_PER_KHZ)
        val channelName = context.getString(
            if (channels >= STEREO_CHANNEL_COUNT) {
                R.string.settings_broadcast_channels_stereo
            } else {
                R.string.settings_broadcast_channels_mono
            }
        )
        context.getString(R.string.settings_broadcast_audio_format_value, khz, channelName)
    }

    fun bitRateIndex(settings: AppSettings): Int =
        bitRatesBps.indexOf(settings.broadcastBitRateBps).coerceAtLeast(0)

    fun micGainIndex(settings: AppSettings): Int =
        micGainPercents.indexOf(settings.broadcastMicGainPercent).coerceAtLeast(DEFAULT_GAIN_INDEX)

    fun audioFormatIndex(settings: AppSettings): Int =
        audioFormats.indexOfFirst { (sampleRate, channels) ->
            sampleRate == settings.broadcastSampleRateHz && channels == settings.broadcastChannelCount
        }.coerceAtLeast(0)

    fun isValidPort(port: Int): Boolean = port in PORT_MIN..PORT_MAX

    private const val BIT_RATE_128 = 128_000
    private const val BIT_RATE_192 = 192_000
    private const val BIT_RATE_256 = 256_000
    private const val BIT_RATE_320 = 320_000
    private const val GAIN_50 = 50
    private const val GAIN_100 = 100
    private const val GAIN_150 = 150
    private const val GAIN_200 = 200
    private const val GAIN_300 = 300
    private const val GAIN_400 = 400
    private const val SAMPLE_RATE_44_1K = 44_100
    private const val SAMPLE_RATE_48K = 48_000
    private const val BPS_PER_KBPS = 1000
    private const val HZ_PER_KHZ = 1000.0
    private const val STEREO_CHANNEL_COUNT = 2

    // 100% is the neutral gain, so an unknown stored value falls back to it rather than to the quietest.
    private const val DEFAULT_GAIN_INDEX = 1
}
