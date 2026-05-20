package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import kotlinx.coroutines.flow.Flow

/**
 * Read/write surface for the streaming-offload cache - files downloaded locally
 * because the remote protocol was not viable for progressive playback.
 *
 * Implementation is [com.sza.fastmediasorter.data.repository.StreamingCacheRepositoryImpl].
 * File I/O (actual download and delete on disk) is delegated to the caller via
 * [delete] for DB+disk cleanup; the repository is responsible for DB rows and for
 * detecting entries whose local file no longer exists.
 *
 * See PLAN/spec_adaptive-playback-strategy.md §5.7.
 */
interface StreamingCacheRepository {

    /** Derives the stable short hash used as primary key from an original URI. */
    fun resolveHash(originalUri: String): String

    suspend fun findByHash(resourceHash: String): StreamingCacheEntry?

    /** Convenience: hash the URI and look up in one call. */
    suspend fun findByOriginalUri(originalUri: String): StreamingCacheEntry?

    /** Insert-or-replace. Used on successful offload and on re-offload. */
    suspend fun record(entry: StreamingCacheEntry)

    /** Bumps `lastPlayedAt` without rewriting the row. */
    suspend fun touchPlayed(resourceHash: String, now: Long = System.currentTimeMillis())

    /**
     * Removes both the DB row and the local file. Returns true if the row existed
     * (the file may already have been deleted by the user out-of-band - that still
     * counts as success because the end state is the same).
     */
    suspend fun delete(resourceHash: String): Boolean

    /**
     * Scans all entries, drops rows whose local files no longer exist on disk, and
     * returns the number of rows pruned. Called on app startup by the GC worker.
     */
    suspend fun verifyAndPrune(): Int

    /**
     * Returns entries whose [StreamingCacheEntry.lastPlayedAt] is older than
     * `now - ttlMs` - the GC worker deletes them.
     */
    suspend fun findStaleByTtl(now: Long, ttlMs: Long): List<StreamingCacheEntry>

    suspend fun totalSizeBytes(): Long

    suspend fun getAll(): List<StreamingCacheEntry>

    fun observeAll(): Flow<List<StreamingCacheEntry>>

    /** Wipes all entries + files. Used by Settings → "Clear streaming cache now". */
    suspend fun clearAll(): Int
}
