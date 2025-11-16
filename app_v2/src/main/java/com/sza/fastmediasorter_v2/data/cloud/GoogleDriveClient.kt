package com.sza.fastmediasorter_v2.data.cloud

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive implementation of CloudStorageClient
 * 
 * Uses Google Sign-In and Drive API v3
 * 
 * Authentication flow:
 * 1. User calls authenticate()
 * 2. GoogleSignIn intent is launched
 * 3. User grants permissions
 * 4. Account credentials are stored as JSON
 * 
 * File operations:
 * - Uses Drive API v3
 * - Supports folders, files, thumbnails
 * - All operations run on IO dispatcher
 */
@Singleton
class GoogleDriveClient @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudStorageClient {
    
    override val provider = CloudProvider.GOOGLE_DRIVE
    
    private var driveService: Drive? = null
    private var accountName: String? = null
    
    companion object {
        private const val APP_NAME = "FastMediaSorter"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val PAGE_SIZE = 100
        
        // Common image MIME types for filtering
        private val IMAGE_MIME_TYPES = listOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp"
        )
        
        // Common video MIME types
        private val VIDEO_MIME_TYPES = listOf(
            "video/mp4",
            "video/mpeg",
            "video/quicktime",
            "video/webm",
            "video/x-matroska"
        )
        
