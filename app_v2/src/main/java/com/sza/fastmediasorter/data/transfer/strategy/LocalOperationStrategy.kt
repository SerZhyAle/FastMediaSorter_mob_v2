package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import com.sza.fastmediasorter.data.transfer.FileExistsException
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Strategy for local file system operations.
 * Handles standard file:// and local path operations.
 */
class LocalOperationStrategy(
    private val context: Context
) : FileOperationStrategy {
    
    override suspend fun copyFile(
        source: String,
        destination: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(source)
            val destFile = File(destination)
            
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $source"))
            }
            
            if (destFile.exists() && !overwrite) {
                return@withContext Result.failure(FileExistsException(destFile.name, destination, isMove = false))
            }
            
            // Create parent directories if needed
            destFile.parentFile?.mkdirs()
            
            // Copy file with progress tracking
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    val fileSize = sourceFile.length()
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.onProgress(totalBytes, fileSize, 0L)
                    }
                }
            }
            
            Timber.d("LocalOperationStrategy: Copied ${sourceFile.name} (${destFile.length()} bytes)")
            Result.success(destination)
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Copy failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun moveFile(source: String, destination: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(source)
            val destFile = File(destination)
            
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $source"))
            }
            
            // Try atomic rename first (same filesystem)
            if (sourceFile.renameTo(destFile)) {
                Timber.d("LocalOperationStrategy: Moved ${sourceFile.name} via rename")
                return@withContext Result.success(Unit)
            }
            
            // Fallback: copy + delete
            val copyResult = copyFile(source, destination, overwrite = true, progressCallback = null)
            if (copyResult.isFailure) {
                return@withContext Result.failure(copyResult.exceptionOrNull() ?: Exception("Copy failed"))
            }
            
            if (!sourceFile.delete()) {
                return@withContext Result.failure(Exception("Copied but failed to delete source"))
            }
            
            Timber.d("LocalOperationStrategy: Moved ${sourceFile.name} via copy+delete")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Move failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            
            if (!file.exists()) {
                Timber.w("LocalOperationStrategy: File does not exist for deletion: $path")
                return@withContext Result.success(Unit) // Already deleted
            }
            
            // Android 10+ (Scoped Storage): For shared storage, use MediaStore
            // For app-private storage (like cache), use File.delete()
            val isSharedStorage = path.startsWith("/storage/emulated/0/") && 
                                 !path.contains("/Android/data/") &&
                                !path.contains("/Android/obb/")
            
            if (isSharedStorage && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Use MediaStore to delete from shared storage
                Timber.d("LocalOperationStrategy: Using MediaStore for deletion: $path")
                val deleted = deleteViaMediaStore(path)
                if (deleted) {
                    Timber.d("LocalOperationStrategy: Deleted via MediaStore: ${file.name}")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete via MediaStore: $path"))
                }
            } else {
                // Use File.delete() for app-private storage
                if (file.delete()) {
                    Timber.d("LocalOperationStrategy: Deleted ${file.name}")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete file: $path"))
                }
            }
        } catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
            // Re-throw permission exception - don't wrap in Result
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Delete failed - $path")
            Result.failure(e)
        }
    }
    
    private suspend fun deleteViaMediaStore(filePath: String): Boolean = withContext(Dispatchers.IO) {
        Timber.d("LocalOperationStrategy.deleteViaMediaStore: ENTRY - filePath=$filePath, API=${android.os.Build.VERSION.SDK_INT}")
        
        // Check for MANAGE_MEDIA permission (Android 12+ / API 31+) - allows direct deletion without dialogs
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val hasManageMedia = try {
                context.checkSelfPermission(android.Manifest.permission.MANAGE_MEDIA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
            
            if (hasManageMedia) {
                Timber.d("LocalOperationStrategy: MANAGE_MEDIA granted, using direct File.delete()")
                val file = File(filePath)
                val deleted = file.delete()
                if (deleted) {
                    // Also delete from MediaStore index
                    try {
                        context.contentResolver.delete(
                            android.provider.MediaStore.Files.getContentUri("external"),
                            "${android.provider.MediaStore.MediaColumns.DATA} = ?",
                            arrayOf(filePath)
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to remove from MediaStore index, but file deleted")
                    }
                }
                return@withContext deleted
            } else {
                Timber.d("LocalOperationStrategy: MANAGE_MEDIA not granted, using MediaStore flow")
            }
        }
        
        var cursor: android.database.Cursor? = null
        try {
            val file = File(filePath)
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            
            // Determine correct MediaStore collection based on MIME type
            val collection = when {
                mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> null // Non-media files - use fallback
            }
            
            // If not a media file, skip MediaStore and use direct delete
            if (collection == null) {
                Timber.d("LocalOperationStrategy: Non-media file ($mimeType), skipping MediaStore")
                return@withContext file.delete()
            }
            
            val selection = "${android.provider.MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            
            // Query for the ID from appropriate media collection
            cursor = context.contentResolver.query(
                collection, 
                arrayOf(android.provider.MediaStore.MediaColumns._ID), 
                selection, 
                selectionArgs, 
                null
            )
            
            val count = cursor?.count ?: 0
            Timber.d("LocalOperationStrategy: MediaStore query for $filePath found $count rows in $collection")
            
            if (cursor != null && cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idColumn)
                val contentUri = android.content.ContentUris.withAppendedId(collection, id)
                cursor.close() // Close early before delete
                cursor = null
                
                Timber.d("LocalOperationStrategy: Attempting delete with media URI: $contentUri")
                
                // On Android 11+, use createDeleteRequest for batch delete with single user prompt
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        // This method requests permission for batch delete with ONE dialog
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(contentUri)
                        )
                        
                        // Throw special exception with PendingIntent for UI to handle
                        Timber.i("LocalOperationStrategy: Batch delete permission required, throwing exception")
                        throw com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException(
                            pendingIntent, 
                            listOf(contentUri)
                        )
                    } catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
                        // Re-throw our custom exception
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "LocalOperationStrategy: createDeleteRequest failed, falling back to regular delete")
                        // Fall through to try regular delete
                    }
                }
                
                // Android 10 or fallback: try direct delete (may throw RecoverableSecurityException)
                try {
                    val deletedRows = context.contentResolver.delete(contentUri, null, null)
                    Timber.d("LocalOperationStrategy: MediaStore delete result: $deletedRows rows deleted")
                    
                    if (deletedRows > 0) {
                        return@withContext true
                    }
                } catch (securityException: SecurityException) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // On Android 10+, check if this is a RecoverableSecurityException
                        val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            Timber.w("LocalOperationStrategy: RecoverableSecurityException for $filePath - need user permission")
                            // Re-throw to be handled by UI layer
                            throw recoverableSecurityException
                        }
                    }
                    // Non-recoverable SecurityException - log and fall through to File.delete()
                    Timber.w(securityException, "LocalOperationStrategy: SecurityException (non-recoverable) for $filePath")
                }
            } else {
                Timber.w("LocalOperationStrategy: File not found in MediaStore: $filePath")
            }
            
            // Fallback: If MediaStore delete failed (e.g. file not indexed), try File.delete()
            if (file.exists() && file.delete()) {
                Timber.d("LocalOperationStrategy: Fallback File.delete() succeeded for $filePath")
                return@withContext true
            } else {
                Timber.w("LocalOperationStrategy: Fallback File.delete() failed for $filePath (exists=${file.exists()})")
                return@withContext false
            }
        } catch (e: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException) {
            // Re-throw our custom exception
            throw e
        } catch (e: Exception) {
            // Re-throw RecoverableSecurityException to be handled by caller/UI
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
                e is android.app.RecoverableSecurityException) {
                Timber.i("LocalOperationStrategy: Propagating RecoverableSecurityException to UI layer")
                throw e
            }
            
            Timber.e(e, "LocalOperationStrategy: MediaStore delete failed for: $filePath")
            
            // One last try with File.delete() for other exceptions
            try {
                val file = File(filePath)
                if (file.exists() && file.delete()) {
                    Timber.d("LocalOperationStrategy: Exception fallback File.delete() succeeded for $filePath")
                    true
                } else {
                    false
                }
            } catch (e2: Exception) {
                false
            }
        } finally {
            cursor?.close()
        }
    }
    
    override suspend fun exists(path: String): Result<Boolean> =
        safeIo("LocalOperationStrategy.exists($path)") { File(path).exists() }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                Result.success(Unit)
            } else if (dir.mkdirs()) {
                Result.success(Unit)
            } else {
                 // Double check in case of race condition
                 if (dir.exists() && dir.isDirectory) Result.success(Unit) 
                 else Result.failure(Exception("Failed to create directory: $path"))
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Create directory failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> =
        safeIo("LocalOperationStrategy.writeFile($path)") { File(path).writeText(content) }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                Result.success(file.readText())
            } else {
                Result.failure(java.io.FileNotFoundException("File not found: $path"))
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: readFile failed - $path")
            Result.failure(e)
        }
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists() || !file.isDirectory) {
                return@withContext Result.failure(java.io.FileNotFoundException("Directory not found: $path"))
            }
            // Return list of absolute paths
            val filesElement = file.listFiles()?.map { it.absolutePath } ?: emptyList()
            Result.success(filesElement)
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: listFiles failed - $path")
            Result.failure(e)
        }
    }
    
    override fun supportsProtocol(path: String): Boolean {
        // Supports local file paths (no protocol prefix or file:// prefix)
        return !path.startsWith("smb://") &&
               !path.startsWith("sftp://") &&
               !path.startsWith("ftp://") &&
               !path.startsWith("content:/") &&
               !path.contains("://") || path.startsWith("file://")
    }
    
    override fun getProtocolName(): String = "local"
    
    // ==================== Directory Operations ====================
    
    override suspend fun deleteDirectory(
        path: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            if (!dir.exists()) {
                return@withContext Result.failure(java.io.FileNotFoundException("Directory not found: $path"))
            }
            if (!dir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
            }
            
            // Count total files first for progress
            val allFiles = mutableListOf<File>()
            collectAllFiles(dir, allFiles)
            val totalCount = allFiles.size
            
            var deletedCount = 0
            
            // Delete files first (bottom-up approach)
            for (file in allFiles.sortedByDescending { it.absolutePath.length }) {
                progressCallback?.invoke(deletedCount, totalCount, file.name)
                if (file.delete()) {
                    deletedCount++
                } else {
                    Timber.w("LocalOperationStrategy: Failed to delete: ${file.absolutePath}")
                }
            }
            
            // Delete the directory itself
            if (dir.delete()) {
                deletedCount++
            }
            
            Timber.d("LocalOperationStrategy: Deleted directory $path ($deletedCount items)")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Delete directory failed - $path")
            Result.failure(e)
        }
    }
    
    private fun collectAllFiles(dir: File, result: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectAllFiles(file, result)
            }
            result.add(file)
        }
    }
    
    override suspend fun renameDirectory(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val oldDir = File(oldPath)
            val newDir = File(newPath)
            
            if (!oldDir.exists()) {
                return@withContext Result.failure(java.io.FileNotFoundException("Directory not found: $oldPath"))
            }
            if (!oldDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $oldPath"))
            }
            if (newDir.exists()) {
                return@withContext Result.failure(IllegalStateException("Destination already exists: $newPath"))
            }
            
            if (oldDir.renameTo(newDir)) {
                Timber.d("LocalOperationStrategy: Renamed directory $oldPath -> $newPath")
                Result.success(newPath)
            } else {
                Result.failure(Exception("Failed to rename directory"))
            }
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Rename directory failed - $oldPath -> $newPath")
            Result.failure(e)
        }
    }
    
    override suspend fun copyDirectory(
        source: String,
        destination: String,
        progressCallback: ((Int, Int, String) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sourceDir = File(source)
            val destDir = File(destination)
            
            if (!sourceDir.exists()) {
                return@withContext Result.failure(java.io.FileNotFoundException("Source directory not found: $source"))
            }
            if (!sourceDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Source is not a directory: $source"))
            }
            
            // Collect all files to copy
            val allFiles = mutableListOf<File>()
            collectAllFiles(sourceDir, allFiles)
            val filesToCopy = allFiles.filter { it.isFile }
            val totalCount = filesToCopy.size
            
            // Create destination directory
            if (!destDir.mkdirs() && !destDir.exists()) {
                return@withContext Result.failure(Exception("Failed to create destination directory: $destination"))
            }
            
            var copiedCount = 0
            
            for (file in filesToCopy) {
                progressCallback?.invoke(copiedCount, totalCount, file.name)
                
                // Calculate relative path and destination
                val relativePath = file.absolutePath.removePrefix(sourceDir.absolutePath)
                val destFile = File(destDir, relativePath)
                
                // Create parent directories if needed
                destFile.parentFile?.mkdirs()
                
                // Copy file
                file.copyTo(destFile, overwrite = true)
                copiedCount++
            }
            
            Timber.d("LocalOperationStrategy: Copied directory $source -> $destination ($copiedCount files)")
            Result.success(copiedCount)
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: Copy directory failed - $source -> $destination")
            Result.failure(e)
        }
    }
    
    override suspend fun isDirectory(path: String): Result<Boolean> =
        safeIo("LocalOperationStrategy.isDirectory($path)") { File(path).let { it.exists() && it.isDirectory } }
    
    override suspend fun getDirectoryInfo(path: String): Result<com.sza.fastmediasorter.data.transfer.DirectoryInfo> = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext Result.failure(java.io.FileNotFoundException("Directory not found: $path"))
            }
            
            val allFiles = mutableListOf<File>()
            collectAllFiles(dir, allFiles)
            val totalSize = allFiles.filter { it.isFile }.sumOf { it.length() }
            
            Result.success(
                com.sza.fastmediasorter.data.transfer.DirectoryInfo(
                    path = path,
                    name = dir.name,
                    childCount = dir.listFiles()?.size ?: 0,
                    totalSize = totalSize,
                    lastModified = dir.lastModified()
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "LocalOperationStrategy: getDirectoryInfo failed - $path")
            Result.failure(e)
        }
    }
}
