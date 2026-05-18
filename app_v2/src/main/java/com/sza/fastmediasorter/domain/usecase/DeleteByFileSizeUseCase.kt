package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Deletes files in [resource] that match the size threshold.
 * Exactly one of [maxSizeMb] or [minSizeMb] must be non-null.
 *
 * Workflow:
 *   1. [scan]    - find matching files, returns count + total bytes (no deletion).
 *   2. [execute] - delete the previously scanned [matching] list.
 */
class DeleteByFileSizeUseCase @Inject constructor(
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase
) {
    data class ScanResult(val files: List<MediaFile>, val totalBytes: Long)
    // DeleteResult kept for legacy invoke() compat
    data class DeleteResult(val deletedCount: Int, val failedCount: Int)

    /**
     * Scans [resource] and returns files that match the given size threshold.
     * Does NOT delete anything.
     */
    suspend fun scan(
        resource: MediaResource,
        maxSizeMb: Float? = null,
        minSizeMb: Float? = null
    ): ScanResult {
        require((maxSizeMb == null) != (minSizeMb == null)) {
            "Exactly one of maxSizeMb or minSizeMb must be provided"
        }

        val allFiles = try {
            getMediaFilesUseCase(resource, forceFullScan = true).first()
        } catch (e: Exception) {
            Timber.e(e, "DeleteByFileSizeUseCase.scan: failed to list files for resource ${resource.id}")
            return ScanResult(emptyList(), 0L)
        }

        val threshold = ((maxSizeMb ?: minSizeMb)!! * 1024 * 1024).toLong()
        val matching = allFiles.filter { file ->
            if (maxSizeMb != null) file.size < threshold else file.size > threshold
        }

        val totalBytes = matching.sumOf { it.size }
        Timber.d("DeleteByFileSizeUseCase.scan: found ${matching.size} files ($totalBytes bytes)")
        return ScanResult(matching, totalBytes)
    }

    /**
     * Deletes the given [files] (previously returned by [scan]).
     * Handles local, SMB, SFTP, FTP and Cloud paths via [DeleteFilesUseCase].
     * Returns [FileOperationResult] directly so the caller can handle PermissionRequired / AuthRequired.
     */
    suspend fun execute(files: List<MediaFile>): FileOperationResult {
        if (files.isEmpty()) return FileOperationResult.Success(0, FileOperation.Delete(emptyList()))

        Timber.d("DeleteByFileSizeUseCase.execute: deleting ${files.size} files")

        // Wrap each MediaFile path in a java.io.File that preserves the original path string.
        // DeleteFilesUseCase / FileOperationUseCase uses absolutePath internally so network
        // URIs (smb://, sftp://, ftp://, cloud://) are routed to the correct handler.
        val javaFiles = files.map { mediaFile ->
            val path = mediaFile.path
            object : java.io.File(path) {
                override fun getAbsolutePath(): String = path
                override fun getPath(): String = path
            }
        }

        return deleteFilesUseCase(javaFiles)
    }

    // Legacy single-shot operator for backward-compat (not used by new UI flow)
    suspend operator fun invoke(
        resource: MediaResource,
        maxSizeMb: Float? = null,
        minSizeMb: Float? = null
    ): DeleteResult {
        val scanResult = scan(resource, maxSizeMb, minSizeMb)
        return when (val r = execute(scanResult.files)) {
            is FileOperationResult.Success -> DeleteResult(r.processedCount, 0)
            is FileOperationResult.PartialSuccess -> DeleteResult(r.processedCount, r.failedCount)
            else -> DeleteResult(0, scanResult.files.size)
        }
    }
}

