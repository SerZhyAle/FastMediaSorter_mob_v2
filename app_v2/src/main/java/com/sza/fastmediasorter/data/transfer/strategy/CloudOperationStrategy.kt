package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.adaptCloudProgress
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Strategy for cloud:// operations (google_drive, dropbox, onedrive).
 * Handles Cloud↔Local and Cloud↔Cloud transfers; cross-protocol (SMB/SFTP/FTP) goes via temp file.
 */
class CloudOperationStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient,
    private val stagingDir: com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider,
    private val stagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter
) : FileOperationStrategy {

    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isSourceCloud = supportsProtocol(source)
            val isDestCloud = supportsProtocol(destination)

            when {
                isSourceCloud && !isDestCloud -> downloadCloudToLocal(source, destination, progressCallback)
                !isSourceCloud && isDestCloud -> uploadLocalToCloud(source, destination, overwrite, progressCallback)
                isSourceCloud && isDestCloud -> copyCloudToCloud(source, destination, overwrite, progressCallback)
                else -> Result.failure(IllegalArgumentException("At least one path must be cloud://"))
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Copy failed - $source -> $destination")
            Result.failure(e)
        }
    }

    override suspend fun moveFile(source: String, destination: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isSourceCloud = supportsProtocol(source)
            val isDestCloud = supportsProtocol(destination)

            when {
                isSourceCloud && isDestCloud -> {
                    val src = parseCloudUri(source)
                        ?: return@withContext Result.failure(Exception("Failed to parse cloud source: $source"))
                    val dst = parseCloudUri(destination)
                        ?: return@withContext Result.failure(Exception("Failed to parse cloud destination: $destination"))

                    val client = getClientOrThrow(src.provider)

                    if (src.provider == dst.provider) {
                        val (dstParent, dstName) = splitParentAndName(dst)

                        val moved = client.moveFile(src.idOrPath, dstParent)
                        when (moved) {
                            is CloudResult.Success -> {
                                if (dstName != null && moved.data.name != dstName) {
                                    client.renameFile(moved.data.id, dstName)
                                }
                                Result.success(Unit)
                            }
                            is CloudResult.Error -> Result.failure(Exception(moved.message, moved.cause))
                        }
                    } else {
                        // Cross-provider move: copy then delete
                        val copyResult = copyCloudToCloud(source, destination, overwrite = true, progressCallback = null)
                        if (copyResult.isFailure) {
                            return@withContext Result.failure(copyResult.exceptionOrNull() ?: Exception("Copy failed"))
                        }
                        deleteFile(source)
                    }
                }
                else -> {
                    // Cross-protocol move: copy + delete
                    val copyResult = copyFile(source, destination, overwrite = true, progressCallback = null)
                    if (copyResult.isFailure) {
                        return@withContext Result.failure(copyResult.exceptionOrNull() ?: Exception("Copy failed"))
                    }
                    deleteFile(source)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Move failed - $source -> $destination")
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(path)) {
                return@withContext Result.failure(IllegalArgumentException("Path must be cloud://: $path"))
            }

            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))

            val client = getClientOrThrow(info.provider)

            when (val result = client.deleteFile(info.idOrPath)) {
                is CloudResult.Success -> Result.success(Unit)
                is CloudResult.Error -> Result.failure(Exception(result.message, result.cause))
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Delete failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(path)) {
                return@withContext Result.success(false)
            }

            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))

            val client = getClientOrThrow(info.provider)

            // Try lightweight fileExists when we can infer (parent + name), otherwise fallback to metadata.
            val (parentId, name) = splitParentAndName(info)
            if (name != null) {
                when (val existsResult = client.fileExists(name, parentId)) {
                    is CloudResult.Success -> return@withContext Result.success(existsResult.data)
                    is CloudResult.Error -> Timber.w("CloudOperationStrategy: fileExists failed, falling back to metadata: ${existsResult.message}")
                }
            }

            when (client.getFileMetadata(info.idOrPath)) {
                is CloudResult.Success -> Result.success(true)
                is CloudResult.Error -> Result.success(false)
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Exists check failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))
            
            val client = getClientOrThrow(info.provider)
            val (parentId, name) = splitParentAndName(info)
            
            if (name == null) {
                return@withContext Result.failure(Exception("Cannot create root directory"))
            }

            when (val result = client.createFolder(name, parentId.ifBlank { null })) {
                is CloudResult.Success -> Result.success(Unit)
                is CloudResult.Error -> Result.failure(Exception(result.message, result.cause))
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Create directory failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun createTextFile(
        parentPath: String,
        fileName: String,
        content: String,
        resourceId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // S0189: defer file creation - see SmbOperationStrategy.createTextFile.
            val dir = stagingDir.directoryFor(com.sza.fastmediasorter.data.local.staging.StagedKind.TEXT_NOTE)
            val localFile = File(dir, "${resourceId}_${fileName}")
            stagingRegistry.register(
                file = localFile,
                targetResourceId = resourceId,
                targetParentPath = parentPath,
                intendedName = fileName,
                kind = com.sza.fastmediasorter.data.local.staging.StagedKind.TEXT_NOTE,
            )
            Result.success(localFile.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy.createTextFile failed - parent=$parentPath name=$fileName")
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("cloud_write", ".tmp", context.cacheDir)
        try {
             tempFile.writeText(content)
             uploadLocalToCloud(tempFile.absolutePath, path, overwrite = true, progressCallback = null).map { }
        } catch (e: Exception) {
             Result.failure(e)
        } finally {
             tempFile.delete()
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("cloud_read", ".tmp", context.cacheDir)
        try {
             val result = downloadCloudToLocal(path, tempFile.absolutePath, progressCallback = null)
             if (result.isSuccess) {
                 Result.success(tempFile.readText())
             } else {
                 Result.failure(Exception("Download failed: ${result.exceptionOrNull()?.message}"))
             }
        } catch (e: Exception) {
             Result.failure(e)
        } finally {
             tempFile.delete()
        }
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))
            
            val client = getClientOrThrow(info.provider)
            // Use idOrPath as folderId. If blank/root, pass null.
            val folderId = if (info.idOrPath.isBlank() || info.idOrPath == "/") null else info.idOrPath
            
            val allFiles = mutableListOf<String>()
            var pageToken: String? = null
            
            do {
                when (val result = client.listFiles(folderId, pageToken)) {
                    is CloudResult.Success -> {
                        val (files, nextToken) = result.data
                        val providerName = info.provider.name.lowercase()
                        files.forEach { file ->
                             val id = file.id
                             if (id.isNotBlank()) {
                                 allFiles.add("cloud://$providerName/$id")
                             }
                        }
                        pageToken = nextToken
                    }
                    is CloudResult.Error -> {
                        return@withContext Result.failure(Exception("List files failed: ${result.message}"))
                    }
                }
            } while (pageToken != null)
            
            Result.success(allFiles)
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: listFiles failed - $path")
            Result.failure(e)
        }
    }

    override fun supportsProtocol(path: String) = path.startsWith("cloud://") || path.startsWith("cloud:/")

    override fun getProtocolName(): String = "cloud"

    private suspend fun downloadCloudToLocal(
        cloudPath: String,
        localPath: String,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val info = parseCloudUri(cloudPath)
            ?: return Result.failure(Exception("Failed to parse cloud path: $cloudPath"))

        val client = getClientOrThrow(info.provider)
        val progressScope = CoroutineScope(currentCoroutineContext())

        // S0231: route writes through LocalDestinationWriter for scoped-storage awareness.
        val category = destinationClassifier.classify(localPath)
        val sink = destinationWriter.open(category, overwrite = true).getOrElse { error ->
            Timber.e(error, "CloudOperationStrategy: writer.open failed for $localPath")
            return Result.failure(error)
        }

        return try {
            val result = client.downloadFile(
                fileId = info.fileIdForDownload,
                outputStream = sink.outputStream,
                // S0730: throttle via the shared adapter (100KB + AtomicLong CAS) instead of launching
                // a raw coroutine per 64KB tick - GoogleDriveRestClient emits untrottled, so the old
                // path spawned thousands of coroutines on a large transfer and flooded the progress bar.
                progressCallback = adaptCloudProgress(progressCallback, progressScope)
            )
            when (result) {
                is CloudResult.Success -> sink.commit().fold(
                    onSuccess = { Result.success(localPath) },
                    onFailure = { err -> Result.failure(err) }
                )
                is CloudResult.Error -> {
                    sink.abort()
                    Result.failure(Exception(result.message, result.cause))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            sink.abort()
            throw e
        } catch (e: Throwable) {
            sink.abort()
            Result.failure(e)
        }
    }

    private suspend fun uploadLocalToCloud(
        localPath: String,
        cloudDestPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val info = parseCloudUri(cloudDestPath)
            ?: return Result.failure(Exception("Failed to parse cloud destination: $cloudDestPath"))

        val client = getClientOrThrow(info.provider)
        val localFile = File(localPath)
        if (!localFile.exists()) {
            return Result.failure(Exception("Source file does not exist: $localPath"))
        }

        val (parentId, fileName) = splitParentAndName(info)
        val targetName = fileName ?: localFile.name

        if (!overwrite) {
            when (val existsResult = client.fileExists(targetName, parentId)) {
                is CloudResult.Success -> if (existsResult.data) {
                    return Result.failure(Exception("Destination file already exists: $cloudDestPath"))
                }
                is CloudResult.Error -> Timber.w("CloudOperationStrategy: fileExists failed: ${existsResult.message}")
            }
        }

        val mimeType = guessMimeType(targetName)
        val progressScope = CoroutineScope(currentCoroutineContext())
        return try {
            localFile.inputStream().use { input ->
                when (val result = client.uploadFile(
                    inputStream = input,
                    fileName = targetName,
                    mimeType = mimeType,
                    parentFolderId = parentId.ifBlank { null },
                    fileSize = localFile.length(),
                    // S0730: throttle via the shared adapter (see downloadCloudToLocal) rather than a
                    // raw coroutine per progress tick.
                    progressCallback = adaptCloudProgress(progressCallback, progressScope)
                )) {
                    is CloudResult.Success -> Result.success(cloudDestPath)
                    is CloudResult.Error -> Result.failure(Exception(result.message, result.cause))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun copyCloudToCloud(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val src = parseCloudUri(source)
            ?: return Result.failure(Exception("Failed to parse cloud source: $source"))
        val dst = parseCloudUri(destination)
            ?: return Result.failure(Exception("Failed to parse cloud destination: $destination"))

        if (src.provider == dst.provider) {
            val client = getClientOrThrow(src.provider)
            val (dstParent, dstName) = splitParentAndName(dst)

            if (!overwrite && dstName != null) {
                when (val existsResult = client.fileExists(dstName, dstParent)) {
                    is CloudResult.Success -> if (existsResult.data) {
                        return Result.failure(Exception("Destination file already exists: $destination"))
                    }
                    is CloudResult.Error -> Timber.w("CloudOperationStrategy: fileExists failed: ${existsResult.message}")
                }
            }

            return when (val copied = client.copyFile(src.idOrPath, dstParent, dstName)) {
                is CloudResult.Success -> Result.success(destination)
                is CloudResult.Error -> Result.failure(Exception(copied.message, copied.cause))
            }
        }

        // Cross-provider: download to temp, then upload.
        val tempFile = File.createTempFile("cloud_copy_", ".tmp", context.cacheDir)
        return try {
            val downloadResult = downloadCloudToLocal(source, tempFile.absolutePath, progressCallback)
            if (downloadResult.isFailure) {
                return downloadResult
            }
            uploadLocalToCloud(tempFile.absolutePath, destination, overwrite, progressCallback)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private suspend fun getClientOrThrow(provider: CloudProvider): CloudStorageClient =
        getClient(provider) ?: throw Exception("Not authenticated: ${provider.name}")

    private suspend fun getClient(provider: CloudProvider): CloudStorageClient? {
        val client: CloudStorageClient = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> googleDriveClient
            CloudProvider.DROPBOX -> dropboxClient
            CloudProvider.ONEDRIVE -> oneDriveClient
        }

        if (client.isAuthenticated()) return client

        val restored = when (provider) {
            CloudProvider.DROPBOX -> (client as? DropboxClient)?.tryRestoreFromStorage() == true
            CloudProvider.GOOGLE_DRIVE -> (client as? GoogleDriveRestClient)?.tryRestoreFromStorage() == true
            CloudProvider.ONEDRIVE -> false
        }

        return if (restored) client else null
    }

    private data class CloudUriInfo(
        val provider: CloudProvider,
        val idOrPath: String,
        val segments: List<String>
    ) {
        val fileIdForDownload: String
            get() = segments.lastOrNull() ?: idOrPath
    }

    private fun parseCloudUri(rawPath: String): CloudUriInfo? {
        return try {
            val normalized = if (rawPath.startsWith("cloud:/") && !rawPath.startsWith("cloud://")) {
                rawPath.replaceFirst("cloud:/", "cloud://")
            } else {
                rawPath
            }

            if (!normalized.startsWith("cloud://")) return null

            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase() ?: return null
            val provider = when (host) {
                "google_drive", "googledrive", "google" -> CloudProvider.GOOGLE_DRIVE
                "dropbox" -> CloudProvider.DROPBOX
                "onedrive" -> CloudProvider.ONEDRIVE
                else -> return null
            }

            val idOrPath = uri.path?.removePrefix("/") ?: ""
            if (idOrPath.isBlank()) return null

            return CloudUriInfo(provider, idOrPath, uri.pathSegments)
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Failed to parse cloud uri: $rawPath")
            null
        }
    }

    private fun splitParentAndName(info: CloudUriInfo): Pair<String, String?> {
        if (info.segments.isEmpty()) return "" to null
        if (info.segments.size == 1) {
            // Ambiguous (could be folderId or fileId). Treat as "no parent".
            return "" to null
        }

        val name = info.segments.last()
        val parentSegments = info.segments.dropLast(1)

        val parentIdOrPath = when (info.provider) {
            CloudProvider.DROPBOX -> "/" + parentSegments.joinToString("/")
            else -> parentSegments.joinToString("/")
        }

        return parentIdOrPath to name
    }

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(path)) {
                return@withContext Result.failure(IllegalArgumentException("Not a cloud path: $path"))
            }
            
            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))
            
            val client = getClientOrThrow(info.provider)
            val allFiles = mutableListOf<Pair<String, String>>() // id to name
            collectCloudFiles(client, info.idOrPath, allFiles)

            var deletedCount = 0
            for ((fileId, fileName) in allFiles.reversed()) {
                progressCallback?.invoke(deletedCount, allFiles.size, fileName)
                when (client.deleteFile(fileId)) {
                    is CloudResult.Success -> deletedCount++
                    is CloudResult.Error -> Timber.w("Failed to delete cloud file: $fileId")
                }
            }
            when (client.deleteFile(info.idOrPath)) {
                is CloudResult.Success -> deletedCount++
                is CloudResult.Error -> Timber.w("Failed to delete cloud directory: ${info.idOrPath}")
            }
            
            Timber.d("CloudOperationStrategy: Deleted directory $path ($deletedCount items)")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Delete directory failed - $path")
            Result.failure(e)
        }
    }
    
    private suspend fun collectCloudFiles(
        client: CloudStorageClient,
        folderId: String,
        result: MutableList<Pair<String, String>>
    ) {
        when (val listResult = client.listFiles(folderId)) {
            is CloudResult.Success -> {
                val (files, _) = listResult.data
                for (file in files) {
                    if (file.isFolder) {
                        collectCloudFiles(client, file.id, result)
                    }
                    result.add(file.id to file.name)
                }
            }
            is CloudResult.Error -> Timber.w("Failed to list cloud directory: $folderId - ${listResult.message}")
        }
    }
    
    override suspend fun renameDirectory(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(oldPath) || !supportsProtocol(newPath)) {
                return@withContext Result.failure(IllegalArgumentException("Both paths must be cloud"))
            }
            
            val oldInfo = parseCloudUri(oldPath)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $oldPath"))
            val newInfo = parseCloudUri(newPath)
                ?: return@withContext Result.failure(Exception("Failed to parse destination path: $newPath"))
            
            // Must be same provider
            if (oldInfo.provider != newInfo.provider) {
                return@withContext Result.failure(IllegalArgumentException("Cannot rename across cloud providers"))
            }
            
            val client = getClientOrThrow(oldInfo.provider)
            val newName = newInfo.segments.lastOrNull() ?: newInfo.idOrPath.substringAfterLast('/')
            
            when (val result = client.renameFile(oldInfo.idOrPath, newName)) {
                is CloudResult.Success -> {
                    Timber.d("CloudOperationStrategy: Renamed directory $oldPath -> $newPath")
                    Result.success(newPath)
                }
                is CloudResult.Error -> Result.failure(Exception(result.message, result.cause))
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Rename directory failed - $oldPath -> $newPath")
            Result.failure(e)
        }
    }
    
    override suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(source)) {
                return@withContext Result.failure(IllegalArgumentException("Source must be cloud path"))
            }
            
            val sourceInfo = parseCloudUri(source)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $source"))
            
            val sourceClient = getClientOrThrow(sourceInfo.provider)
            val allFiles = mutableListOf<Triple<String, String, String>>() // id, name, relativePath
            collectCloudFilesWithPath(sourceClient, sourceInfo.idOrPath, "", allFiles)
            createDirectory(destination).onFailure { return@withContext Result.failure(it) }
            var copiedCount = 0
            val sep = if (destination.endsWith('/')) "" else "/"
            for ((fileId, fileName, relativePath) in allFiles) {
                val destFilePath = "$destination$sep$relativePath$fileName"
                progressCallback?.invoke(copiedCount, allFiles.size, fileName)
                if (relativePath.isNotEmpty()) {
                    createDirectory("$destination$sep$relativePath".trimEnd('/'))
                }
                val fullSourcePath = when (sourceInfo.provider) {
                    CloudProvider.GOOGLE_DRIVE -> "cloud://google_drive/$fileId"
                    CloudProvider.DROPBOX -> "cloud://dropbox/$fileId"
                    CloudProvider.ONEDRIVE -> "cloud://onedrive/$fileId"
                }
                copyFile(fullSourcePath, destFilePath, overwrite = true, progressCallback = null).onSuccess {
                    copiedCount++
                }
            }
            
            Timber.d("CloudOperationStrategy: Copied directory $source -> $destination ($copiedCount files)")
            Result.success(copiedCount)
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: Copy directory failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    private suspend fun collectCloudFilesWithPath(
        client: CloudStorageClient,
        folderId: String,
        currentPath: String,
        result: MutableList<Triple<String, String, String>>
    ) {
        when (val listResult = client.listFiles(folderId)) {
            is CloudResult.Success -> {
                val (files, _) = listResult.data
                for (file in files) {
                    if (file.isFolder) {
                        val newPath = if (currentPath.isEmpty()) "${file.name}/" else "$currentPath${file.name}/"
                        collectCloudFilesWithPath(client, file.id, newPath, result)
                    } else {
                        result.add(Triple(file.id, file.name, currentPath))
                    }
                }
            }
            is CloudResult.Error -> Timber.w("Failed to list cloud directory: $folderId - ${listResult.message}")
        }
    }
    
    override suspend fun isDirectory(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(path)) {
                return@withContext Result.failure(IllegalArgumentException("Not a cloud path: $path"))
            }
            
            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))
            
            val client = getClientOrThrow(info.provider)
            
            when (val result = client.getFileMetadata(info.idOrPath)) {
                is CloudResult.Success -> Result.success(result.data.isFolder)
                is CloudResult.Error -> Result.failure(Exception(result.message, result.cause))
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: isDirectory check failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun getDirectoryInfo(path: String): Result<com.sza.fastmediasorter.data.transfer.DirectoryInfo> = withContext(Dispatchers.IO) {
        try {
            if (!supportsProtocol(path)) {
                return@withContext Result.failure(IllegalArgumentException("Not a cloud path: $path"))
            }
            
            val info = parseCloudUri(path)
                ?: return@withContext Result.failure(Exception("Failed to parse cloud path: $path"))
            
            val client = getClientOrThrow(info.provider)
            
            val metadata = when (val result = client.getFileMetadata(info.idOrPath)) {
                is CloudResult.Success -> result.data
                is CloudResult.Error -> return@withContext Result.failure(Exception(result.message, result.cause))
            }
            if (!metadata.isFolder) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
            }
            val childCount = when (val listResult = client.listFiles(info.idOrPath)) {
                is CloudResult.Success -> listResult.data.first.size
                is CloudResult.Error -> 0
            }
            
            Result.success(
                com.sza.fastmediasorter.data.transfer.DirectoryInfo(
                    path = path,
                    name = metadata.name,
                    childCount = childCount,
                    totalSize = metadata.size,
                    lastModified = metadata.modifiedDate
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "CloudOperationStrategy: getDirectoryInfo failed - $path")
            Result.failure(e)
        }
    }
}
