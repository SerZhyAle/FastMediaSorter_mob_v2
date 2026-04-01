package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.hash.CloudFileHasher
import com.sza.fastmediasorter.data.hash.FtpFileHasher
import com.sza.fastmediasorter.data.hash.LocalFileHasher
import com.sza.fastmediasorter.data.hash.SftpFileHasher
import com.sza.fastmediasorter.data.hash.SmbFileHasher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.DuplicateHashRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Computes the MD5 hash of a file, using the cache first.
 * Dispatches to the correct [com.sza.fastmediasorter.domain.hash.FileHasher] via explicit when(resource.type).
 */
class ComputeFileHashUseCase @Inject constructor(
    private val localFileHasher: LocalFileHasher,
    private val smbFileHasher: SmbFileHasher,
    private val sftpFileHasher: SftpFileHasher,
    private val ftpFileHasher: FtpFileHasher,
    private val cloudFileHasher: CloudFileHasher,
    private val hashRepository: DuplicateHashRepository
) {
    /**
     * @param maxBytes -1L = full hash; QUICK_HASH_BYTES = quick hash
     * @return MD5 hex string, or null if IO error (file is skipped by caller)
     */
    suspend fun compute(file: MediaFile, resource: MediaResource, maxBytes: Long): String? {
        // Check cache first
        val cached = if (maxBytes < 0) {
            hashRepository.getCachedFullHash(file)
        } else {
            hashRepository.getCachedQuickHash(file)
        }
        if (cached != null) return cached

        // Compute via protocol-specific hasher
        return try {
            val hasher = when (resource.type) {
                ResourceType.LOCAL -> localFileHasher
                ResourceType.SMB -> smbFileHasher
                ResourceType.SFTP -> sftpFileHasher
                ResourceType.FTP -> ftpFileHasher
                ResourceType.CLOUD -> cloudFileHasher
            }
            val hash = hasher.computeHash(file, resource, maxBytes)
            // Persist to cache
            if (maxBytes < 0) {
                hashRepository.saveFullHash(file, hash)
            } else {
                hashRepository.saveQuickHash(file, hash)
            }
            hash
        } catch (e: Exception) {
            Timber.w(e, "ComputeFileHashUseCase: skipping ${file.path} due to error")
            null
        }
    }
}
