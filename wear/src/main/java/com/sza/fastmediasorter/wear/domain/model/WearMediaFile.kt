package com.sza.fastmediasorter.wear.domain.model

import android.net.Uri

/**
 * Domain model representing a media file for Wear OS.
 * Simplified version of the main app's MediaFile model.
 */
data class WearMediaFile(
    val id: Long,
    val name: String,
    val uri: Uri,
    val mimeType: String?,
    val size: Long,
    val dateModified: Long,
    val duration: Long = 0, // For audio/video in milliseconds
    val albumArt: Uri? = null, // For music files
    // S1689: the network cover lookup asks by artist and album, so the pair must reach the player.
    // Only a MediaStore row carries them - a network listing knows the file name and nothing else.
    val artist: String? = null,
    val album: String? = null
)

/**
 * Media type categories for browsing.
 */
enum class MediaType {
    MUSIC,
    VIDEO,
    PHOTO
}
