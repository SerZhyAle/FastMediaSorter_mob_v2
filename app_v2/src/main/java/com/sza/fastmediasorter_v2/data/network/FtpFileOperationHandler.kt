package com.sza.fastmediasorter_v2.data.network

import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.data.remote.ftp.FtpClient
import com.sza.fastmediasorter_v2.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter_v2.domain.usecase.FileOperation
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for FTP file operations.
 * Handles copy, move, delete, rename operations for FTP resources.
 */
@Singleton
class FtpFileOperationHandler @Inject constructor(
    private val ftpClient: FtpClient,
    private val credentialsDao: NetworkCredentialsDao
) {

    /**
     * Data class to hold FTP connection info with parsed path
     */
    private data class FtpConnectionInfoWithPath(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val remotePath: String
    )

    suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("FTP executeCopy: Starting copy of ${operation.sources.size} files to ${operation.destination.absolutePath}")
        
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("FTP executeCopy: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val destPath = "${operation.destination.absolutePath}/${source.name}"
                
                val isSourceFtp = source.absolutePath.startsWith("ftp://")
                val isDestFtp = destPath.startsWith("ftp://")
                
                Timber.d("FTP executeCopy: Source=${if (isSourceFtp) "FTP" else "Local"}, Dest=${if (isDestFtp) "FTP" else "Local"}")

                val startTime = System.currentTimeMillis()
                
                when {
                    isSourceFtp && !isDestFtp -> {
                        Timber.d("FTP executeCopy: FTP→Local - downloading ${source.name}")
                        downloadFromFtp(source.absolutePath, File(destPath), progressCallback)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("FTP executeCopy: SUCCESS - downloaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to download from FTP")
                            }
                            Timber.e("FTP executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceFtp && isDestFtp -> {
                        Timber.d("FTP executeCopy: Local→FTP - uploading ${source.name}")
                        uploadToFtp(source, destPath, progressCallback)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("FTP executeCopy: SUCCESS - uploaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to upload to FTP")
                            }
                            Timber.e("FTP executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    isSourceFtp && isDestFtp -> {
                        Timber.d("FTP executeCopy: FTP→FTP - copying ${source.name}")
                        copyFtpToFtp(source.absolutePath, destPath)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("FTP executeCopy: SUCCESS - copied ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy between FTP servers")
                            }
                            Timber.e("FTP executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("FTP executeCopy: $error")
                        errors.add(error)
                    }
                }
            } catch (e: Exception) {
                val error = buildString {
                    append("${source.name}")
                    append("\n  From: ${source.absolutePath}")
                    append("\n  To: ${File(operation.destination, source.name).absolutePath}")
                    append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
                }
                Timber.e(e, "FTP executeCopy: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("FTP executeCopy: All $successCount files copied successfully")
                FileOperationResult.Success(successCount, operation, copiedPaths)
            }
            successCount > 0 -> {
                Timber.w("FTP executeCopy: Partial success - $successCount/${operation.sources.size} files copied")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("FTP executeCopy: All copy operations failed")
                FileOperationResult.Failure("All copy operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("FTP executeMove: Starting move of ${operation.sources.size} files to ${operation.destination.absolutePath}")
        
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("FTP executeMove: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val destPath = "${operation.destination.absolutePath}/${source.name}"
                
                val isSourceFtp = source.absolutePath.startsWith("ftp://")
                val isDestFtp = destPath.startsWith("ftp://")
                
                Timber.d("FTP executeMove: Source=${if (isSourceFtp) "FTP" else "Local"}, Dest=${if (isDestFtp) "FTP" else "Local"}")

                val startTime = System.currentTimeMillis()
                
                when {
                    isSourceFtp && !isDestFtp -> {
                        Timber.d("FTP executeMove: FTP→Local - download+delete ${source.name}")
                        // FTP to Local (download + delete)
                        val localFile = downloadFromFtp(source.absolutePath, File(destPath), progressCallback)
                        if (localFile != null) {
                            val downloadDuration = System.currentTimeMillis() - startTime
                            Timber.d("FTP executeMove: Downloaded in ${downloadDuration}ms, attempting delete from source FTP")
                            
                            if (deleteFromFtp(source.absolutePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("FTP executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: ${source.absolutePath}")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Downloaded but failed to delete from FTP")
                                }
                                Timber.e("FTP executeMove: $error - deleting downloaded file")
                                errors.add(error)
                                // Delete downloaded file to avoid partial state
                                File(destPath).delete()
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to download from FTP")
                            }
                            Timber.e("FTP executeMove: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceFtp && isDestFtp -> {
                        Timber.d("FTP executeMove: Local→FTP - upload+delete ${source.name}")
                        // Local to FTP (upload + delete)
                        if (uploadToFtp(source, destPath, progressCallback) != null) {
                            val uploadDuration = System.currentTimeMillis() - startTime
                            Timber.d("FTP executeMove: Uploaded in ${uploadDuration}ms, attempting delete from local")
                            
                            if (source.delete()) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("FTP executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: ${source.absolutePath}")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Uploaded but failed to delete local file")
                                }
                                Timber.e("FTP executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to upload to FTP")
                            }
                            Timber.e("FTP executeMove: $error")
                            errors.add(error)
                        }
                    }
                    isSourceFtp && isDestFtp -> {
                        Timber.d("FTP executeMove: FTP→FTP - copy+delete ${source.name}")
                        // FTP to FTP (copy + delete)
                        if (copyFtpToFtp(source.absolutePath, destPath) != null) {
                            val copyDuration = System.currentTimeMillis() - startTime
                            Timber.d("FTP executeMove: Copied in ${copyDuration}ms, attempting delete from source FTP")
                            
                            if (deleteFromFtp(source.absolutePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("FTP executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: ${source.absolutePath}")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Copied but failed to delete from source FTP")
                                }
                                Timber.e("FTP executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy between FTP servers")
                            }
                            Timber.e("FTP executeMove: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("FTP executeMove: $error")
                        errors.add(error)
                    }
                }
            } catch (e: Exception) {
                val error = buildString {
                    append("${source.name}")
                    append("\n  From: ${source.absolutePath}")
                    append("\n  To: ${File(operation.destination, source.name).absolutePath}")
                    append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
                }
                Timber.e(e, "FTP executeMove: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("FTP executeMove: All $successCount files moved successfully")
                FileOperationResult.Success(successCount, operation, movedPaths)
            }
            successCount > 0 -> {
                Timber.w("FTP executeMove: Partial success - $successCount/${operation.sources.size} files moved")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("FTP executeMove: All move operations failed")
                FileOperationResult.Failure("All move operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("FTP executeRename: Renaming ${operation.file.name} to ${operation.newName}")
        
        try {
            val ftpPath = operation.file.absolutePath
            
            if (!ftpPath.startsWith("ftp://")) {
                Timber.e("FTP executeRename: File is not FTP path: $ftpPath")
                return@withContext FileOperationResult.Failure("Not an FTP file: $ftpPath")
            }
            
            val connectionInfo = parseFtpPath(ftpPath)
            if (connectionInfo == null) {
                Timber.e("FTP executeRename: Failed to parse FTP path: $ftpPath")
                return@withContext FileOperationResult.Failure("Invalid FTP path: $ftpPath")
            }
            
            Timber.d("FTP executeRename: Parsed - host=${connectionInfo.host}:${connectionInfo.port}, remotePath=${connectionInfo.remotePath}")
            
            val connectResult = ftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
            if (connectResult.isFailure) {
                val error = "Failed to connect: ${connectResult.exceptionOrNull()?.message}"
                Timber.e("FTP executeRename: $error")
                return@withContext FileOperationResult.Failure(error)
            }
            
            val renameResult = ftpClient.renameFile(connectionInfo.remotePath, operation.newName)
            ftpClient.disconnect()
            
            when {
                renameResult.isSuccess -> {
                    val directory = ftpPath.substringBeforeLast('/')
                    val newPath = "$directory/${operation.newName}"
                    Timber.i("FTP executeRename: SUCCESS - renamed to $newPath")
                    FileOperationResult.Success(1, operation, listOf(newPath))
                }
                else -> {
                    val error = buildString {
                        append("${operation.file.name}")
                        append("\n  New name: ${operation.newName}")
                        append("\n  Error: ${renameResult.exceptionOrNull()?.message ?: "Rename failed"}")
                    }
                    Timber.e("FTP executeRename: FAILED - $error")
                    FileOperationResult.Failure(error)
                }
            }
        } catch (e: Exception) {
            val error = buildString {
                append("${operation.file.name}")
                append("\n  New name: ${operation.newName}")
                append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
            }
            Timber.e(e, "FTP executeRename: EXCEPTION - $error")
            FileOperationResult.Failure(error)
        }
    }

    suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var successCount = 0

        operation.files.forEach { file ->
            try {
                val isFtp = file.absolutePath.startsWith("ftp://")

                if (isFtp) {
                    if (deleteFromFtp(file.absolutePath)) {
                        deletedPaths.add(file.absolutePath)
                        successCount++
                    } else {
                        errors.add("${file.name}: Failed to delete from FTP")
                    }
                } else {
                    errors.add("${file.name}: Not an FTP file")
                }
            } catch (e: Exception) {
                val error = "${file.name}: ${e.javaClass.simpleName} - ${e.message}"
                Timber.e(e, "FTP executeDelete: ERROR - $error")
                errors.add(error)
            }
        }

        return@withContext when {
            successCount == operation.files.size -> FileOperationResult.Success(successCount, operation, deletedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All delete operations failed")
        }
    }

    private suspend fun downloadFromFtp(
        ftpPath: String, 
        localFile: File,
        progressCallback: ByteProgressCallback? = null
    ): File? {
        Timber.d("downloadFromFtp: $ftpPath → ${localFile.absolutePath}")
        
        val connectionInfo = parseFtpPath(ftpPath)
        if (connectionInfo == null) {
            Timber.e("downloadFromFtp: Failed to parse FTP path: $ftpPath")
            return null
        }
        
        Timber.d("downloadFromFtp: Parsed - host=${connectionInfo.host}:${connectionInfo.port}")
        
        val connectResult = ftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("downloadFromFtp: Connection FAILED - ${connectResult.exceptionOrNull()?.message}")
            return null
        }
        
        val outputStream = ByteArrayOutputStream()
        val fileSize = 0L // FTP doesn't easily provide file size before download, pass 0L for now
        val downloadResult = ftpClient.downloadFile(connectionInfo.remotePath, outputStream, fileSize, progressCallback)
        ftpClient.disconnect()
        
        return when {
            downloadResult.isSuccess -> {
                try {
                    val bytes = outputStream.toByteArray()
                    Timber.d("downloadFromFtp: Downloaded ${bytes.size} bytes, writing to local file")
                    localFile.outputStream().use { it.write(bytes) }
                    Timber.i("downloadFromFtp: SUCCESS - ${bytes.size} bytes written to ${localFile.name}")
                    localFile
                } catch (e: Exception) {
                    Timber.e(e, "downloadFromFtp: Failed to write local file")
                    null
                }
            }
            else -> {
                Timber.e("downloadFromFtp: FAILED - ${downloadResult.exceptionOrNull()?.message}")
                null
            }
        }
    }

    private suspend fun uploadToFtp(
        localFile: File, 
        ftpPath: String,
        progressCallback: ByteProgressCallback? = null
    ): String? {
        Timber.d("uploadToFtp: ${localFile.absolutePath} → $ftpPath")
        
        if (!localFile.exists()) {
            Timber.e("uploadToFtp: Local file does not exist: ${localFile.absolutePath}")
            return null
        }
        
        val fileSize = localFile.length()
        Timber.d("uploadToFtp: Local file size=$fileSize bytes")
        
        val connectionInfo = parseFtpPath(ftpPath)
        if (connectionInfo == null) {
            Timber.e("uploadToFtp: Failed to parse FTP path: $ftpPath")
            return null
        }
        
        Timber.d("uploadToFtp: Parsed - host=${connectionInfo.host}:${connectionInfo.port}")
        
        val connectResult = ftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("uploadToFtp: Connection FAILED - ${connectResult.exceptionOrNull()?.message}")
            return null
        }
        
        val inputStream = localFile.inputStream()
        val uploadResult = ftpClient.uploadFile(connectionInfo.remotePath, inputStream, fileSize, progressCallback)
        ftpClient.disconnect()

        return when {
            uploadResult.isSuccess -> {
                Timber.i("uploadToFtp: SUCCESS - uploaded ${localFile.name}")
                ftpPath
            }
            else -> {
                Timber.e("uploadToFtp: FAILED - ${uploadResult.exceptionOrNull()?.message}")
                null
            }
        }
    }

    private suspend fun deleteFromFtp(ftpPath: String): Boolean {
        Timber.d("deleteFromFtp: $ftpPath")
        
        val connectionInfo = parseFtpPath(ftpPath)
        if (connectionInfo == null) {
            Timber.e("deleteFromFtp: Failed to parse FTP path: $ftpPath")
            return false
        }
        
        Timber.d("deleteFromFtp: Parsed - host=${connectionInfo.host}:${connectionInfo.port}")

        val connectResult = ftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("deleteFromFtp: Connection FAILED - ${connectResult.exceptionOrNull()?.message}")
            return false
        }

        val deleteResult = ftpClient.deleteFile(connectionInfo.remotePath)
        ftpClient.disconnect()
        
        return when {
            deleteResult.isSuccess -> {
                Timber.i("deleteFromFtp: SUCCESS")
                true
            }
            else -> {
                Timber.e("deleteFromFtp: FAILED - ${deleteResult.exceptionOrNull()?.message}")
                false
            }
        }
    }

    private suspend fun copyFtpToFtp(sourcePath: String, destPath: String): String? {
        Timber.d("copyFtpToFtp: $sourcePath → $destPath")
        
        // Download to memory then upload
        val sourceConnectionInfo = parseFtpPath(sourcePath)
        if (sourceConnectionInfo == null) {
            Timber.e("copyFtpToFtp: Failed to parse source FTP path: $sourcePath")
            return null
        }
        
        Timber.d("copyFtpToFtp: Source parsed - host=${sourceConnectionInfo.host}:${sourceConnectionInfo.port}")
        
        // Download from source
        val connectResult = ftpClient.connect(sourceConnectionInfo.host, sourceConnectionInfo.port, sourceConnectionInfo.username, sourceConnectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("copyFtpToFtp: Source connection FAILED")
            return null
        }
        
        val buffer = ByteArrayOutputStream()
        val downloadResult = ftpClient.downloadFile(sourceConnectionInfo.remotePath, buffer)
        ftpClient.disconnect()
        
        if (downloadResult.isFailure) {
            Timber.e("copyFtpToFtp: Download FAILED - ${downloadResult.exceptionOrNull()?.message}")
            return null
        }
        
        val bytes = buffer.toByteArray()
        Timber.d("copyFtpToFtp: Downloaded ${bytes.size} bytes from source")
        
        // Upload to destination
        val destConnectionInfo = parseFtpPath(destPath)
        if (destConnectionInfo == null) {
            Timber.e("copyFtpToFtp: Failed to parse dest FTP path: $destPath")
            return null
        }
        
        Timber.d("copyFtpToFtp: Dest parsed - host=${destConnectionInfo.host}:${destConnectionInfo.port}")
        
        val connectResult2 = ftpClient.connect(destConnectionInfo.host, destConnectionInfo.port, destConnectionInfo.username, destConnectionInfo.password)
        if (connectResult2.isFailure) {
            Timber.e("copyFtpToFtp: Dest connection FAILED")
            return null
        }
        
        val inputStream = ByteArrayInputStream(bytes)
        val uploadResult = ftpClient.uploadFile(destConnectionInfo.remotePath, inputStream)
        ftpClient.disconnect()

        return when {
            uploadResult.isSuccess -> {
                Timber.i("copyFtpToFtp: SUCCESS - copied ${bytes.size} bytes between FTP servers")
                destPath
            }
            else -> {
                Timber.e("copyFtpToFtp: Upload FAILED - ${uploadResult.exceptionOrNull()?.message}")
                null
            }
        }
    }

    private suspend fun parseFtpPath(path: String): FtpConnectionInfoWithPath? {
        return try {
            if (path.startsWith("ftp://")) {
                // Format: ftp://host:port/path or ftp://host/path
                val withoutProtocol = path.removePrefix("ftp://")
                
                // Split by first '/' to separate host[:port] from path
                val firstSlash = withoutProtocol.indexOf('/')
                if (firstSlash == -1) {
                    Timber.e("parseFtpPath: No path separator found in: $path")
                    return null
                }
                
                val hostPart = withoutProtocol.substring(0, firstSlash)
                val pathPart = withoutProtocol.substring(firstSlash + 1)
                
                // Parse host:port
                val (host, port) = if (hostPart.contains(':')) {
                    val parts = hostPart.split(':', limit = 2)
                    parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 21)
                } else {
                    hostPart to 21
                }
                
                Timber.d("parseFtpPath: Extracted host=$host, port=$port, remotePath=$pathPart")
                
                // Get credentials from database
                val credentials = credentialsDao.getCredentialsByHost(host)
                if (credentials == null) {
                    Timber.e("parseFtpPath: No credentials found for host: $host")
                    return null
                }
                
                FtpConnectionInfoWithPath(
                    host = host,
                    port = port,
                    username = credentials.username,
                    password = credentials.password,
                    remotePath = pathPart
                )
            } else {
                Timber.e("parseFtpPath: Path does not start with ftp://: $path")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "parseFtpPath: Exception parsing path: $path")
            null
        }
    }
}
