package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.HttpTimeouts
import com.sza.fastmediasorter.core.network.applyTimeouts
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationEntity
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** Google Drive REST API v3 client. Auth: Credential Manager (GMS) + AppAuth browser fallback (Quest/XR). https://developers.google.com/drive/api/v3/reference */
@Singleton
class GoogleDriveRestClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentialsManager: GoogleDriveCredentialsManager,
    private val httpClient: GoogleDriveHttpClient,
    private val pendingRevocationDao: PendingRevocationDao,
    private val networkCredentialsRepository: NetworkCredentialsRepository,
    private val reachabilityGate: com.sza.fastmediasorter.core.network.NetworkReachabilityGate,
    private val lifecycleBootstrapper: dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>,
    // S0200 Phase 04a - additive plumbing; consumed by Phase 04b token-source switchover.
    private val identityRepository: GoogleIdentityRepository,
    private val browserAuthManager: GoogleDriveBrowserAuthManager,
) : CloudStorageClient {

    override val provider = CloudProvider.GOOGLE_DRIVE

    private val auth = GoogleDriveAuthCoordinator(context, httpClient, identityRepository, browserAuthManager)

    override fun isAuthenticated(): Boolean = auth.isAuthenticated()

    fun getAccountEmail(): String? = auth.accountEmail

    fun setGmsOnlyMode(enabled: Boolean): Boolean = auth.setGmsOnlyMode(enabled)

    private fun googleDriveReauthRequiredMessage(): String =
        context.getString(R.string.cloud_auth_required, context.getString(R.string.google_drive))

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val PAGE_SIZE = 100
        // 401-retry constants used by the streaming-input path; auth coordinator owns the same
        // values for its own retry loop.
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L
    }

    /** Try to restore client from stored credentials Attempts silent sign-in first, then falls back to stored credentials */
    /** S0195: trigger network lifecycle bootstrap on first Google Drive use. */
    suspend fun tryRestoreFromStorage(): Boolean {
        if (isAuthenticated()) return true
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("Cloud-GDrive")

        // Try silent sign-in first so an active identity-domain account or a stored browser
        // credential blob can both recover without the caller having to know which backend owns it.
        val silentResult = silentSignIn()
        if (silentResult is AuthResult.Success) {
            return true
        }

        // Fallback to stored credentials
        val stored = credentialsManager.loadStoredCredentials()
        if (stored != null) {
            val result = initialize(stored)
            if (result) {
                return true
            }
        }

        Timber.w("Failed to restore Google Drive authentication")
        return false
    }

    /**
     * Try to restore the Google Drive client for a specific account (identified by email).
     * Used for multi-account support when scanning cloud resources.
     * Falls back to any stored credentials if no per-account key found.
     *
     * @param email Account email (stored as credentialsId in ResourceEntity for cloud resources)
     * @return true if client successfully restored or already active for this account
     */
    suspend fun tryRestoreForAccount(email: String): Boolean {
        // Already authenticated with the correct account
        if (isAuthenticated() && auth.accountEmail == email) return true

        // Try per-account stored credentials
        val perAccountStored = credentialsManager.loadStoredCredentials(email)
        if (perAccountStored != null) {
            if (initialize(perAccountStored)) {
                return true
            }
        }

        // Fallback: try whichever token source can restore first (identity or browser credentials).
        return tryRestoreFromStorage()
    }

    override suspend fun authenticate(): AuthResult = auth.authenticate(R.string.google_web_client_id)

    private suspend fun silentSignIn(): AuthResult = auth.silentSignIn(R.string.google_web_client_id)

    /** Fresh Drive access token sourced from the active Google Drive auth backend. */
    private suspend fun currentAccessToken(): String? = auth.fetchAccessToken()

    private suspend fun ensureTokenFresh() = auth.ensureTokenFresh(R.string.google_web_client_id)

    override suspend fun initialize(credentialsJson: String): Boolean =
        auth.initializeFromStored(credentialsJson, R.string.google_web_client_id)

    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val url = URL("$DRIVE_API_BASE/about?fields=user")
                val response = makeAuthenticatedRequest(url, "GET", token)

                if (response.isSuccess) {
                    CloudResult.Success(true)
                } else {
                    CloudResult.Error(
                        context.getString(
                            R.string.google_drive_connection_test_failed,
                            response.errorMessage ?: context.getString(R.string.error_reason_unknown)
                        )
                    )
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Connection test failed")
                CloudResult.Error(
                    context.getString(
                        R.string.google_drive_connection_test_failed,
                        e.message ?: context.getString(R.string.error_reason_unknown)
                    ),
                    e
                )
            }
        }
    }

    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                // Proactively refresh token if old
                ensureTokenFresh()

                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

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
                    CloudResult.Error(context.getString(R.string.cloud_list_files_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to list files")
                CloudResult.Error(context.getString(R.string.cloud_list_files_failed), e)
            }
        }
    }

    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

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
                    CloudResult.Error(context.getString(R.string.cloud_list_folders_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to list folders")
                CloudResult.Error(context.getString(R.string.cloud_list_folders_failed), e)
            }
        }
    }

    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                // Strip cloud:// prefix if present
                val actualFileId = if (fileId.startsWith("cloud://google_drive/")) {
                    fileId.substringAfter("cloud://google_drive/")
                } else {
                    fileId
                }

                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val fields = URLEncoder.encode("id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink, parents", "UTF-8")
                val url = URL("$DRIVE_API_BASE/files/$actualFileId?fields=$fields")
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
                    CloudResult.Error(context.getString(R.string.cloud_metadata_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to get file metadata")
                CloudResult.Error(context.getString(R.string.cloud_metadata_failed), e)
            }
        }
    }

    /** Resolve [fileName] to its Drive ID by searching [parentFolderId]. */
    private suspend fun resolveFileIdFromName(fileName: String, parentFolderId: String?): CloudResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val parentQuery = if (parentFolderId != null && parentFolderId != "root") {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }

                // Escape backslash and single quote per Drive API query language spec
                val escapedFileName = fileName.replace("\\", "\\\\").replace("'", "\\'")
                val query = "$parentQuery and name = '$escapedFileName' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id)", "UTF-8")

                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=1&fields=$fields"
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)

                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    if (files.length() > 0) {
                        val fileId = files.getJSONObject(0).getString("id")
                        CloudResult.Success(fileId)
                    } else {
                        CloudResult.Error(context.getString(R.string.error_reason_not_found))
                    }
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_search_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to resolve file ID from name: $fileName")
                CloudResult.Error(context.getString(R.string.cloud_search_failed), e)
            }
        }
    }

    /** Resolve a Drive ID from raw input which may be: actual ID; "folderId/filename"; bare filename (root); or "cloud://google_drive/..." path. */
    private suspend fun parseAndResolveFileId(fileId: String): String? {
        // Strip cloud:// prefix if present and extract parts
        val actualInput = if (fileId.startsWith("cloud://google_drive/")) {
            fileId.substringAfter("cloud://google_drive/")
        } else {
            fileId
        }

        // Check if it contains folder/filename format
        val (potentialFolderId, potentialFilename) = if (actualInput.contains('/')) {
            val parts = actualInput.split('/', limit = 2)
            parts[0] to parts.getOrNull(1)
        } else {
            null to actualInput
        }

        // If we have a folder/filename split
        if (potentialFolderId != null && potentialFilename != null) {
            // Handle empty folder ID as root
            val targetFolderId = if (potentialFolderId.isEmpty()) {
                "root"
            } else {
                resolveFolderId(potentialFolderId) ?: potentialFolderId
            }

            // Check if first part looks like a Google Drive ID
            if (potentialFolderId.isEmpty() || (targetFolderId.length > 25 && targetFolderId.matches(Regex("^[a-zA-Z0-9_-]+$")))) {
                // Try to resolve the filename in this folder
                val resolveResult = resolveFileIdFromName(potentialFilename, targetFolderId)
                return when (resolveResult) {
                    is CloudResult.Success -> resolveResult.data
                    is CloudResult.Error -> {
                        Timber.w("Could not resolve '$potentialFilename' in folder '$targetFolderId': ${resolveResult.message}")
                        null
                    }
                }
            }
        }

        // If it looks like a Google Drive ID (long alphanumeric + special chars), use it directly
        // Google Drive IDs are typically 30+ characters with alphanumeric and _ - chars
        if (actualInput.length > 25 && actualInput.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return actualInput
        }

        // Otherwise, it might be just a filename - try to resolve it in root
        val resolveResult = resolveFileIdFromName(actualInput, "root")
        return when (resolveResult) {
            is CloudResult.Success -> resolveResult.data
            is CloudResult.Error -> {
                Timber.w("Could not resolve '$actualInput' to file ID: ${resolveResult.message}")
                // Return the input as-is and let the API call fail with a proper error
                actualInput
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
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                // Resolve fileId (may be filename, folderId/filename, or actual ID)
                val actualFileId = parseAndResolveFileId(fileId)
                if (actualFileId == null) {
                    return@withContext CloudResult.Error(context.getString(R.string.error_reason_not_found))
                }

                // Get file size first
                val metadataResult = getFileMetadata(actualFileId)
                val size = if (metadataResult is CloudResult.Success) {
                    metadataResult.data.size
                } else {
                    0L
                }

                // Download file content
                val url = URL("$DRIVE_API_BASE/files/$actualFileId?alt=media")
                val connection = url.openConnection() as HttpURLConnection
                connection.applyTimeouts(HttpTimeouts.STREAM_READ_MS)
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")

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
                    CloudResult.Success(true)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to download file")
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

    // Helper to resolve folder name to ID if needed.
    private suspend fun resolveFolderId(idOrName: String?): String? {
        // Handle null or empty string as root folder (no parent)
        if (idOrName.isNullOrEmpty()) return null

        // Google Drive IDs are typically long (33+ chars). Names like "test_media" are short.
        if (idOrName.length < 25 && !idOrName.startsWith("cloud://")) {
            val result = ensureFolderExists(idOrName)
            if (result is CloudResult.Success) {
                return result.data.id
            }
        }
        return idOrName
    }

    override suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        parentFolderId: String?,
        fileSize: Long,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())
            val resolvedParentId = resolveFolderId(parentFolderId)
            val target = com.sza.fastmediasorter.data.cloud.helpers.DriveUploadTarget(
                fileName = fileName,
                mimeType = mimeType,
                parentFolderId = resolvedParentId,
                fileSize = fileSize
            )
            val json = com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveMultipartUploader.upload(
                DRIVE_UPLOAD_BASE, token, target, inputStream, progressCallback
            ) ?: return@withContext CloudResult.Error(context.getString(R.string.cloud_upload_failed))
            val parents = json.optJSONArray("parents")
            val parentId = if (parents != null && parents.length() > 0) parents.getString(0) else "root"
            CloudResult.Success(parseItem(json, parentId))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to upload file")
            CloudResult.Error(context.getString(R.string.cloud_upload_failed), e)
        }
    }

    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val resolvedParentId = resolveFolderId(parentFolderId)

                val requestBody = JSONObject().apply {
                    put("name", folderName)
                    put("mimeType", MIME_TYPE_FOLDER)
                    if (resolvedParentId != null) {
                        put("parents", JSONArray().put(resolvedParentId))
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
                    CloudResult.Error(context.getString(R.string.cloud_create_folder_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to create folder")
                CloudResult.Error(context.getString(R.string.cloud_create_folder_failed), e)
            }
        }
    }

    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                // Resolve fileId (may be filename, folderId/filename, or actual ID)
                val actualFileId = parseAndResolveFileId(fileId)
                if (actualFileId == null) {
                    return@withContext CloudResult.Error(context.getString(R.string.error_reason_not_found))
                }

                val url = URL("$DRIVE_API_BASE/files/$actualFileId")
                val response = makeAuthenticatedRequest(url, "DELETE", token)

                if (response.isSuccess) {
                    CloudResult.Success(true)
                } else {
                    CloudResult.Error(context.getString(R.string.error_delete_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to delete file")
                CloudResult.Error(context.getString(R.string.error_delete_failed), e)
            }
        }
    }

    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val requestBody = JSONObject().apply {
                    put("name", newName)
                }.toString()

                // Resolve fileId (may be filename, folderId/filename, or actual ID)
                val actualFileId = parseAndResolveFileId(fileId)
                if (actualFileId == null) {
                    return@withContext CloudResult.Error(context.getString(R.string.error_reason_not_found))
                }

                val url = URL("$DRIVE_API_BASE/files/$actualFileId")
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
                    CloudResult.Error(context.getString(R.string.rename_failed_generic))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to rename file")
                CloudResult.Error(context.getString(R.string.rename_failed_generic), e)
            }
        }
    }

    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val metadataResult = getFileMetadata(fileId)
                val metadata = when (metadataResult) {
                    is CloudResult.Success -> metadataResult.data
                    is CloudResult.Error -> return@withContext CloudResult.Error(metadataResult.message, metadataResult.cause)
                }

                // Get current parent from path (simplified)
                val currentParentId = metadata.path.substringBeforeLast("/")

                val fields = URLEncoder.encode("id, name, mimeType, size, modifiedTime, parents", "UTF-8")
                val actualFileId = parseAndResolveFileId(fileId)
                    ?: return@withContext CloudResult.Error(context.getString(R.string.error_reason_not_found))

                val urlString = buildString {
                    append("$DRIVE_API_BASE/files/$actualFileId")
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
                    CloudResult.Error(context.getString(R.string.error_move_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to move file")
                CloudResult.Error(context.getString(R.string.error_move_failed), e)
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
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val requestBody = JSONObject().apply {
                    put("parents", JSONArray().put(newParentId))
                    if (newName != null) {
                        put("name", newName)
                    }
                }.toString()

                val actualFileId = parseAndResolveFileId(fileId)
                    ?: return@withContext CloudResult.Error(context.getString(R.string.error_reason_not_found))

                val url = URL("$DRIVE_API_BASE/files/$actualFileId/copy")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)

                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    CloudResult.Success(parseItem(json, newParentId))
                } else {
                    CloudResult.Error(context.getString(R.string.error_copy_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to copy file")
                CloudResult.Error(context.getString(R.string.error_copy_failed), e)
            }
        }
    }

    override suspend fun fileExists(fileName: String, parentId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                // Escape backslash and single quote per Drive API query language spec
                val escapedFileName = fileName.replace("\\", "\\\\").replace("'", "\\'")
                val query = "name = '$escapedFileName' and '$parentId' in parents and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id)", "UTF-8")

                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=1&fields=$fields"

                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)

                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    CloudResult.Success(files.length() > 0)
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_check_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to check file existence")
                CloudResult.Error(context.getString(R.string.cloud_check_failed), e)
            }
        }
    }

    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

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
                    CloudResult.Error(context.getString(R.string.cloud_search_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Search failed")
                CloudResult.Error(context.getString(R.string.cloud_search_failed), e)
            }
        }
    }

    /**
     * Find folder by exact name match in parent folder.
     * Returns the folder's CloudFile if found, null if not found.
     *
     * @param folderName Exact name of the folder to find
     * @param parentFolderId Parent folder ID to search in (null for root)
     * @return CloudResult with CloudFile if found, null if not found
     */
    suspend fun findFolderByName(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile?> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                val parentQuery = if (parentFolderId != null && parentFolderId != "root") {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }

                val query = "$parentQuery and name = '$folderName' and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id, name, mimeType, modifiedTime, parents)", "UTF-8")

                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=1&fields=$fields"

                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)

                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    if (files.length() > 0) {
                        val folder = parseItem(files.getJSONObject(0), parentFolderId ?: "root")
                        CloudResult.Success(folder)
                    } else {
                        CloudResult.Success(null) // Not found
                    }
                } else {
                    CloudResult.Error(context.getString(R.string.cloud_search_failed))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to find folder: $folderName")
                CloudResult.Error(context.getString(R.string.cloud_search_failed), e)
            }
        }
    }

    /** Find [folderName] or create it under [parentFolderId] (null = root). Used by integration tests / automation. */
    suspend fun ensureFolderExists(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                // First, try to find existing folder
                val findResult = findFolderByName(folderName, parentFolderId)

                when (findResult) {
                    is CloudResult.Success -> {
                        if (findResult.data != null) {
                            // Folder found
                            CloudResult.Success(findResult.data)
                        } else {
                            // Folder not found, create it
                            val createResult = createFolder(folderName, parentFolderId)
                            when (createResult) {
                                is CloudResult.Success -> {
                                    createResult
                                }
                                is CloudResult.Error -> {
                                    Timber.e("ensureFolderExists: Failed to create folder: ${createResult.message}")
                                    createResult
                                }
                            }
                        }
                    }
                    is CloudResult.Error -> {
                        Timber.e("ensureFolderExists: Search failed: ${findResult.message}")
                        findResult
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "ensureFolderExists failed")
                CloudResult.Error(context.getString(R.string.cloud_create_folder_failed), e)
            }
        }
    }

    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val token = currentAccessToken() ?: return@withContext CloudResult.Error(googleDriveReauthRequiredMessage())

                // Get thumbnail link from metadata
                val metadataResult = getFileMetadata(fileId)
                if (metadataResult !is CloudResult.Success) {
                    // Surface a preview-specific message because metadata lookup is internal to thumbnail loading.
                    return@withContext CloudResult.Error(context.getString(R.string.cloud_thumbnail_failed))
                }

                val thumbnailLink = metadataResult.data.thumbnailUrl
                if (thumbnailLink.isNullOrEmpty()) {
                    // Fallback: download actual file content
                    return@withContext downloadFileAsStream(fileId, token)
                }

                // Download thumbnail
                val url = URL(thumbnailLink)
                val connection = url.openConnection() as HttpURLConnection
                connection.applyTimeouts()
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
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to get thumbnail")
                CloudResult.Error(context.getString(R.string.cloud_thumbnail_failed), e)
            }
        }
    }

    // Download file as InputStream (for thumbnails)
    private suspend fun downloadFileAsStream(fileId: String, token: String): CloudResult<InputStream> {
        return httpClient.downloadFileAsStream(fileId, DRIVE_API_BASE, token)
    }

    override suspend fun signOut(): CloudResult<Boolean> {
        // Capture token before clearing - revoke call happens off-Main
        val tokenToRevoke = auth.captureToken()

        // S0200 Phase 04b: delegate primary-account sign-out to identity domain.
        // The repository revokes its own cached state and emits Unbound to observers.
        var signOutError: CloudResult.Error? = null
        try {
            identityRepository.signOutPrimary()
            auth.clearAuth()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to sign out")
            signOutError = CloudResult.Error(context.getString(R.string.cloud_sign_out_failed), e)
        }
        if (signOutError != null) return signOutError!!

        // Server-side revocation - performed on IO after local state is cleared
        if (tokenToRevoke != null) {
            withContext(Dispatchers.IO) {
                try {
                    val url = URL("$GOOGLE_REVOKE_URL?token=$tokenToRevoke")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 200 || code == 400) {
                        Timber.i("GoogleDriveRestClient: token revoked (HTTP $code)")
                    } else {
                        Timber.w("GoogleDriveRestClient: revoke returned HTTP $code - queuing for retry")
                        pendingRevocationDao.insert(
                            PendingRevocationEntity(
                                provider = "google",
                                token = tokenToRevoke,
                                revokeUrl = GOOGLE_REVOKE_URL
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    Timber.w(e, "GoogleDriveRestClient: revoke network error - queuing for retry")
                    pendingRevocationDao.insert(
                        PendingRevocationEntity(
                            provider = "google",
                            token = tokenToRevoke,
                            revokeUrl = GOOGLE_REVOKE_URL
                        )
                    )
                }
            }
        }

        return CloudResult.Success(true)
    }

    /** Range-supported InputStream for ExoPlayer streaming. [length]=-1 reads the rest. */
    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> {
        return getFileInputStreamInternal(fileId, position, length, retryCount = 0)
    }

    // Internal implementation with retry support
    private suspend fun getFileInputStreamInternal(
        fileId: String,
        position: Long,
        length: Long,
        retryCount: Int
    ): CloudResult<InputStream> {
        // S0200 Phase 04b: token sourced via identity domain (no GoogleSignIn.getLastSignedInAccount).
        val token = currentAccessToken()
        if (token == null) {
            Timber.e("getFileInputStream: No primary account / silent refresh failed")
            return CloudResult.Error(googleDriveReauthRequiredMessage())
        }

        val result = httpClient.getFileInputStream(fileId, DRIVE_API_BASE, token, position, length)

        // Handle 401 Unauthorized - Token expired
        if (result is GoogleDriveHttpClient.StreamResult.Error && result.httpCode == 401 && retryCount < TOKEN_MAX_RETRY_ATTEMPTS) {
            Timber.w("getFileInputStream: Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent sign-in and retry...")

            // Delay before retry
            if (retryCount > 0) {
                delay(TOKEN_RETRY_DELAY_MS)
            }

            val authResult = silentSignIn()
            if (authResult is AuthResult.Success) {
                Timber.i("getFileInputStream: Silent sign-in successful. Retrying request (attempt ${retryCount + 2})...")
                return getFileInputStreamInternal(fileId, position, length, retryCount + 1)
            }

            // If not last retry, try again
            if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                delay(TOKEN_RETRY_DELAY_MS)
                return getFileInputStreamInternal(fileId, position, length, retryCount + 1)
            }

            Timber.e("getFileInputStream: All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning error.")
            return CloudResult.Error(googleDriveReauthRequiredMessage())
        }

        return when (result) {
            is GoogleDriveHttpClient.StreamResult.Success -> {
                CloudResult.Success(result.stream)
            }
            is GoogleDriveHttpClient.StreamResult.Error -> {
                Timber.e("getFileInputStream: ${result.message}")
                CloudResult.Error(result.message)
            }
        }
    }

    private suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        body: String? = null,
        retryCount: Int = 0
    ): GoogleDriveHttpClient.ApiResponse =
        auth.makeAuthenticatedRequest(url, method, token, R.string.google_web_client_id, body, retryCount)

    private fun parseItems(items: JSONArray, parentPath: String): List<CloudFile> =
        GoogleDriveRestClientUtils.parseItems(items, parentPath)

    private fun parseItem(item: JSONObject, parentPath: String): CloudFile =
        GoogleDriveRestClientUtils.parseItem(item, parentPath)
}
