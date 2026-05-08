package com.sza.fastmediasorter.wear.domain.model

data class WearPlaybackStatePayload(
    val isPlaying: Boolean,
    val fileName: String,
    val sourceName: String,
    val positionMs: Long,
    val durationMs: Long,
    val mediaType: String   // "AUDIO" | "VIDEO"
)
