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
    val albumArt: Uri? = null // For music files
)

/**
 * Media type categories for browsing.
 */
enum class MediaType {
    MUSIC,
    VIDEO,
    PHOTO
}
