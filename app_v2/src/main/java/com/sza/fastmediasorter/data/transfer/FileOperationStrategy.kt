package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import java.io.File

/**
 * Strategy interface for protocol-specific file operations.
 * Each protocol (SMB, SFTP, FTP, Cloud) implements this interface.
 */
interface FileOperationStrategy {
    
    /**
     * Copy a file from source to destination.
     * 
     * @param source Source file path (can be protocol-specific URL or local path)
     * @param destination Destination file path (can be protocol-specific URL or local path)
     * @param overwrite Whether to overwrite existing files
     * @param progressCallback Optional progress callback for tracking transfer progress
     * @return Result containing the destination path on success, or error on failure
     */
    suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback? = null
    ): Result<String>
    
    /**
     * Move a file from source to destination.
     * This typically performs copy + delete.
     * 
     * @param source Source file path
     * @param destination Destination file path
     * @return Result indicating success or failure
     */
    suspend fun moveFile(
        source: String,
        destination: String
    ): Result<Unit>
    
    /**
     * Delete a file.
     * 
     * @param path File path to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteFile(path: String): Result<Unit>
    
    /**
     * Check if a file exists.
     * 
     * @param path File path to check
     * @return Result containing true if file exists, false otherwise
     */
    suspend fun exists(path: String): Result<Boolean>
    
    /**
     * Create a directory.
     * 
     * @param path Directory path to create
     * @return Result indicating success or failure
     */
    suspend fun createDirectory(path: String): Result<Unit>

    /**
     * Creates a new text file in [parentPath] with given [fileName] and UTF-8 encoded [content].
     * Returns the protocol-specific path of the created file.
     * Caller is responsible for collision resolution - this method overwrites existing files of
     * the same name only if the strategy explicitly states so in its impl docs (default: fail on conflict).
     *
     * @param parentPath  protocol-specific path of the containing directory
     * @param fileName    name of the file to create (no slashes)
     * @param content     initial UTF-8 content (may be empty string for blank notes)
     * @param resourceId  id of the owning resource; used by network strategies for staging registration
     * @return [Result] containing the absolute/protocol path of the created file on success
     */
    suspend fun createTextFile(
        parentPath: String,
        fileName: String,
        content: String,
        resourceId: Long
    ): Result<String>

    /**
     * Write text content to a file.
     * Useful for creating metadata files.
     * 
     * @param path File path to write to
     * @param content Text content to write
     * @return Result indicating success or failure
     */
    suspend fun writeFile(path: String, content: String): Result<Unit>
    
    /**
     * Read content from a file.
     * @param path Full path to the file
     * @return Result containing the file content as String
     */
    suspend fun readFile(path: String): Result<String>
    
    suspend fun listFiles(path: String): Result<List<String>>

    /**
     * S1325: one level of [path] with the type of each child resolved.
     *
     * The default builds the answer from [listFiles] plus one [isDirectory] call per child, which is
     * correct everywhere but costs a round trip per entry on a remote protocol. Strategies whose
     * native listing already carries the type override this and answer from that single listing.
     */
    suspend fun listEntries(path: String): Result<List<DirectoryEntry>> {
        val paths = listFiles(path).getOrElse { return Result.failure(it) }
        return Result.success(
            paths.map { childPath ->
                DirectoryEntry(
                    path = childPath,
                    name = childPath.trimEnd('/').substringAfterLast('/'),
                    isDirectory = isDirectory(childPath).getOrDefault(false),
                    size = 0L,
                )
            }
        )
    }

    /**
     * Check if this strategy supports the given path protocol.
     * 
     * @param path File path to check
     * @return true if this strategy can handle the path
     */
    fun supportsProtocol(path: String): Boolean
    
    /**
     * Get the protocol identifier (e.g., "smb", "sftp", "ftp", "cloud", "local")
     */
    fun getProtocolName(): String
    
    // ==================== Directory Operations ====================
    
    /**
     * Delete a directory and all its contents recursively.
     * 
     * @param path Directory path to delete
     * @param progressCallback Optional callback (deletedCount, totalCount, currentName)
     * @return Result indicating success (with total deleted count) or failure
     */
    suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> {
        // Default implementation: not supported
        return Result.failure(UnsupportedOperationException("deleteDirectory not implemented for ${getProtocolName()}"))
    }
    
    /**
     * Rename a directory.
     * 
     * @param oldPath Current directory path
     * @param newPath New directory path (must be in same parent directory)
     * @return Result containing new path on success, or failure
     */
    suspend fun renameDirectory(
        oldPath: String,
        newPath: String
    ): Result<String> {
        // Default implementation: not supported
        return Result.failure(UnsupportedOperationException("renameDirectory not implemented for ${getProtocolName()}"))
    }
    
    /**
     * Copy a directory and all its contents recursively.
     * 
     * @param source Source directory path
     * @param destination Destination directory path
     * @param progressCallback Optional callback (copiedCount, totalCount, currentName)
     * @return Result indicating success (with total copied count) or failure
     */
    suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> {
        // Default implementation: not supported
        return Result.failure(UnsupportedOperationException("copyDirectory not implemented for ${getProtocolName()}"))
    }
    
    /**
     * Move a directory and all its contents.
     * Typically implemented as copy + delete.
     * 
     * @param source Source directory path
     * @param destination Destination directory path
     * @param progressCallback Optional callback (movedCount, totalCount, currentName)
     * @return Result indicating success (with total moved count) or failure
     */
    suspend fun moveDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> {
        // Default implementation: copy + delete
        val copyResult = copyDirectory(source, destination, progressCallback)
        if (copyResult.isFailure) {
            return copyResult
        }
        val deleteResult = deleteDirectory(source, null)
        if (deleteResult.isFailure) {
            // Rollback: try to delete copied directory (best effort)
            deleteDirectory(destination, null)
            return Result.failure(deleteResult.exceptionOrNull() ?: Exception("Failed to delete source after copy"))
        }
        return copyResult
    }
    
    /**
     * Check if path is a directory.
     * 
     * @param path Path to check
     * @return Result containing true if directory, false if file, or failure
     */
    suspend fun isDirectory(path: String): Result<Boolean> {
        // Default implementation: not supported
        return Result.failure(UnsupportedOperationException("isDirectory not implemented for ${getProtocolName()}"))
    }
    
    /**
     * Get directory info (child count, total size).
     * 
     * @param path Directory path
     * @return Result containing DirectoryInfo or failure
     */
    suspend fun getDirectoryInfo(path: String): Result<DirectoryInfo> {
        // Default implementation: not supported
        return Result.failure(UnsupportedOperationException("getDirectoryInfo not implemented for ${getProtocolName()}"))
    }
}

/**
 * One child of a directory listing. [size] is 0 for a directory and for a strategy whose listing
 * does not report it - the tree transfer uses it for reporting only, never to decide what to copy.
 */
data class DirectoryEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

/**
 * Information about a directory.
 */
data class DirectoryInfo(
    val path: String,
    val name: String,
    val childCount: Int,
    val totalSize: Long,
    val lastModified: Long
)
