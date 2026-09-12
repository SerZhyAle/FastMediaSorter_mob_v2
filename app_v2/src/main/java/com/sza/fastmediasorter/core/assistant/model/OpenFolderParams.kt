package com.sza.fastmediasorter.core.assistant.model

/**
 * Parameters for assistant action to open a source/folder in the browser (S2920).
 *
 * @property resourceId Identifier of the media source.
 * @property folderPath Optional subfolder path within the source.
 */
data class OpenFolderParams(
    val resourceId: Long,
    val folderPath: String? = null
)
