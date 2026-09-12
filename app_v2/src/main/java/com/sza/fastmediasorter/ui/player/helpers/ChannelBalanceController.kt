package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import timber.log.Timber

/**
 * Live source of the per-channel gain pair applied by [ChannelBalanceAudioProcessor].
 *
 * Exists so the processor can be exercised without the process-wide owner below.
 */
interface ChannelBalanceSource {
    val leftGain: Float
    val rightGain: Float
}

/**
 * The one owner of the current stereo-balance value (S1267 ADR-3).
 *
 * The audio path of all four player hosts is the same background service, which no host holds a
 * reference to, so the value cannot live in a per-host handle. Every processor instance reads this
 * owner per buffer, which is what makes a preset tap audible without rebuilding the player.
 */
object ChannelBalanceController : ChannelBalanceSource {

    @Volatile
    override var leftGain: Float = UNITY_GAIN
        private set

    @Volatile
    override var rightGain: Float = UNITY_GAIN
        private set

    /** True only while the stream being decoded actually has two channels to balance between. */
    @Volatile
    var isStereoContentActive: Boolean = false
        private set

    fun setBalance(leftGain: Float, rightGain: Float) {
        this.leftGain = leftGain
        this.rightGain = rightGain
        Timber.d("S1267: balance applied left=$leftGain right=$rightGain")
    }

    /** Called by the processor on every format negotiation, so mono content disables the section. */
    fun reportChannelCount(channelCount: Int) {
        isStereoContentActive = channelCount == STEREO_CHANNEL_COUNT
    }

    fun restore(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        leftGain = prefs.getFloat(PlaybackControlPreferences.KEY_BALANCE_LEFT_GAIN, UNITY_GAIN)
        rightGain = prefs.getFloat(PlaybackControlPreferences.KEY_BALANCE_RIGHT_GAIN, UNITY_GAIN)
    }

    const val UNITY_GAIN = 1f
    const val STEREO_CHANNEL_COUNT = 2
}
