package com.sza.fastmediasorter.domain.model

/**
 * Screen type for resume state — where the user was when playback was active.
 */
enum class ScreenType {
    BROWSER,
    PLAYER
}

/**
 * Persisted state for "resume last playback" feature.
 * Saved periodically while media is playing, read on cold app start.
 */
data class ResumeState(
    val filePath: String,
    val resourceId: Long,
    val currentFolderPath: String?,
    val screenType: ScreenType,
    val sortMode: SortMode,
    val isPlaying: Boolean,
    val isSlideshowEnabled: Boolean,
    val mediaType: MediaType,
    val savedAt: Long
)
