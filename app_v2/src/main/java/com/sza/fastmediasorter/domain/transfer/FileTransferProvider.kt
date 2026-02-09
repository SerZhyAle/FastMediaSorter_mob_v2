package com.sza.fastmediasorter.domain.transfer

import java.io.File

/**
 * Protocol-agnostic interface for file transfer operations.
 * Implementations handle specific protocols (SMB, SFTP, FTP, Cloud, Local).
 * 
 * All methods return Result<T> for type-safe error handling.
 * Progress callbacks report (bytesTransferred, totalBytes).
 */
interface FileTransferProvider {
    
    /**
     * Unique protocol identifier (e.g., "SMB", "SFTP", "FTP", "Cloud", "Local")
     */
    val protocolName: String
    
    /**
     * Download file from remote path to local file.
     * 
     * @param sourcePath Protocol-specific path (e.g., "smb://server/share/file.mp4")
     * @param destinationFile Local file to write to
     * @param onProgress Optional callback (bytesTransferred, totalBytes)
     * @return Result<Unit> Success or failure with exception
     */
    suspend fun downloadFile(
        sourcePath: String,
        destinationFile: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit>
    
    /**
     * Upload local file to remote path.
     * 
     * @param sourceFile Local file to read from
     * @param destinationPath Protocol-specific path
     * @param onProgress Optional callback (bytesTransferred, totalBytes)
     * @return Result<Unit> Success or failure with exception
     */
    suspend fun uploadFile(
        sourceFile: File,
        destinationPath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit>
    
    /**
     * Delete file at remote path.
     * 
     * @param path Protocol-specific path
     * @return Result<Unit> Success or failure with exception
     */
    suspend fun deleteFile(path: String): Result<Unit>
    
    /**
     * Rename file at remote path.
     * 
     * @param oldPath Current protocol-specific path
     * @param newPath New protocol-specific path (must be in same directory)
     * @return Result<String> New path or failure with exception
     */
    suspend fun renameFile(
        oldPath: String,
        newPath: String
    ): Result<String>
    
    /**
     * Move file to new location (can be different directory).
     * 
     * @param sourcePath Current protocol-specific path
     * @param destinationPath New protocol-specific path
     * @return Result<String> New path or failure with exception
     */
    suspend fun moveFile(
        sourcePath: String,
        destinationPath: String
    ): Result<String>
    
    /**
     * Check if file or directory exists.
     * 
     * @param path Protocol-specific path
     * @return Result<Boolean> True if exists, false if not, or failure with exception
     */
    suspend fun exists(path: String): Result<Boolean>
    
    /**
     * Get file metadata.
     * 
     * @param path Protocol-specific path
     * @return Result<FileInfo> File metadata or failure with exception
     */
    suspend fun getFileInfo(path: String): Result<FileInfo>
    
    /**
     * Create directory at remote path.
     * 
     * @param path Protocol-specific path
     * @return Result<Unit> Success or failure with exception
     */
    suspend fun createDirectory(path: String): Result<Unit>
    
    /**
     * Check if path represents a file (not directory).
     * 
     * @param path Protocol-specific path
     * @return Result<Boolean> True if file, false if directory, or failure with exception
     */
    suspend fun isFile(path: String): Result<Boolean>
}

/**
 * File metadata returned by getFileInfo().
 */
data class FileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean
)
