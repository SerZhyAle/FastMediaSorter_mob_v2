package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sza.fastmediasorter.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CloudStorageClient for OneDrive via Microsoft Graph REST API v1.0 (MSAL 6.0.1). */
import kotlinx.coroutines.DelicateCoroutinesApi

@Singleton
class OneDriveRestClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pendingRevocationDao: PendingRevocationDao,
    private val networkCredentialsRepository: NetworkCredentialsRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val reachabilityGate: com.sza.fastmediasorter.core.network.NetworkReachabilityGate,
    private val lifecycleBootstrapper: dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>,
) : CloudStorageClient {
    
    override val provider = CloudProvider.ONEDRIVE
    
    private val auth = OneDriveAuthCoordinator(context, networkCredentialsRepository, applicationScope)

    override fun isAuthenticated(): Boolean = auth.isAuthenticated()

    private fun oneDriveReauthRequiredMessage(): String =
        context.getString(R.string.cloud_auth_required, context.getString(R.string.onedrive))

    private fun oneDriveDownloadFailedMessage(): String =
        context.getString(R.string.download_failed, context.getString(R.string.error_reason_unknown))

    companion object {
        private const val GRAPH_API_BASE = "https://graph.microsoft.com/v1.0"
        // Best-effort token revoke endpoint; POST token=<accessToken> form-encoded
        private const val MSONLINE_REVOKE_URL =
            "https://login.microsoftonline.com/common/oauth2/v2.0/logout"
        private const val FOLDER_MIME_TYPE = "application/vnd.microsoft.folder"
    }
    
    private suspend fun initializeMsal(): Boolean = auth.initializeMsal()
    
    /** S0195: trigger network lifecycle bootstrap on first OneDrive use. */
    override suspend fun authenticate(): AuthResult {
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("Cloud-OneDrive")
        return auth.authenticate()
    }
    
    fun signIn(activity: Activity, callback: (AuthResult) -> Unit) = auth.signIn(activity, callback)

    suspend fun handleAuthenticationResult(result: IAuthenticationResult?): AuthResult =
        auth.handleAuthenticationResult(result)
    
    private suspend fun ensureTokenFresh() = auth.ensureTokenFresh()
    
    override suspend fun initialize(credentialsJson: String): Boolean =
        auth.initializeFromStored(credentialsJson)
    
    override suspend fun testConnection(): CloudResult<Boolean> = withContext(Dispatchers.IO) {
            try {
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val url = URL("$GRAPH_API_BASE/me/drive")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess) {
                    if (auth.accountEmail == null) {
                        try {
                            val userUrl = URL("$GRAPH_API_BASE/me")
                            val userResponse = makeAuthenticatedRequest(userUrl, "GET", token)
                            if (userResponse.isSuccess) {
                                val userJson = JSONObject(userResponse.data ?: "{}")
                                auth.accountEmail = userJson.optString("userPrincipalName") 
                                    ?: userJson.optString("mail")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to fetch user email")
                        }
                    }
                    CloudResult.Success(true)
                } else {
                        CloudResult.Error(
                            context.getString(
                                R.string.onedrive_connection_test_failed,
                                response.errorMessage ?: context.getString(R.string.error_reason_unknown)
                            )
                        )
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection test failed")
                    CloudResult.Error(
                        context.getString(
                            R.string.onedrive_connection_test_failed,
                            e.message ?: context.getString(R.string.error_reason_unknown)
                        ),
                        e
                    )
            }
    }

    fun getAccountEmail(): String? = auth.accountEmail
    
    /**
     * Resolve folder ID from name if necessary.
     * If the ID looks like a name (short), it ensures the folder exists and returns its ID.
     */
    private suspend fun resolveOrEnsureFolder(idOrName: String?): String? {
        if (idOrName.isNullOrEmpty()) return null
        // Heuristic: real IDs contain '!' and are ≥ 32 chars; short strings need folder resolution
        if (!idOrName.contains("!") && idOrName.length < 32) {
            val result = ensureFolderExists(idOrName)
            if (result is CloudResult.Success) return result.data.id
        }
        return idOrName
    }

    private fun normalizeCloudItemReference(fileId: String): String =
        OneDriveRestClientUtils.normalizeCloudItemReference(fileId)

    private suspend fun buildItemUrlFromReference(fileRef: String): URL {
        val actualRef = normalizeCloudItemReference(fileRef)

        return if (actualRef.contains("/")) {
            val parts = actualRef.split("/", limit = 2)
            val resolvedFolderId = resolveOrEnsureFolder(parts[0])
            val encodedFileName = Uri.encode(parts[1])

            if (resolvedFolderId.isNullOrEmpty()) {
                URL("$GRAPH_API_BASE/me/drive/root:/$encodedFileName:")
            } else {
                URL("$GRAPH_API_BASE/me/drive/items/$resolvedFolderId:/$encodedFileName:")
            }
        } else {
            URL("$GRAPH_API_BASE/me/drive/items/$actualRef")
        }
    }

    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                ensureTokenFresh()
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                val resolvedFolderId = resolveOrEnsureFolder(folderId)
                
                val endpoint = resolvedFolderId?.let { "$GRAPH_API_BASE/me/drive/items/$it/children?\$expand=thumbnails" }
                    ?: "$GRAPH_API_BASE/me/drive/root/children?\$expand=thumbnails"
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val cloudFiles = parseItems(items, folderId ?: "root")
                    
                    val nextLink: String? = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
                    val nextToken = nextLink?.substringAfterLast("skiptoken=")
                    
                    CloudResult.Success(cloudFiles to nextToken)
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_list_files_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to list files")
                CloudResult.Error(context.getString(R.string.cloud_list_files_failed), e)
            }
        }
    }
    
    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val endpoint = parentFolderId?.let { "$GRAPH_API_BASE/me/drive/items/$it/children?\$filter=folder ne null" }
                    ?: "$GRAPH_API_BASE/me/drive/root/children?\$filter=folder ne null"
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val folders = parseItems(items, parentFolderId ?: "root")
                        .filter { it.isFolder }
                    
                    CloudResult.Success(folders)
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_list_folders_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to list folders")
                CloudResult.Error(context.getString(R.string.cloud_list_folders_failed), e)
            }
    }

    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val actualFileId = if (fileId.startsWith("cloud://onedrive/")) {
                    fileId.substringAfter("cloud://onedrive/")
                } else {
                    fileId
                }
                
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                val url = URL("$GRAPH_API_BASE/me/drive/items/$actualFileId")
                
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
                    val fileName = json.optString("name", "unknown")
                    Timber.i("OneDrive.getFileMetadata: SUCCESS - fileName='$fileName', parentId='$parentId'")
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    Timber.e("OneDrive.getFileMetadata: FAILED - ${response.errorMessage}")
                    CloudResult.Error(context.getString(R.string.cloud_metadata_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.getFileMetadata: EXCEPTION for fileId='$fileId'")
                CloudResult.Error(context.getString(R.string.cloud_metadata_failed), e)
            }
    }

    override suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                val metadataUrl = buildItemUrlFromReference(fileId)
                val metadataResponse = makeAuthenticatedRequest(metadataUrl, "GET", token)
                
                if (!metadataResponse.isSuccess || metadataResponse.data == null) {
                    Timber.e("OneDrive.downloadFile: Failed to get metadata - ${metadataResponse.errorMessage}")
                    return@withContext CloudResult.Error(oneDriveDownloadFailedMessage())
                }
                
                val json = JSONObject(metadataResponse.data)
                val downloadUrl = json.optString("@microsoft.graph.downloadUrl")
                val size = json.optLong("size", 0L)
                val fileName = json.optString("name", "unknown")
                
                Timber.i("OneDrive.downloadFile: Metadata retrieved - fileName='$fileName', size=$size bytes")
                
                if (downloadUrl.isEmpty()) return@withContext CloudResult.Error(oneDriveDownloadFailedMessage())
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                try {
                    val inputStream = BufferedInputStream(connection.inputStream)
                    val buffer = ByteArray(65536) // 64KB buffer for better network throughput
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, size))
                    }
                    
                    outputStream.flush()
                    Timber.i("OneDrive.downloadFile: SUCCESS - Downloaded $totalBytes bytes for '$fileName'")
                    CloudResult.Success(true)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.downloadFile: EXCEPTION for fileId='$fileId'")
                CloudResult.Error(
                    context.getString(
                        R.string.download_failed,
                        e.message ?: context.getString(R.string.error_reason_unknown)
                    ),
                    e
                )
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
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val resolvedParentId = resolveOrEnsureFolder(parentFolderId)
                val endpoint = resolvedParentId?.let { "$GRAPH_API_BASE/me/drive/items/$it:/$fileName:/content" }
                    ?: "$GRAPH_API_BASE/me/drive/root:/$fileName:/content"
                
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", mimeType)
                connection.doOutput = true
                
                try {
                    val outputStream = connection.outputStream
                    val buffer = ByteArray(65536) // 64KB buffer for better network throughput
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
                        CloudResult.Error(context.getString(R.string.cloud_upload_failed))
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload file")
                CloudResult.Error(context.getString(R.string.cloud_upload_failed), e)
            }
        }
    }
    
    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val resolvedParentId = resolveOrEnsureFolder(parentFolderId)
                val endpoint = resolvedParentId?.let { "$GRAPH_API_BASE/me/drive/items/$it/children" }
                    ?: "$GRAPH_API_BASE/me/drive/root/children"
                
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
                    CloudResult.Error(context.getString(R.string.cloud_create_folder_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create folder")
                CloudResult.Error(context.getString(R.string.cloud_create_folder_failed), e)
            }
        }
    }
    
    /** Finds folder by exact name in parent; returns null data if not found. */
    suspend fun findFolderByName(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile?> =
        withContext(Dispatchers.IO) {
            try {
                when (val r = listFolders(parentFolderId)) {
                    is CloudResult.Success -> CloudResult.Success(r.data.firstOrNull { it.name == folderName })
                    is CloudResult.Error -> { Timber.e("findFolderByName: list failed: ${r.message}"); r }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to find folder: $folderName")
                CloudResult.Error(context.getString(R.string.cloud_search_failed), e)
            }
        }

    /** Ensures folder exists; creates it if not found. */
    suspend fun ensureFolderExists(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile> =
        withContext(Dispatchers.IO) {
            try {
                when (val found = findFolderByName(folderName, parentFolderId)) {
                    is CloudResult.Success -> {
                        if (found.data != null) {
                            Timber.d("ensureFolderExists: '$folderName' exists with ID: ${found.data.id}")
                            CloudResult.Success(found.data)
                        } else {
                            Timber.i("ensureFolderExists: '$folderName' not found, creating...")
                            createFolder(folderName, parentFolderId).also { r ->
                                if (r is CloudResult.Success) Timber.i("ensureFolderExists: created '$folderName' ID: ${r.data.id}")
                                else if (r is CloudResult.Error) Timber.e("ensureFolderExists: create failed: ${r.message}")
                            }
                        }
                    }
                    is CloudResult.Error -> {
                        Timber.e("ensureFolderExists: list failed: ${found.message}")
                        found
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ensureFolderExists failed")
                CloudResult.Error(context.getString(R.string.cloud_create_folder_failed), e)
            }
        }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
            val url = buildItemUrlFromReference(fileId)
                val response = makeAuthenticatedRequest(url, "DELETE", token)
                
                if (response.isSuccess) {
                    Timber.i("OneDrive.deleteFile: SUCCESS for fileId='$fileId'")
                    CloudResult.Success(true)
                } else {
                    Timber.e("OneDrive.deleteFile: FAILED - ${response.errorMessage}")
                    CloudResult.Error(context.getString(R.string.error_delete_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.deleteFile: EXCEPTION for fileId='$fileId'")
                CloudResult.Error(context.getString(R.string.error_delete_failed), e)
            }
    }

    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
            val requestBody = JSONObject().put("name", newName).toString()
            val url = buildItemUrlFromReference(fileId)
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
                    Timber.i("OneDrive.renameFile: SUCCESS - renamed to '$newName'")
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    Timber.e("OneDrive.renameFile: FAILED - ${response.errorMessage}")
                    CloudResult.Error(context.getString(R.string.rename_failed_generic))
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.renameFile: EXCEPTION for fileId='$fileId'")
                CloudResult.Error(context.getString(R.string.rename_failed_generic), e)
            }
    }

    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val requestBody = JSONObject()
                    .put("parentReference", JSONObject().put("id", newParentId))
                    .toString()
                
                val url = buildItemUrlFromReference(fileId)
                
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    CloudResult.Success(parseItem(json, newParentId))
                } else {
                    CloudResult.Error(context.getString(R.string.error_move_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to move file")
                CloudResult.Error(context.getString(R.string.error_move_failed), e)
            }
    }

    override suspend fun copyFile(
        fileId: String,
        newParentId: String,
        newName: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val requestBody = JSONObject()
                    .put("parentReference", JSONObject().put("id", newParentId))
                    .apply { if (newName != null) put("name", newName) }
                    .toString()
                
                val actualFileId = fileId.removePrefix("cloud://onedrive/")
                val url = URL("$GRAPH_API_BASE/me/drive/items/$actualFileId/copy")
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
                    CloudResult.Error(context.getString(R.string.error_copy_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy file")
                CloudResult.Error(context.getString(R.string.error_copy_failed), e)
            }
        }
    }
    
    override suspend fun fileExists(fileName: String, parentId: String): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())

                val endpoint = if (parentId == "root" || parentId.isEmpty()) {
                    "$GRAPH_API_BASE/me/drive/root/children"
                } else {
                    "$GRAPH_API_BASE/me/drive/items/$parentId/children"
                }
                
                val filter = "name eq '$fileName'"
                val encodedFilter = java.net.URLEncoder.encode(filter, "UTF-8")
                val url = URL("$endpoint?\$filter=$encodedFilter")
                
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    CloudResult.Success(items.length() > 0)
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_check_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check file existence")
                CloudResult.Error(context.getString(R.string.cloud_check_failed), e)
            }
    }

    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val url = URL("$GRAPH_API_BASE/me/drive/root/search(q='$query')")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val cloudFiles = parseItems(items, "search")
                    CloudResult.Success(cloudFiles)
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_search_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                CloudResult.Error(context.getString(R.string.cloud_search_failed), e)
            }
    }

    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
                
                val thumbnailSize = when {
                    size <= 96 -> "small"
                    size <= 176 -> "medium"
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
                        CloudResult.Error(context.getString(R.string.cloud_thumbnail_failed))
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get thumbnail")
                CloudResult.Error(context.getString(R.string.cloud_thumbnail_failed), e)
            }
        }
    }
    
    /** Returns input stream for streaming; supports HTTP Range requests for seeking. */
    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
            Timber.d("OneDrive.getFileInputStream: fileId='$fileId', pos=$position, len=$length")
            val url = URL("${buildItemUrlFromReference(fileId)}/content")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")
            // Range header for streaming seek support
            if (position > 0 || length != -1L) {
                val rangeHeader = if (length == -1L) "bytes=$position-" else "bytes=$position-${position + length - 1}"
                connection.setRequestProperty("Range", rangeHeader)
                Timber.d("OneDrive.getFileInputStream: Range=$rangeHeader")
            }
            val responseCode = connection.responseCode
            if (responseCode == 200 || responseCode == 206) {
                Timber.i("OneDrive.getFileInputStream: OK HTTP $responseCode")
                CloudResult.Success(connection.inputStream)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                connection.disconnect()
                Timber.e("OneDrive.getFileInputStream: FAILED HTTP $responseCode: $error")
                CloudResult.Error(oneDriveDownloadFailedMessage())
            }
        } catch (e: Exception) {
            Timber.e(e, "OneDrive.getFileInputStream: Exception")
            CloudResult.Error(
                context.getString(
                    R.string.download_failed,
                    e.message ?: context.getString(R.string.error_reason_unknown)
                ),
                e
            )
        }
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        // Capture token before clearing - best-effort server-side revocation queued to DB
        val tokenToRevoke = auth.captureToken()

        // Local sign-out on Main thread (delegates to coordinator)
        var signOutError: CloudResult.Error? = null
        auth.signOutLocal { e -> signOutError = CloudResult.Error(context.getString(R.string.cloud_sign_out_failed), e) }
        if (signOutError != null) return signOutError!!

        // Queue access token for best-effort revocation (MSAL clears refresh token locally).
        // Access tokens are short-lived (~1 h) but queuing provides an extra safety layer.
        if (tokenToRevoke != null) {
            withContext(Dispatchers.IO) {
                try {
                    pendingRevocationDao.insert(
                        PendingRevocationEntity(
                            provider = "onedrive",
                            token = tokenToRevoke,
                            revokeUrl = MSONLINE_REVOKE_URL
                        )
                    )
                    Timber.d("OneDriveRestClient: access token queued for revocation")
                } catch (e: Exception) {
                    Timber.w(e, "OneDriveRestClient: failed to queue token for revocation")
                }
            }
        }

        return CloudResult.Success(true)
    }
    
    private suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        body: String? = null,
        retryCount: Int = 0
    ): ApiResponse = auth.makeAuthenticatedRequest(url, method, token, body, retryCount)
    
    private fun parseItems(items: JSONArray, parentPath: String): List<CloudFile> =
        OneDriveRestClientUtils.parseItems(items, parentPath)

    private fun parseItem(item: JSONObject, parentPath: String): CloudFile =
        OneDriveRestClientUtils.parseItem(item, parentPath)
}

// ApiResponse moved to OneDriveRestClientUtils.ApiResponse - top-level alias so the rest of
// OneDriveRestClient keeps using the unqualified name without churn.
private typealias ApiResponse = OneDriveRestClientUtils.ApiResponse
