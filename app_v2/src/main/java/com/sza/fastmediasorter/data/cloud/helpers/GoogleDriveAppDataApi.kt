package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
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
import java.net.URL
import java.net.URLEncoder
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
            when (val token = auth.fetchAppDataAccessToken()) {
                null -> CloudResult.Error(reauthRequiredMessage())
                else -> requestChildren(parentFolderId, token)
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
        when (val token = auth.fetchAppDataAccessToken()) {
            null -> CloudResult.Error(reauthRequiredMessage())
            else -> uploadContent(fileName, parentFolderId, mimeType, content, token)
        }
    }

    /** Read one appdata file whole; payloads here are packets, not streamed media. */
    suspend fun downloadFile(fileId: String): CloudResult<ByteArray> = withContext(Dispatchers.IO) {
        when (val token = auth.fetchAppDataAccessToken()) {
            null -> CloudResult.Error(reauthRequiredMessage())
            else -> readBytes(fileId, token)
        }
    }

    /**
     * Permanently delete an appdata entry.
     *
     * Trashing is refused in this space (`notSupportedForAppDataFolderFiles`), so "accept and
     * delete" frees the user's quota at once instead of after a trash retention period.
     */
    suspend fun deleteEntry(fileId: String): CloudResult<Boolean> = withContext(Dispatchers.IO) {
        when (val token = auth.fetchAppDataAccessToken()) {
            null -> CloudResult.Error(reauthRequiredMessage())
            else -> deleteById(fileId, token)
        }
    }

    private fun reauthRequiredMessage(): String =
        context.getString(R.string.cloud_auth_required, context.getString(R.string.google_drive))

    private suspend fun requestChildren(
        parentFolderId: String,
        token: String
    ): CloudResult<List<CloudFile>> {
        val query = URLEncoder.encode("'$parentFolderId' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id, name, mimeType, size, modifiedTime)", "UTF-8")
        val url = URL(
            "$DRIVE_API_BASE/files?q=$query&spaces=$APP_DATA_FOLDER_ALIAS" +
                "&pageSize=$PAGE_SIZE&fields=$fields&orderBy=name"
        )
        return runCatching {
            val response = auth.makeAuthenticatedRequest(url, "GET", token, R.string.google_web_client_id)
            if (response.isSuccess && response.data != null) {
                val files = JSONObject(response.data).getJSONArray("files")
                CloudResult.Success(GoogleDriveRestClientUtils.parseItems(files, parentFolderId))
            } else {
                CloudResult.Error(context.getString(R.string.cloud_list_files_failed))
            }
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.e(error, "Failed to list appdata children of $parentFolderId")
            CloudResult.Error(context.getString(R.string.cloud_list_files_failed), error)
        }
    }

    private suspend fun createFolder(folderName: String, parentFolderId: String): CloudResult<CloudFile> {
        val token = auth.fetchAppDataAccessToken()
        if (token == null) return CloudResult.Error(reauthRequiredMessage())
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
                CloudResult.Error(context.getString(R.string.cloud_create_folder_failed))
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

    private suspend fun readBytes(fileId: String, token: String): CloudResult<ByteArray> =
        when (val stream = httpClient.getFileInputStream(fileId, DRIVE_API_BASE, token, 0L, -1L)) {
            is GoogleDriveHttpClient.StreamResult.Error -> CloudResult.Error(stream.message)
            is GoogleDriveHttpClient.StreamResult.Success -> runCatching {
                CloudResult.Success(stream.stream.use { it.readBytes() })
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

    private suspend fun deleteById(fileId: String, token: String): CloudResult<Boolean> =
        runCatching {
            val url = URL("$DRIVE_API_BASE/files/$fileId")
            val response = auth.makeAuthenticatedRequest(url, "DELETE", token, R.string.google_web_client_id)
            if (response.isSuccess) {
                CloudResult.Success(true)
            } else {
                CloudResult.Error(context.getString(R.string.error_delete_failed))
            }
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.e(error, "Failed to delete appdata entry $fileId")
            CloudResult.Error(context.getString(R.string.error_delete_failed), error)
        }

    companion object {
        /** Drive's alias for the app's private space, valid both as a parent id and as a `spaces` value. */
        const val APP_DATA_FOLDER_ALIAS = "appDataFolder"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val PAGE_SIZE = 100
    }
}
