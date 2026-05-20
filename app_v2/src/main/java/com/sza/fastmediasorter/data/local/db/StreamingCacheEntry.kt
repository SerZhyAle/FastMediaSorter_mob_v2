package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted record of a remote media file that was downloaded locally because
 * streaming over the source protocol was judged NOT_VIABLE for the current network.
 *
 * The primary key [resourceHash] is a short SHA-256 prefix of the original URI -
 * stable across sessions, safe for use as a filename suffix, and collision-resistant
 * enough for the ~hundreds of files a user will ever keep offloaded.
 *
 * The table has no foreign key to `resources` on purpose: offloaded files must survive
 * resource rename/delete (the local copy is still usable) and GC drives cleanup instead.
 *
 * See: PLAN/spec_adaptive-playback-strategy.md §5.7.
 */
@Entity(
    tableName = "streaming_cache_entries",
    indices = [
        Index(value = ["lastPlayedAt"], name = "idx_stream_cache_last_played_at")
    ]
)
data class StreamingCacheEntry(
    /** `sha256(originalUri).take(16)` - stable ID for this offloaded copy. */
    @PrimaryKey val resourceHash: String,

    /** Absolute path on internal storage OR `content://` URI from MediaStore. */
    val localPath: String,

    /** Source URI used by the player when re-playing this offloaded copy. */
    val originalUri: String,

    /** Protocol-normalised key for speed-test lookups (e.g. `smb://host/share/`). */
    val resourceKey: String,

    val sizeBytes: Long,

    /** Epoch ms when the download completed. */
    val downloadedAt: Long,

    /** Epoch ms of the most recent playback - updated on every replay. Drives TTL GC. */
    val lastPlayedAt: Long,

    /** `LOCAL`/`SMB`/`SFTP`/`FTP`/`CLOUD` - matches [com.sza.fastmediasorter.domain.model.Protocol]. */
    val sourceProtocol: String
)
