package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.sza.fastmediasorter.data.transfer.FileExistsException
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/** Strategy for FTP file operations. Handles ftp:// using FtpClient (stateful connection). */
class FtpOperationStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val stagingDir: com.sza.fastmediasorter.data.local.TextNoteStagingDirectory,
    private val stagingRegistry: com.sza.fastmediasorter.data.local.TextNoteStagingRegistry,
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
            // Note: File() normalizes "ftp:///" to "ftp:/" so we check for "ftp:" prefix
            val isSourceFtp = source.startsWith("ftp:", ignoreCase = true)
            val isDestFtp = destination.startsWith("ftp:", ignoreCase = true)
            
            when {
                isSourceFtp && isDestFtp -> {
                    // FTP to FTP: buffer transfer
                    copyFtpToFtp(source, destination, overwrite, progressCallback)
                }
                isSourceFtp && !isDestFtp -> {
                    // FTP to Local: download
                    downloadFromFtp(source, destination, progressCallback)
                }
                !isSourceFtp && isDestFtp -> {
                    // Local to FTP: upload
                    uploadToFtp(source, destination, overwrite, progressCallback)
                }
                else -> {
                    Result.failure(IllegalArgumentException("At least one path must be FTP"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Copy failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun moveFile(source: String, destination: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isSourceFtp = source.startsWith("ftp:", ignoreCase = true)
            val isDestFtp = destination.startsWith("ftp:", ignoreCase = true)
            
            when {
                isSourceFtp && isDestFtp -> {
                    // Try server-side move if on same server
                    val sourceInfo = parseFtpPath(source)
                    val destInfo = parseFtpPath(destination)
                    
                    if (sourceInfo != null && destInfo != null &&
                        sourceInfo.host == destInfo.host &&
                        sourceInfo.port == destInfo.port &&
                        sourceInfo.username == destInfo.username
                    ) {
                        // Server-side move (rename)
                        ensureConnected(sourceInfo)
                        val fromPath = sourceInfo.remotePath
                        val toPath = destInfo.remotePath
                        
                        // Ensure destination directory exists
                        val destParent = toPath.substringBeforeLast('/', "")
                        if (destParent.isNotEmpty()) {
                            ensureFtpDirectoryExists(destParent)
                        }
                        
                        ftpClient.moveFile(fromPath, toPath)
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
            Timber.e(e, "FtpOperationStrategy: Move failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("ftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Path must be FTP"))
            }
            
            val pathInfo = parseFtpPath(path)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid FTP path"))
            
            ensureConnected(pathInfo)
            
            // Check if directory
            val isDir = ftpClient.directoryExists(pathInfo.remotePath).getOrNull() == true
            
            if (isDir) {
                ftpClient.deleteDirectory(pathInfo.remotePath)
            } else {
                ftpClient.deleteFile(pathInfo.remotePath)
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Delete failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseFtpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid FTP path"))
            ensureConnected(pathInfo)
            
            val result = ftpClient.listFiles(pathInfo.remotePath)
            
            if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                
                // Reconstruct base URI
                val userInfo = if (pathInfo.username.isNotEmpty()) "${pathInfo.username}@" else ""
                val portInfo = if (pathInfo.port != 21) ":${pathInfo.port}" else ""
                val baseUri = "ftp://$userInfo${pathInfo.host}$portInfo"
                
                // remotePath usually starts with /
                val dirPath = pathInfo.remotePath.trimEnd('/')
                
                val paths = files.map { fileName ->
                    "$baseUri$dirPath/$fileName"
                }
                Result.success(paths)
            } else {
                Result.failure(Exception("List files failed: ${result.exceptionOrNull()?.message}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: listFiles failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("ftp:", ignoreCase = true)) return@withContext Result.success(false)
            
            val pathInfo = parseFtpPath(path) ?: return@withContext Result.success(false)
            ensureConnected(pathInfo)
            
            // FTP doesn't have a direct exists() method, try stat instead
            val files = ftpClient.listFilesWithMetadata(pathInfo.remotePath, recursive = false)
            Result.success(files.isSuccess && files.getOrNull()?.isNotEmpty() == true)
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: exists check failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
             val pathInfo = parseFtpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid FTP path"))
             ensureConnected(pathInfo)
             // Use internal helper which handles recursion
             ensureFtpDirectoryExists(pathInfo.remotePath)
             Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Create directory failed - $path")
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
            Timber.d("S0189: FtpOperationStrategy.createTextFile parent=$parentPath name=$fileName resource=$resourceId")
            val dir = stagingDir.ensureDirectory()
            val localFile = File(dir, "${resourceId}_${fileName}")
            localFile.createNewFile()
            localFile.writeText(content, Charsets.UTF_8)
            stagingRegistry.register(localFile, resourceId, parentPath, fileName)
            Result.success(localFile.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy.createTextFile failed — parent=$parentPath name=$fileName")
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
             val pathInfo = parseFtpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid FTP path"))
             ensureConnected(pathInfo)
             val inputStream = ByteArrayInputStream(content.toByteArray())
             val uploadResult = ftpClient.uploadFile(pathInfo.remotePath, inputStream, fileSize = content.length.toLong(), progressCallback = null)
             
             if (uploadResult.isSuccess) Result.success(Unit) 
             else Result.failure(Exception("Upload failed: ${uploadResult.exceptionOrNull()?.message}"))
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Write file failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
         try {
             val pathInfo = parseFtpPath(path) ?: return@withContext Result.failure(IllegalArgumentException("Invalid FTP path"))
             ensureConnected(pathInfo)
             val buffer = ByteArrayOutputStream()
             val downloadResult = ftpClient.downloadFile(pathInfo.remotePath, buffer, fileSize = 0L, progressCallback = null)
             
             if (downloadResult.isSuccess) {
                 Result.success(buffer.toString("UTF-8"))
             } else {
                 Result.failure(Exception("Download failed: ${downloadResult.exceptionOrNull()?.message}"))
             }
         } catch (e: Exception) {
             Timber.e(e, "FtpOperationStrategy: Read file failed - $path")
             Result.failure(e) 
         }
    }
    
    override fun supportsProtocol(path: String): Boolean {
        // Accept ftp: prefix (File() normalizes ftp:// to ftp:/)
        return path.startsWith("ftp:", ignoreCase = true)
    }
    
    override fun getProtocolName(): String = "FTP"

    private data class FtpPathInfo(
        val host: String,
        val port: Int,
        val username: String,
        val remotePath: String
    )
    
    private fun parseFtpPath(path: String): FtpPathInfo? {
        try {
            if (!path.startsWith("ftp:", ignoreCase = true)) return null
            
            val withoutProtocol = path.substringAfter("ftp:", "").trimStart('/')
            val userHostPart = withoutProtocol.substringBefore("/")
            val remotePath = "/" + withoutProtocol.substringAfter("/", "")

            // Supported formats:
            // - ftp://host:port/path
            // - ftp://username@host:port/path
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
                port = hostPortPart.substringAfter(":").toIntOrNull() ?: 21
            } else {
                host = hostPortPart
                port = 21
            }

            val username = usernameFromUrl ?: ""

            return FtpPathInfo(host = host, port = port, username = username, remotePath = remotePath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse FTP path: $path")
            return null
        }
    }
    
    private suspend fun ensureConnected(pathInfo: FtpPathInfo) {
        // FTP client maintains connection, but we should ensure it's connected
        // In practice, the handler will connect before using the strategy
        // This is a safety check
        val credentials = credentialsRepository.getByTypeServerAndPort("FTP", pathInfo.host, pathInfo.port)
            ?: credentialsRepository.getCredentialsByHost(pathInfo.host)
        if (credentials == null) {
            throw IllegalStateException("No credentials found for ${pathInfo.host}:${pathInfo.port}")
        }

        val usernameToUse = pathInfo.username.takeIf { it.isNotBlank() } ?: credentials.username
        
        // Connect if needed (FtpClient handles reconnection internally)
        val result = ftpClient.connect(
            host = pathInfo.host,
            port = pathInfo.port,
            username = usernameToUse,
            password = credentials.password
        )
        
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Connection failed")
        }
    }
    
    // Recursively creates each path segment on the FTP server; tolerates pre-existing dirs.
    private suspend fun ensureFtpDirectoryExists(remotePath: String) {
        val parts = remotePath.split('/').filter { it.isNotEmpty() }
        var currentPath = ""
        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            val existsResult = ftpClient.directoryExists(currentPath)
            if (existsResult.isSuccess && existsResult.getOrNull() != true) {
                Timber.d("ensureFtpDirectoryExists: Creating $currentPath")
                val createResult = ftpClient.createDirectory(currentPath)
                if (createResult.isFailure) {
                    Timber.w("ensureFtpDirectoryExists: Failed to create $currentPath: ${createResult.exceptionOrNull()?.message}")
                    // Continue anyway - parent might already exist
                }
            }
        }
    }
    
    private suspend fun copyFtpToFtp(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        try {
            val sourceInfo = parseFtpPath(source)
                ?: return Result.failure(IllegalArgumentException("Invalid source path"))
            val destInfo = parseFtpPath(destination)
                ?: return Result.failure(IllegalArgumentException("Invalid destination path"))
            
            // Check if destination exists
            if (!overwrite) {
                val existsResult = exists(destination)
                if (existsResult.isSuccess && existsResult.getOrNull() == true) {
                    val fileName = destination.substringAfterLast('/')
                    return Result.failure(FileExistsException(fileName, destination, isMove = false))
                }
            }
            
            // Download to buffer
            ensureConnected(sourceInfo)
            val buffer = ByteArrayOutputStream()
            val downloadResult = ftpClient.downloadFile(
                sourceInfo.remotePath,
                buffer,
                fileSize = 0L, // FTP doesn't provide file size easily
                progressCallback = progressCallback
            )
            
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }
            
            // Upload from buffer (may need to reconnect if different server)
            if (sourceInfo.host != destInfo.host || sourceInfo.port != destInfo.port) {
                ensureConnected(destInfo)
            }
            
            val uploadResult = ftpClient.uploadFile(
                destInfo.remotePath,
                ByteArrayInputStream(buffer.toByteArray()),
                fileSize = buffer.size().toLong(),
                progressCallback = null
            )
            
            if (uploadResult.isFailure) {
                return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }
            
            return Result.success(destination)
        } catch (e: Exception) {
            Timber.e(e, "FTP to FTP copy failed: $source -> $destination")
            return Result.failure(e)
        }
    }
    
    private suspend fun downloadFromFtp(
        source: String,
        destination: String,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        Timber.d("S0231: ftp download via LocalDestinationWriter destination=$destination")
        try {
            val sourceInfo = parseFtpPath(source)
                ?: return Result.failure(IllegalArgumentException("Invalid source path"))

            ensureConnected(sourceInfo)

            // S0231: route writes through LocalDestinationWriter for scoped-storage awareness.
            val category = destinationClassifier.classify(destination)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { error ->
                Timber.e(error, "FTP to Local: writer.open failed for $destination")
                return Result.failure(error)
            }

            return try {
                val downloadResult = ftpClient.downloadFile(
                    sourceInfo.remotePath,
                    sink.outputStream,
                    fileSize = 0L,
                    progressCallback = progressCallback
                )
                if (downloadResult.isFailure) {
                    sink.abort()
                    return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
                }
                sink.commit().map { destination }
            } catch (e: kotlinx.coroutines.CancellationException) {
                sink.abort()
                throw e
            } catch (e: Throwable) {
                sink.abort()
                Result.failure(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "FTP to Local download failed: $source -> $destination")
            return Result.failure(e)
        }
    }
    
    private suspend fun uploadToFtp(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        try {
            val destInfo = parseFtpPath(destination)
                ?: return Result.failure(IllegalArgumentException("Invalid destination path"))
            
            // Check if destination exists
            if (!overwrite) {
                val existsResult = exists(destination)
                if (existsResult.isSuccess && existsResult.getOrNull() == true) {
                    val fileName = destination.substringAfterLast('/')
                    return Result.failure(FileExistsException(fileName, destination, isMove = false))
                }
            }
            
            val sourceFile = File(source)
            if (!sourceFile.exists()) {
                return Result.failure(Exception("Source file not found"))
            }
            
            ensureConnected(destInfo)
            
            FileInputStream(sourceFile).use { inputStream ->
                val uploadResult = ftpClient.uploadFile(
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
            Timber.e(e, "Local to FTP upload failed: $source -> $destination")
            return Result.failure(e)
        }
    }

    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("ftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an FTP path: $path"))
            }
            
            val pathInfo = parseFtpPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse FTP path: $path"))
            
            ensureConnected(pathInfo)
            
            // Collect all files recursively
            val allFiles = mutableListOf<String>()
            collectFtpFiles(pathInfo.remotePath, allFiles)
            val totalCount = allFiles.size
            
            var deletedCount = 0
            
            // Delete files from deepest to shallowest
            for (filePath in allFiles.sortedByDescending { it.length }) {
                progressCallback?.invoke(deletedCount, totalCount, filePath.substringAfterLast('/'))
                
                // Try to delete as directory first (directories are listed last when sorted by depth)
                val deleteResult = ftpClient.deleteDirectory(filePath)
                if (deleteResult.isSuccess) {
                    deletedCount++
                } else {
                    // Try as file
                    ftpClient.deleteFile(filePath).onSuccess { deletedCount++ }
                }
            }
            
            // Delete the directory itself
            ftpClient.deleteDirectory(pathInfo.remotePath).onSuccess { deletedCount++ }
            
            Timber.d("FtpOperationStrategy: Deleted directory $path ($deletedCount items)")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Delete directory failed - $path")
            Result.failure(e)
        }
    }
    
    private suspend fun collectFtpFiles(
        remotePath: String,
        result: MutableList<String>
    ) {
        val listResult = ftpClient.listFilesWithMetadata(remotePath, recursive = false)
        if (listResult.isSuccess) {
            for (ftpFile in listResult.getOrNull() ?: emptyList()) {
                val fullPath = if (remotePath.isEmpty() || remotePath == "/") ftpFile.name else "$remotePath/${ftpFile.name}"
                if (ftpFile.isDirectory) {
                    collectFtpFiles(fullPath, result)
                }
                result.add(fullPath)
            }
        }
    }
    
    override suspend fun renameDirectory(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!oldPath.startsWith("ftp:", ignoreCase = true) || !newPath.startsWith("ftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Both paths must be FTP"))
            }
            
            val oldInfo = parseFtpPath(oldPath)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $oldPath"))
            val newInfo = parseFtpPath(newPath)
                ?: return@withContext Result.failure(Exception("Failed to parse destination path: $newPath"))
            
            // Must be on same server
            if (oldInfo.host != newInfo.host || oldInfo.port != newInfo.port) {
                return@withContext Result.failure(IllegalArgumentException("Cannot rename across servers"))
            }
            
            ensureConnected(oldInfo)
            
            val renameResult = ftpClient.moveFile(oldInfo.remotePath, newInfo.remotePath)
            if (renameResult.isSuccess) {
                Timber.d("FtpOperationStrategy: Renamed directory $oldPath -> $newPath")
                Result.success(newPath)
            } else {
                Result.failure(renameResult.exceptionOrNull() ?: Exception("Rename failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Rename directory failed - $oldPath -> $newPath")
            Result.failure(e)
        }
    }
    
    override suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!source.startsWith("ftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Source must be FTP path"))
            }
            
            val sourceInfo = parseFtpPath(source)
                ?: return@withContext Result.failure(Exception("Failed to parse source path: $source"))
            
            ensureConnected(sourceInfo)
            
            // Collect all files to copy (files only)
            val allFiles = mutableListOf<String>()
            collectFtpFilesOnly(sourceInfo.remotePath, allFiles)
            val totalCount = allFiles.size
            
            // Create destination directory
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
                val fullSourcePath = "ftp://${sourceInfo.host}:${sourceInfo.port}$filePath"
                copyFile(fullSourcePath, destFilePath, overwrite = true, progressCallback = null).onSuccess {
                    copiedCount++
                }
            }
            
            Timber.d("FtpOperationStrategy: Copied directory $source -> $destination ($copiedCount files)")
            Result.success(copiedCount)
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: Copy directory failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    private suspend fun collectFtpFilesOnly(
        remotePath: String,
        result: MutableList<String>
    ) {
        val listResult = ftpClient.listFilesWithMetadata(remotePath, recursive = false)
        if (listResult.isSuccess) {
            for (ftpFile in listResult.getOrNull() ?: emptyList()) {
                val fullPath = if (remotePath.isEmpty() || remotePath == "/") ftpFile.name else "$remotePath/${ftpFile.name}"
                if (ftpFile.isDirectory) {
                    collectFtpFilesOnly(fullPath, result)
                } else {
                    result.add(fullPath)
                }
            }
        }
    }
    
    override suspend fun isDirectory(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("ftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an FTP path: $path"))
            }
            
            val pathInfo = parseFtpPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse FTP path: $path"))
            
            ensureConnected(pathInfo)
            
            // List the parent directory and find the entry
            val parentPath = pathInfo.remotePath.substringBeforeLast('/', "")
            val itemName = pathInfo.remotePath.substringAfterLast('/')
            
            val listResult = ftpClient.listFilesWithMetadata(parentPath, recursive = false)
            if (listResult.isSuccess) {
                val item = listResult.getOrNull()?.find { it.name == itemName }
                Result.success(item?.isDirectory == true)
            } else {
                Result.failure(listResult.exceptionOrNull() ?: Exception("Failed to list directory"))
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: isDirectory check failed - $path")
            Result.failure(e)
        }
    }
    
    override suspend fun getDirectoryInfo(path: String): Result<com.sza.fastmediasorter.data.transfer.DirectoryInfo> = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("ftp:", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Not an FTP path: $path"))
            }
            
            val pathInfo = parseFtpPath(path)
                ?: return@withContext Result.failure(Exception("Failed to parse FTP path: $path"))
            
            ensureConnected(pathInfo)
            
            // Verify it's a directory
            val isDirResult = isDirectory(path)
            if (isDirResult.isFailure || isDirResult.getOrNull() != true) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
            }
            
            // Get child count (immediate children only)
            val listResult = ftpClient.listFilesWithMetadata(pathInfo.remotePath, recursive = false)
            val childCount = listResult.getOrNull()?.size ?: 0
            
            Result.success(
                com.sza.fastmediasorter.data.transfer.DirectoryInfo(
                    path = path,
                    name = pathInfo.remotePath.substringAfterLast('/').ifEmpty { "root" },
                    childCount = childCount,
                    totalSize = 0L, // Size calculation would require additional API calls
                    lastModified = System.currentTimeMillis() // FTP doesn't always provide accurate timestamps
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "FtpOperationStrategy: getDirectoryInfo failed - $path")
            Result.failure(e)
        }
    }
}
