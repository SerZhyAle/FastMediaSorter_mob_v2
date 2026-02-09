package com.sza.fastmediasorter.data.transfer

import android.net.Uri
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperationResult

/**
 * Interface defining a strategy for transferring files between diverse sources and destinations.
 * Implementations handle specific protocol combinations (e.g., SMB to Local, SFTP to SMB).
 */
interface TransferStrategy {
    
    /**
     * Check if this strategy can handle the given source and destination URIs.
     * @param source Source URI
     * @param destination Destination URI
     * @return true if this strategy supports this combination
     */
    fun canHandle(source: Uri, destination: Uri): Boolean {
        return supports(source.scheme, destination.scheme)
    }
    
    /**
     * Executes the copy operation.
     * @param source The source file/resource URI.
     * @param destination The destination directory/resource URI.
     * @param overwrite Whether to overwrite existing files.
     * @param progressCallback Callback for transfer progress.
     * @return Result of the operation (true for success, false for failure).
     */
    suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        // Default implementation calls the version with credentials
        val success = copy(source, destination, overwrite, null, progressCallback)
        return if (success) {
            FileOperationResult.Success(
                processedCount = 1,
                operation = com.sza.fastmediasorter.domain.usecase.FileOperation.Copy(emptyList(), java.io.File(""), false),
                copiedFilePaths = listOf(destination.toString())
            )
        } else {
            FileOperationResult.Failure("Copy operation failed")
        }
    }
    
    /**
     * Executes the copy operation with optional credentials.
     * @param source The source file/resource URI.
     * @param destination The destination directory/resource URI.
     * @param overwrite Whether to overwrite existing files.
     * @param sourceCredentialsId Optional credentials ID for source authentication.
     * @param progressCallback Callback for transfer progress.
     * @return true if successful, false otherwise.
     */
    suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean

    /**
     * Executes the move operation.
     * Default implementation delegates to copy() followed by delete() if successful.
     * Implementations can override this for optimized moves (e.g., server-side rename).
     */
    suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        // Default naive implementation: Copy + Delete
        val copyResult = copy(source, destination, overwrite, progressCallback)
        if (copyResult is FileOperationResult.Success) {
            // If copy succeeded, delete source.
            // Note: This requires the Strategy to be able to delete the source, 
            // which implies it knows how to access the source protocol.
            // If the strategy is unidirectional (e.g. LocalToSmb), it might not know how to delete Local.
            // Therefore, default implementation here is risky if strategy doesn't have full access.
            // BETTER APPROACH: Return a specific result indicating "Fallback to generic Copy+Delete"
            // or force implementation.
            // For now, let's leave this abstract or return a "NotImplemented" result to let Handler handle it?
            // Actually, cleanest is to force implementation or return null/fallback.
            // Let's make it abstract to force explicit consideration.
            throw UnsupportedOperationException("Move not implemented by default")
        }
        return copyResult
    }
    
    /**
     * Executes the move operation with optional credentials.
     * Default implementation throws UnsupportedOperationException.
     */
    suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        throw UnsupportedOperationException("Move with credentials not implemented")
    }
    
    /**
     * Checks if this strategy supports the given source and destination schemes.
     */
    fun supports(sourceScheme: String?, destScheme: String?): Boolean
}
