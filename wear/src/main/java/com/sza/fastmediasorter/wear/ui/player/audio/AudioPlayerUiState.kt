package com.sza.fastmediasorter.wear.ui.player.audio

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
    val setSize: Int = 0
) {
    val positionText: String
        get() = if (setSize > 0) "${setIndex + 1}/$setSize" else ""

    /** A single file is a set of one - there is nowhere to page, so no paging control is offered. */
    val hasSet: Boolean
        get() = setSize > 1

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
