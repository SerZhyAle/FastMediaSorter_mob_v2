package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.CloudPathParser
import com.sza.fastmediasorter.data.transfer.TempFileNamingStrategy
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase for cleaning up orphaned temporary files (*.temp_copy) from atomic transfers.
 * 
 * Temporary files can remain if:
 * - App crashes during transfer
 * - Transfer was cancelled mid-operation
 * - Rename operation failed after copy
 * 
 * This UseCase runs on resource open to clean up any leftover temporary files.
 */
class CleanupOrphanedTempFilesUseCase @Inject constructor(
    private val smbFileOperationHandler: SmbFileOperationHandler,
    private val cloudFileOperationHandler: CloudFileOperationHandler,
    private val cloudPathParser: CloudPathParser
) {
    
    /**
     * Clean up orphaned .temp_copy files in the specified directory.
     * 
     * @param directoryPath Path to directory to clean
     * @return Result with count of deleted files or failure
     */
    suspend operator fun invoke(directoryPath: String): Result<Int> = withContext(Dispatchers.IO) {
        // Cloud paths (cloud:// and legacy cloud:/) have no concept of local temp files — nothing to clean.
        // S0236: accept both legacy single-slash and current double-slash forms so a stale resource.path
        // does not leak into SmbFileOperationHandler → LocalOperationStrategy and produce FileNotFoundException.
        if (cloudPathParser.isCloudPath(directoryPath)) {
            Timber.d("CleanupOrphanedTempFilesUseCase: Skipping cloud path: $directoryPath")
            return@withContext Result.success(0)
        }

        // Virtual aggregate paths (virtual://all_audio, virtual://all_video, etc.) are MediaStore
        // virtual views — no real directory, no strategy registered for them
        if (VirtualPathUtils.isVirtualPath(directoryPath)) {
            Timber.d("CleanupOrphanedTempFilesUseCase: Skipping virtual path: $directoryPath")
            return@withContext Result.success(0)
        }

        Timber.d("CleanupOrphanedTempFilesUseCase: Starting cleanup in: $directoryPath")
        
        try {
            // Determine handler based on path protocol
            val handler = when {
                directoryPath.startsWith("gdrive:", ignoreCase = true) -> cloudFileOperationHandler
                directoryPath.startsWith("dropbox:", ignoreCase = true) -> cloudFileOperationHandler
                directoryPath.startsWith("onedrive:", ignoreCase = true) -> cloudFileOperationHandler
                else -> smbFileOperationHandler // Handles SMB, SFTP, FTP, Local
            }
            
            Timber.d("CleanupOrphanedTempFilesUseCase: Using ${handler.javaClass.simpleName}")
            
            // List all files in directory
            val filesResult = handler.listFiles(directoryPath)
            
            if (filesResult.isFailure) {
                Timber.w("CleanupOrphanedTempFilesUseCase: Failed to list files: ${filesResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(
                    filesResult.exceptionOrNull() ?: Exception("Failed to list files")
                )
            }
            
            val allFiles = filesResult.getOrNull() ?: emptyList()
            Timber.d("CleanupOrphanedTempFilesUseCase: Found ${allFiles.size} total files")
            
            // Filter for .temp_copy files
            val tempFiles = allFiles.filter { filePath ->
                TempFileNamingStrategy.isTempFile(filePath)
            }
            
            if (tempFiles.isEmpty()) {
                Timber.d("CleanupOrphanedTempFilesUseCase: No orphaned temp files found")
                return@withContext Result.success(0)
            }
            
            Timber.i("CleanupOrphanedTempFilesUseCase: Found ${tempFiles.size} orphaned temp files to delete")
            
            // Delete each temp file
            var deletedCount = 0
            val errors = mutableListOf<String>()
            
            tempFiles.forEach { tempFilePath ->
                Timber.d("CleanupOrphanedTempFilesUseCase: Deleting orphaned temp file: $tempFilePath")
                
                val deleteResult = handler.deleteFile(tempFilePath)
                
                if (deleteResult.isSuccess) {
                    deletedCount++
                    Timber.d("CleanupOrphanedTempFilesUseCase: Successfully deleted: $tempFilePath")
                } else {
                    val error = "Failed to delete $tempFilePath: ${deleteResult.exceptionOrNull()?.message}"
                    errors.add(error)
                    Timber.w("CleanupOrphanedTempFilesUseCase: $error")
                }
            }
            
            if (errors.isNotEmpty()) {
                Timber.w("CleanupOrphanedTempFilesUseCase: Cleanup completed with errors. Deleted: $deletedCount, Failed: ${errors.size}")
                // Return partial success
                return@withContext Result.success(deletedCount)
            }
            
            Timber.i("CleanupOrphanedTempFilesUseCase: Cleanup completed successfully. Deleted $deletedCount orphaned temp files")
            Result.success(deletedCount)
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("CleanupOrphanedTempFilesUseCase: Cleanup cancelled")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CleanupOrphanedTempFilesUseCase: Exception during cleanup")
            Result.failure(e)
        }
    }
}
