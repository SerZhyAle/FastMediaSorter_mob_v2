package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Caches computed MD5 hashes for duplicate detection.
 * Cache key: (resourceId, filePath, lastModified, fileSize).
 * A cached entry is valid only when lastModified AND fileSize still match the live MediaFile.
 */
interface DuplicateHashRepository {
    /** Returns the cached quick hash (MD5 of first 4 KB), or null if not cached / stale. */
    suspend fun getCachedQuickHash(file: MediaFile): String?

    /** Returns the cached full hash (MD5 of entire file), or null if not cached / stale. */
    suspend fun getCachedFullHash(file: MediaFile): String?

    /** Stores the quick hash for [file]. Upserts on conflict. */
    suspend fun saveQuickHash(file: MediaFile, hash: String)

    /** Stores the full hash for [file]. Upserts on conflict. */
    suspend fun saveFullHash(file: MediaFile, hash: String)

    /** Deletes all cached entries for [resourceId] (called when a resource is removed). */
    suspend fun deleteByResourceId(resourceId: Long)

    /** Deletes the single cached entry for [file] (called after the file is deleted by the user). */
    suspend fun deleteHashEntry(file: MediaFile)
}
