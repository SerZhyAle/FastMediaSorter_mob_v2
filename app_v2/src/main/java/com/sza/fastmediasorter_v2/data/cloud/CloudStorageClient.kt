package com.sza.fastmediasorter_v2.data.cloud

import com.sza.fastmediasorter_v2.domain.model.MediaFile
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Cloud storage provider types
 * Used to identify which cloud service is being used
 */
enum class CloudProvider {
    GOOGLE_DRIVE,
    ONEDRIVE,
    DROPBOX
}

/**
 * Represents a file/folder in cloud storage
 */
data class CloudFile(
    val id: String,              // Unique identifier in cloud storage
    val name: String,            // File/folder name
    val path: String,            // Virtual path (for UI display)
    val isFolder: Boolean,
    val size: Long = 0,
    val modifiedDate: Long = 0,
    val mimeType: String? = null,
    val thumbnailUrl: String? = null,
    val webViewUrl: String? = null
)

/**
 * Authentication result for cloud storage
 */
sealed class AuthResult {
    data class Success(val accountName: String, val credentialsJson: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Cancelled : AuthResult()
}

/**
 * Result wrapper for cloud operations
 */
sealed class CloudResult<out T> {
    data class Success<T>(val data: T) : CloudResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : CloudResult<Nothing>()
}

/**
 * Progress callback for upload/download operations
 */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val percentage: Int
        get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
}

/**
 * Unified interface for cloud storage providers
 * 
 * Implementations: GoogleDriveClient, OneDriveClient, DropboxClient
 * 
 * Design principles:
 * - All methods are suspend functions for coroutine support
 * - Use CloudResult wrapper for error handling
 * - Support progress callbacks for long operations
 * - Store credentials as encrypted JSON strings
 */
interface CloudStorageClient {
    
    /**
     * Get the provider type
     */
    val provider: CloudProvider
    
    /**
     * Authenticate with cloud provider
     * Shows OAuth flow and returns credentials
     * 
     * @return AuthResult with credentials JSON or error
     */
    suspend fun authenticate(): AuthResult
    
    /**
     * Initialize client with stored credentials
     * 
     * @param credentialsJson Encrypted credentials from database
     * @return true if initialization successful
     */
    suspend fun initialize(credentialsJson: String): Boolean
    
    /**
     * Test if current credentials are valid
     * 
     * @return true if authenticated and can access storage
     */
    suspend fun testConnection(): CloudResult<Boolean>
    
    /**
     * List files in a folder
     * 
     * @param folderId Folder ID (null for root folder)
     * @param pageToken Token for pagination (null for first page)
     * @return List of CloudFile items and next page token
     */
    suspend fun listFiles(
        folderId: String? = null,
        pageToken: String? = null
    ): CloudResult<Pair<List<CloudFile>, String?>>
    
    /**
     * List only folders
     * 
     * @param parentFolderId Parent folder ID (null for root folder)
     * @return List of CloudFile folders
     */
    suspend fun listFolders(parentFolderId: String? = null): CloudResult<List<CloudFile>>
    
    /**
     * Get file metadata
     * 
     * @param fileId File ID
     * @return CloudFile metadata
     */
    suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile>
    
    /**
     * Download file content
     * 
     * @param fileId File ID to download
     * @param outputStream Stream to write file content
     * @param progressCallback Optional progress callback
     * @return true if download successful
     */
    suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((TransferProgress) -> Unit)? = null
    ): CloudResult<Boolean>
    
    /**
     * Upload file to cloud storage
     * 
     * @param inputStream Stream with file content
     * @param fileName Name for uploaded file
     * @param mimeType MIME type of file
     * @param parentFolderId Parent folder ID (null for root)
     * @param progressCallback Optional progress callback
     * @return CloudFile metadata of uploaded file
     */
    suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        parentFolderId: String? = null,
        progressCallback: ((TransferProgress) -> Unit)? = null
    ): CloudResult<CloudFile>
    
    /**
     * Create folder in cloud storage
     * 
     * @param folderName Name of new folder
     * @param parentFolderId Parent folder ID (null for root)
     * @return CloudFile metadata of created folder
     */
    suspend fun createFolder(
        folderName: String,
        parentFolderId: String? = null
    ): CloudResult<CloudFile>
    
    /**
     * Delete file or folder
     * 
     * @param fileId File/folder ID to delete
     * @return true if deletion successful
     */
    suspend fun deleteFile(fileId: String): CloudResult<Boolean>
    
    /**
     * Rename file or folder
     * 
     * @param fileId File/folder ID
     * @param newName New name
     * @return CloudFile with updated metadata
     */
    suspend fun renameFile(
        fileId: String,
        newName: String
    ): CloudResult<CloudFile>
    
    /**
     * Move file or folder to different parent
     * 
     * @param fileId File/folder ID to move
     * @param newParentId New parent folder ID
     * @return CloudFile with updated metadata
     */
    suspend fun moveFile(
        fileId: String,
        newParentId: String
    ): CloudResult<CloudFile>
    
    /**
     * Copy file
     * 
     * @param fileId File ID to copy
     * @param newParentId Destination folder ID
     * @param newName Optional new name for copy
     * @return CloudFile metadata of copied file
     */
    suspend fun copyFile(
        fileId: String,
        newParentId: String,
        newName: String? = null
    ): CloudResult<CloudFile>
    
    /**
     * Search files by query
     * 
     * @param query Search query string
     * @param mimeType Optional MIME type filter
     * @return List of matching CloudFile items
     */
    suspend fun searchFiles(
        query: String,
        mimeType: String? = null
    ): CloudResult<List<CloudFile>>
    
    /**
     * Get thumbnail for file (if available)
     * 
     * @param fileId File ID
     * @param size Desired thumbnail size (width or height)
     * @return InputStream with thumbnail data
     */
    suspend fun getThumbnail(
        fileId: String,
        size: Int = 200
    ): CloudResult<InputStream>
    
    /**
     * Revoke authentication and clear credentials
     */
    suspend fun signOut(): CloudResult<Boolean>
}
