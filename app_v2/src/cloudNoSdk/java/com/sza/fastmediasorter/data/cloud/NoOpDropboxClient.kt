package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inert [DropboxClient] for flavors built without the Dropbox SDK (strategic S0403).
 *
 * Mounted via the `cloudNoSdk` source set. Nothing here is reachable in normal use: foss declares
 * `SUPPORT_CLOUD=false`, so every Dropbox entry point is hidden rather than disabled. It still
 * answers honestly instead of throwing - an unreachable path that crashes is a latent dead entry
 * point, and Rule 19 forbids shipping `TODO()` / `NotImplementedError` for exactly that reason.
 */
@Singleton
class NoOpDropboxClient @Inject constructor() : DropboxClient {

    override val provider = CloudProvider.DROPBOX

    override suspend fun tryRestoreForAccount(email: String): Boolean = false

    override suspend fun tryRestoreFromStorage(): Boolean = false

    override fun startPkceAuthentication(activity: Activity, appKey: String) = Unit

    override suspend fun finishAuthentication(): AuthResult = AuthResult.Error(UNAVAILABLE)

    override suspend fun getAccountEmail(): String? = null

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
        const val UNAVAILABLE = "Dropbox is not available in this build"
    }
}
