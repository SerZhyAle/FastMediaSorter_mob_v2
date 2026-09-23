package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import android.database.SQLException
import android.net.Uri
import androidx.annotation.StringRes
import com.microsoft.identity.client.IAuthenticationResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.network.HttpTimeouts
import com.sza.fastmediasorter.core.network.applyTimeouts
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneDrive provider via Microsoft Graph REST API v1.0 (MSAL 6.0.1).
 *
 * S0403: lives in `src/cloudSdk` because it imports MSAL directly. Shared code depends on the
 * [OneDriveRestClient] contract in `src/main`, so a flavor that drops MSAL still compiles.
 */
@Singleton
class OneDriveRestClientImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pendingRevocationDao: PendingRevocationDao,
    private val networkCredentialsRepository: NetworkCredentialsRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val reachabilityGate: com.sza.fastmediasorter.core.network.NetworkReachabilityGate,
    private val lifecycleBootstrapper:
    dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>,
) : OneDriveRestClient {

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

        // Real Graph item ids contain '!' and are at least this long; a shorter string is a folder name.
        private const val GRAPH_ITEM_ID_MIN_LENGTH = 32

        // 64KB buffer for better network throughput
        private const val TRANSFER_BUFFER_BYTES = 65_536

        // Upper bounds of Graph's "small" and "medium" thumbnail sizes; anything larger asks for "large".
        private const val THUMBNAIL_SMALL_MAX_PX = 96
        private const val THUMBNAIL_MEDIUM_MAX_PX = 176

        private val HTTP_SUCCESS_CODES = HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
    }

    /** S0195: trigger network lifecycle bootstrap on first OneDrive use. */
    override suspend fun authenticate(): AuthResult {
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("Cloud-OneDrive")
        return auth.authenticate()
    }

    override fun signIn(activity: Activity, callback: (AuthResult) -> Unit) = auth.signIn(activity, callback)

    suspend fun handleAuthenticationResult(result: IAuthenticationResult?): AuthResult =
        auth.handleAuthenticationResult(result)

    private suspend fun ensureTokenFresh() = auth.ensureTokenFresh()

    override suspend fun initialize(credentialsJson: String): Boolean =
        auth.initializeFromStored(credentialsJson)

    override suspend fun testConnection(): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        recoverGraphFailure("Connection test failed", R.string.onedrive_connection_test_failed, withReason = true) {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())

            val url = URL("$GRAPH_API_BASE/me/drive")
            val response = makeAuthenticatedRequest(url, "GET", token)

            if (response.isSuccess) {
                if (auth.accountEmail == null) {
                    catchGraphFailure({ e -> Timber.w(e, "Failed to fetch user email") }) {
                        val userUrl = URL("$GRAPH_API_BASE/me")
                        val userResponse = makeAuthenticatedRequest(userUrl, "GET", token)
                        if (userResponse.isSuccess) {
                            val userJson = JSONObject(userResponse.data ?: "{}")
                            auth.accountEmail = userJson.optString("userPrincipalName")
                                ?: userJson.optString("mail")
                        }
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
        }
    }

    override fun getAccountEmail(): String? = auth.accountEmail

    /**
     * Resolve folder ID from name if necessary.
     * If the ID looks like a name (short), it ensures the folder exists and returns its ID.
     */
    private suspend fun resolveOrEnsureFolder(idOrName: String?): String? {
        if (idOrName.isNullOrEmpty()) return null
        val looksLikeName = !idOrName.contains("!") && idOrName.length < GRAPH_ITEM_ID_MIN_LENGTH
        val ensured = if (looksLikeName) ensureFolderExists(idOrName) else null
        return if (ensured is CloudResult.Success) ensured.data.id else idOrName
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
    ): CloudResult<Pair<List<CloudFile>, String?>> = withContext(Dispatchers.IO) {
        recoverGraphFailure("Failed to list files", R.string.cloud_list_files_failed) {
            ensureTokenFresh()
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
            val resolvedFolderId = resolveOrEnsureFolder(folderId)

            val endpoint = resolvedFolderId
                ?.let { "$GRAPH_API_BASE/me/drive/items/$it/children?\$expand=thumbnails" }
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
        }
    }

    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> = withContext(
        Dispatchers.IO
    ) {
        recoverGraphFailure("Failed to list folders", R.string.cloud_list_folders_failed) {
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
        }
    }

    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        recoverGraphFailure(
            "OneDrive.getFileMetadata: EXCEPTION for fileId='$fileId'",
            R.string.cloud_metadata_failed
        ) {
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
        }
    }

    override suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        recoverGraphFailure(
            "OneDrive.downloadFile: EXCEPTION for fileId='$fileId'",
            R.string.download_failed,
            withReason = true
        ) {
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
            connection.applyTimeouts(HttpTimeouts.STREAM_READ_MS)
            connection.requestMethod = "GET"

            try {
                val inputStream = BufferedInputStream(connection.inputStream)
                val totalBytes = copyWithProgress(inputStream, outputStream, size, progressCallback)
                outputStream.flush()
                Timber.i("OneDrive.downloadFile: SUCCESS - Downloaded $totalBytes bytes for '$fileName'")
                CloudResult.Success(true)
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        parentFolderId: String?,
        fileSize: Long,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        recoverGraphFailure("Failed to upload file", R.string.cloud_upload_failed) {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())

            val resolvedParentId = resolveOrEnsureFolder(parentFolderId)
            val endpoint = resolvedParentId?.let { "$GRAPH_API_BASE/me/drive/items/$it:/$fileName:/content" }
                ?: "$GRAPH_API_BASE/me/drive/root:/$fileName:/content"

            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.applyTimeouts(HttpTimeouts.uploadReadTimeoutMs(fileSize))
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", mimeType)
            connection.doOutput = true
            // S1361: see GoogleDriveMultipartUploader - a body with no declared length is buffered
            // whole in memory and drains after the response read has already started counting.
            if (fileSize > 0L) connection.setFixedLengthStreamingMode(fileSize)
            try {
                val outputStream = connection.outputStream
                copyWithProgress(inputStream, outputStream, fileSize, progressCallback)
                outputStream.flush()

                val responseCode = connection.responseCode
                if (responseCode in HTTP_SUCCESS_CODES) {
                    val responseData = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseData)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    Timber.e("Failed to upload file: HTTP $responseCode: $error")
                    CloudResult.Error(context.getString(R.string.cloud_upload_failed))
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        recoverGraphFailure("Failed to create folder", R.string.cloud_create_folder_failed) {
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
        }
    }

    /** Finds folder by exact name in parent; returns null data if not found. */
    suspend fun findFolderByName(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile?> =
        when (val r = listFolders(parentFolderId)) {
            is CloudResult.Success -> CloudResult.Success(r.data.firstOrNull { it.name == folderName })
            is CloudResult.Error -> {
                Timber.e("findFolderByName: list failed: ${r.message}")
                r
            }
        }

    /** Ensures folder exists; creates it if not found. */
    suspend fun ensureFolderExists(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile> =
        when (val found = findFolderByName(folderName, parentFolderId)) {
            is CloudResult.Success -> {
                if (found.data != null) {
                    Timber.d("ensureFolderExists: '$folderName' exists with ID: ${found.data.id}")
                    CloudResult.Success(found.data)
                } else {
                    Timber.i("ensureFolderExists: '$folderName' not found, creating...")
                    createFolder(folderName, parentFolderId).also { r ->
                        if (r is CloudResult.Success) {
                            Timber.i("ensureFolderExists: created '$folderName' ID: ${r.data.id}")
                        } else if (r is CloudResult.Error) {
                            Timber.e("ensureFolderExists: create failed: ${r.message}")
                        }
                    }
                }
            }
            is CloudResult.Error -> {
                Timber.e("ensureFolderExists: list failed: ${found.message}")
                found
            }
        }

    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        recoverGraphFailure("OneDrive.deleteFile: EXCEPTION for fileId='$fileId'", R.string.error_delete_failed) {
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
        }
    }

    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> = withContext(
        Dispatchers.IO
    ) {
        recoverGraphFailure("OneDrive.renameFile: EXCEPTION for fileId='$fileId'", R.string.rename_failed_generic) {
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
        }
    }

    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> = withContext(
        Dispatchers.IO
    ) {
        recoverGraphFailure("Failed to move file", R.string.error_move_failed) {
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
        }
    }

    override suspend fun copyFile(
        fileId: String,
        newParentId: String,
        newName: String?
    ): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        recoverGraphFailure("Failed to copy file", R.string.error_copy_failed) {
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
                CloudResult.Success(
                    CloudFile(
                        id = fileId,
                        name = newName ?: "copying...",
                        path = newParentId,
                        isFolder = false,
                        size = 0,
                        modifiedDate = System.currentTimeMillis(),
                        mimeType = null,
                        thumbnailUrl = null,
                        webViewUrl = null
                    )
                )
            } else {
                CloudResult.Error(context.getString(R.string.error_copy_failed))
            }
        }
    }

    override suspend fun fileExists(fileName: String, parentId: String): CloudResult<Boolean> = withContext(
        Dispatchers.IO
    ) {
        recoverGraphFailure("Failed to check file existence", R.string.cloud_check_failed) {
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
        }
    }

    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> = withContext(
        Dispatchers.IO
    ) {
        recoverGraphFailure("Search failed", R.string.cloud_search_failed) {
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
        }
    }

    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> = withContext(
        Dispatchers.IO
    ) {
        recoverGraphFailure("Failed to get thumbnail", R.string.cloud_thumbnail_failed) {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())

            val thumbnailSize = when {
                size <= THUMBNAIL_SMALL_MAX_PX -> "small"
                size <= THUMBNAIL_MEDIUM_MAX_PX -> "medium"
                else -> "large"
            }

            val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId/thumbnails/0/$thumbnailSize/content")
            val connection = url.openConnection() as HttpURLConnection
            connection.applyTimeouts()
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")

            try {
                val responseCode = connection.responseCode
                if (responseCode in HTTP_SUCCESS_CODES) {
                    val bytes = connection.inputStream.readBytes()
                    CloudResult.Success(bytes.inputStream())
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    Timber.e("Failed to get thumbnail: HTTP $responseCode: $error")
                    CloudResult.Error(context.getString(R.string.cloud_thumbnail_failed))
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Returns input stream for streaming; supports HTTP Range requests for seeking. */
    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> = withContext(Dispatchers.IO) {
        recoverGraphFailure("OneDrive.getFileInputStream: Exception", R.string.download_failed, withReason = true) {
            val token = auth.accessToken ?: return@withContext CloudResult.Error(oneDriveReauthRequiredMessage())
            Timber.d("OneDrive.getFileInputStream: fileId='$fileId', pos=$position, len=$length")
            val url = URL("${buildItemUrlFromReference(fileId)}/content")
            val connection = url.openConnection() as HttpURLConnection
            connection.applyTimeouts(HttpTimeouts.STREAM_READ_MS)
            connection.setRequestProperty("Authorization", "Bearer $token")
            // Range header for streaming seek support
            if (position > 0 || length != -1L) {
                val rangeHeader = if (length == -1L) "bytes=$position-" else "bytes=$position-${position + length - 1}"
                connection.setRequestProperty("Range", rangeHeader)
                Timber.d("OneDrive.getFileInputStream: Range=$rangeHeader")
            }
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                Timber.i("OneDrive.getFileInputStream: OK HTTP $responseCode")
                CloudResult.Success(connection.inputStream)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                connection.disconnect()
                Timber.e("OneDrive.getFileInputStream: FAILED HTTP $responseCode: $error")
                CloudResult.Error(oneDriveDownloadFailedMessage())
            }
        }
    }

    override suspend fun signOut(): CloudResult<Boolean> {
        // Capture token before clearing - best-effort server-side revocation queued to DB
        val tokenToRevoke = auth.captureToken()

        // Local sign-out on Main thread (delegates to coordinator)
        var signOutError: CloudResult.Error? = null
        auth.signOutLocal { e ->
            signOutError = CloudResult.Error(context.getString(R.string.cloud_sign_out_failed), e)
        }
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
                } catch (e: SQLException) {
                    Timber.w(e, "OneDriveRestClient: failed to queue token for revocation")
                }
            }
        }

        return CloudResult.Success(true)
    }

    /**
     * Transport failures and a Graph response that does not parse are the recoverable outcomes of a
     * request; any other exception is a defect and propagates.
     */
    private inline fun <T> catchGraphFailure(onFailure: (Exception) -> T, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: JSONException) {
        onFailure(e)
    }

    private inline fun <T> recoverGraphFailure(
        logMessage: String,
        @StringRes errorRes: Int,
        withReason: Boolean = false,
        block: () -> CloudResult<T>
    ): CloudResult<T> = catchGraphFailure(
        onFailure = { e ->
            Timber.e(e, logMessage)
            val message = if (withReason) {
                context.getString(errorRes, e.message ?: context.getString(R.string.error_reason_unknown))
            } else {
                context.getString(errorRes)
            }
            CloudResult.Error(message, e)
        },
        block = block
    )

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalSize: Long,
        progressCallback: ((TransferProgress) -> Unit)?
    ): Long {
        val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
        var totalBytes = 0L
        var bytesRead = input.read(buffer)
        while (bytesRead != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
            progressCallback?.invoke(TransferProgress(totalBytes, totalSize))
            bytesRead = input.read(buffer)
        }
        return totalBytes
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
