@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive implementation of CloudStorageClient using REST API v3
 * 
 * REST API approach avoids heavy Google Drive SDK dependencies (~10-12 MB)
 * 
 * Authentication: Google Sign-In API (play-services-auth only)
 * API: Direct HTTP calls to www.googleapis.com/drive/v3
 * 
 * Endpoints:
 * - /about - Get drive info
 * - /files - List/create/search files
 * - /files/{fileId} - Get/update/delete file
 * - /files/{fileId}?alt=media - Download file content
 * - /files/{fileId}/copy - Copy file
 * 
 * Reference: https://developers.google.com/drive/api/v3/reference
 */
@Singleton
class GoogleDriveRestClient @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudStorageClient {
    
    override val provider = CloudProvider.GOOGLE_DRIVE
    
    private var accessToken: String? = null
    private var accountEmail: String? = null
    
    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive.file"
        private const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
        
        // MIME types
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        
        // Common image MIME types
        private val IMAGE_MIME_TYPES = setOf(
            "image/jpeg", "image/png", "image/gif", "image/webp", 
            "image/bmp", "image/heic", "image/heif"
        )
        
        // Common video MIME types
        private val VIDEO_MIME_TYPES = setOf(
            "video/mp4", "video/mpeg", "video/quicktime", "video/webm", 
            "video/x-matroska", "video/avi"
        )
        
        // Common audio MIME types
        private val AUDIO_MIME_TYPES = setOf(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg", 
            "audio/flac", "audio/aac", "audio/m4a"
        )
        
