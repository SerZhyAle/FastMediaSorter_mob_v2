package com.sza.fastmediasorter.wear.domain.model

/**
 * S1884: a file that has arrived on the watch and is asking to be shown.
 *
 * Carries the landed path rather than a token: the sender addressed the bytes, not a row in any
 * catalogue, so there is nothing on this side to look the file up by.
 */
data class WearFileOpenRequest(
    val path: String,
    val mimeType: String,
    /**
     * S4227: a media row the file lives in instead of a path. When set, [path] carries the display name - the
     * host hands [path] to the player route as the file name, which reads only its extension.
     */
    val contentUri: String? = null,
    /** S4227: the size of the row [contentUri] names, which the host cannot stat. */
    val sizeBytes: Long? = null,
)

/** S1884: what the host needs to route an arrived file to a player, mirroring [WearStreamPlaybackTarget]. */
data class WearFilePlaybackTarget(
    val fileId: Long,
    val mimeType: String
)
