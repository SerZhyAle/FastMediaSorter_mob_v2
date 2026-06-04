package com.sza.fastmediasorter.ui.player.model

import android.net.Uri

/**
 * Carries per-track metadata needed to build a Media3 MediaItem with full MediaMetadata.
 * Used by AudioServiceController.playAudioPlaylistWithMetadata() to populate
 * title/artist/art in the MediaSession and system notification.
 */
data class MediaItemWithMeta(
    val uri: Uri,
    val title: String,       // filename without extension, or ID3 title tag
    val artist: String?,     // ID3 artist tag or null
    val albumArtUri: Uri?,   // local file:// URI to cached cover art, or null
    val mimeType: String? = null,
    val sourcePath: String? = null,
    val resourceId: Long = -1L,
    val size: Long = 0L,
    val dateModified: Long = 0L
)
