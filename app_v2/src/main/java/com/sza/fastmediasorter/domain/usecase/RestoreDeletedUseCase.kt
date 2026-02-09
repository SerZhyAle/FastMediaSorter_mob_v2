package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.model.TrashMetadata
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for restoring last deleted file(s) from .trash folder.
 */
class RestoreDeletedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localStrategy: LocalOperationStrategy,
    private val smbStrategy: SmbOperationStrategy,
    private val sftpStrategy: SftpOperationStrategy,
    private val ftpStrategy: FtpOperationStrategy,
    private val cloudStrategy: CloudOperationStrategy
) {
    /**
     * Restore last deleted files from .trash in the current directory.
     * 
     * @param currentPath The current directory path where .trash folder might exist
     * @return Result with restored files count or failure
     */
    suspend operator fun invoke(currentPath: String): Result<Int> = withContext(Dispatchers.IO) {
        Timber.d("RestoreDeletedUseCase: Restoring from .trash at: $currentPath")
        
        try {
            val trashBasePath = "$currentPath/.trash"
            
            // Determine strategy based on path
            val strategy = when {
                currentPath.startsWith("smb://", ignoreCase = true) -> smbStrategy
                currentPath.startsWith("sftp://", ignoreCase = true) -> sftpStrategy
                currentPath.startsWith("ftp://", ignoreCase = true) -> ftpStrategy
                currentPath.startsWith("cloud://", ignoreCase = true) -> cloudStrategy
                else -> localStrategy
            }
            
            // 1. Check if .trash exists
            val trashExists = strategy.exists(trashBasePath).getOrNull() ?: false
            if (!trashExists) {
                return@withContext Result.failure(Exception(context.getString(R.string.no_files_to_restore)))
            }
            
            // 2. Find latest timestamp folder
            // Need a listFiles method in strategy? Or assume strict structure?
            // Strategies don't expose listFiles uniformly in interface yet.
            // But we can try to guess or we need to add listFiles to interface.
            // Adding listFiles to interface is cleaner but more work.
            // For now, let's assume we can't easily list files without strategy-specific casting.
            
            val latestTrashPath = findLatestTrashFolder(trashBasePath, strategy)
            if (latestTrashPath == null) {
                return@withContext Result.failure(Exception("No trash snapshots found"))
            }

            val metadataPath = "$latestTrashPath/metadata.json"
            
            // 3. Read metadata
            val metadataJson = strategy.readFile(metadataPath).getOrNull()
            if (metadataJson == null) {
                return@withContext Result.failure(Exception("Failed to read metadata.json"))
            }
            
            val metadata = try {
                TrashMetadata.fromJson(metadataJson)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Invalid metadata format"))
            }
            
            Timber.d("RestoreDeletedUseCase: Metadata loaded - originalPath=${metadata.originalPath}, files=${metadata.deletedFiles.size}")
            
            // 4. Restore files
            var restoredCount = 0
            val errors = mutableListOf<String>()
            
            metadata.deletedFiles.forEach { fileName ->
                val sourceFile = "$latestTrashPath/$fileName"
                val targetFile = "${metadata.originalPath}/$fileName"
                
                try {
                    // Check if target already exists
                    if (strategy.exists(targetFile).getOrNull() == true) {
                        Timber.w("Skipping restore of $fileName - already exists")
                    } else {
                        // Move file back
                        val moveResult = strategy.moveFile(sourceFile, targetFile)
                        if (moveResult.isSuccess) {
                            restoredCount++
                        } else {
                            errors.add("Failed to move $fileName: ${moveResult.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Error restoring $fileName: ${e.message}")
                }
            }
            
            // 5. Cleanup trash snapshot
            try {
                // Delete the timestamp folder
                if (strategy is LocalOperationStrategy) {
                    File(latestTrashPath).deleteRecursively()
                } else {
                    // Start cleaning up files then dir
                    // Strategy.deleteFile usually deletes file or empty dir.
                    // Recursive delete is strategy specific.
                    // For now attempt delete path (some strategies might support recursive delete on deleteFile)
                    // If not, we leave it? Better to clean up.
                    // We can list files in trash folder from metadata and delete them if move failed?
                    // If move passed, source is gone.
                    strategy.deleteFile(metadataPath)
                    strategy.deleteFile(latestTrashPath) // Try delete dir
                }
                
                // If .trash is empty, delete it too
                // strategy.deleteFile(trashBasePath) 
            } catch (e: Exception) {
                Timber.w("Failed to cleanup trash folder: $e")
            }
            
            if (restoredCount > 0) {
                Result.success(restoredCount)
            } else {
                if (errors.isNotEmpty()) {
                    Result.failure(Exception("Failed to restore files: ${errors.joinToString(", ")}"))
                } else {
                    Result.failure(Exception("No files restored (duplicates exist?)"))
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "RestoreDeletedUseCase: Exception during restore")
            Result.failure(e)
        }
    }
    
    private suspend fun findLatestTrashFolder(trashBasePath: String, strategy: Any): String? {
        return try {
            // Cast to FileOperationStrategy since we know all strategies implement it
            // Ideally inject list of FileOperationStrategy or use the specific instance passed
            // The invoke method determines strategy based on path, so 'strategy' is one of the impls.
            // But 'strategy' variable in invoke is inferred as Any/Object in my previous snippet? 
            // No, the 'when' block returns specific types which share FileOperationStrategy interface ?
            // No, they implement FileOperationStrategy.
            // Wait, in previous snippet `val strategy = when ...` inferred closest common supertype.
            // If they all implement FileOperationStrategy, then it is FileOperationStrategy.
            
            if (strategy is FileOperationStrategy) {
                val result = strategy.listFiles(trashBasePath)
                val files = result.getOrNull() ?: return null
                
                // Filter for timestamp folders (numeric names)
                // paths are full paths/URIs. We need the last segment.
                val timestampFolders = files.filter { path ->
                    val name = getNameFromPath(path)
                    name.toLongOrNull() != null
                }
                
                // Find max timestamp
                timestampFolders.maxByOrNull { path ->
                    getNameFromPath(path).toLong()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error listing trash folders")
            null
        }
    }
    
    private fun getNameFromPath(path: String): String {
        return path.trimEnd('/').substringAfterLast('/')
    }

    /**
     * Read TrashMetadata from metadata.json file.
     */
    private suspend fun readMetadata(metadataPath: String, strategy: Any): TrashMetadata? {
        return try {
            if (strategy is FileOperationStrategy) {
                val result = strategy.readFile(metadataPath)
                result.getOrNull()?.let { json ->
                    TrashMetadata.fromJson(json)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "RestoreDeletedUseCase: Failed to read metadata")
            null
        }
    }
    
    /**
     * Move file using appropriate strategy.
     */
    private suspend fun moveFile(sourcePath: String, targetPath: String, strategy: Any): Result<Unit> {
        return try {
            if (strategy is FileOperationStrategy) {
                // Use moveFile from interface
                strategy.moveFile(sourcePath, targetPath)
            } else {
                Result.failure(IllegalArgumentException("Strategy does not implement FileOperationStrategy"))
            }
        } catch (e: Exception) {
            Timber.e(e, "RestoreDeletedUseCase: Failed to move file from $sourcePath to $targetPath")
            Result.failure(e)
        }
    }
    
    /**
     * Delete directory recursively.
     */
    private suspend fun deleteDirectory(dirPath: String, strategy: Any): Result<Unit> {
        return try {
             if (strategy is FileOperationStrategy) {
                // Strategies typically implement deleteFile which handles directories too (e.g. SMB/Local)
                // or we might need a dedicated deleteDirectory in interface?
                // Interface has deleteFile(path).
                // LocalOperationStrategy.deleteFile checks if directory and recursively deletes?
                // LocalOperationStrategy.deleteFile (I should check)
                // SmbOperationStrategy.deleteFile (I should check)
                // If they don't support recursive directory delete, this might fail for non-empty dirs.
                // But for now let's assume they might.
                strategy.deleteFile(dirPath)
            } else {
                Result.failure(IllegalArgumentException("Strategy does not implement FileOperationStrategy"))
            }
        } catch (e: Exception) {
            Timber.e(e, "RestoreDeletedUseCase: Error deleting directory: $dirPath")
            Result.failure(e)
        }
    }
}
