package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.data.transfer.FileExistsException
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

/**
 * Strategy for SFTP (SSH File Transfer Protocol) file operations.
 * Handles sftp:// protocol operations using SftpClient.
 */
class SftpOperationStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val endpointResolver: SftpEndpointResolver,
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
            val isSourceSftp = source.startsWith("sftp:", ignoreCase = true)
            val isDestSftp = destination.startsWith("sftp:", ignoreCase = true)
            
            when {
                isSourceSftp && isDestSftp -> {
                    // SFTP to SFTP: buffer transfer
                    copySftpToSftp(source, destination, overwrite, progressCallback)
                }
                isSourceSftp && !isDestSftp -> {
                    // SFTP to Local: download
                    downloadFromSftp(source, destination, progressCallback)
                }
                !isSourceSftp && isDestSftp -> {
                    // Local to SFTP: upload
                    uploadToSftp(source, destination, overwrite, progressCallback)
                }
                else -> {
                    Result.failure(IllegalArgumentException("At least one path must be SFTP"))
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Copy failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun moveFile(source: String, destination: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isSourceSftp = source.startsWith("sftp:", ignoreCase = true)
            val isDestSftp = destination.startsWith("sftp:", ignoreCase = true)
            
            when {
                isSourceSftp && isDestSftp -> {
                    // Try server-side move if on same server
                    val sourceInfo = parseSftpPath(source)
                    val destInfo = parseSftpPath(destination)
                    
                    if (sourceInfo != null && destInfo != null &&
                        sourceInfo.host == destInfo.host &&
                        sourceInfo.port == destInfo.port &&
                        sourceInfo.username == destInfo.username
                    ) {
                        // Server-side move (rename)
                        val connectionInfo = getConnectionInfo(sourceInfo)
                        val fromPath = sourceInfo.remotePath
                        val toPath = destInfo.remotePath
                        
                        sftpClient.rename(connectionInfo, fromPath, toPath)
                    } else {
                        // Different servers: copy + delete
                        val copyResult = copyFile(source, destination, overwrite = true, progressCallback = null)
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
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Move failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("sftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Path must be SFTP"))
            }
            
            val pathInfo = parseSftpPath(path)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid SFTP path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            
            // Try to figure out if it's a directory or file
            val statResult = sftpClient.getFileAttributes(connectionInfo, pathInfo.remotePath)
            
            // Pass SftpException (with its status code) directly so callers can classify the failure
            if (statResult.isSuccess && statResult.getOrNull()?.isDirectory == true) {
                sftpClient.deleteDirectory(connectionInfo, pathInfo.remotePath)
            } else {
                sftpClient.deleteFile(connectionInfo, pathInfo.remotePath)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Delete failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("sftp:", ignoreCase = true)) return@withContext Result.success(false)
            
            val pathInfo = parseSftpPath(path) ?: return@withContext Result.success(false)
            val connectionInfo = getConnectionInfo(pathInfo)
            
            sftpClient.exists(connectionInfo, pathInfo.remotePath)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: exists check failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseSftpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid SFTP path"))
            val connectionInfo = getConnectionInfo(pathInfo)

            sftpClient.createDirectory(connectionInfo, pathInfo.remotePath)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Create directory failed - $path")
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
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy.createTextFile failed - parent=$parentPath name=$fileName")
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseSftpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid SFTP path"))
            val connectionInfo = getConnectionInfo(pathInfo)
            val inputStream = ByteArrayInputStream(content.toByteArray())
            
            val uploadResult = sftpClient.uploadFile(
                connectionInfo,
                pathInfo.remotePath,
                inputStream,
                content.length.toLong(),
                progressCallback = null
            )
            
            // Return the uploadResult directly to preserve any SftpException and its status code
            uploadResult
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Write file failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseSftpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid SFTP path"))
            val connectionInfo = getConnectionInfo(pathInfo)
            val buffer = ByteArrayOutputStream()
            
            val downloadResult = sftpClient.downloadFile(
                connectionInfo,
                pathInfo.remotePath,
                buffer,
                fileSize = 0L,
                progressCallback = null
            )
            
            if (downloadResult.isSuccess) {
                 Result.success(buffer.toString("UTF-8"))
            } else {
                 Result.failure(Exception("Download failed: ${downloadResult.exceptionOrNull()?.message}"))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Read file failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseSftpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid SFTP path"))
            val connectionInfo = getConnectionInfo(pathInfo)
            
            val listResult = sftpClient.listFiles(connectionInfo, pathInfo.remotePath, recursive = false)
            
            if (listResult.isSuccess) {
                // Determine base URI structure to prepend
                // We want to reconstruct sftp://username@host:port/remotePath
                // Or just sftp://host:port/remotePath if username is implicit
                val userInfo = if (pathInfo.username.isNotEmpty()) "${pathInfo.username}@" else ""
                val portInfo = if (pathInfo.port != 22) ":${pathInfo.port}" else ""
                val basePrefix = "sftp://$userInfo${pathInfo.host}$portInfo"
                
                // sftpClient returns absolute paths on server e.g. /home/user/file.txt
                // We construct full URI. Note: remote path includes leading slash usually.
                val paths = listResult.getOrNull()?.map { listing ->
                     // listing.path is absolute on server e.g. /home/user/file.txt
                     // Construct full URI: sftp://host/path/to/file
                     "$basePrefix${listing.path}"
                } ?: emptyList()
                Result.success(paths)
            } else {
                 Result.failure(Exception("List files failed: ${listResult.exceptionOrNull()?.message}"))
            }
        } catch (e: CancellationException) {
            // Navigation away from the screen cancels the listing scope - expected, not a failure.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SftpOperationStrategy: listFiles failed - $path")
            Result.failure(e)
        }
    }
    
    override fun supportsProtocol(path: String): Boolean {
        // Accept sftp: prefix (File() normalizes sftp:// to sftp:/)
        return path.startsWith("sftp:", ignoreCase = true)
    }
    
    override fun getProtocolName(): String = "SFTP"

    private data class SftpPathInfo(
        val host: String,
        val port: Int,
        val username: String,
        val remotePath: String
    )
    
    private fun parseSftpPath(path: String): SftpPathInfo? {
        try {
            if (!path.startsWith("sftp:", ignoreCase = true)) return null
            
            val withoutProtocol = path.substringAfter("sftp:", "").trimStart('/')
            val userHostPart = withoutProtocol.substringBefore("/")
            val remotePath = "/" + withoutProtocol.substringAfter("/", "")

            // Supported formats:
            // - sftp://host:port/path
            // - sftp://username@host:port/path
            val usernameFromUrl = userHostPart.substringBefore("@", missingDelimiterValue = "")
                .takeIf { userHostPart.contains("@") }
                ?.trim()

            val hostPortPart = if (userHostPart.contains("@")) {
                userHostPart.substringAfter("@")
            } else {
                userHostPart
            }
            
            val host: String
            val port: Int
            if (hostPortPart.contains(":")) {
                host = hostPortPart.substringBefore(":")
                port = hostPortPart.substringAfter(":").toIntOrNull() ?: 22
            } else {
                host = hostPortPart
                port = 22
            }

            val username = usernameFromUrl ?: ""

            val result = SftpPathInfo(host = host, port = port, username = username, remotePath = remotePath)
            Timber.v("Parsed SFTP path: '$path' -> Host: $host, Port: $port, User: '$username', Remote: '$remotePath'")
            return result
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse SFTP path: $path")
            return null
        }
    }
    
    private suspend fun getConnectionInfo(pathInfo: SftpPathInfo): SftpClient.SftpConnectionInfo {
        // S1006: reach the resource on whichever address is live now (LAN at home, WAN in transit).
        val resolved = endpointResolver.resolve(pathInfo.host, pathInfo.port)
        val host = resolved.host
        val port = resolved.port
        val credentials = credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
            ?: credentialsRepository.getCredentialsByHost(host)

        if (credentials == null) {
            throw IllegalStateException("No credentials found for $host:$port")
        }

        val usernameToUse = pathInfo.username.takeIf { it.isNotBlank() } ?: credentials.username
        return SftpClient.SftpConnectionInfo(
            host = host,
            port = port,
            username = usernameToUse,
            password = credentials.password,
            privateKey = credentials.decryptedSshPrivateKey,
            passphrase = credentials.password.ifEmpty { null }
        )
    }
    
    private suspend fun copySftpToSftp(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        try {
            val sourceInfo = parseSftpPath(source)
                ?: return Result.failure(IllegalArgumentException("Invalid source path"))
            val destInfo = parseSftpPath(destination)
                ?: return Result.failure(IllegalArgumentException("Invalid destination path"))
            
            // Check if destination exists
            if (!overwrite) {
                val destConnectionInfo = getConnectionInfo(destInfo)
                val existsResult = sftpClient.exists(destConnectionInfo, destInfo.remotePath)
                if (existsResult.isSuccess && existsResult.getOrNull() == true) {
                    val fileName = destination.substringAfterLast('/')
                    return Result.failure(FileExistsException(fileName, destination, isMove = false))
                }
            }
            
            val sourceConnectionInfo = getConnectionInfo(sourceInfo)
            val statResult = sftpClient.stat(sourceConnectionInfo, sourceInfo.remotePath)
            if (statResult.isFailure) {
                return Result.failure(statResult.exceptionOrNull() ?: Exception("Failed to get source file size"))
            }
            val fileSize = statResult.getOrNull()?.size ?: 0L
            
            // Download to buffer
            val buffer = ByteArrayOutputStream()
            val downloadResult = sftpClient.downloadFile(
                sourceConnectionInfo,
                sourceInfo.remotePath,
                buffer,
                fileSize,
                progressCallback
            )
            
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }
            
            // Upload from buffer
            val destConnectionInfo = getConnectionInfo(destInfo)
            val uploadResult = sftpClient.uploadFile(
                destConnectionInfo,
                destInfo.remotePath,
                buffer.toByteArray()
            )
            
            if (uploadResult.isFailure) {
                return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }
            
            return Result.success(destination)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SFTP to SFTP copy failed: $source -> $destination")
            return Result.failure(e)
        }
    }
    
    private suspend fun downloadFromSftp(
        source: String,
        destination: String,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        try {
            val sourceInfo = parseSftpPath(source)
                ?: return Result.failure(IllegalArgumentException("Invalid source path"))

            val connectionInfo = getConnectionInfo(sourceInfo)
            val statResult = sftpClient.stat(connectionInfo, sourceInfo.remotePath)
            val fileSize = statResult.getOrNull()?.size ?: 0L

            // S0231: route writes through LocalDestinationWriter - handles MediaStore IS_PENDING
            // for public collections (Music/Movies/Pictures/DCIM/Downloads) so EACCES does not
            // happen on Android 10+ scoped storage.
            val category = destinationClassifier.classify(destination)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { error ->
                Timber.e(error, "SFTP to Local: writer.open failed for $destination")
                return Result.failure(error)
            }

            return try {
                val downloadResult = sftpClient.downloadFile(
                    connectionInfo,
                    sourceInfo.remotePath,
                    sink.outputStream,
                    fileSize,
                    progressCallback
                )

                if (downloadResult.isFailure) {
                    sink.abort()
                    return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
                }

                sink.commit().map { destination }
            } catch (e: CancellationException) {
                sink.abort()
                throw e
            } catch (e: Throwable) {
                sink.abort()
                Result.failure(e)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SFTP to Local download failed: $source -> $destination")
            return Result.failure(e)
        }
    }
    
    private suspend fun uploadToSftp(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        try {
            val destInfo = parseSftpPath(destination)
                ?: return Result.failure(IllegalArgumentException("Invalid destination path"))
            
            // Check if destination exists
            if (!overwrite) {
                val connectionInfo = getConnectionInfo(destInfo)
                val existsResult = sftpClient.exists(connectionInfo, destInfo.remotePath)
                if (existsResult.isSuccess && existsResult.getOrNull() == true) {
                    val fileName = destination.substringAfterLast('/')
                    return Result.failure(FileExistsException(fileName, destination, isMove = false))
                }
            }
            
            val sourceFile = File(source)
            if (!sourceFile.exists()) {
                return Result.failure(Exception("Source file not found"))
            }
            
            val connectionInfo = getConnectionInfo(destInfo)
            FileInputStream(sourceFile).use { inputStream ->
                val uploadResult = sftpClient.uploadFile(
                    connectionInfo,
                    destInfo.remotePath,
                    inputStream,
                    sourceFile.length(),
                    progressCallback
                )
                
                if (uploadResult.isFailure) {
                    return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
                }
            }
            
            return Result.success(destination)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Local to SFTP upload failed: $source -> $destination")
            return Result.failure(e)
        }
    }

    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("sftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an SFTP path: $path"))
            }
            
            val pathInfo = parseSftpPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse SFTP path: $path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            
            // Collect all files recursively
            val allFiles = mutableListOf<String>()
            collectSftpFiles(connectionInfo, pathInfo.remotePath, allFiles)
            val totalCount = allFiles.size
            
            var deletedCount = 0
            
            // Delete files from deepest to shallowest
            for (filePath in allFiles.sortedByDescending { it.length }) {
                progressCallback?.invoke(deletedCount, totalCount, filePath.substringAfterLast('/'))
                
                // Get attributes to determine if it's a file or directory
                val attrsResult = sftpClient.stat(connectionInfo, filePath)
                if (attrsResult.isSuccess) {
                    val attrs = attrsResult.getOrNull()
                    if (attrs?.isDirectory == true) {
                        sftpClient.deleteDirectory(connectionInfo, filePath).onSuccess { deletedCount++ }
                    } else {
                        sftpClient.deleteFile(connectionInfo, filePath).onSuccess { deletedCount++ }
                    }
                }
            }
            
            // Delete the directory itself
            sftpClient.deleteDirectory(connectionInfo, pathInfo.remotePath).onSuccess { deletedCount++ }
            
            Timber.d("SftpOperationStrategy: Deleted directory $path ($deletedCount items)")
            Result.success(deletedCount)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Delete directory failed - $path")
            Result.failure(e)
        }
    }
    
    private suspend fun collectSftpFiles(
        connectionInfo: SftpClient.SftpConnectionInfo,
        remotePath: String,
        result: MutableList<String>
    ) {
        val listResult = sftpClient.listFiles(connectionInfo, remotePath, recursive = false)
        if (listResult.isSuccess) {
            for (listing in listResult.getOrNull() ?: emptyList()) {
                if (listing.isDirectory) {
                    collectSftpFiles(connectionInfo, listing.path, result)
                }
                result.add(listing.path)
            }
        }
    }
    
    override suspend fun renameDirectory(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!oldPath.startsWith("sftp:", ignoreCase = true) || !newPath.startsWith("sftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Both paths must be SFTP"))
            }
            
            val oldInfo = parseSftpPath(oldPath)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $oldPath"))
            val newInfo = parseSftpPath(newPath)
                ?: return@withContext Result.failure(Exception("Failed to parse destination path: $newPath"))
            
            // Must be on same server
            if (oldInfo.host != newInfo.host || oldInfo.port != newInfo.port) {
                return@withContext Result.failure(IllegalArgumentException("Cannot rename across servers"))
            }
            
            val connectionInfo = getConnectionInfo(oldInfo)
            
            val renameResult = sftpClient.rename(connectionInfo, oldInfo.remotePath, newInfo.remotePath)
            if (renameResult.isSuccess) {
                Timber.d("SftpOperationStrategy: Renamed directory $oldPath -> $newPath")
                Result.success(newPath)
            } else {
                Result.failure(renameResult.exceptionOrNull() ?: Exception("Rename failed"))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Rename directory failed - $oldPath -> $newPath")
            Result.failure(e)
        }
    }
    
    override suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!source.startsWith("sftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Source must be SFTP path"))
            }
            
            val sourceInfo = parseSftpPath(source)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $source"))
            
            val sourceConnectionInfo = getConnectionInfo(sourceInfo)
            
            // Collect all files to copy (files only)
            val allFiles = mutableListOf<String>()
            collectSftpFilesOnly(sourceConnectionInfo, sourceInfo.remotePath, allFiles)
            val totalCount = allFiles.size
            
            createDirectory(destination).onFailure { return@withContext Result.failure(it) }
            
            var copiedCount = 0
            
            for (filePath in allFiles) {
                val relativePath = filePath.removePrefix(sourceInfo.remotePath).trimStart('/')
                val destFilePath = if (destination.endsWith('/')) {
                    "$destination$relativePath"
                } else {
                    "$destination/$relativePath"
                }
                
                progressCallback?.invoke(copiedCount, totalCount, filePath.substringAfterLast('/'))
                
                // Create parent directory if needed
                val parentDir = destFilePath.substringBeforeLast('/')
                if (parentDir != destination) {
                    createDirectory(parentDir)
                }
                
                // Copy file
                val fullSourcePath = "sftp://${sourceInfo.host}:${sourceInfo.port}$filePath"
                copyFile(fullSourcePath, destFilePath, overwrite = true, progressCallback = null).onSuccess {
                    copiedCount++
                }
            }
            
            Timber.d("SftpOperationStrategy: Copied directory $source -> $destination ($copiedCount files)")
            Result.success(copiedCount)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: Copy directory failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    private suspend fun collectSftpFilesOnly(
        connectionInfo: SftpClient.SftpConnectionInfo,
        remotePath: String,
        result: MutableList<String>
    ) {
        val listResult = sftpClient.listFiles(connectionInfo, remotePath, recursive = false)
        if (listResult.isSuccess) {
            for (listing in listResult.getOrNull() ?: emptyList()) {
                if (listing.isDirectory) {
                    collectSftpFilesOnly(connectionInfo, listing.path, result)
                } else {
                    result.add(listing.path)
                }
            }
        }
    }
    
    override suspend fun isDirectory(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("sftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an SFTP path: $path"))
            }
            
            val pathInfo = parseSftpPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse SFTP path: $path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            
            val attrsResult = sftpClient.stat(connectionInfo, pathInfo.remotePath)
            if (attrsResult.isSuccess) {
                Result.success(attrsResult.getOrNull()?.isDirectory == true)
            } else {
                Result.failure(attrsResult.exceptionOrNull() ?: Exception("Failed to get attributes"))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: isDirectory check failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun getDirectoryInfo(path: String): Result<com.sza.fastmediasorter.data.transfer.DirectoryInfo> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("sftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an SFTP path: $path"))
            }
            
            val pathInfo = parseSftpPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse SFTP path: $path"))
            
            val connectionInfo = getConnectionInfo(pathInfo)
            
            val attrsResult = sftpClient.stat(connectionInfo, pathInfo.remotePath)
            if (attrsResult.isFailure) {
                return@withContext Result.failure(attrsResult.exceptionOrNull() ?: Exception("Failed to get attributes"))
            }
            
            val attrs = attrsResult.getOrNull()
            if (attrs?.isDirectory != true) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
            }
            
            // Get child count (immediate children only)
            val listResult = sftpClient.listFiles(connectionInfo, pathInfo.remotePath, recursive = false)
            val childCount = listResult.getOrNull()?.size ?: 0
            
            Result.success(
                com.sza.fastmediasorter.data.transfer.DirectoryInfo(
                    path = path,
                    name = pathInfo.remotePath.substringAfterLast('/').ifEmpty { "root" },
                    childCount = childCount,
                    totalSize = 0L, // Size calculation would require additional API calls
                    lastModified = attrs.modifiedDate
                )
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpOperationStrategy: getDirectoryInfo failed - $path")
            Result.failure(e)
        }
    }
}
