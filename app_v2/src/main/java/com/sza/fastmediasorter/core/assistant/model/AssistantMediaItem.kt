package com.sza.fastmediasorter.core.assistant.model

/**
 * Serializable representation of a media item exposed to system assistant AppFunctions (S2920).
 *
 * @property resourceId Identifier of the parent media resource/source.
 * @property uri Uniform resource identifier or path of the media item.
 * @property displayName Human-readable file name or title.
 * @property mimeType Optional MIME type of the item (e.g. image/jpeg, video/mp4).
 * @property isFolder True if this item represents a directory/folder rather than a media file.
 */
data class AssistantMediaItem(
    val resourceId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val isFolder: Boolean = false
)
