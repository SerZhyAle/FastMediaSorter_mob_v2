package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * S0565: a user stream source in the "Трансляции" catalog. Holds the local pin-to-top order and
 * favorite flag, independent of the global favorites table. De-duplicated by [url] (unique index).
 *
 * S0570/S0761: [category]/[topic]/[language]/[country] are populated only for `sourceOrigin = CATALOG`
 * rows (curated catalog import); manual/imported rows leave them null.
 */
@Entity(
    tableName = "stream_sources",
    indices = [
        Index(value = ["url"], unique = true, name = "index_stream_sources_url")
    ]
)
data class StreamSourceEntity(
    @PrimaryKey
    val id: String,

    val url: String,
    val title: String,
    val mediaKind: String,      // AUDIO / VIDEO / RTSP - drives launch routing
    val sourceOrigin: String,   // MANUAL / IMPORTED
    val sortIndex: Int,         // lower = higher in list; pin-to-top decrements below current min
    val pinned: Boolean = false,
    val addedAt: Long,
    val lastPlayedAt: Long? = null,
    val category: String? = null,
    val topic: String? = null,
    val language: String? = null,
    val country: String? = null,
    // S0593: outcome of the last local playback attempt on this device, driving the row status bullet.
    // null = never tried (amber/unknown), "OK" = last play reached playing (green), "FAIL" = last
    // attempt errored (red). [lastPlayOutcomeAt] is the epoch-millis timestamp of that outcome.
    val lastPlayOutcome: String? = null,
    val lastPlayOutcomeAt: Long? = null,
    // S1117: catalog access flag. "geo" = region-restricted (kept + badged in the list); null/blank =
    // open. Populated only for CATALOG rows from the curated catalog's `access` column; MANUAL rows null.
    val access: String? = null
)
