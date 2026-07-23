package com.sza.fastmediasorter.domain.model

/**
 * S1152: persisted snapshot of the last active stream, used to resume it on cold app start
 * (mirrors the media-file resume feature). Backed by SharedPreferences, single slot.
 *
 * @property mediaKind the persisted `StreamSourceEntity.mediaKind` ("AUDIO" or "VIDEO").
 * @property wasPlaying true only for radio that was playing at exit - governs auto-play on resume.
 */
data class StreamResumeState(
    val url: String,
    val title: String,
    val mediaKind: String,
    val wasPlaying: Boolean,
    val savedAt: Long
)
