package com.sza.fastmediasorter.data.local.db

import androidx.room.ColumnInfo
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
        Index(value = ["url"], unique = true, name = "index_stream_sources_url"),
        // S1832: deliberately NOT unique. 58 groups of published catalog rows fold onto a
        // single identity; a unique index would turn each of them into an insert conflict
        // and shrink the catalog on the first import after the upgrade.
        Index(value = ["identityKey"], name = "index_stream_sources_identityKey")
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
    val subtitlesEnabled: Boolean? = null,
    // S1832: the key every kind of user-authored data about this channel is filed under,
    // derived from [url] by StreamChannelIdentity. Stored rather than recomputed so a merge
    // can join on it instead of re-parsing every address in the bank on each import.
    //
    // The Kotlin default exists so the many construction sites stay readable; it is NOT the
    // value that reaches the table. StreamSourceRepository derives the identity on every write
    // path, which is why no caller has to remember to - see its `withIdentity` helper.
    // The @ColumnInfo default is what MIGRATION_51_52 backfills over, declared here because
    // runMigrationsAndValidate compares defaults as well as names and types.
    @ColumnInfo(defaultValue = "")
    val identityKey: String = ""
)
