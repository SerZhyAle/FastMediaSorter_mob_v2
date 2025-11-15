package com.sza.fastmediasorter_v2.data.network

import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
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
 * Handler for SMB file operations.
 * Handles copy, move, delete operations for SMB resources.
 */
@Singleton
class SmbFileOperationHandler @Inject constructor(
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val credentialsDao: NetworkCredentialsDao
) {

    suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        // Use path instead of absolutePath to preserve SMB URL format
        // Normalize SMB path: ensure smb:/ becomes smb://
        val destinationPath = normalizeSmbPath(operation.destination.path)
        Timber.d("SMB executeCopy: Starting copy of ${operation.sources.size} files to $destinationPath")
        
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("SMB executeCopy: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                // Use source.path to preserve SMB URL format and normalize it
                val sourcePath = normalizeSmbPath(source.path)
                val destPath = "$destinationPath/${source.name}"
                
                // Determine if source or destination is SMB/SFTP
                val isSourceSmb = sourcePath.startsWith("smb://")
                val isDestSmb = destPath.startsWith("smb://")
                val isSourceSftp = sourcePath.startsWith("sftp://")
                val isDestSftp = destPath.startsWith("sftp://")
                
                Timber.d("SMB executeCopy: Source=${if (isSourceSmb) "SMB" else if (isSourceSftp) "SFTP" else "Local"}, Dest=${if (isDestSmb) "SMB" else if (isDestSftp) "SFTP" else "Local"}")
                Timber.d("SMB executeCopy: sourcePath='$sourcePath', destPath='$destPath'")

                val startTime = System.currentTimeMillis()
                
                when {
                    isSourceSmb && !isDestSmb && !isDestSftp -> {
                        Timber.d("SMB executeCopy: SMB→Local - downloading ${source.name}")
                        // SMB to Local
                        downloadFromSmb(sourcePath, File(destPath), progressCallback)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SMB executeCopy: SUCCESS - downloaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to download from SMB")
                            }
                            Timber.e("SMB executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceSmb && !isSourceSftp && isDestSmb -> {
                        Timber.d("SMB executeCopy: Local→SMB - uploading ${source.name}")
                        // Local to SMB
                        uploadToSmb(source, destPath, progressCallback)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SMB executeCopy: SUCCESS - uploaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to upload to SMB")
                            }
                            Timber.e("SMB executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    isSourceSftp && isDestSmb -> {
                        Timber.d("SMB executeCopy: SFTP→SMB - copying ${source.name} via buffer")
                        // SFTP to SMB: download to buffer → upload to SMB
                        copySftpToSmb(sourcePath, destPath)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SMB executeCopy: SUCCESS - copied ${source.name} from SFTP to SMB in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy from SFTP to SMB")
                            }
                            Timber.e("SMB executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    isSourceSmb && isDestSmb -> {
                        Timber.d("SMB executeCopy: SMB→SMB - copying ${source.name}")
                        // SMB to SMB
                        copySmbToSmb(sourcePath, destPath)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("SMB executeCopy: SUCCESS - copied ${source.name} between SMB shares in ${duration}ms")
                        } ?: run {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy between SMB shares")
                            }
                            Timber.e("SMB executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("SMB executeCopy: $error")
                        errors.add(error)
                    }
                }
            } catch (e: Exception) {
                val error = buildString {
                    append("${source.name}")
                    append("\n  From: ${source.path}")
                    append("\n  To: $destinationPath/${source.name}")
                    append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
                }
                Timber.e(e, "SMB executeCopy: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("SMB executeCopy: All $successCount files copied successfully")
                FileOperationResult.Success(successCount, operation, copiedPaths)
            }
            successCount > 0 -> {
                Timber.w("SMB executeCopy: Partial success - $successCount/${operation.sources.size} files copied. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("SMB executeCopy: All copy operations failed. Errors: $errors")
                FileOperationResult.Failure("All copy operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        // Use path instead of absolutePath to preserve SMB URL format
        // Normalize SMB path: ensure smb:/ becomes smb://
        val destinationPath = normalizeSmbPath(operation.destination.path)
        Timber.d("SMB executeMove: Starting move of ${operation.sources.size} files to $destinationPath")
        
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("SMB executeMove: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                // Use source.path to preserve SMB URL format and normalize it
                val sourcePath = normalizeSmbPath(source.path)
                val destPath = "$destinationPath/${source.name}"
                
                val isSourceSmb = sourcePath.startsWith("smb://")
                val isDestSmb = destPath.startsWith("smb://")
                
                Timber.d("SMB executeMove: Source=${if (isSourceSmb) "SMB" else "Local"}, Dest=${if (isDestSmb) "SMB" else "Local"}")
                Timber.d("SMB executeMove: sourcePath='$sourcePath', destPath='$destPath'")

                val startTime = System.currentTimeMillis()

                when {
                    isSourceSmb && !isDestSmb -> {
                        Timber.d("SMB executeMove: SMB→Local - download+delete ${source.name}")
                        // SMB to Local (download + delete)
                        if (downloadFromSmb(sourcePath, File(destPath), progressCallback) != null) {
                            val downloadDuration = System.currentTimeMillis() - startTime
                            Timber.d("SMB executeMove: Downloaded in ${downloadDuration}ms, attempting delete from SMB")
                            
                            if (deleteFromSmb(sourcePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("SMB executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: $sourcePath")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Downloaded but failed to delete from SMB")
                                }
                                Timber.e("SMB executeMove: $error - deleting downloaded file")
                                errors.add(error)
                                // Delete downloaded file to avoid partial state
                                File(destPath).delete()
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to download from SMB")
                            }
                            Timber.e("SMB executeMove: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceSmb && isDestSmb -> {
                        Timber.d("SMB executeMove: Local→SMB - upload+delete ${source.name}")
                        // Local to SMB (upload + delete)
                        if (uploadToSmb(source, destPath, progressCallback) != null) {
                            val uploadDuration = System.currentTimeMillis() - startTime
                            Timber.d("SMB executeMove: Uploaded in ${uploadDuration}ms, attempting local delete")
                            
                            if (source.delete()) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("SMB executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: $sourcePath")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Uploaded but failed to delete local file")
                                }
                                Timber.e("SMB executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to upload to SMB")
                            }
                            Timber.e("SMB executeMove: $error")
                            errors.add(error)
                        }
                    }
                    isSourceSmb && isDestSmb -> {
                        Timber.d("SMB executeMove: SMB→SMB - copy+delete ${source.name}")
                        // SMB to SMB (copy + delete)
                        if (copySmbToSmb(sourcePath, destPath) != null) {
                            val copyDuration = System.currentTimeMillis() - startTime
                            Timber.d("SMB executeMove: Copied in ${copyDuration}ms, attempting delete from source SMB")
                            
                            if (deleteFromSmb(sourcePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("SMB executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = buildString {
                                    append("${source.name}")
                                    append("\n  From: $sourcePath")
                                    append("\n  To: $destPath")
                                    append("\n  Error: Copied but failed to delete from source SMB")
                                }
                                Timber.e("SMB executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = buildString {
                                append("${source.name}")
                                append("\n  From: $sourcePath")
                                append("\n  To: $destPath")
                                append("\n  Error: Failed to copy between SMB shares")
                            }
                            Timber.e("SMB executeMove: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("SMB executeMove: $error")
                        errors.add(error)
                    }
                }
            } catch (e: Exception) {
                val error = buildString {
                    append("${source.name}")
                    append("\n  From: ${source.path}")
                    append("\n  To: $destinationPath/${source.name}")
                    append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
                }
                Timber.e(e, "SMB executeMove: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("SMB executeMove: All $successCount files moved successfully")
                FileOperationResult.Success(successCount, operation, movedPaths)
            }
            successCount > 0 -> {
                Timber.w("SMB executeMove: Partial success - $successCount/${operation.sources.size} files moved. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("SMB executeMove: All move operations failed. Errors: $errors")
                FileOperationResult.Failure("All move operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("SMB executeRename: Renaming ${operation.file.name} to ${operation.newName}")
        
        try {
            // Use path instead of absolutePath to preserve SMB URL format and normalize it
            val smbPath = normalizeSmbPath(operation.file.path)
            
            if (!smbPath.startsWith("smb://")) {
                Timber.e("SMB executeRename: File is not SMB path: $smbPath")
                return@withContext FileOperationResult.Failure("Not an SMB file: $smbPath")
            }
            
            val connectionInfo = parseSmbPath(smbPath)
            if (connectionInfo == null) {
                Timber.e("SMB executeRename: Failed to parse SMB path: $smbPath")
                return@withContext FileOperationResult.Failure("Invalid SMB path: $smbPath")
            }
            
            Timber.d("SMB executeRename: Parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, remotePath=${connectionInfo.remotePath}")
            
            when (val result = smbClient.renameFile(connectionInfo.connectionInfo, connectionInfo.remotePath, operation.newName)) {
                is SmbClient.SmbResult.Success -> {
                    // Construct new path
                    val directory = smbPath.substringBeforeLast('/')
                    val newPath = "$directory/${operation.newName}"
                    
                    Timber.i("SMB executeRename: SUCCESS - renamed to $newPath")
                    FileOperationResult.Success(1, operation, listOf(newPath))
                }
                is SmbClient.SmbResult.Error -> {
                    val error = buildString {
                        append("${operation.file.name}")
                        append("\n  New name: ${operation.newName}")
                        append("\n  Error: ${result.message}")
                    }
                    Timber.e("SMB executeRename: FAILED - $error")
                    FileOperationResult.Failure(error)
                }
            }
        } catch (e: Exception) {
            val error = buildString {
                append("${operation.file.name}")
                append("\n  New name: ${operation.newName}")
                append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
            }
            Timber.e(e, "SMB executeRename: EXCEPTION - $error")
            FileOperationResult.Failure(error)
        }
    }

    suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val trashedPaths = mutableListOf<String>() // For undo: original paths of trashed files
        var successCount = 0
        
        // For soft delete, create .trash folder on first file's parent directory (on remote server)
        var trashDirPath: String? = null
        if (operation.softDelete && operation.files.isNotEmpty()) {
            val firstFilePath = normalizeSmbPath(operation.files.first().path)
            if (firstFilePath.startsWith("smb://")) {
                // Extract parent directory from SMB path
                val parentDir = firstFilePath.substringBeforeLast('/')
                trashDirPath = "$parentDir/.trash_${System.currentTimeMillis()}"
                
                // Create trash directory on remote SMB server
                val connectionInfo = parseSmbPath(trashDirPath)
                if (connectionInfo != null) {
                    val remotePath = connectionInfo.remotePath
                    when (val result = smbClient.createDirectory(connectionInfo.connectionInfo, remotePath)) {
                        is SmbClient.SmbResult.Success -> {
                            Timber.d("SMB executeDelete: Created trash folder: $trashDirPath")
                        }
                        is SmbClient.SmbResult.Error -> {
                            Timber.e("SMB executeDelete: Failed to create trash folder: ${result.message}")
                            trashDirPath = null // Fallback to hard delete
                        }
                    }
                } else {
                    Timber.e("SMB executeDelete: Failed to parse trash path: $trashDirPath")
                    trashDirPath = null
                }
            }
        }

        operation.files.forEach { file ->
            try {
                // Use path instead of absolutePath to preserve SMB URL format and normalize it
                val filePath = normalizeSmbPath(file.path)
                val isSmb = filePath.startsWith("smb://")

                if (isSmb) {
                    if (operation.softDelete && trashDirPath != null) {
                        // Soft delete: move to trash folder using rename
                        val fileName = filePath.substringAfterLast('/')
                        val trashFilePath = "$trashDirPath/$fileName"
                        
                        val connectionInfo = parseSmbPath(filePath)
                        if (connectionInfo != null) {
                            val remotePath = connectionInfo.remotePath
                            val trashRemotePath = parseSmbPath(trashFilePath)?.remotePath
                            
                            if (trashRemotePath != null) {
                                when (val result = smbClient.renameFile(
                                    connectionInfo.connectionInfo, 
                                    remotePath, 
                                    trashRemotePath
                                )) {
                                    is SmbClient.SmbResult.Success -> {
                                        trashedPaths.add(filePath) // Store original path for undo
                                        deletedPaths.add(trashFilePath) // Store trash path
                                        successCount++
                                        Timber.d("SMB soft delete: moved ${file.name} to trash")
                                    }
                                    is SmbClient.SmbResult.Error -> {
                                        errors.add("Failed to move ${file.name} to trash: ${result.message}")
                                    }
                                }
                            } else {
                                errors.add("Failed to parse trash path for ${file.name}")
                            }
                        } else {
                            errors.add("Failed to parse SMB path for ${file.name}")
                        }
                    } else {
                        // Hard delete: permanent deletion
                        if (deleteFromSmb(filePath)) {
                            deletedPaths.add(filePath)
                            successCount++
                            Timber.d("SMB hard delete: permanently deleted ${file.name}")
                        } else {
                            errors.add("Failed to delete ${file.name} from SMB")
                        }
                    }
                } else {
                    errors.add("Invalid operation: file is local")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete ${file.name}")
                errors.add("Delete error for ${file.name}: ${e.message}")
            }
        }
        
        // Return trash directory path in result for undo restoration
        val resultPaths = if (operation.softDelete && trashDirPath != null) {
            listOf(trashDirPath) + trashedPaths
        } else {
            deletedPaths
        }

        return@withContext when {
            successCount == operation.files.size -> FileOperationResult.Success(successCount, operation, resultPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All delete operations failed")
        }
    }

    private suspend fun downloadFromSmb(
        smbPath: String,
        localFile: File,
        progressCallback: ByteProgressCallback? = null
    ): File? {
        Timber.d("downloadFromSmb: $smbPath → ${localFile.absolutePath}")
        
        val connectionInfo = parseSmbPath(smbPath)
        if (connectionInfo == null) {
            Timber.e("downloadFromSmb: Failed to parse SMB path: $smbPath")
            return null
        }
        
        Timber.d("downloadFromSmb: Parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, path=${connectionInfo.remotePath}")
        
        // File size is unknown for SMB downloads, pass 0L
        // Progress will still work, just without percentage
        val outputStream = ByteArrayOutputStream()

        return when (val result = smbClient.downloadFile(
            connectionInfo.connectionInfo,
            connectionInfo.remotePath,
            outputStream,
            fileSize = 0L,
            progressCallback = progressCallback
        )) {
            is SmbClient.SmbResult.Success -> {
                try {
                    val bytes = outputStream.toByteArray()
                    Timber.d("downloadFromSmb: Downloaded ${bytes.size} bytes, writing to local file")
                    localFile.outputStream().use { it.write(bytes) }
                    Timber.i("downloadFromSmb: SUCCESS - ${bytes.size} bytes written to ${localFile.name}")
                    localFile
                } catch (e: Exception) {
                    Timber.e(e, "downloadFromSmb: Failed to write local file")
                    null
                }
            }
            is SmbClient.SmbResult.Error -> {
                Timber.e("downloadFromSmb: FAILED - ${result.message}")
                null
            }
        }
    }

    private suspend fun uploadToSmb(
        localFile: File,
        smbPath: String,
        progressCallback: ByteProgressCallback? = null
    ): String? {
        Timber.d("uploadToSmb: ${localFile.absolutePath} → $smbPath")
        
        if (!localFile.exists()) {
            Timber.e("uploadToSmb: Local file does not exist: ${localFile.absolutePath}")
            return null
        }
        
        val fileSize = localFile.length()
        Timber.d("uploadToSmb: Local file size=$fileSize bytes")
        
        val connectionInfo = parseSmbPath(smbPath)
        if (connectionInfo == null) {
            Timber.e("uploadToSmb: Failed to parse SMB path: $smbPath")
            return null
        }
        
        Timber.d("uploadToSmb: Parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, path=${connectionInfo.remotePath}")
        
        val inputStream = localFile.inputStream()

        return when (val result = smbClient.uploadFile(
            connectionInfo.connectionInfo,
            connectionInfo.remotePath,
            inputStream,
            fileSize,
            progressCallback
        )) {
            is SmbClient.SmbResult.Success -> {
                Timber.i("uploadToSmb: SUCCESS - uploaded ${localFile.name}")
                smbPath
            }
            is SmbClient.SmbResult.Error -> {
                Timber.e("uploadToSmb: FAILED - ${result.message}")
                null
            }
        }
    }

    private suspend fun deleteFromSmb(smbPath: String): Boolean {
        Timber.d("deleteFromSmb: $smbPath")
        
        val connectionInfo = parseSmbPath(smbPath)
        if (connectionInfo == null) {
            Timber.e("deleteFromSmb: Failed to parse SMB path: $smbPath")
            return false
        }
        
        Timber.d("deleteFromSmb: Parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}, path=${connectionInfo.remotePath}")

        return when (val result = smbClient.deleteFile(connectionInfo.connectionInfo, connectionInfo.remotePath)) {
            is SmbClient.SmbResult.Success -> {
                Timber.i("deleteFromSmb: SUCCESS")
                true
            }
            is SmbClient.SmbResult.Error -> {
                Timber.e("deleteFromSmb: FAILED - ${result.message}")
                false
            }
        }
    }

    private suspend fun copySmbToSmb(sourcePath: String, destPath: String): String? {
        Timber.d("copySmbToSmb: $sourcePath → $destPath")
        
        // Download to memory then upload
        val connectionInfo = parseSmbPath(sourcePath)
        if (connectionInfo == null) {
            Timber.e("copySmbToSmb: Failed to parse source SMB path: $sourcePath")
            return null
        }
        
        Timber.d("copySmbToSmb: Source parsed - server=${connectionInfo.connectionInfo.server}, share=${connectionInfo.connectionInfo.shareName}")
        
        val buffer = ByteArrayOutputStream()

        when (val downloadResult = smbClient.downloadFile(connectionInfo.connectionInfo, connectionInfo.remotePath, buffer)) {
            is SmbClient.SmbResult.Success -> {
                val bytes = buffer.toByteArray()
                Timber.d("copySmbToSmb: Downloaded ${bytes.size} bytes from source")
                
                val destConnectionInfo = parseSmbPath(destPath)
                if (destConnectionInfo == null) {
                    Timber.e("copySmbToSmb: Failed to parse dest SMB path: $destPath")
                    return null
                }
                
                Timber.d("copySmbToSmb: Dest parsed - server=${destConnectionInfo.connectionInfo.server}, share=${destConnectionInfo.connectionInfo.shareName}")
                
                val inputStream = ByteArrayInputStream(bytes)

                return when (val uploadResult = smbClient.uploadFile(destConnectionInfo.connectionInfo, destConnectionInfo.remotePath, inputStream)) {
                    is SmbClient.SmbResult.Success -> {
                        Timber.i("copySmbToSmb: SUCCESS - copied ${bytes.size} bytes between SMB shares")
                        destPath
                    }
                    is SmbClient.SmbResult.Error -> {
                        Timber.e("copySmbToSmb: Upload FAILED - ${uploadResult.message}")
                        null
                    }
                }
            }
            is SmbClient.SmbResult.Error -> {
                Timber.e("copySmbToSmb: Download FAILED - ${downloadResult.message}")
                return null
            }
        }
    }

    private suspend fun parseSmbPath(path: String): SmbConnectionInfoWithPath? {
        return try {
            if (path.startsWith("smb://")) {
                val withoutProtocol = path.removePrefix("smb://")
                val parts = withoutProtocol.split("/", limit = 2)
                
                if (parts.isEmpty()) return null
                
                val serverPart = parts[0]
                val pathPart = if (parts.size > 1) parts[1] else ""
                
                val serverPort = serverPart.split(":", limit = 2)
                val server = serverPort[0]
                val port = if (serverPort.size > 1) serverPort[1].toIntOrNull() ?: 445 else 445
                
                val pathParts = pathPart.split("/", limit = 2)
                val share = if (pathParts.isNotEmpty()) pathParts[0] else ""
                val remotePath = if (pathParts.size > 1) pathParts[1] else ""
                
                if (server.isEmpty() || share.isEmpty()) return null
                
                val credentials = credentialsDao.getByServerAndShare(server, share)
                
                SmbConnectionInfoWithPath(
                    connectionInfo = SmbClient.SmbConnectionInfo(
                        server = server,
                        shareName = share,
                        username = credentials?.username ?: "",
                        password = credentials?.password ?: "",
                        domain = credentials?.domain ?: "",
                        port = port
                    ),
                    remotePath = remotePath
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SMB path: $path")
            null
        }
    }

    /**
     * Copy file from SFTP to SMB via in-memory buffer
     * @param sftpPath Source SFTP path (sftp://host:port/path/file)
     * @param smbPath Destination SMB path (smb://server/share/path/file)
     * @return SMB path on success, null on failure
     */
    private suspend fun copySftpToSmb(sftpPath: String, smbPath: String): String? {
        Timber.d("copySftpToSmb: $sftpPath → $smbPath")
        
        try {
            // Parse SFTP path: sftp://host:port/remotePath
            if (!sftpPath.startsWith("sftp://")) {
                Timber.e("copySftpToSmb: Invalid SFTP path format: $sftpPath")
                return null
            }
            
            val sftpParts = sftpPath.removePrefix("sftp://").split("/", limit = 2)
            if (sftpParts.isEmpty()) {
                Timber.e("copySftpToSmb: Failed to parse SFTP host from path")
                return null
            }
            
            val hostPort = sftpParts[0].split(":", limit = 2)
            val host = hostPort[0]
            val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 22 else 22
            val remotePath = if (sftpParts.size > 1) "/" + sftpParts[1] else "/"
            
            Timber.d("copySftpToSmb: SFTP parsed - host=$host, port=$port, path=$remotePath")
            
            // Get SFTP credentials
            val sftpCredentials = credentialsDao.getByTypeServerAndPort("SFTP", host, port)
            if (sftpCredentials == null) {
                Timber.e("copySftpToSmb: No SFTP credentials found for $host:$port")
                return null
            }
            
            // Connect to SFTP and download to buffer
            val connectResult = sftpClient.connect(host, port, sftpCredentials.username, sftpCredentials.password)
            if (connectResult.isFailure) {
                Timber.e("copySftpToSmb: SFTP connection failed: ${connectResult.exceptionOrNull()?.message}")
                return null
            }
            
            Timber.d("copySftpToSmb: Downloading from SFTP...")
            val downloadResult = sftpClient.readFileBytes(remotePath)
            sftpClient.disconnect()
            
            if (downloadResult.isFailure) {
                Timber.e("copySftpToSmb: SFTP download failed: ${downloadResult.exceptionOrNull()?.message}")
                return null
            }
            
            val fileBytes = downloadResult.getOrNull()
            if (fileBytes == null || fileBytes.isEmpty()) {
                Timber.e("copySftpToSmb: Downloaded file is empty")
                return null
            }
            
            Timber.d("copySftpToSmb: Downloaded ${fileBytes.size} bytes from SFTP, uploading to SMB...")
            
            // Upload to SMB
            val smbConnectionInfo = parseSmbPath(smbPath)
            if (smbConnectionInfo == null) {
                Timber.e("copySftpToSmb: Failed to parse SMB path: $smbPath")
                return null
            }
            
            val inputStream = ByteArrayInputStream(fileBytes)
            val uploadResult = smbClient.uploadFile(smbConnectionInfo.connectionInfo, smbConnectionInfo.remotePath, inputStream)
            
            return when (uploadResult) {
                is SmbClient.SmbResult.Success -> {
                    Timber.i("copySftpToSmb: SUCCESS - ${fileBytes.size} bytes copied from SFTP to SMB")
                    smbPath
                }
                is SmbClient.SmbResult.Error -> {
                    Timber.e("copySftpToSmb: SMB upload failed: ${uploadResult.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "copySftpToSmb: Exception during copy")
            return null
        }
    }

    private data class SmbConnectionInfoWithPath(
        val connectionInfo: SmbClient.SmbConnectionInfo,
        val remotePath: String
    )

    /**
     * Normalize SMB path to ensure proper format with double slashes.
     * Converts "smb:/server/share" to "smb://server/share"
     * Also handles "sftp:/" -> "sftp://"
     */
    private fun normalizeSmbPath(path: String): String {
        return when {
            path.startsWith("smb:/") && !path.startsWith("smb://") -> {
                path.replaceFirst("smb:/", "smb://")
            }
            path.startsWith("sftp:/") && !path.startsWith("sftp://") -> {
                path.replaceFirst("sftp:/", "sftp://")
            }
            else -> path
        }
    }
}