        private const val PAGE_SIZE = 100
    }
    
    /**
     * Get Google Sign-In options
     */
    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(com.sza.fastmediasorter.BuildConfig.APPLICATION_ID)
            .requestScopes(Scope(SCOPE_DRIVE))
            .requestScopes(Scope(SCOPE_DRIVE_READONLY))
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
     * Start Google Sign-In authentication flow
     * Must be called from Activity context
     */
    override suspend fun authenticate(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                
                if (account != null && hasRequiredPermissions(account)) {
                    // Get OAuth token
                    val token = getAccessToken(account)
                    if (token != null) {
                        accessToken = token
                        accountEmail = account.email
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeAccount(account)
                        )
                    }
                }
                
                // Need interactive authentication
                AuthResult.Error("Interactive sign-in required. Please use AddResourceActivity to authenticate.")
            } catch (e: Exception) {
                Timber.e(e, "Google Drive authentication failed")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Handle sign-in result from Activity
     */
    suspend fun handleSignInResult(account: GoogleSignInAccount?): AuthResult {
        return if (account != null) {
            val token = getAccessToken(account)
            if (token != null) {
                accessToken = token
                accountEmail = account.email
                AuthResult.Success(
                    accountName = accountEmail ?: "Unknown",
                    credentialsJson = serializeAccount(account)
                )
            } else {
                AuthResult.Error("Failed to get access token")
            }
        } else {
            AuthResult.Error("Sign-in failed or cancelled")
        }
    }
    
    /**
     * Get OAuth access token from signed-in account
     */
    private suspend fun getAccessToken(account: GoogleSignInAccount): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Use ID token as bearer token
                account.idToken
            } catch (e: Exception) {
                Timber.e(e, "Failed to get access token")
                null
            }
        }
    }
    
    /**
     * Check if account has required Drive permissions
     */
    private fun hasRequiredPermissions(account: GoogleSignInAccount): Boolean {
        val grantedScopes = account.grantedScopes
        val requiredScope = Scope(SCOPE_DRIVE)
        return grantedScopes.contains(requiredScope)
    }
    
    /**
     * Serialize account info for storage
     */
    private fun serializeAccount(account: GoogleSignInAccount): String {
        return JSONObject().apply {
            put("email", account.email)
            put("id", account.id)
            put("displayName", account.displayName)
        }.toString()
    }
    
    /**
     * Deserialize account info
     */
    private fun deserializeAccount(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.getString("email")
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize account")
            ""
        }
    }
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            val account = withContext(Dispatchers.Main) {
                GoogleSignIn.getLastSignedInAccount(context)
            }
            
            if (account != null) {
                val email = deserializeAccount(credentialsJson)
                if (account.email == email) {
                    // Get fresh token
                    val token = getAccessToken(account)
                    if (token != null) {
                        accessToken = token
                        accountEmail = account.email
                        true
                    } else {
                        false
                    }
                } else {
                    Timber.w("Stored account doesn't match current account")
                    false
                }
            } else {
                Timber.w("No account signed in")
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val url = URL("$DRIVE_API_BASE/about?fields=user")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess) {
                    CloudResult.Success(true)
                } else {
                    CloudResult.Error("Connection test failed: ${response.errorMessage}")
                }
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val query = if (folderId != null) {
                    "'$folderId' in parents and trashed = false"
                } else {
                    "'root' in parents and trashed = false"
                }
                
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("nextPageToken, files(id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink)", "UTF-8")
                
                val urlString = buildString {
                    append("$DRIVE_API_BASE/files")
                    append("?q=$encodedQuery")
                    append("&pageSize=$PAGE_SIZE")
                    append("&fields=$fields")
                    append("&orderBy=folder,name")
                    if (pageToken != null) {
                        append("&pageToken=$pageToken")
                    }
                }
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    val cloudFiles = parseItems(files, folderId ?: "root")
                    
                    val nextToken: String? = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
                    
                    CloudResult.Success(cloudFiles to nextToken)
                } else {
                    CloudResult.Error("Failed to list files: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to list files")
                CloudResult.Error("Failed to list files: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentQuery = if (parentFolderId != null) {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }
                
                val query = "$parentQuery and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id, name, mimeType, modifiedTime)", "UTF-8")
                
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=$PAGE_SIZE&fields=$fields&orderBy=name"
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    val folders = parseItems(files, parentFolderId ?: "root")
                        .filter { it.isFolder }
                    
                    CloudResult.Success(folders)
                } else {
                    CloudResult.Error("Failed to list folders: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to list folders")
                CloudResult.Error("Failed to list folders: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fields = URLEncoder.encode("id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink, parents", "UTF-8")
                val url = URL("$DRIVE_API_BASE/files/$fileId?fields=$fields")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parents = json.optJSONArray("parents")
                    val parentId = if (parents != null && parents.length() > 0) {
                        parents.getString(0)
                    } else {
                        "root"
                    }
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    CloudResult.Error("Failed to get metadata: ${response.errorMessage}")
                }
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get file size first
                val metadataResult = getFileMetadata(fileId)
                val size = if (metadataResult is CloudResult.Success) {
                    metadataResult.data.size
                } else {
                    0L
                }
                
                // Download file content
                val url = URL("$DRIVE_API_BASE/files/$fileId?alt=media")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                try {
                    val inputStream = BufferedInputStream(connection.inputStream)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, size))
                    }
                    
                    outputStream.flush()
                    CloudResult.Success(true)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download file")
                CloudResult.Error("Download failed: ${e.message}", e)
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Create metadata
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    if (parentFolderId != null) {
                        put("parents", JSONArray().put(parentFolderId))
                    }
                }
                
                // Multipart upload
                val boundary = "----FastMediaSorterBoundary${System.currentTimeMillis()}"
                val url = URL("$DRIVE_UPLOAD_BASE/files?uploadType=multipart")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                connection.doOutput = true
                
                try {
                    val outputStream = connection.outputStream
                    
                    // Write metadata part
                    outputStream.write("--$boundary\r\n".toByteArray())
                    outputStream.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                    outputStream.write(metadata.toString().toByteArray())
                    outputStream.write("\r\n".toByteArray())
                    
                    // Write file content part
                    outputStream.write("--$boundary\r\n".toByteArray())
                    outputStream.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                    
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, 0L))
                    }
                    
                    outputStream.write("\r\n--$boundary--\r\n".toByteArray())
                    outputStream.flush()
                    
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseData = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseData)
                        val parents = json.optJSONArray("parents")
                        val parentId = if (parents != null && parents.length() > 0) {
                            parents.getString(0)
                        } else {
                            "root"
                        }
                        CloudResult.Success(parseItem(json, parentId))
                    } else {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        CloudResult.Error("Upload failed: $error")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload file")
                CloudResult.Error("Upload failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("name", folderName)
                    put("mimeType", MIME_TYPE_FOLDER)
                    if (parentFolderId != null) {
                        put("parents", JSONArray().put(parentFolderId))
                    }
                }.toString()
                
                val url = URL(DRIVE_API_BASE + "/files")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parents = json.optJSONArray("parents")
                    val parentId = if (parents != null && parents.length() > 0) {
                        parents.getString(0)
                    } else {
                        "root"
                    }
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    CloudResult.Error("Failed to create folder: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create folder")
                CloudResult.Error("Failed to create folder: ${e.message}", e)
            }
        }
    }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val url = URL("$DRIVE_API_BASE/files/$fileId")
                val response = makeAuthenticatedRequest(url, "DELETE", token)
                
                if (response.isSuccess) {
                    CloudResult.Success(true)
                } else {
                    CloudResult.Error("Failed to delete: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete file")
                CloudResult.Error("Deletion failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("name", newName)
                }.toString()
                
                val url = URL("$DRIVE_API_BASE/files/$fileId")
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parents = json.optJSONArray("parents")
                    val parentId = if (parents != null && parents.length() > 0) {
                        parents.getString(0)
                    } else {
                        "root"
                    }
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    CloudResult.Error("Failed to rename: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename file")
                CloudResult.Error("Rename failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get current parents
                val metadataResult = getFileMetadata(fileId)
                if (metadataResult !is CloudResult.Success) {
                    return@withContext CloudResult.Error("Failed to get file metadata")
                }
                
                // Get current parent from path (simplified)
                val currentParentId = metadataResult.data.path.substringBeforeLast("/")
                
                val fields = URLEncoder.encode("id, name, mimeType, size, modifiedTime, parents", "UTF-8")
                val urlString = buildString {
                    append("$DRIVE_API_BASE/files/$fileId")
                    append("?addParents=$newParentId")
                    if (currentParentId.isNotEmpty() && currentParentId != "root") {
                        append("&removeParents=$currentParentId")
                    }
                    append("&fields=$fields")
                }
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "PATCH", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    CloudResult.Success(parseItem(json, newParentId))
                } else {
                    CloudResult.Error("Failed to move: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to move file")
                CloudResult.Error("Move failed: ${e.message}", e)
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("parents", JSONArray().put(newParentId))
                    if (newName != null) {
                        put("name", newName)
                    }
                }.toString()
                
                val url = URL("$DRIVE_API_BASE/files/$fileId/copy")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    CloudResult.Success(parseItem(json, newParentId))
                } else {
                    CloudResult.Error("Failed to copy: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy file")
                CloudResult.Error("Copy failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val searchQuery = buildString {
                    append("name contains '$query' and trashed = false")
                    if (mimeType != null) {
                        append(" and mimeType = '$mimeType'")
                    }
                }
                
                val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
                val fields = URLEncoder.encode("files(id, name, mimeType, size, modifiedTime, thumbnailLink, parents)", "UTF-8")
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=$PAGE_SIZE&fields=$fields"
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    val cloudFiles = parseItems(files, "search")
                    CloudResult.Success(cloudFiles)
                } else {
                    CloudResult.Error("Search failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                CloudResult.Error("Search failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get thumbnail link from metadata
                val metadataResult = getFileMetadata(fileId)
                if (metadataResult !is CloudResult.Success) {
                    return@withContext CloudResult.Error("Failed to get metadata")
                }
                
                val thumbnailLink = metadataResult.data.thumbnailUrl
                if (thumbnailLink.isNullOrEmpty()) {
                    // Fallback: download actual file content
                    return@withContext downloadFileAsStream(fileId, token)
                }
                
                // Download thumbnail
                val url = URL(thumbnailLink)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                try {
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val bytes = connection.inputStream.readBytes()
                        CloudResult.Success(bytes.inputStream())
                    } else {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        CloudResult.Error("Thumbnail failed: $error")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get thumbnail")
                CloudResult.Error("Thumbnail failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Download file as InputStream (for thumbnails)
     */
    private fun downloadFileAsStream(fileId: String, token: String): CloudResult<InputStream> {
        return try {
            val url = URL("$DRIVE_API_BASE/files/$fileId?alt=media")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val bytes = connection.inputStream.readBytes()
                connection.disconnect()
                CloudResult.Success(bytes.inputStream())
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                connection.disconnect()
                CloudResult.Error("Download failed: $error")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download file as stream")
            CloudResult.Error("Download failed: ${e.message}", e)
        }
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        return withContext(Dispatchers.Main) {
            try {
                val signInClient = GoogleSignIn.getClient(context, getSignInOptions())
                signInClient.signOut()
                
                accessToken = null
                accountEmail = null
                CloudResult.Success(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sign out")
                CloudResult.Error("Sign-out failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Make authenticated HTTP request to Drive API
     */
    private fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        body: String? = null
    ): ApiResponse {
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }
            
            val responseCode = connection.responseCode
            return if (responseCode in 200..299) {
                val data = if (method == "DELETE") {
                    "{}" // DELETE returns empty response
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                ApiResponse(isSuccess = true, data = data, errorMessage = null)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                ApiResponse(isSuccess = false, data = null, errorMessage = error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Request failed: $method $url")
            return ApiResponse(isSuccess = false, data = null, errorMessage = e.message)
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Parse JSON array of File objects
     */
    private fun parseItems(items: JSONArray, parentPath: String): List<CloudFile> {
        val cloudFiles = mutableListOf<CloudFile>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            cloudFiles.add(parseItem(item, parentPath))
        }
        return cloudFiles
    }
    
    /**
     * Parse single File JSON to CloudFile
     */
    private fun parseItem(item: JSONObject, parentPath: String): CloudFile {
        val id = item.getString("id")
        val name = item.getString("name")
        val mimeType: String? = item.optString("mimeType").takeIf { it.isNotEmpty() }
        val isFolder = mimeType == MIME_TYPE_FOLDER
        val size = item.optLong("size", 0L)
        val modifiedTime = item.optString("modifiedTime", "")
        
        // Parse RFC 3339 date to timestamp
        val modifiedDate = try {
            if (modifiedTime.isNotEmpty()) {
                // Simple RFC 3339 parsing (assumes format: 2024-11-17T12:00:00.000Z)
                val instant = java.time.Instant.parse(modifiedTime)
                instant.toEpochMilli()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
        
        val thumbnailUrl: String? = item.optString("thumbnailLink").takeIf { it.isNotEmpty() }
        val webViewUrl: String? = item.optString("webViewLink").takeIf { it.isNotEmpty() }
        
        return CloudFile(
            id = id,
            name = name,
            path = "$parentPath/$name",
            isFolder = isFolder,
            size = size,
            modifiedDate = modifiedDate,
            mimeType = mimeType,
            thumbnailUrl = thumbnailUrl,
            webViewUrl = webViewUrl
        )
    }
    
    /**
     * API response wrapper
     */
    private data class ApiResponse(
        val isSuccess: Boolean,
        val data: String?,
        val errorMessage: String?
    )
}
