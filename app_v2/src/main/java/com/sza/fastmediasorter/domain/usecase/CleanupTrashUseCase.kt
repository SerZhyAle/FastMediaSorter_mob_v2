package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for cleaning up .trash folder for a specific resource.
 * Deletes the entire .trash directory silently.
 */
class CleanupTrashUseCase @Inject constructor(
    private val localStrategy: LocalOperationStrategy,
    private val smbStrategy: SmbOperationStrategy,
    private val sftpStrategy: SftpOperationStrategy,
    private val ftpStrategy: FtpOperationStrategy,
    private val cloudStrategy: CloudOperationStrategy
) {
    /**
     * Clean up .trash folder for given resource path.
     * 
     * @param resourceRootPath Root path of the resource (e.g., "/storage/emulated/0/DCIM", "smb://server/share")
     * @return Result with success/failure
     */
    suspend operator fun invoke(resourceRootPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        Timber.d("CleanupTrashUseCase: Cleaning .trash for resource: $resourceRootPath")
        
        try {
            val trashPath = "$resourceRootPath/.trash"
            
            // Determine which strategy to use based on path
            val strategy = when {
                resourceRootPath.startsWith("smb://") -> smbStrategy
                resourceRootPath.startsWith("sftp://") -> sftpStrategy
                resourceRootPath.startsWith("ftp://") -> ftpStrategy
                resourceRootPath.startsWith("cloud://") -> cloudStrategy
                else -> localStrategy
            }
            
            // Check if .trash exists
            val existsResult = strategy.exists(trashPath)
            if (existsResult.isSuccess && existsResult.getOrNull() == true) {
                Timber.d("CleanupTrashUseCase: .trash exists, deleting...")
                
                // Delete .trash directory recursively
                val deleteResult = deleteDirectory(trashPath, strategy)
                if (deleteResult.isSuccess) {
                    Timber.d("CleanupTrashUseCase: .trash deleted successfully")
                    Result.success(Unit)
                } else {
                    Timber.w("CleanupTrashUseCase: Failed to delete .trash: ${deleteResult.exceptionOrNull()?.message}")
                    Result.failure(deleteResult.exceptionOrNull() ?: Exception("Unknown error deleting .trash"))
                }
            } else {
                Timber.d("CleanupTrashUseCase: .trash does not exist or cannot be checked")
                Result.success(Unit) // No .trash to clean = success
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.d("CleanupTrashUseCase: Cleanup cancelled")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CleanupTrashUseCase: Exception during cleanup")
            Result.failure(e)
        }
    }
    
    /**
     * Recursively delete directory and all its contents.
     */
    private suspend fun deleteDirectory(dirPath: String, strategy: Any): Result<Unit> {
        return try {
            // For local files, use File API
            if (strategy is LocalOperationStrategy) {
                val dir = File(dirPath)
                if (dir.exists() && dir.isDirectory) {
                    dir.deleteRecursively()
                    Timber.d("CleanupTrashUseCase: Recursively deleted local directory: $dirPath")
                }
                Result.success(Unit)
            } else {
                // For network/cloud, use strategy's delete method
                // Note: Strategies should support recursive delete or we need to list & delete files individually
                when (strategy) {
                    is SmbOperationStrategy -> strategy.deleteFile(dirPath)
                    is SftpOperationStrategy -> strategy.deleteFile(dirPath)
                    is FtpOperationStrategy -> strategy.deleteFile(dirPath)
                    is CloudOperationStrategy -> strategy.deleteFile(dirPath)
                    else -> Result.failure(IllegalArgumentException("Unsupported strategy"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CleanupTrashUseCase: Error deleting directory: $dirPath")
            Result.failure(e)
        }
    }
}
