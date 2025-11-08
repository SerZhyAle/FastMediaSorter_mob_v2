package com.sza.fastmediasorter_v2.data.network

import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
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
    private val credentialsDao: NetworkCredentialsDao
) {

    suspend fun executeCopy(operation: FileOperation.Copy): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEach { source ->
            try {
                val destPath = "${operation.destination.absolutePath}/${source.name}"
                
                // Determine if source or destination is SMB
                val isSourceSmb = source.absolutePath.startsWith("smb://")
                val isDestSmb = destPath.startsWith("smb://")

                when {
                    isSourceSmb && !isDestSmb -> {
                        // SMB to Local
                        downloadFromSmb(source.absolutePath, File(destPath))?.let {
                            copiedPaths.add(destPath)
                            successCount++
                        } ?: run {
                            errors.add("Failed to download ${source.name} from SMB")
                        }
                    }
                    !isSourceSmb && isDestSmb -> {
                        // Local to SMB
                        uploadToSmb(source, destPath)?.let {
                            copiedPaths.add(destPath)
                            successCount++
                        } ?: run {
                            errors.add("Failed to upload ${source.name} to SMB")
                        }
                    }
                    isSourceSmb && isDestSmb -> {
                        // SMB to SMB
                        copySmbToSmb(source.absolutePath, destPath)?.let {
                            copiedPaths.add(destPath)
                            successCount++
                        } ?: run {
                            errors.add("Failed to copy ${source.name} between SMB shares")
                        }
                    }
                    else -> {
                        errors.add("Invalid operation: both source and destination are local")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy ${source.name}")
                errors.add("Failed to copy ${source.name}: ${e.message}")
            }
        }

        return@withContext when {
            successCount == operation.sources.size -> FileOperationResult.Success(successCount, operation, copiedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All copy operations failed")
        }
    }

    suspend fun executeMove(operation: FileOperation.Move): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEach { source ->
            try {
                val destPath = "${operation.destination.absolutePath}/${source.name}"
                
                val isSourceSmb = source.absolutePath.startsWith("smb://")
                val isDestSmb = destPath.startsWith("smb://")

                when {
                    isSourceSmb && !isDestSmb -> {
                        // SMB to Local (download + delete)
                        if (downloadFromSmb(source.absolutePath, File(destPath)) != null) {
                            if (deleteFromSmb(source.absolutePath)) {
                                movedPaths.add(destPath)
                                successCount++
                            } else {
                                errors.add("Downloaded ${source.name} but failed to delete from SMB")
                                // Delete downloaded file to avoid partial state
                                File(destPath).delete()
                            }
                        } else {
                            errors.add("Failed to download ${source.name} from SMB")
                        }
                    }
                    !isSourceSmb && isDestSmb -> {
                        // Local to SMB (upload + delete)
                        if (uploadToSmb(source, destPath) != null) {
                            if (source.delete()) {
                                movedPaths.add(destPath)
                                successCount++
                            } else {
                                errors.add("Uploaded ${source.name} but failed to delete local file")
                            }
                        } else {
                            errors.add("Failed to upload ${source.name} to SMB")
                        }
                    }
                    isSourceSmb && isDestSmb -> {
                        // SMB to SMB (copy + delete)
                        if (copySmbToSmb(source.absolutePath, destPath) != null) {
                            if (deleteFromSmb(source.absolutePath)) {
                                movedPaths.add(destPath)
                                successCount++
                            } else {
                                errors.add("Copied ${source.name} but failed to delete from source SMB")
                            }
                        } else {
                            errors.add("Failed to move ${source.name} between SMB shares")
                        }
                    }
                    else -> {
                        errors.add("Invalid operation: both source and destination are local")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to move ${source.name}")
                errors.add("Failed to move ${source.name}: ${e.message}")
            }
        }

        return@withContext when {
            successCount == operation.sources.size -> FileOperationResult.Success(successCount, operation, movedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All move operations failed")
        }
    }

    suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var successCount = 0

        operation.files.forEach { file ->
            try {
                val isSmb = file.absolutePath.startsWith("smb://")

                if (isSmb) {
                    if (deleteFromSmb(file.absolutePath)) {
                        deletedPaths.add(file.absolutePath)
                        successCount++
                    } else {
                        errors.add("Failed to delete ${file.name} from SMB")
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
            else -> FileOperationResult.Failure("All delete operations failed")
        }
    }

    private suspend fun downloadFromSmb(smbPath: String, localFile: File): File? {
        val connectionInfo = parseSmbPath(smbPath) ?: return null
        val outputStream = ByteArrayOutputStream()

        return when (val result = smbClient.downloadFile(connectionInfo.connectionInfo, connectionInfo.remotePath, outputStream)) {
            is SmbClient.SmbResult.Success -> {
                localFile.outputStream().use { it.write(outputStream.toByteArray()) }
                localFile
            }
            is SmbClient.SmbResult.Error -> {
                Timber.e("Failed to download from SMB: ${result.message}")
                null
            }
        }
    }

    private suspend fun uploadToSmb(localFile: File, smbPath: String): String? {
        val connectionInfo = parseSmbPath(smbPath) ?: return null
        val inputStream = localFile.inputStream()

        return when (val result = smbClient.uploadFile(connectionInfo.connectionInfo, connectionInfo.remotePath, inputStream)) {
            is SmbClient.SmbResult.Success -> smbPath
            is SmbClient.SmbResult.Error -> {
                Timber.e("Failed to upload to SMB: ${result.message}")
                null
            }
        }
    }

    private suspend fun deleteFromSmb(smbPath: String): Boolean {
        val connectionInfo = parseSmbPath(smbPath) ?: return false

        return when (val result = smbClient.deleteFile(connectionInfo.connectionInfo, connectionInfo.remotePath)) {
            is SmbClient.SmbResult.Success -> true
            is SmbClient.SmbResult.Error -> {
                Timber.e("Failed to delete from SMB: ${result.message}")
                false
            }
        }
    }

    private suspend fun copySmbToSmb(sourcePath: String, destPath: String): String? {
        // Download to memory then upload
        val connectionInfo = parseSmbPath(sourcePath) ?: return null
        val buffer = ByteArrayOutputStream()

        when (val downloadResult = smbClient.downloadFile(connectionInfo.connectionInfo, connectionInfo.remotePath, buffer)) {
            is SmbClient.SmbResult.Success -> {
                val destConnectionInfo = parseSmbPath(destPath) ?: return null
                val inputStream = ByteArrayInputStream(buffer.toByteArray())

                return when (val uploadResult = smbClient.uploadFile(destConnectionInfo.connectionInfo, destConnectionInfo.remotePath, inputStream)) {
                    is SmbClient.SmbResult.Success -> destPath
                    is SmbClient.SmbResult.Error -> {
                        Timber.e("Failed to upload in SMB-to-SMB copy: ${uploadResult.message}")
                        null
                    }
                }
            }
            is SmbClient.SmbResult.Error -> {
                Timber.e("Failed to download in SMB-to-SMB copy: ${downloadResult.message}")
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

    private data class SmbConnectionInfoWithPath(
        val connectionInfo: SmbClient.SmbConnectionInfo,
        val remotePath: String
    )
}
