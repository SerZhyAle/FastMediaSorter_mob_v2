package com.sza.fastmediasorter.core.assistant.model

/**
 * Result for assistant action to open a media item in the player (S2920).
 *
 * @property success True if the player was successfully launched.
 * @property message Descriptive status or error message.
 */
data class OpenMediaResult(
    val success: Boolean,
    val message: String? = null
)
