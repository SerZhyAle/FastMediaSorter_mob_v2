package com.sza.fastmediasorter.data.cloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.BaseFileOperationHandler
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.adaptCloudProgress
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategy
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Copy/move/delete for cloud resources; supports Cloud<->Local/SMB/SFTP/FTP/Cloud. Cloud paths: cloud://provider/folderId/file.ext */
@Singleton
class CloudFileOperationHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val cloudPathParser: CloudPathParser,
    private val networkCredentialsResolver: NetworkCredentialsResolver,
    private val cloudAuthHelper: CloudAuthenticationHelper,
    private val stagingDir: com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider,
    private val stagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter
) : BaseFileOperationHandler(context) {

    private val pathUtils = CloudFileOperationPathUtils(cloudPathParser)
    private val cloudToCloud = CloudToCloudTransferHelper(context, cloudPathParser, cloudAuthHelper)

    private val cloudStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        CloudOperationStrategy(context, googleDriveClient, dropboxClient, oneDriveClient, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val smbStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        SmbOperationStrategy(context, smbClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val sftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        SftpOperationStrategy(context, sftpClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val ftpStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        FtpOperationStrategy(context, ftpClient, credentialsRepository, stagingDir, stagingRegistry, destinationClassifier, destinationWriter),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    private val localStrategy: FileOperationStrategy = AtomicFileOperationStrategy(
        LocalOperationStrategy(context, stagingRegistry),
        destinationClassifier = destinationClassifier,
        enableAtomic = true
    )

    override fun getStrategies(): List<FileOperationStrategy> {
        return listOf(cloudStrategy, smbStrategy, sftpStrategy, ftpStrategy, localStrategy)
    }

    // Helper methods delegated to CloudFileOperationPathUtils
    private fun getResourceType(path: String): ResourceType = pathUtils.getResourceType(path)
    private fun isNetworkPath(path: String): Boolean = pathUtils.isNetworkPath(path)
    private fun normalizeNetworkPath(path: String): String = pathUtils.normalizeNetworkPath(path)
    private fun extractSftpRemotePath(path: String, credentials: NetworkCredentialsResolver.NetworkCredentials): String =
        pathUtils.extractSftpRemotePath(path, credentials)
    private fun extractFtpRemotePath(path: String, credentials: NetworkCredentialsResolver.NetworkCredentials): String =
        pathUtils.extractFtpRemotePath(path, credentials)

    override suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        val destinationPath = operation.destination.path

        // Check auth for destination if it's cloud
        if (cloudPathParser.isCloudPath(destinationPath)) {
            val destInfo = cloudPathParser.parseCloudPath(destinationPath)
            if (destInfo != null) {
                checkAuthenticationRequired(destInfo.provider)?.let { return it }
            }

            // Handle Upload (Any -> Cloud)
            val errors = mutableListOf<String>()
            val copiedPaths = mutableListOf<String>()
            var successCount = 0

            operation.sources.forEachIndexed { index, source ->
                val sourceType = getResourceType(source.path)
                val fileName = extractFileName(source.path, source.name)

                // Handle Cloud-to-Cloud copy separately
                val cloudFile = if (sourceType == ResourceType.CLOUD) {
                    copyCloudToCloud(source.path, destinationPath)
                } else {
                    uploadToCloudFromPath(
                        sourcePath = source.path,
                        fileName = fileName,
                        cloudPath = destinationPath,
                        sourceType = sourceType,
                        progressCallback = progressCallback
                    )
                }

                if (cloudFile != null) {
                    copiedPaths.add(cloudFile)
                    successCount++
                } else {
                    val error = "Failed to upload $fileName to cloud"
                    errors.add(error)
                }
            }

            return buildCopyResult(successCount, operation, copiedPaths, errors)
        }

        // Check auth for sources if any are cloud
        val firstSource = operation.sources.firstOrNull()
        if (firstSource != null && cloudPathParser.isCloudPath(firstSource.path)) {
            val sourceInfo = cloudPathParser.parseCloudPath(firstSource.path)
            if (sourceInfo != null) {
                checkAuthenticationRequired(sourceInfo.provider)?.let { return it }
            }

            // Handle Download (Cloud -> Any)
            val errors = mutableListOf<String>()
            val copiedPaths = mutableListOf<String>()
            var successCount = 0

            operation.sources.forEachIndexed { index, source ->
                val fileName = extractFileName(source.path, source.name)
                // S0266: announce current file to progress UI before the blocking download starts.
                progressCallback?.onFileStarted(index + 1, fileName, operation.sources.size)

                val success = downloadFromCloudTo(
                    cloudPath = source.path,
                    destPath = destinationPath,
                    fileName = fileName,
                    progressCallback = progressCallback
                )

                if (success) {
                    val resultPath = if (destinationPath.endsWith("/")) "$destinationPath$fileName" else "$destinationPath/$fileName"
                    copiedPaths.add(resultPath)
                    successCount++
                } else {
                    errors.add("Failed to download $fileName from cloud")
                }
            }
            return buildCopyResult(successCount, operation, copiedPaths, errors)
        }

        return super.executeCopy(operation, progressCallback)
    }

    suspend fun executeCopy(operation: FileOperation.Copy): FileOperationResult {
        return executeCopy(operation, null)
    }

    override suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        val destinationPath = operation.destination.path

        // Check auth for destination if it's cloud
        if (cloudPathParser.isCloudPath(destinationPath)) {
            val destInfo = cloudPathParser.parseCloudPath(destinationPath)
            if (destInfo != null) {
                checkAuthenticationRequired(destInfo.provider)?.let { return it }
            }

            // Handle Move to Cloud (Upload + Delete Source)

            // NO pre-flight check! Upload first, then delete.
            // Delete uses createDeleteRequest which auto-deletes after user grants permission.

            val errors = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()
            var successCount = 0

            // Collect files that need permission for batch delete after all uploads
            val pendingDeletePaths = mutableListOf<String>()

            operation.sources.forEachIndexed { index, source ->
                val sourceType = getResourceType(source.path)
                val fileName = extractFileName(source.path, source.name)

                // 1. Upload/Copy to destination
                val cloudFile = if (sourceType == ResourceType.CLOUD) {
                    // Use moveCloudToCloud for native move (or copy+delete for cross-provider)
                    moveCloudToCloud(source.path, destinationPath)
                } else {
                    uploadToCloudFromPath(
                        sourcePath = source.path,
                        fileName = fileName,
                        cloudPath = destinationPath,
                        sourceType = sourceType,
                        progressCallback = progressCallback
                    )
                }

                if (cloudFile != null) {
                    // 2. Delete Source (only for non-CLOUD, as moveCloudToCloud handles it)
                    val deleteSuccess = if (sourceType == ResourceType.CLOUD) {
                        // moveCloudToCloud already deleted source
                        true
                    } else {
                        try {
                            when (sourceType) {
                                ResourceType.LOCAL -> {
                                    if (source.path.startsWith("content:/")) {
                                        deleteWithSaf(source.path)
                                    } else {
                                        deleteFile(source.path).isSuccess
                                    }
                                }
                                else -> {
                                    val deleteResult = deleteFile(source.path)
                                    deleteResult.isSuccess
                                }
                            }
                        } catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
                            // File is already uploaded to cloud! Collect for batch delete after all uploads.
                            Timber.i("Cloud executeMove: File uploaded, permission needed for delete - $fileName")
                            pendingDeletePaths.add(source.path)
                            movedPaths.add(cloudFile)
                            successCount++
                            true // Consider delete "pending"
                        }
                    }

                    if (deleteSuccess && !pendingDeletePaths.contains(source.path)) {
                        movedPaths.add(cloudFile)
                        successCount++
                        Timber.i("Cloud executeMove: SUCCESS - moved $fileName")
                    } else if (!pendingDeletePaths.contains(source.path)) {
                        val error = "Uploaded $fileName but failed to delete source"
                        Timber.w("Cloud executeMove: PARTIAL - $error")
                        errors.add(error)
                        // Do NOT add to movedPaths or successCount
                        // File was copied but not moved (source still exists)
                    }
                } else {
                    val error = "Failed to upload $fileName to cloud"
                    errors.add(error)
                }
            }

            // After all uploads complete, check if any files need permission for batch delete.
            // requestBatchDeletePermission uses MediaStore.createDeleteRequest (API 30+); skip on older devices.
            if (pendingDeletePaths.isNotEmpty() &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Timber.i("Cloud executeMove: All ${pendingDeletePaths.size} files uploaded, requesting batch delete permission")
                requestBatchDeletePermission(pendingDeletePaths)
            }

            return buildMoveResult(successCount, operation, movedPaths, errors)
        }

        // Check auth for sources if any are cloud
        val firstSource = operation.sources.firstOrNull()
        if (firstSource != null && cloudPathParser.isCloudPath(firstSource.path)) {
            val sourceInfo = cloudPathParser.parseCloudPath(firstSource.path)
            if (sourceInfo != null) {
                checkAuthenticationRequired(sourceInfo.provider)?.let { return it }
            }

            // Handle Move from Cloud (Download + Delete Cloud)
            val errors = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()
            var successCount = 0

            operation.sources.forEachIndexed { index, source ->
                val fileName = extractFileName(source.path, source.name)
                // S0266: announce current file to progress UI before the blocking download starts.
                progressCallback?.onFileStarted(index + 1, fileName, operation.sources.size)

                // 1. Download
                val success = downloadFromCloudTo(
                    cloudPath = source.path,
                    destPath = destinationPath,
                    fileName = fileName,
                    progressCallback = progressCallback
                )

                if (success) {
                    // 2. Delete from Cloud
                    if (deleteFromCloud(source.path)) {
                        val resultPath = if (destinationPath.endsWith("/")) "$destinationPath$fileName" else "$destinationPath/$fileName"
                        movedPaths.add(resultPath)
                        successCount++
                        Timber.i("Cloud executeMove: SUCCESS - moved $fileName")
                    } else {
                        errors.add("Downloaded $fileName but failed to delete from cloud")
                    }
                } else {
                    errors.add("Failed to download $fileName from cloud")
                }
            }
            return buildMoveResult(successCount, operation, movedPaths, errors)
        }

        return super.executeMove(operation, progressCallback)
    }

    suspend fun executeMove(operation: FileOperation.Move): FileOperationResult {
        return executeMove(operation, null)
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("Cloud executeRename: Renaming ${operation.file.name} to ${operation.newName}")

        try {
            val cloudPath = operation.file.path

            if (!cloudPathParser.isCloudPath(cloudPath)) {
                Timber.e("Cloud executeRename: File is not cloud path: $cloudPath")
                return@withContext FileOperationResult.Failure("Not a cloud file: $cloudPath")
            }

            val pathInfo = cloudPathParser.parseCloudPath(cloudPath)
            if (pathInfo == null) {
                Timber.e("Cloud executeRename: Failed to parse cloud path: $cloudPath")
                return@withContext FileOperationResult.Failure("Invalid cloud path: $cloudPath")
            }

            val result = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
                // Check if file with new name already exists
                val existsResult = client.fileExists(operation.newName, pathInfo.folderId ?: "root")
                if (existsResult is CloudResult.Success && existsResult.data) {
                    val error = "File with name '${operation.newName}' already exists"
                    Timber.w("Cloud executeRename: SKIPPED - $error")
                    return@executeWithAutoReauth CloudResult.Error(error)
                }

                client.renameFile(pathInfo.fileId, operation.newName)
            }

            when (result) {
                is CloudResult.Success -> {
                    val newPath = "cloud://${pathInfo.provider}/${result.data.path}"
                    Timber.i("Cloud executeRename: SUCCESS - renamed to $newPath")
                    FileOperationResult.Success(1, operation, listOf(newPath))
                }
                is CloudResult.Error -> {
                    val error = "${operation.file.name}\n  New name: ${operation.newName}\n  Error: ${result.message}"
                    Timber.e("Cloud executeRename: FAILED - $error")
                    FileOperationResult.Failure(error)
                }
                null -> {
                    val error = "${operation.file.name}\n  New name: ${operation.newName}\n  Error: Re-authentication failed or cancelled"
                    Timber.e("Cloud executeRename: FAILED - $error")
                    FileOperationResult.Failure(error)
                }
            }
        } catch (e: Exception) {
            val error = "${operation.file.name}\n  New name: ${operation.newName}\n  Error: ${e.javaClass.simpleName} - ${e.message}"
            Timber.e(e, "Cloud executeRename: EXCEPTION - $error")
            FileOperationResult.Failure(error)
        }
    }

    override suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var successCount = 0

        // Cloud services use native trash, no need for manual .trash folder
        operation.files.forEach { file ->
            try {
                val filePath = file.path
                val isCloud = cloudPathParser.isCloudPath(filePath)

                if (isCloud) {
                    val info = cloudPathParser.parseCloudPath(filePath)
                    if (info != null) {
                        checkAuthenticationRequired(info.provider)?.let { return@withContext it }
                    }
                    if (deleteFromCloud(filePath)) {
                        deletedPaths.add(filePath)
                        successCount++
                        Timber.d("Cloud delete: deleted ${file.name}")
                    } else {
                        errors.add("Failed to delete ${file.name} from cloud")
                    }
                } else {
                    errors.add("Invalid operation: file is local")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete ${file.name}")
                errors.add("Delete error for ${file.name}: ${e.message}")
            }
        }

        return@withContext when {
            successCount == operation.files.size -> FileOperationResult.Success(successCount, operation, deletedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> {
                val errorMessage = errors.joinToString("; ")
                FileOperationResult.Failure(
                    error = "All delete operations failed: $errorMessage",
                    errorRes = R.string.all_delete_operations_failed,
                    formatArgs = listOf(errorMessage)
                )
            }
        }
    }

    /** S0266: public wrapper for noLegal silent APK install. Forwards into the internal download path so callers (e.g. BrowseApkInstallHandlerImpl) can hide the operation from the universal FileOperationProgressDialog. */
    suspend fun downloadFromCloudToPublic(
        cloudPath: String,
        destPath: String,
        fileName: String,
    ): Boolean = downloadFromCloudTo(
        cloudPath = cloudPath,
        destPath = destPath,
        fileName = fileName,
        progressCallback = null,
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

        // S0266: defensive metadata fetch. If the caller passed a bare fileId (no extension, fits the cloud-id charset), look up the real display-name from the provider so the final file lands with the correct name and MIME-derivable extension.
        val bareIdPattern = Regex("^[A-Za-z0-9_-]{20,}$")
        val resolvedFileName = if (bareIdPattern.matches(fileName)) {
            val metadataResult = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
                client.getFileMetadata(pathInfo.fileId)
            }
            when (metadataResult) {
                is CloudResult.Success -> metadataResult.data.name.takeIf { it.isNotBlank() } ?: fileName
                else -> {
                    fileName
                }
            }
        } else {
            fileName
        }

        // Download to temp file first
        val tempFile = File.createTempFile("cloud_download_", ".tmp", context.cacheDir)
        try {
            val downloadResult = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
                val resourceKey = "cloud://${pathInfo.provider}"
                val bufferSize = ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)

                // Use BufferedOutputStream for performance
                val outputStream = java.io.BufferedOutputStream(tempFile.outputStream(), bufferSize)
                val result = client.downloadFile(pathInfo.fileId, outputStream, cloudProgressEmitter)
                outputStream.close()
                result
            }

            if (downloadResult !is CloudResult.Success) {
                val errorMsg = (downloadResult as? CloudResult.Error)?.message ?: "Re-authentication failed"
                Timber.e("downloadFromCloudTo: Download failed - $errorMsg")
                return false
            }

            // Determine destination type and write
            val destType = getResourceType(normalizedDestPath)
            return when (destType) {
                    ResourceType.LOCAL -> {
                        // Move temp file to local destination
                        if (!tempFile.exists()) {
                            Timber.w("downloadFromCloudTo: downloaded temp copy vanished before cloud->local transfer")
                            return false
                        }
                        val localFile = File(normalizedDestPath, resolvedFileName)
                        localFile.parentFile?.mkdirs()
                        // Move (rename) instead of copy so only one full copy ever exists on the volume:
                        // copying held the temp and the destination simultaneously, doubling the peak
                        // footprint and filling the cache volume when large cloud APKs are classified.
                        // renameTo is atomic and same-volume only; fall back to copy across volumes.
                        if (localFile.exists()) {
                            localFile.delete()
                        }
                        if (!tempFile.renameTo(localFile)) {
                            try {
                                tempFile.copyTo(localFile, overwrite = true)
                            } catch (e: java.io.IOException) {
                                Timber.w(e, "downloadFromCloudTo: cloud->local transfer failed")
                                // Drop any partially written destination so a truncated file is not
                                // left behind or later mistaken for a valid copy.
                                localFile.delete()
                                return false
                            }
                        }
                        Timber.i("downloadFromCloudTo: SUCCESS - wrote to local ${localFile.absolutePath}")
                        MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "cloud-download")
                        true
                    }
                    ResourceType.SMB -> {
                        // Upload to SMB
                        if (!tempFile.exists()) {
                            Timber.w("downloadFromCloudTo: downloaded temp copy vanished before cloud->SMB transfer")
                            return false
                        }
                        val credentials = networkCredentialsResolver.getCredentials(normalizedDestPath)
                        if (credentials == null) {
                            Timber.e("downloadFromCloudTo: No credentials for SMB path $normalizedDestPath")
                            return false
                        }
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
                            return false
                        }

                    when (uploadResult) {
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
                ResourceType.SFTP -> {
                    // Upload to SFTP
                    if (!tempFile.exists()) {
                        Timber.w("downloadFromCloudTo: downloaded temp copy vanished before cloud->SFTP transfer")
                        return false
                    }
                    val credentials = networkCredentialsResolver.getCredentials(normalizedDestPath)
                    if (credentials == null) {
                        Timber.e("downloadFromCloudTo: No credentials for SFTP path $normalizedDestPath")
                        return false
                    }
                    val remotePath = extractSftpRemotePath(normalizedDestPath, credentials)
                    val remoteFilePath = if (remotePath.isEmpty() || remotePath == "/") resolvedFileName else "$remotePath/$resolvedFileName"

                    val uploadResult = try {
                        sftpClient.uploadFile(
                            networkCredentialsResolver.run { credentials.toSftpConnectionInfo() },
                            remoteFilePath,
                            tempFile.inputStream(),
                            tempFile.length()
                        )
                    } catch (e: java.io.IOException) {
                        Timber.w(e, "downloadFromCloudTo: cloud->SFTP transfer failed")
                        return false
                    }

                    uploadResult.fold(
                        onSuccess = {
                            Timber.i("downloadFromCloudTo: SUCCESS - uploaded to SFTP $remoteFilePath")
                            true
                        },
                        onFailure = { e ->
                            Timber.e("downloadFromCloudTo: SFTP upload failed - ${e.message}")
                            false
                        }
                    )
                }
                ResourceType.FTP -> {
                    // Upload to FTP
                    if (!tempFile.exists()) {
                        Timber.w("downloadFromCloudTo: downloaded temp copy vanished before cloud->FTP transfer")
                        return false
                    }
                    val credentials = networkCredentialsResolver.getCredentials(normalizedDestPath)
                    if (credentials == null) {
                        Timber.e("downloadFromCloudTo: No credentials for FTP path $normalizedDestPath")
                        return false
                    }

                    // Connect to FTP
                    val connectResult = ftpClient.connect(
                        host = credentials.server,
                        port = credentials.port,
                        username = credentials.username,
                        password = credentials.password
                    )

                    if (connectResult.isFailure) {
                        Timber.e("downloadFromCloudTo: FTP connection failed - ${connectResult.exceptionOrNull()?.message}")
                        return false
                    }

                    try {
                        val remotePath = extractFtpRemotePath(normalizedDestPath, credentials)
                        val remoteFilePath = if (remotePath.isEmpty() || remotePath == "/") resolvedFileName else "$remotePath/$resolvedFileName"

                        val uploadResult = try {
                            ftpClient.uploadFile(
                                remoteFilePath,
                                tempFile.inputStream(),
                                tempFile.length(),
                                progressCallback
                            )
                        } catch (e: java.io.IOException) {
                            Timber.w(e, "downloadFromCloudTo: cloud->FTP transfer failed")
                            return false
                        }

                        if (uploadResult.isSuccess) {
                            Timber.i("downloadFromCloudTo: SUCCESS - uploaded to FTP $remoteFilePath")
                            true
                        } else {
                            Timber.e("downloadFromCloudTo: FTP upload failed - ${uploadResult.exceptionOrNull()?.message}")
                            false
                        }
                    } finally {
                        ftpClient.disconnect()
                    }
                }
                ResourceType.CLOUD -> {
                    Timber.e("downloadFromCloudTo: Cannot download cloud to cloud, use copyCloudToCloud")
                    false
                }
                ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM ->
                    throw IllegalArgumentException("Cannot download to an internet stream destination: $destType")
            }
        } finally {
            tempFile.delete()
        }
    }

    /** Download cloud file to a local [localFile]. */
    private suspend fun downloadFromCloud(
        cloudPath: String,
        localFile: File,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): File? {
        Timber.d("downloadFromCloud: $cloudPath → ${localFile.absolutePath}")

        val pathInfo = cloudPathParser.parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("downloadFromCloud: Failed to parse cloud path: $cloudPath")
            return null
        }

        val tempFile = File.createTempFile("cloud_download_", ".tmp", context.cacheDir)

        return try {
            val result = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
                val resourceKey = "cloud://${pathInfo.provider}"
                val bufferSize = ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)

                val outputStream = java.io.BufferedOutputStream(tempFile.outputStream(), bufferSize)
                val downloadResult = client.downloadFile(pathInfo.fileId, outputStream, null)
                outputStream.close()
                downloadResult
            }

            when (result) {
                is CloudResult.Success -> {
                    if (!tempFile.exists()) {
                        Timber.w("downloadFromCloud: downloaded temp copy vanished before local transfer")
                        return null
                    }
                    localFile.parentFile?.mkdirs()
                    try {
                        tempFile.copyTo(localFile, overwrite = true)
                    } catch (e: java.io.IOException) {
                        Timber.w(e, "downloadFromCloud: local transfer failed")
                        return null
                    }
                    Timber.i("downloadFromCloud: SUCCESS - ${tempFile.length()} bytes written to ${localFile.name}")
                    localFile
                }
                is CloudResult.Error -> {
                    Timber.e("downloadFromCloud: FAILED - ${result.message}")
                    null
                }
                null -> {
                    Timber.e("downloadFromCloud: Re-authentication failed or cancelled")
                    null
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    /** Universal any->cloud upload via temp file (OOM-safe). [sourcePath] may be local, smb://, sftp://, ftp://. */
    private suspend fun uploadToCloudFromPath(
        sourcePath: String,
        fileName: String,
        cloudPath: String,
        sourceType: ResourceType,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): String? {
        val normalizedSourcePath = normalizeNetworkPath(sourcePath)
        Timber.d("uploadToCloudFromPath: START - sourcePath=$normalizedSourcePath (orig: $sourcePath), fileName=$fileName, cloudPath=$cloudPath, sourceType=$sourceType")

        // Check if source is SAF URI (needs temp file even though it's LOCAL type)
        val isSafUri = normalizedSourcePath.startsWith("content:/") || normalizedSourcePath.startsWith("content://")

        // Get file from source (use temp file for network sources and SAF URIs)
        val tempFile = if (sourceType != ResourceType.LOCAL || isSafUri) {
            File.createTempFile("cloud_upload_", ".tmp", context.cacheDir)
        } else {
            null
        }

        val sourceFile = try {
            when (sourceType) {
                ResourceType.LOCAL -> {
                    // Check if it's a SAF URI (content://)
                    if (isSafUri) {
                        // Handle SAF URI - copy to temp file first
                        val normalizedPath = if (normalizedSourcePath.startsWith("content:/") && !normalizedSourcePath.startsWith("content://")) {
                            normalizedSourcePath.replaceFirst("content:/", "content://")
                        } else {
                            normalizedSourcePath
                        }
                        val uri = Uri.parse(normalizedPath)
                        val docFile = DocumentFile.fromSingleUri(context, uri)

                        if (docFile == null || !docFile.exists()) {
                            Timber.e("uploadToCloudFromPath: SAF file does not exist: $normalizedPath")
                            tempFile?.delete()
                            return null
                        }

                        // Copy SAF content to temp file for upload
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile!!.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            } ?: run {
                                Timber.e("uploadToCloudFromPath: Failed to open SAF input stream: $normalizedPath")
                                tempFile?.delete()
                                return null
                            }
                            tempFile
                        } catch (e: Exception) {
                            Timber.e(e, "uploadToCloudFromPath: Failed to copy SAF content to temp")
                            tempFile?.delete()
                            return null
                        }
                    } else {
                        // Regular local file path
                        val localFile = File(normalizedSourcePath)
                        if (!localFile.exists()) {
                            Timber.e("uploadToCloudFromPath: Local file does not exist: ${localFile.absolutePath}")
                            return null
                        }
                        localFile
                    }
                }
                ResourceType.SMB -> {
                    val credentials = networkCredentialsResolver.getCredentials(normalizedSourcePath)
                    if (credentials == null) {
                        Timber.e("uploadToCloudFromPath: No credentials for SMB path $normalizedSourcePath")
                        tempFile?.delete()
                        return null
                    }
                    // Extract remote path: smb://server:port/share/path/file.ext -> path/file.ext
                    val remotePath = networkCredentialsResolver.extractSmbRemotePath(normalizedSourcePath)

                    val outputStream = tempFile!!.outputStream()
                    val downloadResult = smbClient.downloadFile(
                        networkCredentialsResolver.run { credentials.toSmbConnectionInfo() },
                        remotePath,
                        outputStream,
                        0L, // fileSize unknown
                        progressCallback
                    )

                    outputStream.close()
                    when (downloadResult) {
                        is SmbResult.Success -> {
                            tempFile
                        }
                        is SmbResult.Error -> {
                            Timber.e("uploadToCloudFromPath: SMB download failed - ${downloadResult.message}")
                            tempFile.delete()
                            return null
                        }
                    }
                }
                ResourceType.SFTP -> {
                    val credentials = networkCredentialsResolver.getCredentials(normalizedSourcePath)
                    if (credentials == null) {
                        Timber.e("uploadToCloudFromPath: No credentials for SFTP path $normalizedSourcePath")
                        tempFile?.delete()
                        return null
                    }
                    val remotePath = extractSftpRemotePath(normalizedSourcePath, credentials)

                    val outputStream = tempFile!!.outputStream()
                    val downloadResult = sftpClient.downloadFile(
                        networkCredentialsResolver.run { credentials.toSftpConnectionInfo() },
                        remotePath,
                        outputStream,
                        0L, // fileSize unknown
                        progressCallback
                    )

                    outputStream.close()
                    if (downloadResult.isSuccess) {
                        tempFile
                    } else {
                        Timber.e("uploadToCloudFromPath: SFTP download failed - ${downloadResult.exceptionOrNull()?.message}")
                        tempFile.delete()
                        return null
                    }
                }
                ResourceType.FTP -> {
                    val credentials = networkCredentialsResolver.getCredentials(normalizedSourcePath)
                    if (credentials == null) {
                        Timber.e("uploadToCloudFromPath: No credentials for FTP path $normalizedSourcePath")
                        tempFile?.delete()
                        return null
                    }

                    // Connect to FTP
                    val connectResult = ftpClient.connect(
                        host = credentials.server,
                        port = credentials.port,
                        username = credentials.username,
                        password = credentials.password
                    )

                    if (connectResult.isFailure) {
                        Timber.e("uploadToCloudFromPath: FTP connection failed - ${connectResult.exceptionOrNull()?.message}")
                        tempFile?.delete()
                        return null
                    }

                    try {
                        val remotePath = extractFtpRemotePath(normalizedSourcePath, credentials)

                        val outputStream = tempFile!!.outputStream()
                        val downloadResult = ftpClient.downloadFile(
                            remotePath,
                            outputStream,
                            0L, // fileSize unknown
                            progressCallback
                        )

                        outputStream.close()
                        if (downloadResult.isSuccess) {
                            tempFile
                        } else {
                            Timber.e("uploadToCloudFromPath: FTP download failed - ${downloadResult.exceptionOrNull()?.message}")
                            tempFile.delete()
                            return null
                        }
                    } finally {
                        ftpClient.disconnect()
                    }
                }
                ResourceType.CLOUD -> {
                    Timber.e("uploadToCloudFromPath: Cannot upload cloud to cloud, use copyCloudToCloud")
                    tempFile?.delete()
                    return null
                }
                ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM ->
                    throw IllegalArgumentException("Cannot upload from an internet stream source: $sourceType")
            }
        } catch (e: Exception) {
            Timber.e(e, "uploadToCloudFromPath: Exception during read from $sourceType: ${e.message}")
            tempFile?.delete()
            return null
        }

        // Upload to cloud
        val pathInfo = cloudPathParser.parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("uploadToCloudFromPath: Failed to parse cloud path: $cloudPath")
            tempFile?.delete()
            return null
        }

        val mimeType = getMimeType(fileName)

        return try {
            val result = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
                val resourceKey = "cloud://${pathInfo.provider}"
                val bufferSize = ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)

                val inputStream = java.io.BufferedInputStream(FileInputStream(sourceFile), bufferSize)
                val uploadResult = client.uploadFile(inputStream, fileName, mimeType, pathInfo.folderId, null)
                inputStream.close()
                uploadResult
            }

            when (result) {
                is CloudResult.Success -> {
                    Timber.i("uploadToCloudFromPath: SUCCESS - uploaded $fileName, cloud path=${result.data.path}")
                    "cloud://${pathInfo.provider}/${result.data.path}"
                }
                is CloudResult.Error -> {
                    Timber.e("uploadToCloudFromPath: FAILED - ${result.message}")
                    null
                }
                null -> {
                    Timber.e("uploadToCloudFromPath: Re-authentication failed or cancelled")
                    null
                }
            }
        } finally {
            tempFile?.delete()
        }
    }

    /** Upload local [localFile] to cloud at [cloudPath]. */
    private suspend fun uploadToCloud(
        localFile: File,
        cloudPath: String,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): String? {
        Timber.d("uploadToCloud: ${localFile.absolutePath} → $cloudPath")

        if (!localFile.exists()) {
            Timber.e("uploadToCloud: Local file does not exist: ${localFile.absolutePath}")
            return null
        }

        val fileSize = localFile.length()

        val pathInfo = cloudPathParser.parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("uploadToCloud: Failed to parse cloud path: $cloudPath")
            return null
        }

        val mimeType = getMimeType(localFile.name)

        return localFile.inputStream().use { inputStream ->
            val result = cloudAuthHelper.executeWithAutoReauth(pathInfo.provider) { client ->
                client.uploadFile(inputStream, localFile.name, mimeType, pathInfo.folderId, null)
            }

            when (result) {
                is CloudResult.Success -> {
                    Timber.i("uploadToCloud: SUCCESS - uploaded ${localFile.name}")
                    "cloud://${pathInfo.provider}/${result.data.path}"
                }
                is CloudResult.Error -> {
                    Timber.e("uploadToCloud: FAILED - ${result.message}")
                    null
                }
                null -> {
                    Timber.e("uploadToCloud: Re-authentication failed or cancelled")
                    null
                }
            }
        }
    }

    private suspend fun deleteFromCloud(cloudPath: String): Boolean =
        cloudToCloud.deleteFromCloud(cloudPath)

    private suspend fun copyCloudToCloud(sourcePath: String, destPath: String): String? =
        cloudToCloud.copyCloudToCloud(sourcePath, destPath)

    private suspend fun moveCloudToCloud(sourcePath: String, destPath: String): String? =
        cloudToCloud.moveCloudToCloud(sourcePath, destPath)
    /** Returns AuthenticationRequired result when [provider] needs re-auth; null otherwise. */
    internal suspend fun checkAuthenticationRequired(provider: CloudProvider): FileOperationResult? =
        when (cloudAuthHelper.getCloudClientResult(provider)) {
            is CloudAuthenticationHelper.CloudClientResult.AuthRequired -> {
                val providerName = provider.name.lowercase().replaceFirstChar { it.uppercase() }
                FileOperationResult.AuthenticationRequired(
                    provider = providerName,
                    message = context.getString(R.string.cloud_auth_required, providerName)
                )
            }
            else -> null
        }

    private fun getMimeType(fileName: String): String = CloudFileOperationPathUtils.getMimeType(fileName)
}
