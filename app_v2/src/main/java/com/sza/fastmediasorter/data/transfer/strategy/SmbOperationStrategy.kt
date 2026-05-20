package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import com.sza.fastmediasorter.data.network.SmbClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.FileExistsException
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.SmbPathUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/** Strategy for SMB file operations (smb:// protocol via SmbClient). */
class SmbOperationStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val credentialsRepository: NetworkCredentialsRepository,
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
            val isSourceSmb = source.startsWith("smb:", ignoreCase = true)
            val isDestSmb = destination.startsWith("smb:", ignoreCase = true)
            
            when {
                isSourceSmb && isDestSmb -> copySmbToSmb(source, destination, overwrite, progressCallback)
                isSourceSmb && !isDestSmb -> downloadFromSmb(source, destination, progressCallback)
                !isSourceSmb && isDestSmb -> uploadToSmb(source, destination, overwrite, progressCallback)
                else -> Result.failure(IllegalArgumentException("At least one path must be SMB"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Copy failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun moveFile(source: String, destination: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceInfo = SmbPathUtils.parseSmbPath(source)
            val destInfo = SmbPathUtils.parseSmbPath(destination)
            val sameSmbShare = source.startsWith("smb:", ignoreCase = true) &&
                destination.startsWith("smb:", ignoreCase = true) &&
                sourceInfo != null && destInfo != null &&
                sourceInfo.connectionInfo.server == destInfo.connectionInfo.server &&
                sourceInfo.connectionInfo.shareName == destInfo.connectionInfo.shareName
            if (sameSmbShare) {
                when (val result = smbClient.moveFile(getConnectionInfo(sourceInfo!!), sourceInfo.remotePath, destInfo!!.remotePath)) {
                    is SmbResult.Success -> Result.success(Unit)
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                val copyResult = copyFile(source, destination, overwrite = true, progressCallback = null)
                if (copyResult.isFailure) return@withContext Result.failure(copyResult.exceptionOrNull() ?: Exception("Copy failed"))
                deleteFile(source)
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Move failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("smb:", ignoreCase = true)) {
                val pathInfo = SmbPathUtils.parseSmbPath(path)
                    ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
                
                val connectionInfo = getConnectionInfo(pathInfo)
                
                when (val result = smbClient.deleteFile(connectionInfo, pathInfo.remotePath)) {
                    is SmbResult.Success -> Result.success(Unit)
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                try {
                    val uri = parseAndFixUri(path)
                    if (uri.scheme == "content") {
                        val deleted = com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(context, path, "SmbOperationStrategy.deleteFile")
                        if (deleted) Result.success(Unit) else Result.failure(Exception("Failed to delete content URI: $path"))
                    } else {
                        val file = File(if (uri.scheme == "file") uri.path ?: path else path)
                        if (file.delete()) Result.success(Unit) else Result.failure(Exception("Failed to delete local file: $path"))
                    }
                } catch (e: Exception) { Result.failure(e) }
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Delete failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("smb:", ignoreCase = true)) {
                val pathInfo = SmbPathUtils.parseSmbPath(path)
                    ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
                
                val connectionInfo = getConnectionInfo(pathInfo)
                
                when (val result = smbClient.createDirectory(connectionInfo, pathInfo.remotePath)) {
                    is SmbResult.Success -> Result.success(Unit)
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                val dir = File(path)
                if (dir.mkdirs()) Result.success(Unit) else Result.failure(Exception("Failed to create local directory: $path"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Create directory failed - $path")
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
            // S0189: defer file creation - only register intent. Disk write happens on first Save
            // (handled by SaveTextNoteUseCase). Cancel before save leaves no file on disk.
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
            Timber.e(e, "SmbOperationStrategy.createTextFile failed - parent=$parentPath name=$fileName")
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("smb:", ignoreCase = true)) {
                val pathInfo = SmbPathUtils.parseSmbPath(path)
                    ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
                
                val connectionInfo = getConnectionInfo(pathInfo)
                val bytes = content.toByteArray()
                val inputStream = ByteArrayInputStream(bytes)
                
                when (val result = smbClient.uploadFile(
                    connectionInfo,
                    pathInfo.remotePath,
                    inputStream, 
                    fileSize = bytes.size.toLong()
                )) {
                    is SmbResult.Success -> Result.success(Unit)
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                File(path).writeText(content)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Write file failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("smb:", ignoreCase = true)) {
                val pathInfo = SmbPathUtils.parseSmbPath(path)
                    ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
                
                val connectionInfo = getConnectionInfo(pathInfo)
                val buffer = ByteArrayOutputStream()
                
                when (val result = smbClient.downloadFile(
                    connectionInfo,
                    pathInfo.remotePath,
                    buffer,
                    fileSize = 0L,
                    progressCallback = null
                )) {
                    is SmbResult.Success -> Result.success(buffer.toString("UTF-8"))
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                val file = File(path)
                if (file.exists()) Result.success(file.readText())
                else Result.failure(java.io.FileNotFoundException("File not found: $path"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Read file failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("smb:", ignoreCase = true)) {
                val pathInfo = SmbPathUtils.parseSmbPath(path)
                    ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
                
                val connectionInfo = getConnectionInfo(pathInfo)
                
                when (val result = smbClient.exists(connectionInfo, pathInfo.remotePath)) {
                    is SmbResult.Success -> Result.success(result.data)
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Exists check failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("smb:", ignoreCase = true)) {
                val pathInfo = SmbPathUtils.parseSmbPath(path)
                    ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
                
                val connectionInfo = getConnectionInfo(pathInfo)
                
                when (val result = smbClient.listFiles(connectionInfo, pathInfo.remotePath)) {
                    is SmbResult.Success -> {
                        val baseUrl = "smb://${connectionInfo.server}/${connectionInfo.shareName}"
                        Result.success(result.data.map { "$baseUrl/${it.path}" })
                    }
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            } else {
                Result.failure(IllegalArgumentException("Not an SMB path"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: listFiles failed - $path")
            Result.failure(e)
        }
    }

    override fun supportsProtocol(path: String): Boolean {
        // Accept smb: prefix (File() normalizes smb:// to smb:/)
        return path.startsWith("smb:", ignoreCase = true)
    }
    
    override fun getProtocolName(): String = "smb"
    
    /** Fix malformed content URI (replace "content:/" with "content://") if needed. */
    private fun parseAndFixUri(path: String): android.net.Uri =
        if (path.startsWith("content:") && !path.startsWith("content://"))
            android.net.Uri.parse(path.replaceFirst("content:/", "content://"))
        else android.net.Uri.parse(path)

    private suspend fun downloadFromSmb(
        smbPath: String,
        localPath: String,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val pathInfo = SmbPathUtils.parseSmbPath(smbPath)
            ?: return Result.failure(Exception("Failed to parse SMB path: $smbPath"))

        val connectionInfo = getConnectionInfo(pathInfo)
        val destUri = parseAndFixUri(localPath)

        // S0231: content URIs already bypass scoped storage (provider-backed). Filesystem paths
        // are routed through LocalDestinationWriter for MediaStore-aware writes.
        if (destUri.scheme == "content") {
            val outputStream = try {
                context.contentResolver.openOutputStream(destUri)
                    ?: return Result.failure(Exception("Failed to open output stream for content URI: $localPath"))
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to open local destination: ${e.message}"))
            }
            return try {
                outputStream.use { stream ->
                    when (val result = smbClient.downloadFile(
                        connectionInfo,
                        pathInfo.remotePath,
                        stream,
                        fileSize = 0L,
                        progressCallback = progressCallback
                    )) {
                        is SmbResult.Success -> {
                            Timber.d("SmbOperationStrategy: Downloaded to $localPath")
                            Result.success(localPath)
                        }
                        is SmbResult.Error -> Result.failure(Exception(result.message))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SmbOperationStrategy: Download failed")
                Result.failure(e)
            }
        }

        val resolvedPath = if (destUri.scheme == "file") destUri.path ?: localPath else localPath
        val category = destinationClassifier.classify(resolvedPath)
        val sink = destinationWriter.open(category, overwrite = true).getOrElse { error ->
            Timber.e(error, "SmbOperationStrategy: writer.open failed for $resolvedPath")
            return Result.failure(error)
        }

        return try {
            val result = smbClient.downloadFile(
                connectionInfo,
                pathInfo.remotePath,
                sink.outputStream,
                fileSize = 0L,
                progressCallback = progressCallback
            )
            when (result) {
                is SmbResult.Success -> {
                    sink.commit().fold(
                        onSuccess = {
                            Timber.d("SmbOperationStrategy: Downloaded to $localPath")
                            Result.success(localPath)
                        },
                        onFailure = { err -> Result.failure(err) }
                    )
                }
                is SmbResult.Error -> {
                    sink.abort()
                    Result.failure(Exception(result.message))
                }
            }
        } catch (e: CancellationException) {
            sink.abort()
            throw e
        } catch (e: Throwable) {
            sink.abort()
            Timber.e(e, "SmbOperationStrategy: Download failed")
            Result.failure(e)
        }
    }
    
    private suspend fun uploadToSmb(
        localPath: String,
        smbPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val pathInfo = SmbPathUtils.parseSmbPath(smbPath)
            ?: return Result.failure(Exception("Failed to parse SMB path: $smbPath"))
        
        val connectionInfo = getConnectionInfo(pathInfo)
        if (!overwrite) {
            when (val existsResult = smbClient.exists(connectionInfo, pathInfo.remotePath)) {
                is SmbResult.Success -> {
                    if (existsResult.data) {
                        val fileName = smbPath.substringAfterLast('/')
                        return Result.failure(FileExistsException(fileName, smbPath, isMove = false))
                    }
                }
                is SmbResult.Error -> Timber.w("Failed to check if destination exists: ${existsResult.message}")
            }
        }
        
        val sourceUri = parseAndFixUri(localPath)
        
        return try {
            val (inputStream, size) = if (sourceUri.scheme == "content") {
                val stream = context.contentResolver.openInputStream(sourceUri) 
                    ?: return Result.failure(Exception("No content provider: $localPath"))
                
                    val fileSize = try {
                    context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: -1L
                } catch (e: Exception) { -1L }
                
                Pair(stream, fileSize)
            } else {
                val file = File(if (sourceUri.scheme == "file") sourceUri.path ?: localPath else localPath)
                if (!file.exists()) {
                    return Result.failure(Exception("Source file does not exist: $localPath"))
                }
                Pair(FileInputStream(file), file.length())
            }

            inputStream.use { stream ->
                when (val result = smbClient.uploadFile(
                    connectionInfo,
                    pathInfo.remotePath,
                    stream,
                    fileSize = size,
                    progressCallback = progressCallback
                )) {
                    is SmbResult.Success -> {
                        Timber.d("SmbOperationStrategy: Uploaded $localPath")
                        Result.success(smbPath)
                    }
                    is SmbResult.Error -> Result.failure(Exception(result.message))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Upload failed")
            Result.failure(e)
        }
    }
    
    private suspend fun copySmbToSmb(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val sourceInfo = SmbPathUtils.parseSmbPath(sourcePath)
            ?: return Result.failure(Exception("Failed to parse source SMB path: $sourcePath"))
        
        val destInfo = SmbPathUtils.parseSmbPath(destPath)
            ?: return Result.failure(Exception("Failed to parse destination SMB path: $destPath"))
        
        val sourceConnectionInfo = getConnectionInfo(sourceInfo)
        val destConnectionInfo = getConnectionInfo(destInfo)
        if (!overwrite) {
            when (val existsResult = smbClient.exists(destConnectionInfo, destInfo.remotePath)) {
                is SmbResult.Success -> {
                    if (existsResult.data) {
                        val fileName = destPath.substringAfterLast('/')
                        return Result.failure(FileExistsException(fileName, destPath, isMove = false))
                    }
                }
                is SmbResult.Error -> Timber.w("Failed to check if destination exists: ${existsResult.message}")
            }
        }
        return try {
            val buffer = ByteArrayOutputStream()
            when (val downloadResult = smbClient.downloadFile(
                sourceConnectionInfo,
                sourceInfo.remotePath,
                buffer,
                fileSize = 0L,
                progressCallback = null // No progress for first half
            )) {
                is SmbResult.Error -> return Result.failure(Exception("Download failed: ${downloadResult.message}"))
                is SmbResult.Success -> {
                    val inputStream = ByteArrayInputStream(buffer.toByteArray())
                    when (val uploadResult = smbClient.uploadFile(
                        destConnectionInfo,
                        destInfo.remotePath,
                        inputStream,
                        fileSize = buffer.size().toLong(),
                        progressCallback = progressCallback
                    )) {
                        is SmbResult.Success -> {
                            Timber.d("SmbOperationStrategy: Copied SMB→SMB (${buffer.size()} bytes)")
                            Result.success(destPath)
                        }
                        is SmbResult.Error -> Result.failure(Exception("Upload failed: ${uploadResult.message}"))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: SMB→SMB copy failed")
            Result.failure(e)
        }
    }
    
    private suspend fun getConnectionInfo(pathInfo: SmbPathUtils.SmbPathInfo): SmbConnectionInfo {
        val credentials = resolveSmbCredentials(pathInfo.connectionInfo.server, pathInfo.connectionInfo.shareName)
        return credentials?.let {
            pathInfo.connectionInfo.copy(username = it.username, password = it.password, domain = it.domain)
        } ?: pathInfo.connectionInfo
    }

    private suspend fun resolveSmbCredentials(
        server: String,
        shareName: String
    ): com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity? {
        val normalizedShare = shareName.trim().trim('/', '\\')
        val firstSegment = normalizedShare.substringBefore('/', normalizedShare)

        val shareCandidates = linkedSetOf(shareName).also {
            if (normalizedShare.isNotEmpty()) it.add(normalizedShare)
            if (firstSegment.isNotEmpty()) it.add(firstSegment)
        }

        Timber.d("SmbOperationStrategy: resolving credentials for server='$server', share='$shareName'. Candidates: $shareCandidates")

        for (candidate in shareCandidates) {
            val candidateCredentials = credentialsRepository.getByServerAndShare(server, candidate)
            if (candidateCredentials != null) {
                Timber.d("SmbOperationStrategy: Credentials resolved for server='$server', share='$candidate' (user: ${candidateCredentials.username})")
                return candidateCredentials
            } else {
                Timber.v("SmbOperationStrategy: No credentials for '$candidate'")
            }
        }

        val hostCredentials = credentialsRepository.getCredentialsByHost(server)
        if (hostCredentials != null && hostCredentials.type.equals("SMB", ignoreCase = true)) {
            // D instead of W - expected fallback during batch ops, not an error worth spamming W-level
            Timber.d("SmbOperationStrategy: Share-specific credentials not found for '$server/$shareName', using SMB host credentials (user: ${hostCredentials.username})")
            return hostCredentials
        }

        Timber.w("SmbOperationStrategy: No SMB credentials found for '$server/$shareName'")
        return null
    }
    
    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("smb:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an SMB path: $path"))
            }
            
            val pathInfo = SmbPathUtils.parseSmbPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            val allFiles = mutableListOf<String>()
            collectSmbEntries(connectionInfo, pathInfo.remotePath, allFiles)
            val totalCount = allFiles.size
            var deletedCount = 0
            for (filePath in allFiles.sortedByDescending { it.length }) {
                progressCallback?.invoke(deletedCount, totalCount, filePath.substringAfterLast('/'))
                when (smbClient.deleteFile(connectionInfo, filePath)) {
                    is SmbResult.Success -> deletedCount++
                    is SmbResult.Error -> {
                        when (val dirResult = smbClient.deleteDirectory(connectionInfo, filePath)) {
                            is SmbResult.Success -> deletedCount++
                            is SmbResult.Error -> Timber.w("Failed to delete: $filePath - ${dirResult.message}")
                        }
                    }
                }
            }
            when (val result = smbClient.deleteDirectory(connectionInfo, pathInfo.remotePath)) {
                is SmbResult.Success -> deletedCount++
                is SmbResult.Error -> Timber.w("Failed to delete directory: $path - ${result.message}")
            }
            
            Timber.d("SmbOperationStrategy: Deleted directory $path ($deletedCount items)")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Delete directory failed - $path")
            Result.failure(e)
        }
    }
    
    private suspend fun collectSmbEntries(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        result: MutableList<String>,
        filesOnly: Boolean = false
    ) {
        when (val scanResult = smbClient.scanMediaFiles(
            connectionInfo = connectionInfo,
            remotePath = remotePath,
            extensions = null,
            scanSubdirectories = false,
            progressCallback = null,
            includeDirectories = true
        )) {
            is SmbResult.Success -> {
                for (file in scanResult.data) {
                    val fullPath = if (remotePath.isEmpty()) file.name else "$remotePath/${file.name}"
                    if (file.isDirectory) collectSmbEntries(connectionInfo, fullPath, result, filesOnly)
                    if (!filesOnly || !file.isDirectory) result.add(fullPath)
                }
            }
            is SmbResult.Error -> Timber.w("Failed to list SMB directory: $remotePath")
        }
    }
    
    override suspend fun renameDirectory(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!oldPath.startsWith("smb:", ignoreCase = true) || !newPath.startsWith("smb:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Both paths must be SMB"))
            }
            
            val oldInfo = SmbPathUtils.parseSmbPath(oldPath)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $oldPath"))
            val newInfo = SmbPathUtils.parseSmbPath(newPath)
                ?: return@withContext Result.failure(Exception("Failed to parse destination path: $newPath"))
            
            if (oldInfo.connectionInfo.server != newInfo.connectionInfo.server ||
                oldInfo.connectionInfo.shareName != newInfo.connectionInfo.shareName) {
                return@withContext Result.failure(IllegalArgumentException("Cannot rename across servers or shares"))
            }
            
            val connectionInfo = getConnectionInfo(oldInfo)
            
            when (val result = smbClient.moveFile(connectionInfo, oldInfo.remotePath, newInfo.remotePath)) {
                is SmbResult.Success -> {
                    Timber.d("SmbOperationStrategy: Renamed directory $oldPath -> $newPath")
                    Result.success(newPath)
                }
                is SmbResult.Error -> Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Rename directory failed - $oldPath -> $newPath")
            Result.failure(e)
        }
    }
    
    override suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!source.startsWith("smb:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Source must be SMB path"))
            }
            
            val sourceInfo = SmbPathUtils.parseSmbPath(source)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $source"))
            
            val sourceConnectionInfo = getConnectionInfo(sourceInfo)
            val allFiles = mutableListOf<String>()
            collectSmbEntries(sourceConnectionInfo, sourceInfo.remotePath, allFiles, filesOnly = true)
            val totalCount = allFiles.size
            createDirectory(destination).onFailure { return@withContext Result.failure(it) }
            var copiedCount = 0
            for (filePath in allFiles) {
                val relativePath = filePath.removePrefix(sourceInfo.remotePath).trimStart('/')
                val destFilePath = "${destination.trimEnd('/')}/$relativePath"
                progressCallback?.invoke(copiedCount, totalCount, filePath.substringAfterLast('/'))
                val parentDir = destFilePath.substringBeforeLast('/')
                if (parentDir != destination) createDirectory(parentDir)
                val fullSourcePath = "smb://${sourceInfo.connectionInfo.server}/${sourceInfo.connectionInfo.shareName}/$filePath"
                copyFile(fullSourcePath, destFilePath, overwrite = true, progressCallback = null).onSuccess {
                    copiedCount++
                }
            }
            
            Timber.d("SmbOperationStrategy: Copied directory $source -> $destination ($copiedCount files)")
            Result.success(copiedCount)
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: Copy directory failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun isDirectory(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("smb:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an SMB path: $path"))
            }
            
            val pathInfo = SmbPathUtils.parseSmbPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            
            when (val result = smbClient.getFileInfo(connectionInfo, pathInfo.remotePath)) {
                is SmbResult.Success -> Result.success(result.data.isDirectory)
                is SmbResult.Error -> Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: isDirectory check failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun getDirectoryInfo(path: String): Result<com.sza.fastmediasorter.data.transfer.DirectoryInfo> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("smb:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an SMB path: $path"))
            }
            
            val pathInfo = SmbPathUtils.parseSmbPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse SMB path: $path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            val fileInfo = when (val result = smbClient.getFileInfo(connectionInfo, pathInfo.remotePath)) {
                is SmbResult.Success -> result.data
                is SmbResult.Error -> return@withContext Result.failure(Exception(result.message))
            }
            if (!fileInfo.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
            }
            val allFiles = mutableListOf<String>()
            collectSmbEntries(connectionInfo, pathInfo.remotePath, allFiles, filesOnly = true)
            val childCount = when (val scanResult = smbClient.scanMediaFiles(
                connectionInfo = connectionInfo,
                remotePath = pathInfo.remotePath,
                extensions = null,
                scanSubdirectories = false,
                progressCallback = null,
                includeDirectories = true
            )) {
                is SmbResult.Success -> scanResult.data.size
                is SmbResult.Error -> 0
            }
            
            Result.success(
                com.sza.fastmediasorter.data.transfer.DirectoryInfo(
                    path = path,
                    name = pathInfo.remotePath.substringAfterLast('/').ifEmpty { pathInfo.connectionInfo.shareName },
                    childCount = childCount,
                    totalSize = 0L, // Size calculation would require additional API calls
                    lastModified = fileInfo.lastModified
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "SmbOperationStrategy: getDirectoryInfo failed - $path")
            Result.failure(e)
        }
    }
}
