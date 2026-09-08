package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index

/**
 * S2669: membership of one stream in one curated collection. The composite key lets the same [url]
 * appear in as many collections as the curator put it in, which is the whole point of the feature -
 * nothing is duplicated in `stream_sources` to express it.
 *
 * Keyed by the stream URL rather than by `stream_sources.id`, and carrying no foreign key on purpose.
 * A catalog row's id is a fresh UUID on every import and the merge deletes rows that left the bank, so
 * a relation to either would break on the first refresh; the URL is the key the merge itself uses and
 * the only field whose stability the system guarantees. A member naming a stream that is momentarily
 * absent simply matches nothing until it returns.
 */
@Entity(
    tableName = "stream_collection_members",
    primaryKeys = ["collectionId", "url"],
    indices = [Index(value = ["url"])]
)
data class StreamCollectionMemberEntity(
    val collectionId: String,

    val url: String,

    /** Contiguous from 1 within the collection; the curator's reading order. */
    val sortOrder: Int
)
