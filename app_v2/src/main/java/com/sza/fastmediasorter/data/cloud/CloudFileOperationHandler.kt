package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for cloud storage file operations.
 * Handles copy, move, delete operations for Google Drive resources.
 * 
 * Cloud path format: cloud://google_drive/<folderId>/file.ext
 * 
 * Operations:
 * - Cloud→Local: Download file from cloud to local storage
 * - Local→Cloud: Upload file from local storage to cloud
 * - Cloud→Cloud: Copy/move between cloud folders (same or different providers)
 */
@Singleton
class CloudFileOperationHandler @Inject constructor(
    private val googleDriveClient: GoogleDriveClient,
    private val resourceDao: ResourceDao
) {

    suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val destinationPath = operation.destination.path
        Timber.d("Cloud executeCopy: Starting copy of ${operation.sources.size} files to $destinationPath")
        
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("Cloud executeCopy: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val sourcePath = source.path
                val destPath = "${destinationPath}/${source.name}"
                
                val isSourceCloud = sourcePath.startsWith("cloud://")
                val isDestCloud = destPath.startsWith("cloud://")
                
                Timber.d("Cloud executeCopy: Source=${if (isSourceCloud) "Cloud" else "Local"}, Dest=${if (isDestCloud) "Cloud" else "Local"}")
                Timber.d("Cloud executeCopy: sourcePath='$sourcePath', destPath='$destPath'")

                val startTime = System.currentTimeMillis()
                
                when {
                    isSourceCloud && !isDestCloud -> {
                        Timber.d("Cloud executeCopy: Cloud→Local - downloading ${source.name}")
                        // Cloud to Local
                        downloadFromCloud(sourcePath, File(destPath), progressCallback)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("Cloud executeCopy: SUCCESS - downloaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Failed to download from cloud"
                            Timber.e("Cloud executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceCloud && isDestCloud -> {
                        Timber.d("Cloud executeCopy: Local→Cloud - uploading ${source.name}")
                        // Local to Cloud
                        uploadToCloud(source, destPath, progressCallback)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("Cloud executeCopy: SUCCESS - uploaded ${source.name} in ${duration}ms")
                        } ?: run {
                            val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Failed to upload to cloud"
                            Timber.e("Cloud executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    isSourceCloud && isDestCloud -> {
                        Timber.d("Cloud executeCopy: Cloud→Cloud - copying ${source.name}")
                        // Cloud to Cloud
                        copyCloudToCloud(sourcePath, destPath)?.let {
                            val duration = System.currentTimeMillis() - startTime
                            copiedPaths.add(destPath)
                            successCount++
                            Timber.i("Cloud executeCopy: SUCCESS - copied ${source.name} between cloud folders in ${duration}ms")
                        } ?: run {
                            val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Failed to copy between cloud folders"
                            Timber.e("Cloud executeCopy: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("Cloud executeCopy: $error")
                        errors.add(error)
                    }
                }
            } catch (e: Exception) {
                val error = "${source.name}\n  From: ${source.path}\n  To: $destinationPath/${source.name}\n  Error: ${e.javaClass.simpleName} - ${e.message}"
                Timber.e(e, "Cloud executeCopy: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("Cloud executeCopy: All $successCount files copied successfully")
                FileOperationResult.Success(successCount, operation, copiedPaths)
            }
            successCount > 0 -> {
                Timber.w("Cloud executeCopy: Partial success - $successCount/${operation.sources.size} files copied. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("Cloud executeCopy: All copy operations failed. Errors: $errors")
                FileOperationResult.Failure("All copy operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val destinationPath = operation.destination.path
        Timber.d("Cloud executeMove: Starting move of ${operation.sources.size} files to $destinationPath")
        
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEachIndexed { index, source ->
            Timber.d("Cloud executeMove: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val sourcePath = source.path
                val destPath = "$destinationPath/${source.name}"
                
                val isSourceCloud = sourcePath.startsWith("cloud://")
                val isDestCloud = destPath.startsWith("cloud://")
                
                Timber.d("Cloud executeMove: Source=${if (isSourceCloud) "Cloud" else "Local"}, Dest=${if (isDestCloud) "Cloud" else "Local"}")
                Timber.d("Cloud executeMove: sourcePath='$sourcePath', destPath='$destPath'")

                val startTime = System.currentTimeMillis()

                when {
                    isSourceCloud && !isDestCloud -> {
                        Timber.d("Cloud executeMove: Cloud→Local - download+delete ${source.name}")
                        // Cloud to Local (download + delete)
                        if (downloadFromCloud(sourcePath, File(destPath), progressCallback) != null) {
                            val downloadDuration = System.currentTimeMillis() - startTime
                            Timber.d("Cloud executeMove: Downloaded in ${downloadDuration}ms, attempting delete from cloud")
                            
                            if (deleteFromCloud(sourcePath)) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("Cloud executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Downloaded but failed to delete from cloud"
                                Timber.e("Cloud executeMove: $error - deleting downloaded file")
                                errors.add(error)
                                // Delete downloaded file to avoid partial state
                                File(destPath).delete()
                            }
                        } else {
                            val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Failed to download from cloud"
                            Timber.e("Cloud executeMove: $error")
                            errors.add(error)
                        }
                    }
                    !isSourceCloud && isDestCloud -> {
                        Timber.d("Cloud executeMove: Local→Cloud - upload+delete ${source.name}")
                        // Local to Cloud (upload + delete)
                        if (uploadToCloud(source, destPath, progressCallback) != null) {
                            val uploadDuration = System.currentTimeMillis() - startTime
                            Timber.d("Cloud executeMove: Uploaded in ${uploadDuration}ms, attempting local delete")
                            
                            if (source.delete()) {
                                val totalDuration = System.currentTimeMillis() - startTime
                                movedPaths.add(destPath)
                                successCount++
                                Timber.i("Cloud executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                            } else {
                                val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Uploaded but failed to delete local file"
                                Timber.e("Cloud executeMove: $error")
                                errors.add(error)
                            }
                        } else {
                            val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Failed to upload to cloud"
                            Timber.e("Cloud executeMove: $error")
                            errors.add(error)
                        }
                    }
                    isSourceCloud && isDestCloud -> {
                        Timber.d("Cloud executeMove: Cloud→Cloud - move ${source.name}")
                        // Cloud to Cloud (use native move if possible)
                        moveCloudToCloud(sourcePath, destPath)?.let {
                            val totalDuration = System.currentTimeMillis() - startTime
                            movedPaths.add(destPath)
                            successCount++
                            Timber.i("Cloud executeMove: SUCCESS - moved ${source.name} in ${totalDuration}ms")
                        } ?: run {
                            val error = "${source.name}\n  From: $sourcePath\n  To: $destPath\n  Error: Failed to move between cloud folders"
                            Timber.e("Cloud executeMove: $error")
                            errors.add(error)
                        }
                    }
                    else -> {
                        val error = "Invalid operation: both source and destination are local"
                        Timber.e("Cloud executeMove: $error")
                        errors.add(error)
                    }
                }
            } catch (e: Exception) {
                val error = "${source.name}\n  From: ${source.path}\n  To: $destinationPath/${source.name}\n  Error: ${e.javaClass.simpleName} - ${e.message}"
                Timber.e(e, "Cloud executeMove: ERROR - $error")
                errors.add(error)
            }
        }

        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("Cloud executeMove: All $successCount files moved successfully")
                FileOperationResult.Success(successCount, operation, movedPaths)
            }
            successCount > 0 -> {
                Timber.w("Cloud executeMove: Partial success - $successCount/${operation.sources.size} files moved. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("Cloud executeMove: All move operations failed. Errors: $errors")
                FileOperationResult.Failure("All move operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return@withContext result
    }

    suspend fun executeRename(operation: FileOperation.Rename): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("Cloud executeRename: Renaming ${operation.file.name} to ${operation.newName}")
        
        try {
            val cloudPath = operation.file.path
            
            if (!cloudPath.startsWith("cloud://")) {
                Timber.e("Cloud executeRename: File is not cloud path: $cloudPath")
                return@withContext FileOperationResult.Failure("Not a cloud file: $cloudPath")
            }
            
            val pathInfo = parseCloudPath(cloudPath)
            if (pathInfo == null) {
                Timber.e("Cloud executeRename: Failed to parse cloud path: $cloudPath")
                return@withContext FileOperationResult.Failure("Invalid cloud path: $cloudPath")
            }
            
            Timber.d("Cloud executeRename: Parsed - provider=${pathInfo.provider}, fileId=${pathInfo.fileId}")
            
            val client = getCloudClient(pathInfo.provider)
            if (client == null) {
                Timber.e("Cloud executeRename: No client for provider ${pathInfo.provider}")
                return@withContext FileOperationResult.Failure("Unsupported cloud provider: ${pathInfo.provider}")
            }
            
            when (val result = client.renameFile(pathInfo.fileId, operation.newName)) {
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
            }
        } catch (e: Exception) {
            val error = "${operation.file.name}\n  New name: ${operation.newName}\n  Error: ${e.javaClass.simpleName} - ${e.message}"
            Timber.e(e, "Cloud executeRename: EXCEPTION - $error")
            FileOperationResult.Failure(error)
        }
    }

    suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var successCount = 0
        
        // Cloud services use native trash, no need for manual .trash folder
        operation.files.forEach { file ->
            try {
                val filePath = file.path
                val isCloud = filePath.startsWith("cloud://")

                if (isCloud) {
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
            else -> FileOperationResult.Failure("All delete operations failed")
        }
    }

    /**
     * Download file from cloud to local storage
     * @param cloudPath Cloud path (cloud://provider/folderId/fileId/filename)
     * @param localFile Destination local file
     * @return Local file on success, null on failure
     */
    private suspend fun downloadFromCloud(
        cloudPath: String,
        localFile: File,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): File? {
        Timber.d("downloadFromCloud: $cloudPath → ${localFile.absolutePath}")
        
        val pathInfo = parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("downloadFromCloud: Failed to parse cloud path: $cloudPath")
            return null
        }
        
        val client = getCloudClient(pathInfo.provider)
        if (client == null) {
            Timber.e("downloadFromCloud: No client for provider ${pathInfo.provider}")
            return null
        }
        
        val outputStream = ByteArrayOutputStream()

        return when (val result = client.downloadFile(pathInfo.fileId, outputStream, null)) {
            is CloudResult.Success -> {
                try {
                    val bytes = outputStream.toByteArray()
                    Timber.d("downloadFromCloud: Downloaded ${bytes.size} bytes, writing to local file")
                    localFile.parentFile?.mkdirs()
                    localFile.outputStream().use { it.write(bytes) }
                    Timber.i("downloadFromCloud: SUCCESS - ${bytes.size} bytes written to ${localFile.name}")
                    localFile
                } catch (e: Exception) {
                    Timber.e(e, "downloadFromCloud: Failed to write local file")
                    null
                }
            }
            is CloudResult.Error -> {
                Timber.e("downloadFromCloud: FAILED - ${result.message}")
                null
            }
        }
    }

    /**
     * Upload file from local storage to cloud
     * @param localFile Source local file
     * @param cloudPath Destination cloud path (cloud://provider/parentFolderId/filename)
     * @return Cloud path on success, null on failure
     */
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
        Timber.d("uploadToCloud: Local file size=$fileSize bytes")
        
        val pathInfo = parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("uploadToCloud: Failed to parse cloud path: $cloudPath")
            return null
        }
        
        val client = getCloudClient(pathInfo.provider)
        if (client == null) {
            Timber.e("uploadToCloud: No client for provider ${pathInfo.provider}")
            return null
        }
        
        val inputStream = localFile.inputStream()
        val mimeType = getMimeType(localFile.name)

        return when (val result = client.uploadFile(inputStream, localFile.name, mimeType, pathInfo.folderId, null)) {
            is CloudResult.Success -> {
                Timber.i("uploadToCloud: SUCCESS - uploaded ${localFile.name}")
                "cloud://${pathInfo.provider}/${result.data.path}"
            }
            is CloudResult.Error -> {
                Timber.e("uploadToCloud: FAILED - ${result.message}")
                null
            }
        }
    }

    /**
     * Delete file from cloud storage
     */
    private suspend fun deleteFromCloud(cloudPath: String): Boolean {
        Timber.d("deleteFromCloud: $cloudPath")
        
        val pathInfo = parseCloudPath(cloudPath)
        if (pathInfo == null) {
            Timber.e("deleteFromCloud: Failed to parse cloud path: $cloudPath")
            return false
        }
        
        val client = getCloudClient(pathInfo.provider)
        if (client == null) {
            Timber.e("deleteFromCloud: No client for provider ${pathInfo.provider}")
            return false
        }

        return when (val result = client.deleteFile(pathInfo.fileId)) {
            is CloudResult.Success -> {
                Timber.i("deleteFromCloud: SUCCESS")
                true
            }
            is CloudResult.Error -> {
                Timber.e("deleteFromCloud: FAILED - ${result.message}")
                false
            }
        }
    }

    /**
     * Copy file between cloud folders (same or different providers)
     */
    private suspend fun copyCloudToCloud(sourcePath: String, destPath: String): String? {
        Timber.d("copyCloudToCloud: $sourcePath → $destPath")
        
        val sourceInfo = parseCloudPath(sourcePath)
        val destInfo = parseCloudPath(destPath)
        
        if (sourceInfo == null || destInfo == null) {
            Timber.e("copyCloudToCloud: Failed to parse paths")
            return null
        }
        
        // If same provider, use native copy
        if (sourceInfo.provider == destInfo.provider) {
            val client = getCloudClient(sourceInfo.provider)
            if (client == null) {
                Timber.e("copyCloudToCloud: No client for provider ${sourceInfo.provider}")
                return null
            }
            
            val fileName = sourcePath.substringAfterLast('/')
            when (val result = client.copyFile(sourceInfo.fileId, destInfo.folderId ?: "root", fileName)) {
                is CloudResult.Success -> {
                    Timber.i("copyCloudToCloud: SUCCESS - native copy")
                    return "cloud://${destInfo.provider}/${result.data.path}"
                }
                is CloudResult.Error -> {
                    Timber.e("copyCloudToCloud: Native copy FAILED - ${result.message}")
                    return null
                }
            }
        }
        
        // Cross-provider: download to buffer, then upload
        Timber.d("copyCloudToCloud: Cross-provider copy via buffer")
        val sourceClient = getCloudClient(sourceInfo.provider) ?: return null
        val destClient = getCloudClient(destInfo.provider) ?: return null
        
        val buffer = ByteArrayOutputStream()
        when (val downloadResult = sourceClient.downloadFile(sourceInfo.fileId, buffer, null)) {
            is CloudResult.Success -> {
                val bytes = buffer.toByteArray()
                Timber.d("copyCloudToCloud: Downloaded ${bytes.size} bytes from source")
                
                val fileName = sourcePath.substringAfterLast('/')
                val mimeType = getMimeType(fileName)
                val inputStream = ByteArrayInputStream(bytes)

                return when (val uploadResult = destClient.uploadFile(inputStream, fileName, mimeType, destInfo.folderId, null)) {
                    is CloudResult.Success -> {
                        Timber.i("copyCloudToCloud: SUCCESS - ${bytes.size} bytes copied between providers")
                        "cloud://${destInfo.provider}/${uploadResult.data.path}"
                    }
                    is CloudResult.Error -> {
                        Timber.e("copyCloudToCloud: Upload FAILED - ${uploadResult.message}")
                        null
                    }
                }
            }
            is CloudResult.Error -> {
                Timber.e("copyCloudToCloud: Download FAILED - ${downloadResult.message}")
                return null
            }
        }
    }

    /**
     * Move file between cloud folders using native move API
     */
    private suspend fun moveCloudToCloud(sourcePath: String, destPath: String): String? {
        Timber.d("moveCloudToCloud: $sourcePath → $destPath")
        
        val sourceInfo = parseCloudPath(sourcePath)
        val destInfo = parseCloudPath(destPath)
        
        if (sourceInfo == null || destInfo == null) {
            Timber.e("moveCloudToCloud: Failed to parse paths")
            return null
        }
        
        // Only same provider supports native move
        if (sourceInfo.provider != destInfo.provider) {
            Timber.w("moveCloudToCloud: Cross-provider move not supported, fallback to copy+delete")
            val copied = copyCloudToCloud(sourcePath, destPath)
            return if (copied != null && deleteFromCloud(sourcePath)) {
                copied
            } else {
                null
            }
        }
        
        val client = getCloudClient(sourceInfo.provider)
        if (client == null) {
            Timber.e("moveCloudToCloud: No client for provider ${sourceInfo.provider}")
            return null
        }
        
        return when (val result = client.moveFile(sourceInfo.fileId, destInfo.folderId ?: "root")) {
            is CloudResult.Success -> {
                Timber.i("moveCloudToCloud: SUCCESS - native move")
                "cloud://${destInfo.provider}/${result.data.path}"
            }
            is CloudResult.Error -> {
                Timber.e("moveCloudToCloud: FAILED - ${result.message}")
                null
            }
        }
    }

    /**
     * Parse cloud path to extract provider, folderId, and fileId
     * Format: cloud://provider/folderId/fileId or cloud://provider/folderId/path/to/file.ext
     */
    private fun parseCloudPath(path: String): CloudPathInfo? {
        try {
            if (!path.startsWith("cloud://")) return null
            
            val withoutProtocol = path.removePrefix("cloud://")
            val parts = withoutProtocol.split("/", limit = 3)
            
            if (parts.isEmpty()) return null
            
            val provider = CloudProvider.valueOf(parts[0].uppercase())
            val folderId = if (parts.size > 1) parts[1] else null
            val fileId = if (parts.size > 2) parts[2].substringBefore('/') else folderId
            
            return CloudPathInfo(provider, folderId, fileId ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error parsing cloud path: $path")
            return null
        }
    }

    private fun getCloudClient(provider: CloudProvider): CloudStorageClient? {
        return when (provider) {
            CloudProvider.GOOGLE_DRIVE -> googleDriveClient
            // Add other providers here when implemented
            else -> null
        }
    }

    private fun getMimeType(fileName: String): String {
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

    private data class CloudPathInfo(
        val provider: CloudProvider,
        val folderId: String?,
        val fileId: String
    )
}
