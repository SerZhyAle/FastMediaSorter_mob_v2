package com.sza.fastmediasorter.domain.model

import androidx.annotation.Keep

/**
 * Audio track metadata from online sources (iTunes API)
 */
@Keep
data class AudioMetadata(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val releaseYear: String?,
    val coverArtUrl: String?
)
