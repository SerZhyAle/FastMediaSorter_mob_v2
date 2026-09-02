package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inert [OneDriveRestClient] for flavors built without MSAL (strategic S0403).
 *
 * Mounted via the `cloudNoSdk` source set. See [NoOpDropboxClient] for why these answer rather
 * than throw.
 */
@Singleton
class NoOpOneDriveRestClient @Inject constructor() : OneDriveRestClient {

    override val provider = CloudProvider.ONEDRIVE

    override fun signIn(activity: Activity, callback: (AuthResult) -> Unit) {
        callback(AuthResult.Error(UNAVAILABLE))
    }

    override fun getAccountEmail(): String? = null

    override suspend fun authenticate(): AuthResult = AuthResult.Error(UNAVAILABLE)

    override suspend fun initialize(credentialsJson: String): Boolean = false

    override fun isAuthenticated(): Boolean = false

    override suspend fun testConnection(): CloudResult<Boolean> = failure()

    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> = failure()

    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> = failure()

    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> = failure()

    override suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<Boolean> = failure()

    override suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        parentFolderId: String?,
        fileSize: Long,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<CloudFile> = failure()

    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> = failure()

    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> = failure()

    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> = failure()

    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> = failure()

    override suspend fun copyFile(
        fileId: String,
        newParentId: String,
        newName: String?
    ): CloudResult<CloudFile> = failure()

    override suspend fun fileExists(fileName: String, parentId: String): CloudResult<Boolean> = failure()

    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> = failure()

    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> = failure()

    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> = failure()

    override suspend fun signOut(): CloudResult<Boolean> = CloudResult.Success(true)

    private fun <T> failure(): CloudResult<T> = CloudResult.Error(UNAVAILABLE)

    private companion object {
        const val UNAVAILABLE = "OneDrive is not available in this build"
    }
}
