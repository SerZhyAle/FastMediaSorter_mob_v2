package com.sza.fastmediasorter.data.local.db

import androidx.room.*

/**
 * DAO for [FileMetadataCacheEntity] — per-file scan metadata cache (A5).
 *
 * The primary lookup key is (resourceId, filePath) — unique index guarantees
 * at-most-one cache entry per file per resource.
 */
@Dao
interface FileMetadataCacheDao {

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the cache entry for a specific file within a resource, or null
     * if not cached yet.
     */
    @Query("SELECT * FROM file_metadata_cache WHERE resourceId = :resourceId AND filePath = :filePath LIMIT 1")
    suspend fun getEntry(resourceId: Long, filePath: String): FileMetadataCacheEntity?

    /**
     * Returns all cache entries for a resource (used during incremental scan
     * to build the known-files set and detect deletions).
     */
    @Query("SELECT resourceId, filePath, lastModified, fileSize FROM file_metadata_cache WHERE resourceId = :resourceId")
    suspend fun getChecksumSnapshot(resourceId: Long): List<FileChecksumRow>

    /** Count of cached entries for a resource (diagnostic / stats). */
    @Query("SELECT COUNT(*) FROM file_metadata_cache WHERE resourceId = :resourceId")
    suspend fun countForResource(resourceId: Long): Int

    /** Returns all complete entities for a resource (for batch enrichment). */
    @Query("SELECT * FROM file_metadata_cache WHERE resourceId = :resourceId")
    suspend fun getAllForResource(resourceId: Long): List<FileMetadataCacheEntity>

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Insert or replace (upsert) a single cache entry.
     * Replaces existing entry for the same (resourceId, filePath) combination.
     */
    @Upsert
    suspend fun upsert(entry: FileMetadataCacheEntity)

    /**
     * Batch upsert for writing multiple entries after a scan pass.
     */
    @Upsert
    suspend fun upsertAll(entries: List<FileMetadataCacheEntity>)

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Remove a single file entry (e.g. when file is detected as deleted). */
    @Query("DELETE FROM file_metadata_cache WHERE resourceId = :resourceId AND filePath = :filePath")
    suspend fun deleteEntry(resourceId: Long, filePath: String)

    /** Remove a batch of deleted-file entries. */
    @Query("DELETE FROM file_metadata_cache WHERE resourceId = :resourceId AND filePath IN (:filePaths)")
    suspend fun deleteEntries(resourceId: Long, filePaths: List<String>)

    /** Remove all entries for a resource (full re-scan or resource deletion). */
    @Query("DELETE FROM file_metadata_cache WHERE resourceId = :resourceId")
    suspend fun deleteAllForResource(resourceId: Long): Int

    /**
     * TTL cleanup: remove entries cached before [olderThanMs] (epoch ms).
     * Called by [OrphanCleanupWorker] or a dedicated TTL job.
     */
    @Query("DELETE FROM file_metadata_cache WHERE cachedAt < :olderThanMs")
    suspend fun deleteExpired(olderThanMs: Long): Int

    /**
     * Remove entries whose parent resource no longer exists.
     * Defensive cleanup — foreign key CASCADE handles most cases, but
     * this covers any edge cases (e.g. manual DB manipulation).
     */
    @Query("DELETE FROM file_metadata_cache WHERE resourceId NOT IN (SELECT id FROM resources)")
    suspend fun deleteOrphaned(): Int

    /**
     * Invalidate cache for all files associated with specific credentials.
     * Call when credentials change or are revoked.
     */
    @Query("DELETE FROM file_metadata_cache WHERE credentialsId = :credentialsId")
    suspend fun deleteByCredentials(credentialsId: String): Int
}

/**
 * Lightweight projection used for incremental-scan checksum comparison.
 * Avoids loading full metadata rows when only mtime + size are needed.
 */
data class FileChecksumRow(
    val resourceId: Long,
    val filePath: String,
    val lastModified: Long,
    val fileSize: Long
)
