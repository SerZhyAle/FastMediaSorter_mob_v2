package com.sza.fastmediasorter.domain.hash

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource

/**
 * Computes MD5 hash of a file's content by streaming through the appropriate protocol.
 * Each implementation handles one [com.sza.fastmediasorter.domain.model.ResourceType].
 */
interface FileHasher {
    /**
     * Reads up to [maxBytes] bytes of [file] and returns the MD5 hex digest.
     * Pass [maxBytes] = -1L to hash the entire file (full hash / Phase 2).
     * Pass [maxBytes] = QUICK_HASH_BYTES (4096) for Phase 1 quick hash.
     *
     * @throws Exception on IO failure - caller must handle and skip the file.
     */
    suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long = -1L
    ): String
}
