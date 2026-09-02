package com.sza.fastmediasorter.wear.domain.model

/**
 * S1884: a file that has arrived on the watch and is asking to be shown.
 *
 * Carries the landed path rather than a token: the sender addressed the bytes, not a row in any
 * catalogue, so there is nothing on this side to look the file up by.
 */
data class WearFileOpenRequest(
    val path: String,
    val mimeType: String
)

/** S1884: what the host needs to route an arrived file to a player, mirroring [WearStreamPlaybackTarget]. */
data class WearFilePlaybackTarget(
    val fileId: Long,
    val mimeType: String
)
