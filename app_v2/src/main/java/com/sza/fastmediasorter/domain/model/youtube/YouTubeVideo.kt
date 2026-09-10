package com.sza.fastmediasorter.domain.model.youtube

/**
 * S2032: the channel's latest upload - what the window cell shows while stopped and what its play
 * button starts (strategic §6.3 and §6.4 resolve to the same video for exactly that reason).
 */
data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
)
