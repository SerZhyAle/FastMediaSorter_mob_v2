package com.sza.fastmediasorter.core.assistant.model

/**
 * Parameters for assistant action to open a media item in the player (S2920).
 *
 * @property resourceId Identifier of the parent media source.
 * @property filePath Optional file path or URI of the media item.
 */
data class OpenMediaParams(
    val resourceId: Long,
    val filePath: String? = null
)
