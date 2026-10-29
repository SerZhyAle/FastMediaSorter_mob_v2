package com.sza.fastmediasorter.ui.player.helpers

import android.util.Log
import androidx.media3.common.Player

/** What the player was doing when the audio sink reported an underrun. */
internal enum class UnderrunContext(val logLabel: String) {
    ACTIVE_PLAYBACK("active"),
    BUFFERING("buffering"),
    PAUSED("paused"),
    STOPPED("stopped"),
}

/**
 * Separates an underrun the listener can hear from one the sink reports while it drains after a pause.
 *
 * A pause stops feeding the sink, so the sink reports an underrun shortly after it - on the archived
 * device every one of nine underruns followed `isPlaying=false` by 93-1069 ms. Logging those at warning
 * level made a normal pause indistinguishable from an audible stutter.
 */
internal object AudioDiagnosticClassifier {

    fun classifyUnderrun(playWhenReady: Boolean, playbackState: Int, suppressionReason: Int): UnderrunContext =
        when {
            !playWhenReady || suppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> UnderrunContext.PAUSED
            playbackState == Player.STATE_READY -> UnderrunContext.ACTIVE_PLAYBACK
            playbackState == Player.STATE_BUFFERING -> UnderrunContext.BUFFERING
            else -> UnderrunContext.STOPPED
        }

    // Only an underrun during active playback is a possible audible fault; the rest stay in the log at
    // info level so a diagnostic archive still shows them in sequence.
    fun underrunLogPriority(context: UnderrunContext): Int =
        if (context == UnderrunContext.ACTIVE_PLAYBACK) Log.WARN else Log.INFO
}
