package com.sza.fastmediasorter.data.local.db

/**
 * S4675: one row's endpoint column as stored, read by the secret migration. A projection, not an entity: the
 * migration needs the exact text to compare-and-set against, and nothing else of the row.
 */
data class StoredBroadcastEndpoints(
    val id: String,
    val broadcastEndpoints: String,
) {
    // The text holds relay keys and tunnel pins until it is migrated: it is never printed.
    override fun toString(): String = "StoredBroadcastEndpoints(id=$id)"
}
