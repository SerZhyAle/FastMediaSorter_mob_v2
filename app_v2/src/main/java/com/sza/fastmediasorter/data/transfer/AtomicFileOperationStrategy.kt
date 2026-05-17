package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Wrapper strategy that adds atomic file transfer capability to existing FileOperationStrategy.
 * 
 * **Atomic Transfer Pattern**:
 * 1. Copy file to temporary name (`file.ext.temp_copy`)
 * 2. On success, rename to final name (`file.ext`)
 * 3. On failure, cleanup temporary file
 * 
 * This prevents external systems from detecting incomplete files during transfer.
 * 
 * **Usage**:
 * ```kotlin
 * val baseStrategy = LocalOperationStrategy(context)
 * val atomicStrategy = AtomicFileOperationStrategy(baseStrategy, enableAtomic = true)
 * 
 * // Will use atomic pattern if enabled
 * atomicStrategy.copyFile(source, destination, overwrite, progressCallback)
 * ```
 * 
 * @param delegate Underlying strategy to delegate to (e.g., LocalOperationStrategy)
 * @param enableAtomic Whether to use atomic pattern (default = true)
 */
class AtomicFileOperationStrategy(
    private val delegate: FileOperationStrategy,
    private val destinationClassifier: LocalDestinationClassifier,
    private var enableAtomic: Boolean = true
) : FileOperationStrategy by delegate {

    private sealed interface AtomicCopyOutcome {
        data object Success : AtomicCopyOutcome
        data class Cancelled(val error: CancellationException) : AtomicCopyOutcome
        data class Failed(val error: Throwable) : AtomicCopyOutcome
    }
    
    /**
     * Enable or disable atomic transfer mode at runtime.
     */
    fun setAtomicEnabled(enabled: Boolean) {
        enableAtomic = enabled
        Timber.d("AtomicFileOperationStrategy: Atomic mode ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    /**
     * Copy file with atomic transfer pattern.
     * 
     * If atomic mode enabled:
     * 1. Copy to `destination.temp_copy`
     * 2. Rename to `destination`
     * 
     * If atomic mode disabled:
     * - Delegates directly to underlying strategy
     */
    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        if (!enableAtomic) {
            Timber.d("AtomicFileOperationStrategy.copyFile: Atomic disabled, using direct copy")
            return delegate.copyFile(source, destination, overwrite, progressCallback)
        }

        // S0231: public collections handle atomicity via MediaStore IS_PENDING in
        // LocalDestinationWriter — skip the *.temp_copy + rename layer for these paths.
        val category = destinationClassifier.classify(destination)
        if (category is LocalDestinationCategory.PublicCollection) {
            Timber.d("S0231: atomic short-circuit (public collection) destination=$destination")
            return delegate.copyFile(source, destination, overwrite, progressCallback)
        }

        Timber.d("AtomicFileOperationStrategy.copyFile: Using atomic pattern")
        Timber.d("  Source: $source")
        Timber.d("  Destination: $destination")
        
        val tempDestination = TempFileNamingStrategy.getTempPath(destination)
        Timber.d("  Temp destination: $tempDestination")
        
        val outcome = try {
            // Step 0: Check destination exists BEFORE copying if overwrite=false
            // This prevents unnecessary data transfer if file already exists
            if (!overwrite) {
                val destExists = pathExists(destination).getOrNull() ?: false
                if (destExists) {
                    Timber.w("AtomicFileOperationStrategy: Destination exists and overwrite=false, aborting")
                    // Extract filename from destination path for error message
                    val fileName = destination.substringAfterLast('/')
                        .ifEmpty { destination.substringAfterLast('\\') }
                        .ifEmpty { destination }
                    return Result.failure(FileExistsException(fileName, destination, isMove = false))
                }
            }
            
            // Step 1: Handle collision - if temp file exists, delete it (overwrite policy)
            val tempExists = pathExists(tempDestination).getOrNull() ?: false
            if (tempExists) {
                Timber.w("AtomicFileOperationStrategy: Temp file already exists, deleting: $tempDestination")
                val deleteResult = deletePath(tempDestination)
                if (deleteResult.isFailure) {
                    Timber.e("AtomicFileOperationStrategy: Failed to delete existing temp file")
                    return Result.failure(
                        Exception("Failed to cleanup existing temp file: ${deleteResult.exceptionOrNull()?.message}")
                    )
                }
            }
            
            // Step 2: Copy to temporary destination
            Timber.d("AtomicFileOperationStrategy: Starting copy to temp destination")
            val copyResult = delegate.copyFile(source, tempDestination, overwrite = true, progressCallback)
            
            if (copyResult.isFailure) {
                Timber.e("AtomicFileOperationStrategy: Copy to temp destination failed")
                AtomicCopyOutcome.Failed(
                    copyResult.exceptionOrNull() ?: Exception("Copy to temp destination failed")
                )
            } else {
                Timber.d("AtomicFileOperationStrategy: Copy to temp completed, size check...")
                finalizeSuccessfulCopy(source, tempDestination, destination, overwrite)
            }
        } catch (e: CancellationException) {
            handleCancelledCopy(tempDestination, e)
        } catch (e: Exception) {
            handleFailedCopy(tempDestination, e)
        }

        return completeCopyOutcome(tempDestination, destination, outcome)
    }
    
    /**
     * Move file operation.
     * 
     * Note: Move operations don't use atomic pattern - they either:
     * - Use native rename (already atomic for same-filesystem)
     * - Use copy+delete (atomic copy is used if enabled)
     */
    override suspend fun moveFile(source: String, destination: String): Result<Unit> {
        // Delegate moveFile as-is
        // Underlying strategies will call copyFile() internally if needed (which is atomic)
        Timber.d("AtomicFileOperationStrategy.moveFile: Delegating to underlying strategy")
        return delegate.moveFile(source, destination)
    }
    
    /**
     * Rename file using protocol-specific method.
     * 
     * For local files: Use File.renameTo()
     * For network protocols: Delegate expects protocol-specific rename
     */
    private suspend fun renamePath(oldPath: String, newPath: String): Result<Unit> {
        return try {
            if (isLocalPath(oldPath) && isLocalPath(newPath)) {
                return renameLocalPath(oldPath, newPath)
            }

            // Try using moveFile (which should use rename for same-location files)
            val moveResult = delegate.moveFile(oldPath, newPath)
            
            if (moveResult.isSuccess) {
                Timber.d("AtomicFileOperationStrategy: Rename via moveFile successful")
                Result.success(Unit)
            } else {
                Timber.w("AtomicFileOperationStrategy: moveFile failed, trying copy+delete")
                // Fallback: copy + delete
                val copyResult = delegate.copyFile(oldPath, newPath, overwrite = true, progressCallback = null)
                if (copyResult.isFailure) {
                    return Result.failure(copyResult.exceptionOrNull() ?: Exception("Rename failed"))
                }
                
                val deleteResult = delegate.deleteFile(oldPath)
                if (deleteResult.isFailure) {
                    Timber.w("AtomicFileOperationStrategy: Copied but failed to delete old file")
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "AtomicFileOperationStrategy: renamePath exception")
            Result.failure(e)
        }
    }

    private fun renameLocalPath(oldPath: String, newPath: String): Result<Unit> {
        return try {
            val oldFile = File(oldPath)
            val newFile = File(newPath)

            if (!oldFile.exists()) {
                return Result.failure(Exception("Source temp file does not exist: $oldPath"))
            }

            newFile.parentFile?.mkdirs()

            if (oldFile.renameTo(newFile)) {
                return Result.success(Unit)
            }

            FileInputStream(oldFile).use { input ->
                FileOutputStream(newFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!oldFile.delete()) {
                Timber.w("AtomicFileOperationStrategy: Local fallback rename copied file but failed to delete source: $oldPath")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun finalizeSuccessfulCopy(
        source: String,
        tempDestination: String,
        destination: String,
        overwrite: Boolean
    ): AtomicCopyOutcome {
        val postConditionResult = verifyTempPostCondition(tempDestination)
        if (postConditionResult.isFailure) {
            Timber.e(
                "AtomicFileOperationStrategy: temp-missing-invariant source=$source temp=$tempDestination destination=$destination"
            )
            return AtomicCopyOutcome.Failed(
                postConditionResult.exceptionOrNull() ?: Exception("Temp file not found after copy: $tempDestination")
            )
        }

        if (overwrite) {
            val destExists = pathExists(destination).getOrNull() ?: false
            if (destExists) {
                Timber.d("AtomicFileOperationStrategy: Destination exists, deleting before rename")
                val deleteResult = deletePath(destination)
                if (deleteResult.isFailure) {
                    Timber.e("AtomicFileOperationStrategy: Failed to delete existing destination")
                    return AtomicCopyOutcome.Failed(
                        Exception("Failed to delete existing destination: ${deleteResult.exceptionOrNull()?.message}")
                    )
                }
            }
        }

        Timber.d("AtomicFileOperationStrategy: Renaming temp to final destination")
        val renameResult = renamePath(tempDestination, destination)

        if (renameResult.isFailure) {
            Timber.e("AtomicFileOperationStrategy: Rename failed: ${renameResult.exceptionOrNull()?.message}")
            return AtomicCopyOutcome.Failed(
                renameResult.exceptionOrNull() ?: Exception("Rename failed: $tempDestination -> $destination")
            )
        }

        Timber.i("AtomicFileOperationStrategy: Atomic copy completed successfully")
        return AtomicCopyOutcome.Success
    }

    private suspend fun verifyTempPostCondition(tempPath: String): Result<Unit> {
        return if (isLocalPath(tempPath)) {
            try {
                val tempFile = File(tempPath)
                if (!tempFile.exists()) {
                    return Result.failure(Exception("Temp file not found after copy: $tempPath"))
                }

                val tempLength = tempFile.length()
                if (tempLength < 0L) {
                    return Result.failure(Exception("Temp file length unavailable after copy: $tempPath"))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            val tempExists = pathExists(tempPath).getOrNull() ?: false
            if (tempExists) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Temp file not found after copy: $tempPath"))
            }
        }
    }

    private suspend fun handleCancelledCopy(
        tempDestination: String,
        error: CancellationException
    ): AtomicCopyOutcome {
        Timber.i("AtomicFileOperationStrategy: Atomic copy cancelled")
        return AtomicCopyOutcome.Cancelled(error)
    }

    private suspend fun handleFailedCopy(
        tempDestination: String,
        error: Exception
    ): AtomicCopyOutcome {
        Timber.e(error, "AtomicFileOperationStrategy: Unexpected error during atomic copy")
        return AtomicCopyOutcome.Failed(error)
    }

    private suspend fun completeCopyOutcome(
        tempDestination: String,
        destination: String,
        outcome: AtomicCopyOutcome
    ): Result<String> {
        if (outcome !is AtomicCopyOutcome.Success) {
            cleanupTempFile(tempDestination)
        }

        return when (outcome) {
            AtomicCopyOutcome.Success -> Result.success(destination)
            is AtomicCopyOutcome.Cancelled -> Result.failure(outcome.error)
            is AtomicCopyOutcome.Failed -> Result.failure(outcome.error)
        }
    }

    private suspend fun pathExists(path: String): Result<Boolean> {
        return if (isLocalPath(path)) {
            Result.success(File(path).exists())
        } else {
            delegate.exists(path)
        }
    }

    private suspend fun deletePath(path: String): Result<Unit> {
        return if (isLocalPath(path)) {
            try {
                val file = File(path)
                if (!file.exists()) {
                    Result.success(Unit)
                } else if (file.delete()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete local file: $path"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            delegate.deleteFile(path)
        }
    }

    private fun isLocalPath(path: String): Boolean {
        val normalized = path.lowercase()
        return !normalized.startsWith("smb:") &&
            !normalized.startsWith("sftp:") &&
            !normalized.startsWith("ftp:") &&
            !normalized.startsWith("gdrive:") &&
            !normalized.startsWith("dropbox:") &&
            !normalized.startsWith("onedrive:") &&
            !normalized.startsWith("content:")
    }
    
    /**
     * Cleanup temporary file on failure.
     * Best-effort — logs error but doesn't throw.
     *
     * Runs under NonCancellable so cleanup completes even when the parent scope
     * (e.g. Activity lifecycleScope) is being cancelled mid-copy. Without this,
     * cancellation leaves orphan `*.temp_copy` files on the destination FS
     * because deletePath() observes the cancelled context and bails out.
     */
    private suspend fun cleanupTempFile(tempPath: String) {
        withContext(NonCancellable) {
            try {
                Timber.d("AtomicFileOperationStrategy: Attempting cleanup of temp file: $tempPath")
                val deleteResult = deletePath(tempPath)
                if (deleteResult.isSuccess) {
                    Timber.d("AtomicFileOperationStrategy: Temp file cleanup successful")
                } else {
                    Timber.w("AtomicFileOperationStrategy: Temp file cleanup failed: ${deleteResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "AtomicFileOperationStrategy: Exception during temp file cleanup")
            }
        }
    }
    
    override fun getProtocolName(): String {
        return "atomic-${delegate.getProtocolName()}"
    }
}