        // Common audio MIME types
        private val AUDIO_MIME_TYPES = listOf(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/x-wav",
            "audio/ogg",
            "audio/flac"
        )
    }
    
    /**
     * Start Google Sign-In flow
     * Should be called from an Activity
     */
    override suspend fun authenticate(): AuthResult {
        return try {
            val account = withContext(Dispatchers.Main) {
                GoogleSignIn.getLastSignedInAccount(context)
            }
            
            if (account != null && hasRequiredPermissions(account)) {
                initializeWithAccount(account)
                AuthResult.Success(
                    accountName = account.email ?: "Unknown",
                    credentialsJson = account.email ?: ""
                )
            } else {
                // Need to launch sign-in intent
                // This should be handled by the calling Activity
                AuthResult.Error("Sign-in required. Please use AddResourceActivity to authenticate.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Google Drive authentication failed")
            AuthResult.Error("Authentication failed: ${e.message}")
        }
    }
    
    /**
     * Get Google Sign-In options
     */
    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
    }
    
    /**
     * Get sign-in intent for launching from Activity
     */
    fun getSignInIntent(): Intent {
        val signInOptions = getSignInOptions()
        val client = GoogleSignIn.getClient(context, signInOptions)
        return client.signInIntent
    }
    
    /**
     * Handle sign-in result from Activity
     */
    suspend fun handleSignInResult(account: GoogleSignInAccount?): AuthResult {
        return if (account != null) {
            initializeWithAccount(account)
            AuthResult.Success(
                accountName = account.email ?: "Unknown",
                credentialsJson = account.email ?: ""
            )
        } else {
            AuthResult.Error("Sign-in failed or cancelled")
        }
    }
    
    /**
     * Initialize Drive service with Google account
     */
    private fun initializeWithAccount(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account.account
        
        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
        
        accountName = account.email
        Timber.d("Google Drive service initialized for account: ${account.email}")
    }
    
    /**
     * Check if account has required Drive permissions
     */
    private fun hasRequiredPermissions(account: GoogleSignInAccount): Boolean {
        val grantedScopes = account.grantedScopes
        val requiredScope = Scope(DriveScopes.DRIVE_FILE)
        return grantedScopes.contains(requiredScope)
    }
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            val account = withContext(Dispatchers.Main) {
                GoogleSignIn.getLastSignedInAccount(context)
            }
            
            if (account != null && account.email == credentialsJson) {
                initializeWithAccount(account)
                true
            } else {
                Timber.w("Stored credentials don't match signed-in account")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Google Drive client")
            false
        }
    }
    
    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Try to get "about" info to test connection
                service.about().get()
                    .setFields("user")
                    .execute()
                
                CloudResult.Success(true)
            } catch (e: Exception) {
                Timber.e(e, "Connection test failed")
                CloudResult.Error("Connection test failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val query = if (folderId != null) {
                    "'$folderId' in parents and trashed = false"
                } else {
                    "'root' in parents and trashed = false"
                }
                
                val result = service.files().list()
                    .setQ(query)
                    .setPageSize(PAGE_SIZE)
                    .setFields("nextPageToken, files(id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink)")
                    .setPageToken(pageToken)
                    .setOrderBy("folder,name")
                    .execute()
                
                val cloudFiles = result.files.map { it.toCloudFile(folderId ?: "root") }
                CloudResult.Success(cloudFiles to result.nextPageToken)
            } catch (e: Exception) {
                Timber.e(e, "Failed to list files")
                CloudResult.Error("Failed to list files: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentQuery = if (parentFolderId != null) {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }
                
                val query = "$parentQuery and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
                
                val result = service.files().list()
                    .setQ(query)
                    .setPageSize(PAGE_SIZE)
                    .setFields("files(id, name, mimeType, modifiedTime)")
                    .setOrderBy("name")
                    .execute()
                
                val folders = result.files.map { it.toCloudFile(parentFolderId ?: "root") }
                CloudResult.Success(folders)
            } catch (e: Exception) {
                Timber.e(e, "Failed to list folders")
                CloudResult.Error("Failed to list folders: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val file = service.files().get(fileId)
                    .setFields("id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink, parents")
                    .execute()
                
                val parentPath = file.parents?.firstOrNull() ?: "root"
                CloudResult.Success(file.toCloudFile(parentPath))
            } catch (e: Exception) {
                Timber.e(e, "Failed to get file metadata")
                CloudResult.Error("Failed to get metadata: ${e.message}", e)
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
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get file size for progress tracking
                val metadata = service.files().get(fileId)
                    .setFields("size")
                    .execute()
                val totalSize = metadata.getSize() ?: 0L
                
                service.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream)
                
                progressCallback?.invoke(TransferProgress(totalSize, totalSize))
                CloudResult.Success(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download file")
                CloudResult.Error("Failed to download: ${e.message}", e)
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
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fileMetadata = File()
                fileMetadata.name = fileName
                if (parentFolderId != null) {
                    fileMetadata.parents = listOf(parentFolderId)
                }
                
                val mediaContent = com.google.api.client.http.InputStreamContent(mimeType, inputStream)
                
                val file = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, mimeType, size, modifiedTime")
                    .execute()
                
                CloudResult.Success(file.toCloudFile(parentFolderId ?: "root"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload file")
                CloudResult.Error("Failed to upload: ${e.message}", e)
            }
        }
    }
    
    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val folderMetadata = File()
                folderMetadata.name = folderName
                folderMetadata.mimeType = MIME_TYPE_FOLDER
                if (parentFolderId != null) {
                    folderMetadata.parents = listOf(parentFolderId)
                }
                
                val folder = service.files().create(folderMetadata)
                    .setFields("id, name, mimeType, modifiedTime")
                    .execute()
                
                CloudResult.Success(folder.toCloudFile(parentFolderId ?: "root"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to create folder")
                CloudResult.Error("Failed to create folder: ${e.message}", e)
            }
        }
    }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                service.files().delete(fileId).execute()
                CloudResult.Success(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete file")
                CloudResult.Error("Failed to delete: ${e.message}", e)
            }
        }
    }
    
    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val file = File()
                file.name = newName
                
                val updated = service.files().update(fileId, file)
                    .setFields("id, name, mimeType, size, modifiedTime, parents")
                    .execute()
                
                val parentPath = updated.parents?.firstOrNull() ?: "root"
                CloudResult.Success(updated.toCloudFile(parentPath))
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename file")
                CloudResult.Error("Failed to rename: ${e.message}", e)
            }
        }
    }
    
    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get current parents
                val file = service.files().get(fileId)
                    .setFields("parents")
                    .execute()
                val previousParents = file.parents?.joinToString(",") ?: ""
                
                // Move file
                val updated = service.files().update(fileId, null)
                    .setAddParents(newParentId)
                    .setRemoveParents(previousParents)
                    .setFields("id, name, mimeType, size, modifiedTime, parents")
                    .execute()
                
                CloudResult.Success(updated.toCloudFile(newParentId))
            } catch (e: Exception) {
                Timber.e(e, "Failed to move file")
                CloudResult.Error("Failed to move: ${e.message}", e)
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
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val copiedFile = File()
                copiedFile.parents = listOf(newParentId)
                if (newName != null) {
                    copiedFile.name = newName
                }
                
                val copy = service.files().copy(fileId, copiedFile)
                    .setFields("id, name, mimeType, size, modifiedTime")
                    .execute()
                
                CloudResult.Success(copy.toCloudFile(newParentId))
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy file")
                CloudResult.Error("Failed to copy: ${e.message}", e)
            }
        }
    }
    
    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val searchQuery = buildString {
                    append("name contains '$query' and trashed = false")
                    if (mimeType != null) {
                        append(" and mimeType = '$mimeType'")
                    }
                }
                
                val result = service.files().list()
                    .setQ(searchQuery)
                    .setPageSize(PAGE_SIZE)
                    .setFields("files(id, name, mimeType, size, modifiedTime, thumbnailLink, parents)")
                    .execute()
                
                val cloudFiles = result.files.map { 
                    val parentPath = it.parents?.firstOrNull() ?: "root"
                    it.toCloudFile(parentPath)
                }
                CloudResult.Success(cloudFiles)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search files")
                CloudResult.Error("Search failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val service = driveService ?: return@withContext CloudResult.Error("Not authenticated")
                
                val outputStream = ByteArrayOutputStream()
                service.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream)
                
                CloudResult.Success(outputStream.toByteArray().inputStream())
            } catch (e: Exception) {
                Timber.e(e, "Failed to get thumbnail")
                CloudResult.Error("Failed to get thumbnail: ${e.message}", e)
            }
        }
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        return try {
            withContext(Dispatchers.Main) {
                val signInClient = GoogleSignIn.getClient(context, getSignInOptions())
                signInClient.signOut()
            }
            driveService = null
            accountName = null
            CloudResult.Success(true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign out")
            CloudResult.Error("Sign out failed: ${e.message}", e)
        }
    }
    
    /**
     * Convert Drive API File to CloudFile
     */
    private fun File.toCloudFile(parentPath: String): CloudFile {
        return CloudFile(
            id = id,
            name = name,
            path = "$parentPath/$name",
            isFolder = mimeType == MIME_TYPE_FOLDER,
            size = getSize() ?: 0L,
            modifiedDate = modifiedTime?.value ?: 0L,
            mimeType = mimeType,
            thumbnailUrl = thumbnailLink,
            webViewUrl = webViewLink
        )
    }
}
