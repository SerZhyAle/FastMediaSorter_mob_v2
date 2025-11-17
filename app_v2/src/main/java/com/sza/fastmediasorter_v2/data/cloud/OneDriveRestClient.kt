package com.sza.fastmediasorter_v2.data.cloud

import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OneDrive implementation of CloudStorageClient using Microsoft Graph REST API v1.0
 * 
 * REST API approach avoids Graph SDK v5 CompletableFuture/Kotlin coroutine incompatibilities
 * 
 * Authentication: MSAL 6.0.1 OAuth 2.0 flow
 * API: Direct HTTP calls to graph.microsoft.com/v1.0
 * 
 * Endpoints:
 * - /me/drive - Get drive metadata
 * - /me/drive/root/children - List root files
 * - /me/drive/items/{id}/children - List folder contents
 * - /me/drive/items/{id} - Get/update/delete item
 * - /me/drive/items/{id}/content - Download/upload file
 * - /me/drive/items/{id}/thumbnails - Get thumbnails
 * 
 * Reference: https://learn.microsoft.com/en-us/graph/api/resources/onedrive
 */
@Singleton
class OneDriveRestClient @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudStorageClient {
    
    override val provider = CloudProvider.ONEDRIVE
    
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var accessToken: String? = null
    private var accountEmail: String? = null
    
    companion object {
        private const val GRAPH_API_BASE = "https://graph.microsoft.com/v1.0"
        private const val SCOPES = "Files.ReadWrite.All offline_access"
        
        // MIME types for filtering
        private const val FOLDER_MIME_TYPE = "application/vnd.microsoft.folder"
        
        // Common image MIME types
        private val IMAGE_MIME_TYPES = setOf(
            "image/jpeg", "image/png", "image/gif", "image/webp", 
            "image/bmp", "image/heic", "image/heif"
        )
        
        // Common video MIME types
        private val VIDEO_MIME_TYPES = setOf(
            "video/mp4", "video/quicktime", "video/x-matroska", 
            "video/webm", "video/mpeg", "video/avi"
        )
        
        // Common audio MIME types
        private val AUDIO_MIME_TYPES = setOf(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg", 
            "audio/flac", "audio/aac", "audio/m4a"
        )
    }
    
    /**
     * Initialize MSAL application
     */
    private suspend fun initializeMsal(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                com.sza.fastmediasorter_v2.R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        msalApp = application
                        Timber.d("MSAL app initialized successfully")
                        continuation.resume(true)
                    }
                    
