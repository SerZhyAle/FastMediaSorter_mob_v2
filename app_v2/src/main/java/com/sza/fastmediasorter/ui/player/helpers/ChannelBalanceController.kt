package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guards a fresh user choice against a restore that is still on its way back from disk. */
    @Volatile
    private var hasUserChoice = false

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
        hasUserChoice = true
        this.leftGain = leftGain
        this.rightGain = rightGain
    }

    /** Called by the processor on every format negotiation, so mono content disables the section. */
    fun reportChannelCount(channelCount: Int) {
        isStereoContentActive = channelCount == STEREO_CHANNEL_COUNT
    }

    /**
     * S3239: the caller is the renderers-factory thread, which runs under a StrictMode policy that
     * detects disk reads, so the preferences load is handed to [Dispatchers.IO]. Both gains stay at
     * [UNITY_GAIN] until it lands - plain 50/50 audio, never silence.
     */
    fun restore(context: Context) {
        val appContext = context.applicationContext
        ioScope.launch { loadStoredGains(appContext) }
    }

    @VisibleForTesting
    internal fun loadStoredGains(context: Context) {
        val prefs = context
            .getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val storedLeft = prefs.getFloat(PlaybackControlPreferences.KEY_BALANCE_LEFT_GAIN, UNITY_GAIN)
        val storedRight = prefs.getFloat(PlaybackControlPreferences.KEY_BALANCE_RIGHT_GAIN, UNITY_GAIN)
        // A tap on a preset while the read was in flight wins - it is the newer of the two values.
        if (hasUserChoice) return
        leftGain = storedLeft
        rightGain = storedRight
    }

    /** The gains outlive a test method because the owner is an object, so a suite resets them here. */
    @VisibleForTesting
    internal fun resetForTest() {
        hasUserChoice = false
        leftGain = UNITY_GAIN
        rightGain = UNITY_GAIN
    }

    const val UNITY_GAIN = 1f
    const val STEREO_CHANNEL_COUNT = 2
}
