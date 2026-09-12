package com.sza.fastmediasorter.wear.domain.model

import android.net.Uri

/**
 * S2694: a file the network folder walk found and the wearer tapped.
 *
 * Separate from [WearFileOpenRequest], which addresses bytes already on the watch by their landed
 * path. Nothing has landed here: the file is still on the share, so the request carries the address
 * the owning protocol client understands plus [sourceId], without which a player cannot reconnect -
 * the same two things the flat network listing hands the holder for the same file.
 *
 * The listing metadata travels rather than being re-derived from [uri]: the level that produced this
 * row already read the name, the size and the timestamp off the protocol, and re-deriving a poorer
 * copy here would show one thing in the walk and another in the player for the same file.
 */
data class WearNetworkFileOpenRequest(
    val sourceId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateModifiedEpochSeconds: Long
)
