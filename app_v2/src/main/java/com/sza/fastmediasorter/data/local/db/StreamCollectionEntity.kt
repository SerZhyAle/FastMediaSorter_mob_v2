package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * S2669: one curated collection of streams - "Russian TV", "Radio of the former USSR" - as delivered in
 * the `collections.json` entry of the stream-catalog archive.
 *
 * [namesJson] holds the payload's locale map verbatim rather than one column per language, because the
 * set of locales is deliberately open: a new locale must reach the user with the next catalog import and
 * without an app release, which a fixed column layout would prevent. Resolution happens at the
 * presentation edge, where the current app locale is known.
 *
 * Rows here are catalog-owned and replaced whole on every import that carries the entry; the user
 * authors nothing in this table, so there is no counterpart of `stream_user_state` beside it.
 */
@Entity(tableName = "stream_collections")
data class StreamCollectionEntity(
    @PrimaryKey
    val collectionId: String,

    /** Ascending; the order collections are presented in. Not necessarily contiguous. */
    val sortOrder: Int,

    /** JSON object of BCP-47 language tag to display name. `en` is guaranteed by the publisher. */
    val namesJson: String
)
