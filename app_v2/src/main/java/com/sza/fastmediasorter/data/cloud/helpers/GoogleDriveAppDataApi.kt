package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.AppDataToken
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveAuthCoordinator
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClientUtils
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: the Drive REST verbs the app's private `appDataFolder` space needs.
 *
 * `GoogleDriveRestClient` cannot serve this space: every listing it makes queries the default
 * `drive` corpus, where an appdata entry stays invisible however valid its parent id is, and the
 * browsing token it mints carries no `drive.appdata`
 * ([Drive AppData guide](https://developers.google.com/workspace/drive/api/guides/appdata)). This
 * class is that one space's transport, and the scope is minted on its own path so a session that
 * consented before S3040 keeps browsing Drive even while the transfer queue refuses.
 */
/** S4231: one appdata entry with Drives own timestamps. */
data class DriveAppDataEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val createdTimeMs: Long,
    val modifiedTimeMs: Long
)

/** S4231: a full listing and Drives clock at the time it was taken. */
data class DriveAppDataListing(val entries: List<DriveAppDataEntry>, val serverDateMs: Long)

/** S4231: bout.storageQuota; [limitBytes] null means the account has no limit. */
data class DriveStorageQuota(val limitBytes: Long?, val usageBytes: Long)

@Singleton
class GoogleDriveAppDataApi @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: GoogleDriveHttpClient,
    identityRepository: GoogleIdentityRepository,
    browserAuthManager: GoogleDriveBrowserAuthManager
) {

    private val auth = GoogleDriveAuthCoordinator(context, httpClient, identityRepository, browserAuthManager)

    /** Children of [parentFolderId] inside the appdata space. */
    suspend fun listChildren(parentFolderId: String): CloudResult<List<CloudFile>> =
        withContext(Dispatchers.IO) {
            when (val token = auth.fetchAppDataToken()) {
                is AppDataToken.Token -> requestChildren(parentFolderId, token.value)
                else -> CloudResult.Error(reauthRequiredMessage(), failureOf(token))
            }
        }

    /** One named child of [parentFolderId], or `Success(null)` when the space holds no such entry. */
    suspend fun findEntry(name: String, parentFolderId: String): CloudResult<CloudFile?> =
        when (val children = listChildren(parentFolderId)) {
            is CloudResult.Error -> children
            is CloudResult.Success -> CloudResult.Success(children.data.firstOrNull { it.name == name })
        }

    /** Find or create the appdata folder [folderName] under [parentFolderId]. */
    suspend fun ensureFolder(
        folderName: String,
        parentFolderId: String = APP_DATA_FOLDER_ALIAS
    ): CloudResult<CloudFile> =
        when (val existing = findEntry(folderName, parentFolderId)) {
            is CloudResult.Error -> existing
            is CloudResult.Success ->
                existing.data
                    ?.let { CloudResult.Success(it) }
                    ?: createFolder(folderName, parentFolderId)
        }

    /**
     * Write [content] as [fileName] under [parentFolderId].
     *
     * A same-named entry is deleted only after the new one landed, so a failed upload leaves the
     * packet a receiver may already be reading intact.
     */
    suspend fun uploadFile(
        fileName: String,
        parentFolderId: String,
        mimeType: String,
        content: ByteArray
    ): CloudResult<CloudFile> = withContext(Dispatchers.IO) {
        when (val token = auth.fetchAppDataToken()) {
            is AppDataToken.Token -> uploadContent(fileName, parentFolderId, mimeType, content, token.value)
            else -> CloudResult.Error(reauthRequiredMessage(), failureOf(token))
        }
    }

    /** Read one appdata file whole; payloads here are packets, not streamed media. */
    suspend fun downloadFile(fileId: String): CloudResult<ByteArray> = downloadFile(fileId, maxBytes = -1L)

    /**
     * Read one appdata file, bounded by [maxBytes] when non-negative.
     * When the content exceeds [maxBytes], the stream is closed immediately on the first excess byte
     * and a [CloudResult.Error] is returned before whole-body allocation.
     */
    suspend fun downloadFile(fileId: String, maxBytes: Long): CloudResult<ByteArray> = withContext(Dispatchers.IO) {
        when (val token = auth.fetchAppDataToken()) {
            is AppDataToken.Token -> readBytes(fileId, token.value, maxBytes)
            else -> CloudResult.Error(reauthRequiredMessage(), failureOf(token))
        }
    }

    /**
     * Permanently delete an appdata entry.
     *
     * Trashing is refused in this space (`notSupportedForAppDataFolderFiles`), so "accept and
     * delete" frees the user's quota at once instead of after a trash retention period.
     */
    suspend fun deleteEntry(fileId: String): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        when (val token = auth.fetchAppDataToken()) {
            is AppDataToken.Token -> deleteById(fileId, token.value)
            else -> CloudResult.Error(reauthRequiredMessage(), failureOf(token))
        }
    }

    // S4427: a missing app folder grant must stay distinguishable from a dead sign-in downstream.
    private fun failureOf(token: AppDataToken): DriveAppDataFailure =
        if (token is AppDataToken.ConsentRequired) {
            DriveAppDataFailure.ConsentRequired()
        } else {
            DriveAppDataFailure.AuthRequired()
        }

    private fun reauthRequiredMessage(): String =
        context.getString(R.string.cloud_auth_required, context.getString(R.string.google_drive))

    private suspend fun requestChildren(
        parentFolderId: String,
        token: String
    ): CloudResult<List<CloudFile>> =
        listPages(parentFolderId, token, BASIC_FIELDS).fold(
            onSuccess = { pages ->
                CloudResult.Success(pages.items.map { GoogleDriveRestClientUtils.parseItem(it, parentFolderId) })
            },
            onFailure = { error ->
                Timber.e(error, "Failed to list appdata children of $parentFolderId")
                CloudResult.Error(context.getString(R.string.cloud_list_files_failed), error)
            }
        )

    // Follows nextPageToken: a queue longer than one page must never be cut silently (S4231).
    private suspend fun listPages(parentFolderId: String, token: String, fields: String): Result<ListedPages> =
        runCatching {
            val query = URLEncoder.encode("'$parentFolderId' in parents and trashed = false", "UTF-8")
            val encodedFields = URLEncoder.encode("nextPageToken, files($fields)", "UTF-8")
            val items = mutableListOf<JSONObject>()
            var serverDateMs: Long? = null
            var pageToken: String? = null
            do {
                val tokenPart = pageToken?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
                val url = URL(
                    "$DRIVE_API_BASE/files?q=$query&spaces=$APP_DATA_FOLDER_ALIAS" +
                        "&pageSize=$PAGE_SIZE&fields=$encodedFields&orderBy=name$tokenPart"
                )
                val response = auth.makeAuthenticatedRequest(url, "GET", token, R.string.google_web_client_id)
                val page = JSONObject(successBody(response))
                serverDateMs = serverDateMs ?: response.serverDateMs
                page.optJSONArray("files")?.let { files -> (0 until files.length()).mapTo(items, files::getJSONObject) }
                pageToken = page.optString("nextPageToken").ifEmpty { null }
            } while (pageToken != null)
            ListedPages(items, serverDateMs)
        }.onFailure { it.rethrowIfCancellation() }

    private fun successBody(response: GoogleDriveHttpClient.ApiResponse): String {
        val body = response.data
        if (!response.isSuccess || body == null) {
            throw DriveAppDataFailure.classify(response.httpCode, response.errorMessage)
        }
        return body
    }

    /**
     * S4231: every child of [parentFolderId] with Drive's `createdTime`, and Drive's clock from the response
     * `Date` header - packet expiry is judged on that clock, never on this device's (contract item CR).
     */
    suspend fun listChildrenDetailed(parentFolderId: String): Result<DriveAppDataListing> = withToken { token ->
        listPages(parentFolderId, token, DETAILED_FIELDS).map { pages ->
            val serverNow = pages.serverDateMs ?: System.currentTimeMillis().also {
                Timber.w("GoogleDriveAppDataApi: no Date header on the listing, falling back to the device clock")
            }
            DriveAppDataListing(pages.items.map(::entryOf), serverNow)
        }
    }

    /** S4231: the account's storage quota (`about.storageQuota`); a null limit means unlimited. */
    suspend fun readStorageQuota(): Result<DriveStorageQuota> = withToken { token ->
        runCatching {
            val url = URL("$DRIVE_API_BASE/about?fields=storageQuota")
            val response = auth.makeAuthenticatedRequest(url, "GET", token, R.string.google_web_client_id)
            val quota = JSONObject(successBody(response)).getJSONObject("storageQuota")
            DriveStorageQuota(
                limitBytes = quota.optString("limit").toLongOrNull(),
                usageBytes = quota.optString("usage").toLongOrNull() ?: 0L
            )
        }.onFailure { it.rethrowIfCancellation() }
    }

    /**
     * S4231: write [size] bytes of [source] as [fileName] through Drive's resumable upload, holding at most
     * one chunk in memory. The resumable protocol serves the small files too, so every failure arrives
     * classified (a multipart failure loses its status).
     */
    suspend fun uploadStream(
        fileName: String,
        parentFolderId: String,
        mimeType: String,
        size: Long,
        source: InputStream,
        onProgress: ((Long) -> Unit)? = null
    ): Result<CloudFile> = withToken { token ->
        runCatching {
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("mimeType", mimeType)
                put("parents", JSONArray().put(parentFolderId))
            }
            val session = GoogleDriveResumableUploader.initiate(DRIVE_UPLOAD_BASE, token, metadata, size, mimeType)
            val json = GoogleDriveResumableUploader.upload(session, token, source, size, onProgress)
            GoogleDriveRestClientUtils.parseItem(json, parentFolderId)
        }.onFailure { it.rethrowIfCancellation() }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(error as? DriveAppDataFailure ?: DriveAppDataFailure.Network(error))
            }
        )
    }

    /** S4231: open one appdata file as a stream; the caller closes it. */
    suspend fun openDownload(fileId: String): Result<InputStream> = withToken { token ->
        when (val stream = httpClient.getFileInputStream(fileId, DRIVE_API_BASE, token, 0L, -1L)) {
            is GoogleDriveHttpClient.StreamResult.Success -> Result.success(stream.stream)
            is GoogleDriveHttpClient.StreamResult.Error ->
                Result.failure(DriveAppDataFailure.classify(stream.httpCode, null))
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> Result<T>): Result<T> =
        withContext(Dispatchers.IO) {
            when (val token = auth.fetchAppDataToken()) {
                is AppDataToken.Token -> block(token.value)
                else -> Result.failure(failureOf(token))
            }
        }

    private fun entryOf(item: JSONObject): DriveAppDataEntry = DriveAppDataEntry(
        id = item.getString("id"),
        name = item.getString("name"),
        isFolder = item.optString("mimeType") == MIME_TYPE_FOLDER,
        size = item.optString("size").toLongOrNull() ?: 0L,
        createdTimeMs = epochMsOf(item.optString("createdTime")),
        modifiedTimeMs = epochMsOf(item.optString("modifiedTime"))
    )

    private fun epochMsOf(rfc3339: String): Long =
        runCatching { Instant.parse(rfc3339).toEpochMilli() }.getOrDefault(0L)

    private data class ListedPages(val items: List<JSONObject>, val serverDateMs: Long?)

    private suspend fun createFolder(folderName: String, parentFolderId: String): CloudResult<CloudFile> {
        val token = when (val issued = auth.fetchAppDataToken()) {
            is AppDataToken.Token -> issued.value
            else -> return CloudResult.Error(reauthRequiredMessage(), failureOf(issued))
        }
        val body = JSONObject().apply {
            put("name", folderName)
            put("mimeType", MIME_TYPE_FOLDER)
            put("parents", JSONArray().put(parentFolderId))
        }.toString()
        return runCatching {
            val url = URL("$DRIVE_API_BASE/files")
            val response = auth.makeAuthenticatedRequest(url, "POST", token, R.string.google_web_client_id, body)
            if (response.isSuccess && response.data != null) {
                CloudResult.Success(GoogleDriveRestClientUtils.parseItem(JSONObject(response.data), parentFolderId))
            } else {
                CloudResult.Error(
                    context.getString(R.string.cloud_create_folder_failed),
                    DriveAppDataFailure.classify(response.httpCode, response.errorMessage)
                )
            }
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.e(error, "Failed to create appdata folder $folderName")
            CloudResult.Error(context.getString(R.string.cloud_create_folder_failed), error)
        }
    }

    private suspend fun uploadContent(
        fileName: String,
        parentFolderId: String,
        mimeType: String,
        content: ByteArray,
        token: String
    ): CloudResult<CloudFile> {
        val previous = (findEntry(fileName, parentFolderId) as? CloudResult.Success)?.data
        val target = DriveUploadTarget(
            fileName = fileName,
            mimeType = mimeType,
            parentFolderId = parentFolderId,
            fileSize = content.size.toLong()
        )
        val json = runCatching {
            GoogleDriveMultipartUploader.upload(DRIVE_UPLOAD_BASE, token, target, content.inputStream(), null)
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.e(error, "Failed to upload appdata file $fileName")
            null
        }
        return when (json) {
            null -> CloudResult.Error(context.getString(R.string.cloud_upload_failed))
            else -> replacePrevious(GoogleDriveRestClientUtils.parseItem(json, parentFolderId), previous, token)
        }
    }

    private suspend fun replacePrevious(
        uploaded: CloudFile,
        previous: CloudFile?,
        token: String
    ): CloudResult<CloudFile> {
        if (previous != null && previous.id != uploaded.id) {
            deleteById(previous.id, token)
        }
        return CloudResult.Success(uploaded)
    }

    private suspend fun readBytes(fileId: String, token: String, maxBytes: Long = -1L): CloudResult<ByteArray> =
        when (val stream = httpClient.getFileInputStream(fileId, DRIVE_API_BASE, token, 0L, -1L)) {
            is GoogleDriveHttpClient.StreamResult.Error -> CloudResult.Error(stream.message)
            is GoogleDriveHttpClient.StreamResult.Success -> runCatching {
                val data = stream.stream.use { input ->
                    if (maxBytes < 0L) {
                        input.readBytes()
                    } else {
                        readBounded(input, maxBytes)
                    }
                }
                CloudResult.Success(data)
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                Timber.e(error, "Failed to read appdata file $fileId")
                CloudResult.Error(
                    context.getString(
                        R.string.download_failed,
                        error.message ?: context.getString(R.string.error_reason_unknown)
                    ),
                    error
                )
            }
        }

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(
            DEFAULT_BUFFER_SIZE.coerceAtMost((maxBytes + 1).coerceAtLeast(MIN_BOUNDED_BUFFER_BYTES).toInt())
        )
        var totalRead = 0L
        while (true) {
            val toRead = ((maxBytes + 1) - totalRead).coerceAtMost(buffer.size.toLong()).toInt()
            if (toRead <= 0) {
                throw IOException("Appdata payload exceeds max bounded size of $maxBytes bytes")
            }
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            totalRead += read
            if (totalRead > maxBytes) {
                throw IOException("Appdata payload exceeds max bounded size of $maxBytes bytes")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private suspend fun deleteById(fileId: String, token: String): CloudResult<Boolean> =
        runCatching {
            val url = URL("$DRIVE_API_BASE/files/$fileId")
            val response = auth.makeAuthenticatedRequest(url, "DELETE", token, R.string.google_web_client_id)
            if (response.isSuccess) {
                CloudResult.Success(true)
            } else {
                CloudResult.Error(
                    context.getString(R.string.error_delete_failed),
                    DriveAppDataFailure.classify(response.httpCode, response.errorMessage)
                )
            }
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.e(error, "Failed to delete appdata entry $fileId")
            CloudResult.Error(context.getString(R.string.error_delete_failed), error)
        }

    companion object {
        /** Drive's alias for the app's private space, valid both as a parent id and as a `spaces` value. */
        const val APP_DATA_FOLDER_ALIAS = "appDataFolder"
        private const val MIN_BOUNDED_BUFFER_BYTES = 1024L
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val PAGE_SIZE = 100
        private const val BASIC_FIELDS = "id, name, mimeType, size, modifiedTime"
        private const val DETAILED_FIELDS = "id, name, mimeType, size, createdTime, modifiedTime"
    }
}
