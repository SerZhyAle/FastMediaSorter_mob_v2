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
    val album: String? = null,
    val title: String? = null
)

/** Maximum file count displayed numerically before capping at "###" (S2476). */
const val MAX_COUNTER_DISPLAY_COUNT = 999

/**
 * S2476: returns the file name without extension for compact display on watch screens.
 */
val WearMediaFile.displayName: String
    get() {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

/**
 * Media type categories for browsing.
 */
enum class MediaType {
    MUSIC,
    VIDEO,
    PHOTO
}
