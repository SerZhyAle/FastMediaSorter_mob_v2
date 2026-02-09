package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Use case for editing network images (SMB/S/FTP)
 * Downloads image to temp folder, applies transformation, uploads back
 * 
 * Supports:
 * - Rotate (via RotateImageUseCase)
 * - Flip (via FlipImageUseCase)
 * - Filters: Grayscale, Sepia, Negative (via ApplyImageFilterUseCase)
 * - Adjustments: Brightness, Contrast, Saturation (via AdjustImageUseCase)
 */
class NetworkImageEditUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rotateImageUseCase: RotateImageUseCase,
    private val flipImageUseCase: FlipImageUseCase,
    private val applyImageFilterUseCase: ApplyImageFilterUseCase,
    private val adjustImageUseCase: AdjustImageUseCase,
    private val smbFileOperationHandler: SmbFileOperationHandler,
    private val sftpFileOperationHandler: SftpFileOperationHandler,
    private val ftpFileOperationHandler: FtpFileOperationHandler
) {

    sealed class EditOperation {
        data class Rotate(val angle: Float) : EditOperation()
        data class Flip(val direction: FlipImageUseCase.FlipDirection) : EditOperation()
        data class Filter(val filterType: ApplyImageFilterUseCase.FilterType) : EditOperation()
        data class Adjust(val adjustments: AdjustImageUseCase.Adjustments) : EditOperation()
    }

    sealed class EditProgress {
        object Downloading : EditProgress()
        data class Downloaded(val tempFilePath: String) : EditProgress()
        object Editing : EditProgress()
        object Uploading : EditProgress()
        object Completed : EditProgress()
        data class Error(val message: String, val exception: Throwable? = null) : EditProgress()
    }

    /**
     * Edit network image with specified operation
     * @param networkPath full network path (smb://, sftp://, ftp://)
     * @param operation edit operation (rotate or flip)
     * @param progressCallback callback for progress updates (optional)
     * @return Result with success/failure
     */
    suspend fun execute(
        networkPath: String,
        operation: EditOperation,
        progressCallback: ((EditProgress) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        
        try {
            // Validate network path
            if (!isNetworkPath(networkPath)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Not a network path: $networkPath")
                )
            }

            Timber.d("NetworkImageEdit: Starting edit for $networkPath")
            
            // Step 1: Download to temp folder
            progressCallback?.invoke(EditProgress.Downloading)
            tempFile = downloadToTemp(networkPath)
            
            if (tempFile == null || !tempFile.exists()) {
                return@withContext Result.failure(
                    Exception("Failed to download network image to temp")
                )
            }
            
            Timber.d("NetworkImageEdit: Downloaded to ${tempFile.absolutePath}")
            progressCallback?.invoke(EditProgress.Downloaded(tempFile.absolutePath))
            
            // Step 2: Apply transformation
            progressCallback?.invoke(EditProgress.Editing)
            val editResult = when (operation) {
                is EditOperation.Rotate -> {
                    rotateImageUseCase.execute(tempFile.absolutePath, operation.angle)
                }
                is EditOperation.Flip -> {
                    flipImageUseCase.execute(tempFile.absolutePath, operation.direction)
                }
                is EditOperation.Filter -> {
                    applyImageFilterUseCase.execute(tempFile.absolutePath, operation.filterType)
                }
                is EditOperation.Adjust -> {
                    adjustImageUseCase.execute(tempFile.absolutePath, operation.adjustments)
                }
            }
            
            if (editResult.isFailure) {
                return@withContext Result.failure(
                    editResult.exceptionOrNull() ?: Exception("Edit operation failed")
                )
            }
            
            Timber.d("NetworkImageEdit: Edit operation completed")
            
            // Step 3: Upload back to network location
            progressCallback?.invoke(EditProgress.Uploading)
            val uploadSuccess = uploadFromTemp(tempFile, networkPath)
            
            if (!uploadSuccess) {
                return@withContext Result.failure(
                    Exception("Failed to upload edited image back to network")
                )
            }
            
            Timber.i("NetworkImageEdit: Successfully edited and uploaded $networkPath")
            progressCallback?.invoke(EditProgress.Completed)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "NetworkImageEdit: Failed to edit $networkPath")
            progressCallback?.invoke(EditProgress.Error(e.message ?: "Unknown error", e))
            Result.failure(e)
        } finally {
            // Step 4: Cleanup temp file
            tempFile?.let { file ->
                if (file.exists()) {
                    try {
                        file.delete()
                        Timber.d("NetworkImageEdit: Cleaned up temp file ${file.absolutePath}")
                    } catch (e: Exception) {
                        Timber.w(e, "NetworkImageEdit: Failed to delete temp file")
                    }
                }
            }
        }
    }
    
    /**
     * Rotate network image by specified angle
     * Convenience method that wraps execute() with Rotate operation
     */
    suspend fun rotateImage(networkPath: String, angle: Float): Result<Unit> {
        return execute(networkPath, EditOperation.Rotate(angle))
    }
    
    /**
     * Flip network image in specified direction
     * Convenience method that wraps execute() with Flip operation
     */
    suspend fun flipImage(networkPath: String, direction: FlipImageUseCase.FlipDirection): Result<Unit> {
        return execute(networkPath, EditOperation.Flip(direction))
    }
    
    /**
     * Apply filter to network image
     * Convenience method that wraps execute() with Filter operation
     */
    suspend fun applyFilter(networkPath: String, filterType: ApplyImageFilterUseCase.FilterType): Result<Unit> {
        return execute(networkPath, EditOperation.Filter(filterType))
    }
    
    /**
     * Apply adjustments to network image
     * Convenience method that wraps execute() with Adjust operation
     */
    suspend fun applyAdjustments(networkPath: String, adjustments: AdjustImageUseCase.Adjustments): Result<Unit> {
        return execute(networkPath, EditOperation.Adjust(adjustments))
    }

    private fun isNetworkPath(path: String): Boolean {
        return path.startsWith("smb://") || 
               path.startsWith("sftp://") || 
               path.startsWith("ftp://")
    }

    private suspend fun downloadToTemp(networkPath: String): File? {
        // Create temp directory in app cache
        val tempDir = File(context.cacheDir, "network_image_edit")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // Use original filename (handlers will create file with this name in tempDir)
        val fileName = networkPath.substringAfterLast('/')
        val tempFile = File(tempDir, fileName)
        
        return try {
            when {
                networkPath.startsWith("smb://") -> {
                    // Use SMB handler to download
                    val sourceFile = object : File(networkPath) {
                        override fun getAbsolutePath(): String = networkPath
                        override fun getPath(): String = networkPath
                    }
                    
                    val copyOperation = FileOperation.Copy(
                        sources = listOf(sourceFile),
                        destination = tempDir,
                        overwrite = true
                    )
                    
                    when (smbFileOperationHandler.executeCopy(copyOperation)) {
                        is FileOperationResult.Success -> tempFile
                        else -> {
                            Timber.e("SMB download failed for $networkPath")
                            null
                        }
                    }
                }
                networkPath.startsWith("sftp://") -> {
                    // Use SFTP handler to download
                    val sourceFile = object : File(networkPath) {
                        override fun getAbsolutePath(): String = networkPath
                        override fun getPath(): String = networkPath
                    }
                    
                    val copyOperation = FileOperation.Copy(
                        sources = listOf(sourceFile),
                        destination = tempDir,
                        overwrite = true
                    )
                    
                    when (sftpFileOperationHandler.executeCopy(copyOperation)) {
                        is FileOperationResult.Success -> tempFile
                        else -> {
                            Timber.e("SFTP download failed for $networkPath")
                            null
                        }
                    }
                }
                networkPath.startsWith("ftp://") -> {
                    // Use FTP handler to download
                    val sourceFile = object : File(networkPath) {
                        override fun getAbsolutePath(): String = networkPath
                        override fun getPath(): String = networkPath
                    }
                    
                    val copyOperation = FileOperation.Copy(
                        sources = listOf(sourceFile),
                        destination = tempDir,
                        overwrite = true
                    )
                    
                    when (ftpFileOperationHandler.executeCopy(copyOperation)) {
                        is FileOperationResult.Success -> tempFile
                        else -> {
                            Timber.e("FTP download failed for $networkPath")
                            null
                        }
                    }
                }
                else -> {
                    Timber.e("Unsupported network protocol: $networkPath")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download network image: $networkPath")
            null
        }
    }

    private suspend fun uploadFromTemp(tempFile: File, networkPath: String): Boolean {
        return try {
            // Extract destination directory from network path
            val networkDir = networkPath.substringBeforeLast('/')
            val destinationFolder = object : File(networkDir) {
                override fun getAbsolutePath(): String = networkDir
                override fun getPath(): String = networkDir
            }
            
            when {
                networkPath.startsWith("smb://") -> {
                    val copyOperation = FileOperation.Copy(
                        sources = listOf(tempFile),
                        destination = destinationFolder,
                        overwrite = true
                    )
                    
                    when (smbFileOperationHandler.executeCopy(copyOperation)) {
                        is FileOperationResult.Success -> true
                        else -> {
                            Timber.e("SMB upload failed for $networkPath")
                            false
                        }
                    }
                }
                networkPath.startsWith("sftp://") -> {
                    val copyOperation = FileOperation.Copy(
                        sources = listOf(tempFile),
                        destination = destinationFolder,
                        overwrite = true
                    )
                    
                    when (sftpFileOperationHandler.executeCopy(copyOperation)) {
                        is FileOperationResult.Success -> true
                        else -> {
                            Timber.e("SFTP upload failed for $networkPath")
                            false
                        }
                    }
                }
                networkPath.startsWith("ftp://") -> {
                    val copyOperation = FileOperation.Copy(
                        sources = listOf(tempFile),
                        destination = destinationFolder,
                        overwrite = true
                    )
                    
                    when (ftpFileOperationHandler.executeCopy(copyOperation)) {
                        is FileOperationResult.Success -> true
                        else -> {
                            Timber.e("FTP upload failed for $networkPath")
                            false
                        }
                    }
                }
                else -> {
                    Timber.e("Unsupported network protocol: $networkPath")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload edited image: $networkPath")
            false
        }
    }
}
