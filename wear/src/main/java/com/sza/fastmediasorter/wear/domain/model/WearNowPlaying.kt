package com.sza.fastmediasorter.wear.domain.model

/**
 * S2047: pure domain representation of the watch's now playing / last played track state.
 *
 * Truthful in two states: [isPlaying] is true during active playback, and false after playback stops
 * while preserving [title] and [subtitle] for the "last played" complication display.
 */
data class WearNowPlaying(
    val title: String,
    val subtitle: String?,
    val isPlaying: Boolean,
    val updatedAtEpochMs: Long
) {
    val hasContent: Boolean
        get() = title.isNotBlank()

    companion object {
        val EMPTY = WearNowPlaying(
            title = "",
            subtitle = null,
            isPlaying = false,
            updatedAtEpochMs = 0L
        )
    }
}
