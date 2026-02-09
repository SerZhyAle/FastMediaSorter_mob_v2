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
    val isAlbumArtLoading: Boolean = false
) {
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
