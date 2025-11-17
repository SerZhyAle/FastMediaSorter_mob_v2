package com.sza.fastmediasorter_v2.data.cloud

import android.content.Context
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.Metadata
import com.dropbox.core.v2.files.SearchMatchV2
import com.dropbox.core.v2.files.ThumbnailFormat
import com.dropbox.core.v2.files.ThumbnailSize
import com.dropbox.core.v2.files.WriteMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dropbox implementation of CloudStorageClient
 * 
 * Uses Dropbox API v2 with OAuth 2.0 PKCE flow
 * 
 * Authentication flow:
 * 1. User calls authenticate() -> launches Dropbox OAuth in browser/app
 * 2. After authorization, credentials are retrieved via Auth.getDbxCredential()
 * 3. Credentials stored as JSON (access token, refresh token, expiry)
 * 
 * File operations:
 * - Uses Dropbox API v2
 * - Paths use "/" prefix (e.g., "/Photos/vacation.jpg")
 * - Empty string "" represents root folder
 * - All operations run on IO dispatcher
 */
@Singleton
class DropboxClient @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudStorageClient {
    
    override val provider = CloudProvider.DROPBOX
    
    private var dbxClient: DbxClientV2? = null
    private var accountEmail: String? = null
    
    companion object {
        private const val APP_NAME = "FastMediaSorter/2.0"
        
        // Dropbox uses "" for root folder, not null
        private const val ROOT_PATH = ""
        
        // Common image extensions
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        
        // Common video extensions
        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "m4v", "3gp")
        
