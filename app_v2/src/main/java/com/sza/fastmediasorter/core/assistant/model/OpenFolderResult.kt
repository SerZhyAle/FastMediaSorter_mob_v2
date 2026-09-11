package com.sza.fastmediasorter.core.assistant.model

/**
 * Result for assistant action to open a source/folder in the browser (S2920).
 *
 * @property success True if the browser was successfully opened.
 * @property message Descriptive status or error message.
 */
data class OpenFolderResult(
    val success: Boolean,
    val message: String? = null
)