                    override fun onError(exception: MsalException) {
                        Timber.e(exception, "MSAL initialization failed")
                        continuation.resume(false)
                    }
                }
            )
        }
    }
    
    /**
     * Start OAuth 2.0 authentication flow
     * Must be called from Activity context
     */
    override suspend fun authenticate(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                // Initialize MSAL if needed
                if (msalApp == null) {
                    val initialized = initializeMsal()
                    if (!initialized) {
                        return@withContext AuthResult.Error("Failed to initialize MSAL")
                    }
                }
                
                val app = msalApp ?: return@withContext AuthResult.Error("MSAL not initialized")
                
                // Check if already signed in
                val account = app.currentAccount.currentAccount
                if (account != null) {
                    // Try silent authentication
                    val result = acquireTokenSilently(account)
                    if (result != null) {
                        accessToken = result.accessToken
                        accountEmail = result.account.username
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeAccount(result.account)
                        )
                    }
                }
                
                // Need interactive authentication
                AuthResult.Error("Interactive sign-in required. Please use AddResourceActivity to authenticate.")
            } catch (e: Exception) {
                Timber.e(e, "OneDrive authentication failed")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Acquire token silently for cached account
     */
    private suspend fun acquireTokenSilently(account: IAccount): IAuthenticationResult? {
        return suspendCancellableCoroutine { continuation ->
            val app = msalApp ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val scopes = arrayOf(SCOPES)
            app.acquireTokenSilentAsync(
                scopes,
                account.authority,
                object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        Timber.d("Silent token acquisition successful")
                        continuation.resume(authenticationResult)
                    }
                    
                    override fun onError(exception: MsalException) {
                        Timber.w(exception, "Silent token acquisition failed")
                        continuation.resume(null)
                    }
                }
            )
        }
    }
    
    /**
     * Handle interactive authentication result
     * Call from Activity after user completes OAuth flow
     */
    suspend fun handleAuthenticationResult(result: IAuthenticationResult?): AuthResult {
        return if (result != null) {
            accessToken = result.accessToken
            accountEmail = result.account.username
            AuthResult.Success(
                accountName = accountEmail ?: "Unknown",
                credentialsJson = serializeAccount(result.account)
            )
        } else {
            AuthResult.Error("Authentication failed or cancelled")
        }
    }
    
    /**
     * Serialize account info for storage
     */
    private fun serializeAccount(account: IAccount): String {
        return JSONObject().apply {
            put("username", account.username)
            put("id", account.id)
            put("authority", account.authority)
        }.toString()
    }
    
    /**
     * Deserialize account info
     */
    private fun deserializeAccount(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.getString("username")
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize account")
            ""
        }
    }
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            if (msalApp == null) {
                val initialized = initializeMsal()
                if (!initialized) return false
            }
            
            val app = msalApp ?: return false
            val account = app.currentAccount.currentAccount
            
            if (account != null) {
                val username = deserializeAccount(credentialsJson)
                if (account.username == username) {
                    // Try to get fresh token
                    val result = acquireTokenSilently(account)
                    if (result != null) {
                        accessToken = result.accessToken
                        accountEmail = result.account.username
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
            Timber.e(e, "Failed to initialize OneDrive client")
            false
        }
    }
    
    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val url = URL("$GRAPH_API_BASE/me/drive")
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
                
                val endpoint = if (folderId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$folderId/children"
                } else {
                    "$GRAPH_API_BASE/me/drive/root/children"
                }
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val cloudFiles = parseItems(items, folderId ?: "root")
                    
                    val nextLink = json.optString("@odata.nextLink", null)
                    val nextToken = nextLink?.substringAfterLast("skiptoken=")
                    
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
                
                val endpoint = if (parentFolderId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$parentFolderId/children?\$filter=folder ne null"
                } else {
                    "$GRAPH_API_BASE/me/drive/root/children?\$filter=folder ne null"
                }
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val folders = parseItems(items, parentFolderId ?: "root")
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
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
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
                
                // Get download URL
                val metadataUrl = URL("$GRAPH_API_BASE/me/drive/items/$fileId")
                val metadataResponse = makeAuthenticatedRequest(metadataUrl, "GET", token)
                
                if (!metadataResponse.isSuccess || metadataResponse.data == null) {
                    return@withContext CloudResult.Error("Failed to get download URL: ${metadataResponse.errorMessage}")
                }
                
                val json = JSONObject(metadataResponse.data)
                val downloadUrl = json.optString("@microsoft.graph.downloadUrl")
                val size = json.optLong("size", 0L)
                
                if (downloadUrl.isEmpty()) {
                    return@withContext CloudResult.Error("Download URL not available")
                }
                
                // Download file
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
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
                
                val endpoint = if (parentFolderId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$parentFolderId:/$fileName:/content"
                } else {
                    "$GRAPH_API_BASE/me/drive/root:/$fileName:/content"
                }
                
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", mimeType)
                connection.doOutput = true
                
                try {
                    val outputStream = connection.outputStream
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, 0L))
                    }
                    
                    outputStream.flush()
                    
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseData = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseData)
                        val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
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
                
                val endpoint = if (parentFolderId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$parentFolderId/children"
                } else {
                    "$GRAPH_API_BASE/me/drive/root/children"
                }
                
                val requestBody = JSONObject().apply {
                    put("name", folderName)
                    put("folder", JSONObject())
                    put("@microsoft.graph.conflictBehavior", "rename")
                }.toString()
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
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
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId")
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
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId")
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
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
                
                val requestBody = JSONObject().apply {
                    put("parentReference", JSONObject().apply {
                        put("id", newParentId)
                    })
                }.toString()
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId")
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
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
                    put("parentReference", JSONObject().apply {
                        put("id", newParentId)
                    })
                    if (newName != null) {
                        put("name", newName)
                    }
                }.toString()
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId/copy")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess) {
                    // Copy is async, returns 202 Accepted with Location header
                    // For now, return success without waiting for completion
                    CloudResult.Success(CloudFile(
                        id = fileId,
                        name = newName ?: "copying...",
                        path = newParentId,
                        isFolder = false,
                        size = 0,
                        modifiedDate = System.currentTimeMillis(),
                        mimeType = null,
                        thumbnailUrl = null,
                        webViewUrl = null
                    ))
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
                
                val url = URL("$GRAPH_API_BASE/me/drive/root/search(q='$query')")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val cloudFiles = parseItems(items, "search")
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
                
                val thumbnailSize = when {
                    size <= 96 -> "small"   // 96x96
                    size <= 176 -> "medium" // 176x176
                    size <= 800 -> "large"  // 800x800
                    else -> "large"
                }
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId/thumbnails/0/$thumbnailSize/content")
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
    
    override suspend fun signOut(): CloudResult<Boolean> {
        return withContext(Dispatchers.Main) {
            try {
                msalApp?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        Timber.d("OneDrive sign-out successful")
                    }
                    
                    override fun onError(exception: MsalException) {
                        Timber.e(exception, "Sign-out error")
                    }
                })
                
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
     * Make authenticated HTTP request to Graph API
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
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }
            
            val responseCode = connection.responseCode
            return if (responseCode in 200..299) {
                val data = connection.inputStream.bufferedReader().use { it.readText() }
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
     * Parse JSON array of DriveItem objects
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
     * Parse single DriveItem JSON to CloudFile
     */
    private fun parseItem(item: JSONObject, parentPath: String): CloudFile {
        val id = item.getString("id")
        val name = item.getString("name")
        val isFolder = item.has("folder")
        val size = item.optLong("size", 0L)
        val modifiedTime = item.optString("lastModifiedDateTime", "")
        val mimeType = item.optString("mimeType", null)
        
        // Parse ISO 8601 date to timestamp
        val modifiedDate = try {
            if (modifiedTime.isNotEmpty()) {
                // Simple ISO 8601 parsing (assumes format: 2024-11-17T12:00:00Z)
                val instant = java.time.Instant.parse(modifiedTime)
                instant.toEpochMilli()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
        
        val thumbnailUrl = item.optJSONObject("thumbnails")
            ?.optJSONArray("value")
            ?.optJSONObject(0)
            ?.optJSONObject("large")
            ?.optString("url")
        
        val webViewUrl = item.optString("webUrl", null)
        
        return CloudFile(
            id = id,
            name = name,
            path = parentPath,
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
