package com.sza.fastmediasorter.wear.ui.player.audio

import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile

/**
 * UI state for the audio player screen.
 */
data class AudioPlayerUiState(
    val isLoading: Boolean = true,
    val mediaFile: WearMediaFile? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val albumArtUrl: String? = null,
    // S1683: the screen-off mode of strategic 6.7. It is screen state and nothing else - playback does
    // not know about it, which is the whole point of the mode.
    val isDimmed: Boolean = false,
    // S1683: position inside the browsed set. Paging wraps around, so without a visible marker an
    // endlessly looping folder loses every landmark the user could navigate by.
    val setIndex: Int = 0,
    val setSize: Int = 0,
    // S1701: playback order of the browsed set, remembered between launches.
    val isShuffleEnabled: Boolean = false,
    /**
     * S1701: the system media volume, read back after each change rather than counted here.
     *
     * The player keeps no scale of its own (strategic 5.1.3): anything else on the watch may move the
     * same stream, and a private copy would drift from what the user actually hears.
     */
    val volumeLevel: Int = 0,
    val volumeMax: Int = 0,
    /** True only while the bezel is being turned, plus the short tail after it stops. */
    val isVolumeVisible: Boolean = false,
    /** S1866: parsed track title from MediaStore or ExoPlayer ID3 tags. */
    val trackTitle: String? = null,
    /** S1866: parsed artist name from MediaStore or ExoPlayer ID3 tags. */
    val artistName: String? = null,
    /**
     * S1728: why the network channel affected this stream, or null for "say nothing".
     *
     * Null is the normal case by design - the owner's ruling is that the player speaks only when the
     * channel actually stopped or disturbed a stream. A reason rather than a message, because the
     * screen owns the wording and the locale.
     */
    val channelReason: StreamChannelReason? = null
) {
    val positionText: String
        get() = if (setSize > 0) "${setIndex + 1}/$setSize" else ""

    val progress: Float
        get() = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
    
    val currentPositionFormatted: String
        get() = formatTime(currentPositionMs)
    
    val durationFormatted: String
        get() = formatTime(durationMs)
    
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
