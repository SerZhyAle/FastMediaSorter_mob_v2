package com.sza.fastmediasorter.ui.player.contracts

/**
 * Unified access to playback preferences without dependency on a specific
 * backend (ExoPlayer) or Activity lifecycle callbacks.
 * Used by both standard and vr flavors.
 *
 * This is a read-only facade; preference writes go through SettingsRepository.
 */
interface PlaybackPreferencesFacade {
    fun getPlaybackSpeed(): Float
    fun getResumePosition(mediaId: String): Long
    fun saveResumePosition(mediaId: String, positionMs: Long)
    fun getPreferredSubtitleTrackIndex(): Int
    fun isStereoPlaybackEnabled(): Boolean
}
