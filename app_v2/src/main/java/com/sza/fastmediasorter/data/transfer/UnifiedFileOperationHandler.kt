package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.transfer.FileOperationErrorHandler
import com.sza.fastmediasorter.domain.transfer.FileTransferProvider
import com.sza.fastmediasorter.domain.transfer.ProgressTracker
import com.sza.fastmediasorter.domain.transfer.TempFileManager
import com.sza.fastmediasorter.domain.transfer.generateOperationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified file operation handler for all protocols.
 * Orchestrates file transfers using protocol-specific providers.
 * 
 * Eliminates duplication across SMB/SFTP/FTP/Cloud handlers.
 */
@Singleton
class UnifiedFileOperationHandler @Inject constructor(
    private val localProvider: LocalTransferProvider,
    private val tempFileManager: TempFileManager,
    private val progressTracker: ProgressTracker,
    private val errorHandler: FileOperationErrorHandler
) {
    
    // Providers map (will be populated as providers are created)
    private val providers = mutableMapOf<String, FileTransferProvider>(
        "local" to localProvider
    )
    
    /**
     * Register protocol provider.
     * Called by Hilt module after providers are created.
     */
    fun registerProvider(protocol: String, provider: FileTransferProvider) {
        providers[protocol] = provider
        Timber.d("Registered provider: $protocol (${provider.protocolName})")
    }
    
    /**
     * Execute copy operation.
     * 
     * @param sourceFile Source file to copy
     * @param sourceResource Source resource (for credentials)
     * @param destResource Destination resource
     * @param onProgress Progress callback (percentage 0-100)
     * @param cancelFlag Function to check if operation should be cancelled
     * @return Result with destination path or error
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun executeCopy(
        sourceFile: MediaFile,
        sourceResource: MediaResource,
        destResource: MediaResource,
        onProgress: ((Int) -> Unit)? = null,
        cancelFlag: () -> Boolean = { false }
    ): Result<String> = withContext(Dispatchers.IO) {
        val operationId = generateOperationId("copy", sourceFile.path, destResource.path)
        
        try {
            if (cancelFlag()) {
                return@withContext Result.failure(Exception("Operation cancelled"))
            }
            
            val sourceProvider = getProvider(sourceFile.path)
            val destProvider = getProvider(destResource.path)
            
            Timber.d("Copy: ${sourceProvider.protocolName} -> ${destProvider.protocolName}")
            
            // Generate destination path
            val destPath = generateDestinationPath(destResource.path, sourceFile.name)
            
            // Check if same protocol (optimization possible)
            val result = if (sourceProvider::class == destProvider::class && 
                           sourceProvider.protocolName != "Local") {
                // Same protocol - let provider handle it (may be optimized)
                executeSameProtocolCopy(
                    sourceProvider,
                    sourceFile.path,
                    destPath,
                    operationId,
                    onProgress,
                    cancelFlag
                )
            } else {
                // Cross-protocol - download -> upload via temp file
                executeCrossProtocolCopy(
                    sourceProvider,
                    destProvider,
                    sourceFile.path,
                    destPath,
                    sourceFile.name,
                    operationId,
                    onProgress,
                    cancelFlag
                )
            }
            
            progressTracker.clearOperation(operationId)
            result
            
        } catch (e: Exception) {
            progressTracker.clearOperation(operationId)
            val errorMsg = errorHandler.handleError(e, "copy", sourceFile.path, destResource.path)
            Timber.e(e, "Copy failed: $errorMsg")
            Result.failure(Exception(errorMsg, e))
        }
    }
    
    /**
     * Execute move operation (copy + soft delete).
     * 
     * @param sourceFile Source file to move
     * @param sourceResource Source resource
     * @param destResource Destination resource
     * @param onProgress Progress callback
     * @param cancelFlag Cancel check function
     * @return Result with destination path and original path for undo
     */
    suspend fun executeMove(
        sourceFile: MediaFile,
        sourceResource: MediaResource,
        destResource: MediaResource,
        onProgress: ((Int) -> Unit)? = null,
        cancelFlag: () -> Boolean = { false }
    ): Result<MoveResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Copy file
            val copyResult = executeCopy(
                sourceFile,
                sourceResource,
                destResource,
                onProgress,
                cancelFlag
            )
            
            if (copyResult.isFailure) {
                return@withContext Result.failure(
                    copyResult.exceptionOrNull() ?: Exception("Copy failed")
                )
            }
            
            if (cancelFlag()) {
                // Cleanup copied file
                val destPath = copyResult.getOrNull()
                if (destPath != null) {
                    getProvider(destPath).deleteFile(destPath)
                }
                return@withContext Result.failure(Exception("Operation cancelled"))
            }
            
            // Step 2: Soft delete source (move to .trash/)
            val deleteResult = executeSoftDelete(sourceFile.path, sourceResource)
            
            if (deleteResult.isFailure) {
                // Rollback: delete copied file
                val destPath = copyResult.getOrNull()
                if (destPath != null) {
                    getProvider(destPath).deleteFile(destPath)
                }
                return@withContext Result.failure(
                    deleteResult.exceptionOrNull() ?: Exception("Delete failed")
                )
            }
            
            val destinationPath = copyResult.getOrNull()
            if (destinationPath == null) {
                val error = Exception("Copy succeeded but destination path is null")
                Timber.e(error, "Unexpected null destination path after successful copy")
                return@withContext Result.failure(error)
            }
            
            Result.success(
                MoveResult(
                    destinationPath = destinationPath,
                    originalPath = sourceFile.path,
                    trashPath = deleteResult.getOrNull()
                )
            )
            
        } catch (e: Exception) {
            val errorMsg = errorHandler.handleError(e, "move", sourceFile.path, destResource.path)
            Timber.e(e, "Move failed: $errorMsg")
            Result.failure(Exception(errorMsg, e))
        }
    }
    
    /**
     * Execute rename operation.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun executeRename(
        filePath: String,
        newName: String,
        resource: MediaResource
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(filePath)
            
            // Generate new path in same directory
            val directory = filePath.substringBeforeLast('/')
            val newPath = "$directory/$newName"
            
            return@withContext provider.renameFile(filePath, newPath)
            
        } catch (e: Exception) {
            val errorMsg = errorHandler.handleError(e, "rename", filePath, newName)
            Timber.e(e, "Rename failed: $errorMsg")
            return@withContext Result.failure(Exception(errorMsg, e))
        }
    }
    
    /**
     * Execute delete operation (soft delete to .trash/).
     */
    suspend fun executeDelete(
        filePath: String,
        resource: MediaResource
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeSoftDelete(filePath, resource)
        } catch (e: Exception) {
            val errorMsg = errorHandler.handleError(e, "delete", filePath)
            Timber.e(e, "Delete failed: $errorMsg")
            Result.failure(Exception(errorMsg, e))
        }
    }
    
    /**
     * Soft delete file (move to .trash/ folder).
     * 
     * @return Result with trash path
     */
    private suspend fun executeSoftDelete(
        filePath: String,
        resource: MediaResource
    ): Result<String> {
        val provider = getProvider(filePath)
        
        // Generate trash path
        val fileName = filePath.substringAfterLast('/')
        val trashPath = "${resource.path}/.trash/$fileName"
        
        // Ensure .trash/ directory exists
        val trashDir = trashPath.substringBeforeLast('/')
        provider.createDirectory(trashDir)
        
        // Move to trash
        return provider.moveFile(filePath, trashPath)
    }
    
    /**
     * Same protocol copy (let provider optimize).
     */
    private suspend fun executeSameProtocolCopy(
        provider: FileTransferProvider,
        sourcePath: String,
        destPath: String,
        operationId: String,
        onProgress: ((Int) -> Unit)?,
        cancelFlag: () -> Boolean
    ): Result<String> {
        // For same protocol, use provider's move/copy if available
        // For now, fallback to cross-protocol method
        return executeCrossProtocolCopy(
            provider,
            provider,
            sourcePath,
            destPath,
            sourcePath.substringAfterLast('/'),
            operationId,
            onProgress,
            cancelFlag
        )
    }
    
    /**
     * Cross-protocol copy (download -> upload via temp file).
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeCrossProtocolCopy(
        sourceProvider: FileTransferProvider,
        destProvider: FileTransferProvider,
        sourcePath: String,
        destPath: String,
        fileName: String,
        operationId: String,
        onProgress: ((Int) -> Unit)?,
        cancelFlag: () -> Boolean
    ): Result<String> {
        var tempFile: File? = null
        
        try {
            // Create temp file
            tempFile = tempFileManager.createTempFileFromName(fileName)
            
            if (cancelFlag()) {
                return Result.failure(Exception("Operation cancelled"))
            }
            
            // Download to temp
            val downloadResult = sourceProvider.downloadFile(
                sourcePath,
                tempFile
            ) { transferred, total ->
                // Report progress (50% for download)
                val percentage = ((transferred.toDouble() / total.toDouble()) * 50).toInt()
                onProgress?.invoke(percentage)
            }
            
            if (downloadResult.isFailure) {
                return Result.failure(
                    downloadResult.exceptionOrNull() ?: Exception("Download failed")
                )
            }
            
            if (cancelFlag()) {
                return Result.failure(Exception("Operation cancelled"))
            }
            
            // Upload from temp
            val uploadResult = destProvider.uploadFile(
                tempFile,
                destPath
            ) { transferred, total ->
                // Report progress (50-100% for upload)
                val percentage = 50 + ((transferred.toDouble() / total.toDouble()) * 50).toInt()
                onProgress?.invoke(percentage)
            }
            
            if (uploadResult.isFailure) {
                return Result.failure(
                    uploadResult.exceptionOrNull() ?: Exception("Upload failed")
                )
            }
            
            Timber.d("Cross-protocol copy complete: $sourcePath -> $destPath")
            return Result.success(destPath)
            
        } finally {
            // Cleanup temp file
            tempFile?.let { tempFileManager.cleanupTempFile(it) }
        }
    }
    
    /**
     * Get provider for path based on protocol prefix.
     */
    private fun getProvider(path: String): FileTransferProvider {
        val protocol = when {
            path.startsWith("smb://") -> "smb"
            path.startsWith("sftp://") -> "sftp"
            path.startsWith("ftp://") -> "ftp"
            path.startsWith("cloud://") -> "cloud"
            else -> "local"
        }
        
        return providers[protocol] 
            ?: throw IllegalStateException("No provider registered for protocol: $protocol")
    }
    
    /**
     * Generate destination file path.
     */
    private fun generateDestinationPath(destResourcePath: String, fileName: String): String {
        return if (destResourcePath.endsWith('/')) {
            "$destResourcePath$fileName"
        } else {
            "$destResourcePath/$fileName"
        }
    }
}

/**
 * Result of move operation with undo information.
 */
data class MoveResult(
    val destinationPath: String,
    val originalPath: String,
    val trashPath: String?
)
