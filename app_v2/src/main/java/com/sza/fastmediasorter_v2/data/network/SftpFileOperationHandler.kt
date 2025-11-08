package com.sza.fastmediasorter_v2.data.network

import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
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
 * Handler for SFTP file operations.
 * Handles copy, move, delete, rename operations for SFTP resources.
 */
@Singleton
class SftpFileOperationHandler @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsDao: NetworkCredentialsDao
) {

    /**
     * Data class to hold SFTP connection info with parsed path
     */
    private data class SftpConnectionInfoWithPath(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val remotePath: String
    )

    suspend fun executeCopy(operation: FileOperation.Copy): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("SFTP executeCopy: Starting copy of ${operation.sources.size} files to ${operation.destination.absolutePath}")
        
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("SFTP executeCopy: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val destPath = "${operation.destination.absolutePath}/${source.name}"
                
                val isSourceSftp = source.absolutePath.startsWith("sftp://")
                val isDestSftp = destPath.startsWith("sftp://")
                
                Timber.d("SFTP executeCopy: Source=${if (isSourceSftp) "SFTP" else "Local"}, Dest=${if (isDestSftp) "SFTP" else "Local"}")

                val startTime = System.currentTimeMillis()
                
                when {
                    isSourceSftp && !isDestSftp -> {
                        Timber.d("SFTP executeCopy: SFTP→Local - downloading ${source.name}")
                        downloadFromSftp(source.absolutePath, File(destPath))?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SFTP executeCopy: SUCCESS - downloaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to download from SFTP")
                            }
                            Timber.e("SFTP executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceSftp && isDestSftp -> {
                        Timber.d("SFTP executeCopy: Local→SFTP - uploading ${source.name}")
                        uploadToSftp(source, destPath)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SFTP executeCopy: SUCCESS - uploaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to upload to SFTP")
                            }
                            Timber.e("SFTP executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    isSourceSftp && isDestSftp -> {
                        Timber.d("SFTP executeCopy: SFTP→SFTP - copying ${source.name}")
                        copySftpToSftp(source.absolutePath, destPath)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SFTP executeCopy: SUCCESS - copied ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy between SFTP servers")
                            }
                            Timber.e("SFTP executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("SFTP executeCopy: $error")
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
                Timber.e(e, "SFTP executeCopy: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("SFTP executeCopy: All $successCount files copied successfully")
                FileOperationResult.Success(successCount, operation, copiedPaths)
            }
            successCount > 0 -> {
                Timber.w("SFTP executeCopy: Partial success - $successCount/${operation.sources.size} files copied")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("SFTP executeCopy: All copy operations failed")
                FileOperationResult.Failure("All copy operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeMove(operation: FileOperation.Move): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("SFTP executeMove: Starting move of ${operation.sources.size} files to ${operation.destination.absolutePath}")
        
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("SFTP executeMove: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val destPath = "${operation.destination.absolutePath}/${source.name}"
                
                val isSourceSftp = source.absolutePath.startsWith("sftp://")
                val isDestSftp = destPath.startsWith("sftp://")
                
                Timber.d("SFTP executeMove: Source=${if (isSourceSftp) "SFTP" else "Local"}, Dest=${if (isDestSftp) "SFTP" else "Local"}")

                val startTime = System.currentTimeMillis()
                
                when {
                    isSourceSftp && !isDestSftp -> {
                        Timber.d("SFTP executeMove: SFTP→Local - download+delete ${source.name}")
                        // SFTP to Local (download + delete)
                        val localFile = downloadFromSftp(source.absolutePath, File(destPath))
                        if (localFile != null) {
                            val downloadDuration = System.currentTimeMillis() - startTime
                            Timber.d("SFTP executeMove: Downloaded in ${downloadDuration}ms, attempting delete from source SFTP")
                            
                            if (deleteFromSftp(source.absolutePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("SFTP executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: ${source.absolutePath}")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Downloaded but failed to delete from SFTP")
                                }
                                Timber.e("SFTP executeMove: $error - deleting downloaded file")
                                errors.add(error)
                                // Delete downloaded file to avoid partial state
                                File(destPath).delete()
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to download from SFTP")
                            }
                            Timber.e("SFTP executeMove: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceSftp && isDestSftp -> {
                        Timber.d("SFTP executeMove: Local→SFTP - upload+delete ${source.name}")
                        // Local to SFTP (upload + delete)
                        if (uploadToSftp(source, destPath) != null) {
                            val uploadDuration = System.currentTimeMillis() - startTime
                            Timber.d("SFTP executeMove: Uploaded in ${uploadDuration}ms, attempting delete from local")
                            
                            if (source.delete()) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("SFTP executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: ${source.absolutePath}")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Uploaded but failed to delete local file")
                                }
                                Timber.e("SFTP executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to upload to SFTP")
                            }
                            Timber.e("SFTP executeMove: $error")
                            errors.add(error)
                        }
                    }
                    isSourceSftp && isDestSftp -> {
                        Timber.d("SFTP executeMove: SFTP→SFTP - copy+delete ${source.name}")
                        // SFTP to SFTP (copy + delete)
                        if (copySftpToSftp(source.absolutePath, destPath) != null) {
                            val copyDuration = System.currentTimeMillis() - startTime
                            Timber.d("SFTP executeMove: Copied in ${copyDuration}ms, attempting delete from source SFTP")
                            
                            if (deleteFromSftp(source.absolutePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("SFTP executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: ${source.absolutePath}")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Copied but failed to delete from source SFTP")
                                }
                                Timber.e("SFTP executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: ${source.absolutePath}")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy between SFTP servers")
                            }
                            Timber.e("SFTP executeMove: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("SFTP executeMove: $error")
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
                Timber.e(e, "SFTP executeMove: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("SFTP executeMove: All $successCount files moved successfully")
                FileOperationResult.Success(successCount, operation, movedPaths)
            }
            successCount > 0 -> {
                Timber.w("SFTP executeMove: Partial success - $successCount/${operation.sources.size} files moved")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("SFTP executeMove: All move operations failed")
                FileOperationResult.Failure("All move operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("SFTP executeRename: Renaming ${operation.file.name} to ${operation.newName}")
        
        try {
            val sftpPath = operation.file.absolutePath
            
            if (!sftpPath.startsWith("sftp://")) {
                Timber.e("SFTP executeRename: File is not SFTP path: $sftpPath")
                return@withContext FileOperationResult.Failure("Not an SFTP file: $sftpPath")
            }
            
            val connectionInfo = parseSftpPath(sftpPath)
            if (connectionInfo == null) {
                Timber.e("SFTP executeRename: Failed to parse SFTP path: $sftpPath")
                return@withContext FileOperationResult.Failure("Invalid SFTP path: $sftpPath")
            }
            
            Timber.d("SFTP executeRename: Parsed - host=${connectionInfo.host}:${connectionInfo.port}, remotePath=${connectionInfo.remotePath}")
            
            val connectResult = sftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
            if (connectResult.isFailure) {
                val error = "Failed to connect: ${connectResult.exceptionOrNull()?.message}"
                Timber.e("SFTP executeRename: $error")
                return@withContext FileOperationResult.Failure(error)
            }
            
            val renameResult = sftpClient.renameFile(connectionInfo.remotePath, operation.newName)
            sftpClient.disconnect()
            
            when {
                renameResult.isSuccess -> {
                    val directory = sftpPath.substringBeforeLast('/')
                    val newPath = "$directory/${operation.newName}"
                    Timber.i("SFTP executeRename: SUCCESS - renamed to $newPath")
                    FileOperationResult.Success(1, operation, listOf(newPath))
                }
                else -> {
                    val error = buildString {
                        append("${operation.file.name}")
                        append("\n  New name: ${operation.newName}")
                        append("\n  Error: ${renameResult.exceptionOrNull()?.message ?: "Rename failed"}")
                    }
                    Timber.e("SFTP executeRename: FAILED - $error")
                    FileOperationResult.Failure(error)
                }
            }
        } catch (e: Exception) {
            val error = buildString {
                append("${operation.file.name}")
                append("\n  New name: ${operation.newName}")
                append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
            }
            Timber.e(e, "SFTP executeRename: EXCEPTION - $error")
            FileOperationResult.Failure(error)
        }
    }

    suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var successCount = 0

        operation.files.forEach { file ->
            try {
                val isSftp = file.absolutePath.startsWith("sftp://")

                if (isSftp) {
                    if (deleteFromSftp(file.absolutePath)) {
                        deletedPaths.add(file.absolutePath)
                        successCount++
                    } else {
                        errors.add("${file.name}: Failed to delete from SFTP")
                    }
                } else {
                    errors.add("${file.name}: Not an SFTP file")
                }
            } catch (e: Exception) {
                val error = "${file.name}: ${e.javaClass.simpleName} - ${e.message}"
                Timber.e(e, "SFTP executeDelete: ERROR - $error")
                errors.add(error)
            }
        }

        return@withContext when {
            successCount == operation.files.size -> FileOperationResult.Success(successCount, operation, deletedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All delete operations failed")
        }
    }

    private suspend fun downloadFromSftp(sftpPath: String, localFile: File): File? {
        Timber.d("downloadFromSftp: $sftpPath → ${localFile.absolutePath}")
        
        val connectionInfo = parseSftpPath(sftpPath)
        if (connectionInfo == null) {
            Timber.e("downloadFromSftp: Failed to parse SFTP path: $sftpPath")
            return null
        }
        
        Timber.d("downloadFromSftp: Parsed - host=${connectionInfo.host}:${connectionInfo.port}")
        
        val connectResult = sftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("downloadFromSftp: Connection FAILED - ${connectResult.exceptionOrNull()?.message}")
            return null
        }
        
        val outputStream = ByteArrayOutputStream()
        val downloadResult = sftpClient.downloadFile(connectionInfo.remotePath, outputStream)
        sftpClient.disconnect()
        
        return when {
            downloadResult.isSuccess -> {
                try {
                    val bytes = outputStream.toByteArray()
                    Timber.d("downloadFromSftp: Downloaded ${bytes.size} bytes, writing to local file")
                    localFile.outputStream().use { it.write(bytes) }
                    Timber.i("downloadFromSftp: SUCCESS - ${bytes.size} bytes written to ${localFile.name}")
                    localFile
                } catch (e: Exception) {
                    Timber.e(e, "downloadFromSftp: Failed to write local file")
                    null
                }
            }
            else -> {
                Timber.e("downloadFromSftp: FAILED - ${downloadResult.exceptionOrNull()?.message}")
                null
            }
        }
    }

    private suspend fun uploadToSftp(localFile: File, sftpPath: String): String? {
        Timber.d("uploadToSftp: ${localFile.absolutePath} → $sftpPath")
        
        if (!localFile.exists()) {
            Timber.e("uploadToSftp: Local file does not exist: ${localFile.absolutePath}")
            return null
        }
        
        Timber.d("uploadToSftp: Local file size=${localFile.length()} bytes")
        
        val connectionInfo = parseSftpPath(sftpPath)
        if (connectionInfo == null) {
            Timber.e("uploadToSftp: Failed to parse SFTP path: $sftpPath")
            return null
        }
        
        Timber.d("uploadToSftp: Parsed - host=${connectionInfo.host}:${connectionInfo.port}")
        
        val connectResult = sftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("uploadToSftp: Connection FAILED - ${connectResult.exceptionOrNull()?.message}")
            return null
        }
        
        val inputStream = localFile.inputStream()
        val uploadResult = sftpClient.uploadFile(connectionInfo.remotePath, inputStream)
        sftpClient.disconnect()

        return when {
            uploadResult.isSuccess -> {
                Timber.i("uploadToSftp: SUCCESS - uploaded ${localFile.name}")
                sftpPath
            }
            else -> {
                Timber.e("uploadToSftp: FAILED - ${uploadResult.exceptionOrNull()?.message}")
                null
            }
        }
    }

    private suspend fun deleteFromSftp(sftpPath: String): Boolean {
        Timber.d("deleteFromSftp: $sftpPath")
        
        val connectionInfo = parseSftpPath(sftpPath)
        if (connectionInfo == null) {
            Timber.e("deleteFromSftp: Failed to parse SFTP path: $sftpPath")
            return false
        }
        
        Timber.d("deleteFromSftp: Parsed - host=${connectionInfo.host}:${connectionInfo.port}")

        val connectResult = sftpClient.connect(connectionInfo.host, connectionInfo.port, connectionInfo.username, connectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("deleteFromSftp: Connection FAILED - ${connectResult.exceptionOrNull()?.message}")
            return false
        }

        val deleteResult = sftpClient.deleteFile(connectionInfo.remotePath)
        sftpClient.disconnect()
        
        return when {
            deleteResult.isSuccess -> {
                Timber.i("deleteFromSftp: SUCCESS")
                true
            }
            else -> {
                Timber.e("deleteFromSftp: FAILED - ${deleteResult.exceptionOrNull()?.message}")
                false
            }
        }
    }

    private suspend fun copySftpToSftp(sourcePath: String, destPath: String): String? {
        Timber.d("copySftpToSftp: $sourcePath → $destPath")
        
        // Download to memory then upload
        val sourceConnectionInfo = parseSftpPath(sourcePath)
        if (sourceConnectionInfo == null) {
            Timber.e("copySftpToSftp: Failed to parse source SFTP path: $sourcePath")
            return null
        }
        
        Timber.d("copySftpToSftp: Source parsed - host=${sourceConnectionInfo.host}:${sourceConnectionInfo.port}")
        
        // Download from source
        val connectResult = sftpClient.connect(sourceConnectionInfo.host, sourceConnectionInfo.port, sourceConnectionInfo.username, sourceConnectionInfo.password)
        if (connectResult.isFailure) {
            Timber.e("copySftpToSftp: Source connection FAILED")
            return null
        }
        
        val buffer = ByteArrayOutputStream()
        val downloadResult = sftpClient.downloadFile(sourceConnectionInfo.remotePath, buffer)
        sftpClient.disconnect()
        
        if (downloadResult.isFailure) {
            Timber.e("copySftpToSftp: Download FAILED - ${downloadResult.exceptionOrNull()?.message}")
            return null
        }
        
        val bytes = buffer.toByteArray()
        Timber.d("copySftpToSftp: Downloaded ${bytes.size} bytes from source")
        
        // Upload to destination
        val destConnectionInfo = parseSftpPath(destPath)
        if (destConnectionInfo == null) {
            Timber.e("copySftpToSftp: Failed to parse dest SFTP path: $destPath")
            return null
        }
        
        Timber.d("copySftpToSftp: Dest parsed - host=${destConnectionInfo.host}:${destConnectionInfo.port}")
        
        val connectResult2 = sftpClient.connect(destConnectionInfo.host, destConnectionInfo.port, destConnectionInfo.username, destConnectionInfo.password)
        if (connectResult2.isFailure) {
            Timber.e("copySftpToSftp: Dest connection FAILED")
            return null
        }
        
        val inputStream = ByteArrayInputStream(bytes)
        val uploadResult = sftpClient.uploadFile(destConnectionInfo.remotePath, inputStream)
        sftpClient.disconnect()

        return when {
            uploadResult.isSuccess -> {
                Timber.i("copySftpToSftp: SUCCESS - copied ${bytes.size} bytes between SFTP servers")
                destPath
            }
            else -> {
                Timber.e("copySftpToSftp: Upload FAILED - ${uploadResult.exceptionOrNull()?.message}")
                null
            }
        }
    }

    private suspend fun parseSftpPath(path: String): SftpConnectionInfoWithPath? {
        return try {
            if (path.startsWith("sftp://")) {
                // Format: sftp://host:port/path or sftp://host/path
                val withoutProtocol = path.removePrefix("sftp://")
                
                // Split by first '/' to separate host[:port] from path
                val firstSlash = withoutProtocol.indexOf('/')
                if (firstSlash == -1) {
                    Timber.e("parseSftpPath: No path separator found in: $path")
                    return null
                }
                
                val hostPart = withoutProtocol.substring(0, firstSlash)
                val pathPart = withoutProtocol.substring(firstSlash + 1)
                
                // Parse host:port
                val (host, port) = if (hostPart.contains(':')) {
                    val parts = hostPart.split(':', limit = 2)
                    parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 22)
                } else {
                    hostPart to 22
                }
                
                Timber.d("parseSftpPath: Extracted host=$host, port=$port, remotePath=$pathPart")
                
                // Get credentials from database - assume credentialsId is encoded in host or use default
                // For now, we need to retrieve credentials by resource
                // This is a simplified version - in production, credentials should be passed or retrieved properly
                val credentials = credentialsDao.getCredentialsByHost(host)
                if (credentials == null) {
                    Timber.e("parseSftpPath: No credentials found for host: $host")
                    return null
                }
                
                SftpConnectionInfoWithPath(
                    host = host,
                    port = port,
                    username = credentials.username,
                    password = credentials.password,
                    remotePath = pathPart
                )
            } else {
                Timber.e("parseSftpPath: Path does not start with sftp://: $path")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "parseSftpPath: Exception parsing path: $path")
            null
        }
    }
}