        // Common audio extensions
        private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma", "opus")
    }
    
    /**
     * Start Dropbox OAuth 2.0 PKCE flow
     * This launches browser/Dropbox app for user authentication
     * 
     * After user authorizes, call finishAuthentication() to complete
     */
    override suspend fun authenticate(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                // Check if already authenticated
                val credential = Auth.getDbxCredential()
                if (credential != null) {
                    // Already authenticated, initialize client
                    val result = initializeWithCredential(credential)
                    if (result) {
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeCredential(credential)
                        )
                    }
                }
                
                // Need OAuth flow - must be initiated from Activity
                // This will be handled by AddResourceActivity
                AuthResult.Error("OAuth flow required. Please use AddResourceActivity to authenticate.")
            } catch (e: Exception) {
                Timber.e(e, "Dropbox authentication failed")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Complete authentication after OAuth flow
     * Call this from Activity's onResume() after Auth.startOAuth2PKCE()
     */
    suspend fun finishAuthentication(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                val credential = Auth.getDbxCredential()
                if (credential != null) {
                    val result = initializeWithCredential(credential)
                    if (result) {
                        AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeCredential(credential)
                        )
                    } else {
                        AuthResult.Error("Failed to initialize Dropbox client")
                    }
                } else {
                    AuthResult.Cancelled
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to finish Dropbox authentication")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Initialize Dropbox client with credential
     */
    private suspend fun initializeWithCredential(credential: DbxCredential): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val config = DbxRequestConfig.newBuilder(APP_NAME).build()
                dbxClient = DbxClientV2(config, credential)
                
                // Get account info to verify connection and get email
                val account = dbxClient!!.users().currentAccount
                accountEmail = account.email
                
                Timber.d("Dropbox client initialized for account: ${account.email}")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Dropbox client")
                false
            }
        }
    }
    
    /**
     * Serialize DbxCredential to JSON for storage
     */
    private fun serializeCredential(credential: DbxCredential): String {
        return JSONObject().apply {
            put("access_token", credential.accessToken)
            put("refresh_token", credential.refreshToken)
            put("expires_at", credential.expiresAt)
            put("app_key", credential.appKey)
        }.toString()
    }
    
    /**
     * Deserialize DbxCredential from JSON
     */
    private fun deserializeCredential(json: String): DbxCredential? {
        return try {
            val obj = JSONObject(json)
            val accessToken = obj.getString("access_token")
            val refreshToken = if (obj.has("refresh_token")) obj.getString("refresh_token") else null
            val expiresAt = obj.optLong("expires_at", -1L).takeIf { it > 0 }
            val appKey = obj.getString("app_key")
            
            DbxCredential(accessToken, expiresAt, refreshToken, appKey)
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize Dropbox credential")
            null
        }
    }
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            val credential = deserializeCredential(credentialsJson) ?: return false
            initializeWithCredential(credential)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Dropbox client from stored credentials")
            false
        }
    }
    
    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Test connection by getting account info
                client.users().currentAccount
                CloudResult.Success(true)
            } catch (e: DbxException) {
                Timber.e(e, "Dropbox connection test failed")
                CloudResult.Error("Connection test failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during connection test")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val path = folderId ?: ROOT_PATH
                val result = client.files().listFolder(path)
                
                val cloudFiles = result.entries.map { metadata ->
                    metadataToCloudFile(metadata, path)
                }
                
                val nextToken = if (result.hasMore) result.cursor else null
                
                CloudResult.Success(cloudFiles to nextToken)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to list files in folder: $folderId")
                CloudResult.Error("Failed to list files: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error listing files")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val path = parentFolderId ?: ROOT_PATH
                val result = client.files().listFolder(path)
                
                val folders = result.entries
                    .filterIsInstance<FolderMetadata>()
                    .map { metadata ->
                        metadataToCloudFile(metadata, path)
                    }
                
                CloudResult.Success(folders)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to list folders in: $parentFolderId")
                CloudResult.Error("Failed to list folders: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error listing folders")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val metadata = client.files().getMetadata(fileId)
                val parentPath = fileId.substringBeforeLast('/', "")
                
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: GetMetadataErrorException) {
                Timber.e(e, "File not found: $fileId")
                CloudResult.Error("File not found: ${e.message}", e)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to get file metadata: $fileId")
                CloudResult.Error("Failed to get metadata: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting metadata")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val downloader = client.files().download(fileId)
                val metadata = downloader.result
                val totalBytes = metadata.size
                var bytesTransferred = 0L
                
                downloader.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesTransferred += bytesRead
                        
                        progressCallback?.invoke(
                            TransferProgress(bytesTransferred, totalBytes)
                        )
                    }
                }
                
                outputStream.flush()
                Timber.d("Successfully downloaded file: $fileId ($bytesTransferred bytes)")
                CloudResult.Success(true)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to download file: $fileId")
                CloudResult.Error("Download failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error downloading file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        parentFolderId: String?,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentPath = parentFolderId ?: ROOT_PATH
                val filePath = if (parentPath.isEmpty()) "/$fileName" else "$parentPath/$fileName"
                
                // Upload with overwrite mode
                val metadata = client.files().uploadBuilder(filePath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
                
                Timber.d("Successfully uploaded file: $filePath")
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to upload file: $fileName")
                CloudResult.Error("Upload failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error uploading file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentPath = parentFolderId ?: ROOT_PATH
                val folderPath = if (parentPath.isEmpty()) "/$folderName" else "$parentPath/$folderName"
                
                val metadata = client.files().createFolderV2(folderPath).metadata
                
                Timber.d("Successfully created folder: $folderPath")
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: CreateFolderErrorException) {
                Timber.e(e, "Failed to create folder: $folderName")
                CloudResult.Error("Folder creation failed: ${e.message}", e)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to create folder: $folderName")
                CloudResult.Error("Failed to create folder: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error creating folder")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                client.files().deleteV2(fileId)
                
                Timber.d("Successfully deleted: $fileId")
                CloudResult.Success(true)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to delete: $fileId")
                CloudResult.Error("Deletion failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error deleting file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentPath = fileId.substringBeforeLast('/', "")
                val newPath = if (parentPath.isEmpty()) "/$newName" else "$parentPath/$newName"
                
                val metadata = client.files().moveV2(fileId, newPath).metadata
                
                Timber.d("Successfully renamed: $fileId -> $newPath")
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to rename: $fileId")
                CloudResult.Error("Rename failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error renaming file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fileName = fileId.substringAfterLast('/')
                val newPath = if (newParentId.isEmpty()) "/$fileName" else "$newParentId/$fileName"
                
                val metadata = client.files().moveV2(fileId, newPath).metadata
                
                Timber.d("Successfully moved: $fileId -> $newPath")
                CloudResult.Success(metadataToCloudFile(metadata, newParentId))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to move: $fileId")
                CloudResult.Error("Move failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error moving file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun copyFile(
        fileId: String,
        newParentId: String,
        newName: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fileName = newName ?: fileId.substringAfterLast('/')
                val newPath = if (newParentId.isEmpty()) "/$fileName" else "$newParentId/$fileName"
                
                val metadata = client.files().copyV2(fileId, newPath).metadata
                
                Timber.d("Successfully copied: $fileId -> $newPath")
                CloudResult.Success(metadataToCloudFile(metadata, newParentId))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to copy: $fileId")
                CloudResult.Error("Copy failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error copying file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun searchFiles(
        query: String,
        mimeType: String?
    ): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val result = client.files().searchV2(query)
                
                val cloudFiles = result.matches.mapNotNull { match: SearchMatchV2 ->
                    val metadata = match.metadata.metadataValue
                    if (metadata != null) {
                        val parentPath = metadata.pathDisplay?.substringBeforeLast('/', "") ?: ""
                        metadataToCloudFile(metadata, parentPath)
                    } else null
                }
                
                CloudResult.Success(cloudFiles)
            } catch (e: DbxException) {
                Timber.e(e, "Search failed: $query")
                CloudResult.Error("Search failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during search")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val thumbnailSize = when {
                    size <= 64 -> ThumbnailSize.W64H64
                    size <= 128 -> ThumbnailSize.W128H128
                    size <= 256 -> ThumbnailSize.W256H256
                    size <= 480 -> ThumbnailSize.W480H320
                    size <= 640 -> ThumbnailSize.W640H480
                    size <= 960 -> ThumbnailSize.W960H640
                    size <= 1024 -> ThumbnailSize.W1024H768
                    else -> ThumbnailSize.W2048H1536
                }
                
                val downloader = client.files().getThumbnailBuilder(fileId)
                    .withFormat(ThumbnailFormat.JPEG)
                    .withSize(thumbnailSize)
                    .start()
                
                // Read thumbnail into byte array to return as InputStream
                val thumbnailBytes = downloader.inputStream.readBytes()
                
                CloudResult.Success(ByteArrayInputStream(thumbnailBytes))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to get thumbnail: $fileId")
                CloudResult.Error("Thumbnail failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting thumbnail")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Revoke access token if possible
                dbxClient?.auth()?.tokenRevoke()
                
                // Clear client
                dbxClient = null
                accountEmail = null
                
                Timber.d("Dropbox sign-out successful")
                CloudResult.Success(true)
            } catch (e: Exception) {
                Timber.e(e, "Error during sign-out")
                CloudResult.Error("Sign-out failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Convert Dropbox Metadata to CloudFile
     */
    private fun metadataToCloudFile(metadata: Metadata, parentPath: String): CloudFile {
        return when (metadata) {
            is FileMetadata -> {
                CloudFile(
                    id = metadata.pathDisplay ?: metadata.pathLower ?: "",
                    name = metadata.name,
                    path = parentPath,
                    isFolder = false,
                    size = metadata.size,
                    modifiedDate = metadata.serverModified?.time ?: 0L,
                    mimeType = guessMimeType(metadata.name),
                    thumbnailUrl = null, // Thumbnails fetched separately
                    webViewUrl = null
                )
            }
            is FolderMetadata -> {
                CloudFile(
                    id = metadata.pathDisplay ?: metadata.pathLower ?: "",
                    name = metadata.name,
                    path = parentPath,
                    isFolder = true,
                    size = 0,
                    modifiedDate = 0,
                    mimeType = null,
                    thumbnailUrl = null,
                    webViewUrl = null
                )
            }
            else -> {
                CloudFile(
                    id = metadata.pathDisplay ?: metadata.pathLower ?: "",
                    name = metadata.name,
                    path = parentPath,
                    isFolder = false,
                    size = 0,
                    modifiedDate = 0,
                    mimeType = null,
                    thumbnailUrl = null,
                    webViewUrl = null
                )
            }
        }
    }
    
    /**
     * Guess MIME type from file extension
     */
    private fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            extension in IMAGE_EXTENSIONS -> "image/$extension"
            extension in VIDEO_EXTENSIONS -> "video/$extension"
            extension in AUDIO_EXTENSIONS -> "audio/$extension"
            else -> null
        }
    }
}
