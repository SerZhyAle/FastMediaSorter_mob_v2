package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import timber.log.Timber

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
    private var enableAtomic: Boolean = true
) : FileOperationStrategy by delegate {
    
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
        
        Timber.d("AtomicFileOperationStrategy.copyFile: Using atomic pattern")
        Timber.d("  Source: $source")
        Timber.d("  Destination: $destination")
        
        val tempDestination = TempFileNamingStrategy.getTempPath(destination)
        Timber.d("  Temp destination: $tempDestination")
        
        return try {
            // Step 1: Handle collision - if temp file exists, delete it (overwrite policy)
            val tempExists = delegate.exists(tempDestination).getOrNull() ?: false
            if (tempExists) {
                Timber.w("AtomicFileOperationStrategy: Temp file already exists, deleting: $tempDestination")
                val deleteResult = delegate.deleteFile(tempDestination)
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
                // Cleanup temp file on failure
                cleanupTempFile(tempDestination)
                return copyResult
            }
            
            Timber.d("AtomicFileOperationStrategy: Copy to temp completed, size check...")
            
            // Step 3: Verify temp file exists
            val tempExistsAfterCopy = delegate.exists(tempDestination).getOrNull() ?: false
            if (!tempExistsAfterCopy) {
                Timber.e("AtomicFileOperationStrategy: Temp file doesn't exist after copy!")
                return Result.failure(Exception("Temp file not found after copy: $tempDestination"))
            }
            
            // Step 4: Handle destination overwrite if needed
            if (overwrite) {
                val destExists = delegate.exists(destination).getOrNull() ?: false
                if (destExists) {
                    Timber.d("AtomicFileOperationStrategy: Destination exists, deleting before rename")
                    val deleteResult = delegate.deleteFile(destination)
                    if (deleteResult.isFailure) {
                        Timber.e("AtomicFileOperationStrategy: Failed to delete existing destination")
                        cleanupTempFile(tempDestination)
                        return Result.failure(
                            Exception("Failed to delete existing destination: ${deleteResult.exceptionOrNull()?.message}")
                        )
                    }
                }
            }
            
            // Step 5: Rename temp to final destination
            Timber.d("AtomicFileOperationStrategy: Renaming temp to final destination")
            val renameResult = renameFile(tempDestination, destination)
            
            if (renameResult.isFailure) {
                Timber.e("AtomicFileOperationStrategy: Rename failed: ${renameResult.exceptionOrNull()?.message}")
                // Cleanup temp file on failure
                cleanupTempFile(tempDestination)
                return renameResult.map { destination }
            }
            
            Timber.i("AtomicFileOperationStrategy: Atomic copy completed successfully")
            Result.success(destination)
            
        } catch (e: Exception) {
            Timber.e(e, "AtomicFileOperationStrategy: Unexpected error during atomic copy")
            // Attempt cleanup
            cleanupTempFile(tempDestination)
            Result.failure(e)
        }
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
    private suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> {
        return try {
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
            Timber.e(e, "AtomicFileOperationStrategy: renameFile exception")
            Result.failure(e)
        }
    }
    
    /**
     * Cleanup temporary file on failure.
     * Best-effort - logs error but doesn't throw.
     */
    private suspend fun cleanupTempFile(tempPath: String) {
        try {
            Timber.d("AtomicFileOperationStrategy: Attempting cleanup of temp file: $tempPath")
            val deleteResult = delegate.deleteFile(tempPath)
            if (deleteResult.isSuccess) {
                Timber.d("AtomicFileOperationStrategy: Temp file cleanup successful")
            } else {
                Timber.w("AtomicFileOperationStrategy: Temp file cleanup failed: ${deleteResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "AtomicFileOperationStrategy: Exception during temp file cleanup")
        }
    }
    
    override fun getProtocolName(): String {
        return "atomic-${delegate.getProtocolName()}"
    }
}
