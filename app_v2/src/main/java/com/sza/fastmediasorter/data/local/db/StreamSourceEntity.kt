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
    // S1117: catalog access flag. "geo" = region-restricted (kept + badged in the list); null/blank =
    // open. Populated only for CATALOG rows from the curated catalog's `access` column; MANUAL rows null.
    val access: String? = null,
    // S1144: per-channel track memory (language-code, not raw index - ADR-2, index is unstable across
    // live-manifest reloads). subtitlesEnabled null = follow global default, true/false = per-channel override.
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val subtitlesEnabled: Boolean? = null
)
