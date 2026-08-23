package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.adaptCloudProgress
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud -> any download primitive, extracted from `CloudFileOperationHandler` (S0494): the handler was
 * already above its size cap and the share-materialization path needs this trio without the copy/move
 * machinery that surrounds it there.
 *
 * Destinations may be local, smb://, sftp:// or ftp://; cloud->cloud belongs to `CloudToCloudTransferHelper`.
 */
@Singleton
class CloudDownloadUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val cloudPathParser: CloudPathParser,
    private val networkCredentialsResolver: NetworkCredentialsResolver,
    private val cloudAuthHelper: CloudAuthenticationHelper
) {

    private val pathUtils = CloudFileOperationPathUtils(cloudPathParser)

    private fun getResourceType(path: String): ResourceType = pathUtils.getResourceType(path)

    private fun normalizeNetworkPath(path: String): String = pathUtils.normalizeNetworkPath(path)

    private fun extractSftpRemotePath(
        path: String,
        credentials: NetworkCredentialsResolver.NetworkCredentials
    ): String = pathUtils.extractSftpRemotePath(path, credentials)

    private fun extractFtpRemotePath(
        path: String,
        credentials: NetworkCredentialsResolver.NetworkCredentials
    ): String = pathUtils.extractFtpRemotePath(path, credentials)

    /**
     * S0266: single public entry point. Callers that hide the operation from the universal progress
     * dialog pass no callback.
     */
    suspend fun downloadToPublic(
        cloudPath: String,
        destPath: String,
        fileName: String,
        progressCallback: ByteProgressCallback? = null
    ): Boolean = downloadFromCloudTo(
        cloudPath = cloudPath,
        destPath = destPath,
        fileName = fileName,
        progressCallback = progressCallback,
    )

    /** Universal cloud->any download. [destPath] may be local, smb://, sftp://, or ftp://. */
    private suspend fun downloadFromCloudTo(
        cloudPath: String,
        destPath: String,
        fileName: String,
        progressCallback: ByteProgressCallback? = null
    ): Boolean {
        val normalizedDestPath = normalizeNetworkPath(destPath)
        Timber.d("downloadFromCloudTo: $cloudPath → $normalizedDestPath (orig: $destPath)")
        // S0266: build a scope bound to the current suspend context so the cloud progress adapter
        // can launch onProgress emissions concurrently with the download stream.
        val downloadScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext)
        val cloudProgressEmitter = adaptCloudProgress(progressCallback, downloadScope)

        val pathInfo = cloudPathParser.parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("downloadFromCloudTo: Failed to parse cloud path: $cloudPath")
            return false
        }

        val resolvedFileName = resolveDisplayName(pathInfo, fileName)

        // Download to temp file first
        val tempFile = File.createTempFile("cloud_download_", ".tmp", context.cacheDir)
        return try {
            downloadToTempFile(pathInfo, tempFile, cloudProgressEmitter) &&
                writeTempFileToDestination(
                    tempFile = tempFile,
                    normalizedDestPath = normalizedDestPath,
                    resolvedFileName = resolvedFileName,
                    progressCallback = progressCallback
                )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * S0266: defensive metadata fetch. If the caller passed a bare fileId (no extension, fits the
     * cloud-id charset), look up the real display-name from the provider so the final file lands with
     * the correct name and MIME-derivable extension.
     */
    private suspend fun resolveDisplayName(
        pathInfo: CloudPathParser.CloudPathInfo,
        fileName: String
    ): String {
        val bareIdPattern = Regex("^[A-Za-z0-9_-]{20,}$")
        if (!bareIdPattern.matches(fileName)) return fileName

        val metadataResult = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
            client.getFileMetadata(pathInfo.fileId)
        }
        return when (metadataResult) {
            is CloudResult.Success -> metadataResult.data.name.takeIf { it.isNotBlank() } ?: fileName
            else -> fileName
        }
    }

    /** Streams the cloud object into [tempFile]; false means the download itself failed. */
    private suspend fun downloadToTempFile(
        pathInfo: CloudPathParser.CloudPathInfo,
        tempFile: File,
        cloudProgressEmitter: ((TransferProgress) -> Unit)?
    ): Boolean {
        val downloadResult = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
            val resourceKey = "cloud://${pathInfo.provider}"
            val bufferSize = ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)

            // Use BufferedOutputStream for performance
            val outputStream = java.io.BufferedOutputStream(tempFile.outputStream(), bufferSize)
            val result = client.downloadFile(pathInfo.fileId, outputStream, cloudProgressEmitter)
            outputStream.close()
            result
        }

        return if (downloadResult is CloudResult.Success) {
            true
        } else {
            val errorMsg = (downloadResult as? CloudResult.Error)?.message ?: "Re-authentication failed"
            Timber.e("downloadFromCloudTo: Download failed - $errorMsg")
            false
        }
    }

    /** Determine destination type and write. */
    private suspend fun writeTempFileToDestination(
        tempFile: File,
        normalizedDestPath: String,
        resolvedFileName: String,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val destType = getResourceType(normalizedDestPath)
        return when (destType) {
            ResourceType.LOCAL -> moveToLocal(tempFile, normalizedDestPath, resolvedFileName)
            ResourceType.SMB -> uploadToSmb(tempFile, normalizedDestPath, resolvedFileName, progressCallback)
            ResourceType.SFTP -> uploadToSftp(tempFile, normalizedDestPath, resolvedFileName)
            ResourceType.FTP -> uploadToFtp(tempFile, normalizedDestPath, resolvedFileName, progressCallback)
            ResourceType.CLOUD -> {
                Timber.e("downloadFromCloudTo: Cannot download cloud to cloud, use copyCloudToCloud")
                false
            }
            ResourceType.WEAR_WATCH -> {
                // S1861: a cloud file reaches the watch by landing on the phone first and then going
                // over the transfer queue, so there is no cloud -> watch destination to write to here.
                Timber.e("downloadFromCloudTo: the watch is not a download destination, transfer it instead")
                false
            }
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM ->
                throw IllegalArgumentException("Cannot download to an internet stream destination: $destType")
        }
    }

    /** Move temp file to local destination. */
    private fun moveToLocal(tempFile: File, destDir: String, resolvedFileName: String): Boolean {
        if (!tempFile.exists()) {
            Timber.w("downloadFromCloudTo: downloaded temp copy vanished before cloud->local transfer")
            return false
        }
        val localFile = File(destDir, resolvedFileName)
        localFile.parentFile?.mkdirs()
        if (localFile.exists()) {
            localFile.delete()
        }
        return if (moveOrCopy(tempFile, localFile)) {
            Timber.i("downloadFromCloudTo: SUCCESS - wrote to local ${localFile.absolutePath}")
            MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "cloud-download")
            true
        } else {
            false
        }
    }

    /**
     * Move (rename) instead of copy so only one full copy ever exists on the volume: copying held the
     * temp and the destination simultaneously, doubling the peak footprint and filling the cache
     * volume when large cloud APKs are classified. renameTo is atomic and same-volume only; fall back
     * to copy across volumes.
     */
    private fun moveOrCopy(tempFile: File, localFile: File): Boolean {
        if (tempFile.renameTo(localFile)) return true
        return try {
            tempFile.copyTo(localFile, overwrite = true)
            true
        } catch (e: java.io.IOException) {
            Timber.w(e, "downloadFromCloudTo: cloud->local transfer failed")
            // Drop any partially written destination so a truncated file is not
            // left behind or later mistaken for a valid copy.
            localFile.delete()
            false
        }
    }

    /**
     * The two guards every network destination shares: the downloaded temp copy must still exist, and
     * the destination must resolve to stored credentials. [kind] only names the protocol in the logs.
     */
    private suspend fun resolveUploadCredentials(
        tempFile: File,
        normalizedDestPath: String,
        kind: String
    ): NetworkCredentialsResolver.NetworkCredentials? =
        if (!tempFile.exists()) {
            Timber.w("downloadFromCloudTo: downloaded temp copy vanished before cloud->$kind transfer")
            null
        } else {
            networkCredentialsResolver.getCredentials(normalizedDestPath).also { credentials ->
                if (credentials == null) {
                    Timber.e("downloadFromCloudTo: No credentials for $kind path $normalizedDestPath")
                }
            }
        }

    /** Upload to SMB. */
    private suspend fun uploadToSmb(
        tempFile: File,
        normalizedDestPath: String,
        resolvedFileName: String,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val credentials = resolveUploadCredentials(tempFile, normalizedDestPath, "SMB") ?: return false
        val prefix = "smb://${credentials.server}/${credentials.shareName}"
        val remotePath = normalizedDestPath.removePrefix(prefix).removePrefix("/")
        val remoteFilePath = if (remotePath.isEmpty()) resolvedFileName else "$remotePath/$resolvedFileName"

        val uploadResult = try {
            smbClient.uploadFile(
                networkCredentialsResolver.run { credentials.toSmbConnectionInfo() },
                remoteFilePath,
                tempFile.inputStream(),
                tempFile.length(),
                progressCallback
            )
        } catch (e: java.io.IOException) {
            Timber.w(e, "downloadFromCloudTo: cloud->SMB transfer failed")
            null
        }

        return when (uploadResult) {
            null -> false
            is SmbResult.Success -> {
                Timber.i("downloadFromCloudTo: SUCCESS - uploaded to SMB $remoteFilePath")
                true
            }
            is SmbResult.Error -> {
                Timber.e("downloadFromCloudTo: SMB upload failed - ${uploadResult.message}")
                false
            }
        }
    }

    /** Upload to SFTP. */
    private suspend fun uploadToSftp(
        tempFile: File,
        normalizedDestPath: String,
        resolvedFileName: String
    ): Boolean {
        val credentials = resolveUploadCredentials(tempFile, normalizedDestPath, "SFTP") ?: return false
        val remotePath = extractSftpRemotePath(normalizedDestPath, credentials)
        val remoteFilePath = if (remotePath.isEmpty() || remotePath == "/") {
            resolvedFileName
        } else {
            "$remotePath/$resolvedFileName"
        }

        val uploadResult = try {
            sftpClient.uploadFile(
                networkCredentialsResolver.run { credentials.toSftpConnectionInfo() },
                remoteFilePath,
                tempFile.inputStream(),
                tempFile.length()
            )
        } catch (e: java.io.IOException) {
            Timber.w(e, "downloadFromCloudTo: cloud->SFTP transfer failed")
            null
        }

        return uploadResult?.fold(
            onSuccess = {
                Timber.i("downloadFromCloudTo: SUCCESS - uploaded to SFTP $remoteFilePath")
                true
            },
            onFailure = { e ->
                Timber.e("downloadFromCloudTo: SFTP upload failed - ${e.message}")
                false
            }
        ) ?: false
    }

    /** Upload to FTP. A failed connect leaves nothing to disconnect, so only a live session is closed. */
    private suspend fun uploadToFtp(
        tempFile: File,
        normalizedDestPath: String,
        resolvedFileName: String,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val credentials = resolveUploadCredentials(tempFile, normalizedDestPath, "FTP") ?: return false

        // Connect to FTP
        val connectResult = ftpClient.connect(
            host = credentials.server,
            port = credentials.port,
            username = credentials.username,
            password = credentials.password
        )

        return if (connectResult.isFailure) {
            val cause = connectResult.exceptionOrNull()?.message
            Timber.e("downloadFromCloudTo: FTP connection failed - $cause")
            false
        } else {
            try {
                putOverFtp(tempFile, normalizedDestPath, resolvedFileName, credentials, progressCallback)
            } finally {
                ftpClient.disconnect()
            }
        }
    }

    private suspend fun putOverFtp(
        tempFile: File,
        normalizedDestPath: String,
        resolvedFileName: String,
        credentials: NetworkCredentialsResolver.NetworkCredentials,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val remotePath = extractFtpRemotePath(normalizedDestPath, credentials)
        val remoteFilePath = if (remotePath.isEmpty() || remotePath == "/") {
            resolvedFileName
        } else {
            "$remotePath/$resolvedFileName"
        }

        val uploadResult = try {
            ftpClient.uploadFile(
                remoteFilePath,
                tempFile.inputStream(),
                tempFile.length(),
                progressCallback
            )
        } catch (e: java.io.IOException) {
            Timber.w(e, "downloadFromCloudTo: cloud->FTP transfer failed")
            null
        }

        return when {
            uploadResult == null -> false
            uploadResult.isSuccess -> {
                Timber.i("downloadFromCloudTo: SUCCESS - uploaded to FTP $remoteFilePath")
                true
            }
            else -> {
                Timber.e("downloadFromCloudTo: FTP upload failed - ${uploadResult.exceptionOrNull()?.message}")
                false
            }
        }
    }
}
