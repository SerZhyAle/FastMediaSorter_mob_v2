package com.sza.fastmediasorter.data.transfer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase.BatchDeletePermissionRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Base class for file operation handlers.
 * 
 * Provides common implementations for copy, move, and delete operations that are
 * protocol-agnostic. Subclasses provide protocol-specific strategy implementations.
 * 
 * This base class handles:
 * - Loop structure over multiple files
 * - Error collection and aggregation
 * - Result building (Success/PartialSuccess/Failure)
 * - Common utility methods (SAF deletion, path extraction, etc.)
 * 
 * Subclasses must provide:
 * - Protocol-specific strategies (SMB, SFTP, FTP, Cloud, Local)
 * - Path normalization logic
 * - Connection credential management
 */
abstract class BaseFileOperationHandler(
    protected val context: Context
) {
    
    /**
     * Get the list of strategies this handler supports.
     * Each strategy handles a specific protocol (e.g., SMB, SFTP).
     */
    protected abstract fun getStrategies(): List<FileOperationStrategy>
    
    /**
     * Find the appropriate strategy for a given file path.
     * 
     * @param path File path (may be protocol URL like smb://, sftp://, etc.)
     * @return The matching strategy, or null if no strategy supports the path
     */
    protected fun getStrategyForPath(path: String): FileOperationStrategy? {
        return getStrategies().firstOrNull { it.supportsProtocol(path) }
    }
    
    /**
     * Execute a copy operation.
     * 
     * Common implementation that iterates over source files, delegates to strategies,
     * collects errors, and builds the result.
     * 
     * Can be overridden by subclasses for custom behavior (e.g., cross-protocol transfers).
     */
    open suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val destinationPath = getSafePath(operation.destination)
        Timber.d("executeCopy: Starting copy of ${operation.sources.size} files to $destinationPath")
        
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.sources.forEachIndexed { index, source ->
            Timber.d("executeCopy: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val sourcePath = getSafePath(source)
                val fileName = extractFileName(sourcePath, source.name)
                val destPath = joinPath(destinationPath, fileName)
                
                // Delegate to protocol-specific copy logic
                val result = copyFile(sourcePath, destPath, operation.overwrite, progressCallback)
                
                result.fold(
                    onSuccess = { resultPath ->
                        copiedPaths.add(resultPath)
                        successCount++
                        Timber.i("executeCopy: SUCCESS - copied ${source.name}")
                    },
                    onFailure = { error ->
                        val errorMsg = if (error is FileExistsException) {
                            // Use localized string for file exists error
                            context.getString(
                                R.string.error_file_exists_copy,
                                error.fileName,
                                error.destinationPath
                            )
                        } else {
                            FileOperationError.formatTransferError(
                                fileName = source.name,
                                sourcePath = sourcePath,
                                destinationPath = destPath,
                                errorMessage = error.message ?: "Unknown error"
                            )
                        }
                        Timber.e("executeCopy: FAILED - $errorMsg")
                        errors.add(errorMsg)
                    }
                )
            } catch (e: Exception) {
                val error = if (e is FileExistsException) {
                    // Use localized string for file exists error
                    context.getString(
                        R.string.error_file_exists_copy,
                        e.fileName,
                        e.destinationPath
                    )
                } else {
                    FileOperationError.formatTransferError(
                        fileName = source.name,
                        sourcePath = getSafePath(source),
                        destinationPath = joinPath(destinationPath, source.name),
                        errorMessage = FileOperationError.extractErrorMessage(e)
                    )
                }
                Timber.e(e, "executeCopy: ERROR - $error")
                errors.add(error)
            }
        }
        
        return@withContext buildCopyResult(successCount, operation, copiedPaths, errors)
    }
    
    /**
     * Execute a move operation.
     * 
     * Common implementation that iterates over source files, delegates to strategies,
     * collects errors, and builds the result.
     * 
     * Can be overridden by subclasses for custom behavior (e.g., cross-protocol transfers).
     */
    open suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val destinationPath = getSafePath(operation.destination)
        Timber.d("executeMove: Starting move of ${operation.sources.size} files to $destinationPath")
        
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.sources.forEachIndexed { index, source ->
            Timber.d("executeMove: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            try {
                val sourcePath = getSafePath(source)
                val fileName = extractFileName(sourcePath, source.name)
                val destPath = joinPath(destinationPath, fileName)
                
                // Delegate to protocol-specific move logic
                val result = moveFile(sourcePath, destPath, operation.overwrite, progressCallback)
                
                result.fold(
                    onSuccess = { resultPath ->
                        movedPaths.add(resultPath)
                        successCount++
                        Timber.i("executeMove: SUCCESS - moved ${source.name}")
                    },
                    onFailure = { error ->
                        val errorMsg = if (error is FileExistsException) {
                            // Use localized string for file exists error
                            context.getString(
                                R.string.error_file_exists_move,
                                error.fileName,
                                error.destinationPath
                            )
                        } else {
                            FileOperationError.formatTransferError(
                                fileName = source.name,
                                sourcePath = sourcePath,
                                destinationPath = destPath,
                                errorMessage = error.message ?: "Unknown error"
                            )
                        }
                        Timber.e("executeMove: FAILED - $errorMsg")
                        errors.add(errorMsg)
                    }
                )
            } catch (e: Exception) {
                val error = if (e is FileExistsException) {
                    // Use localized string for file exists error
                    context.getString(
                        R.string.error_file_exists_move,
                        e.fileName,
                        e.destinationPath
                    )
                } else {
                    FileOperationError.formatTransferError(
                        fileName = source.name,
                        sourcePath = getSafePath(source),
                        destinationPath = joinPath(destinationPath, source.name),
                        errorMessage = FileOperationError.extractErrorMessage(e)
                    )
                }
                Timber.e(e, "executeMove: ERROR - $error")
                errors.add(error)
            }
        }
        
        return@withContext buildMoveResult(successCount, operation, movedPaths, errors)
    }
    
    /**
     * Execute a delete operation.
     * 
     * Common implementation that handles both soft delete (trash) and hard delete.
     * 
     * Can be overridden by subclasses for custom behavior.
     */
    open suspend fun executeDelete(
        operation: FileOperation.Delete
    ): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("executeDelete: START - ${operation.files.size} files, softDelete=${operation.softDelete}")
        
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val trashedPaths = mutableListOf<String>()
        var successCount = 0
        
        // Group files by parent to handle trash folder creation per directory
        // Use getSafePath/substring to support both local and network paths correctly
        val filesByParent = operation.files.groupBy { file ->
            val safePath = getSafePath(file)
            if (safePath.contains("/")) safePath.substringBeforeLast('/') else ""
        }
        
        filesByParent.forEach { (parentPath, files) ->
            if (parentPath.isEmpty()) {
                // Root files or invalid paths - attempt hard delete individually
                files.forEach { file ->
                     if (deleteFile(getSafePath(file)).isSuccess) {
                         deletedPaths.add(getSafePath(file))
                         successCount++
                     } else {
                         errors.add("Failed to delete ${file.name}")
                     }
                }
                return@forEach
            }

            var trashFolderCreated = false
            var currentTrashPath: String? = null
            
            // Attempt Soft Delete if requested
            if (operation.softDelete) {
                try {
                    // 1. Create unique trash folder: parent/.trash/TIMESTAMP
                    val timestamp = System.currentTimeMillis()
                    // Ensure base .trash exists first? Strategies usually handle mkdirs/recursive.
                    // But to be safe and cleaner structure:
                    // We want: parent/.trash/TIMESTAMP/
                    
                    // Construct path. Note: We use / for network paths too.
                    val baseTrash = "$parentPath/.trash"
                    val batchTrash = "$baseTrash/$timestamp"
                    
                    // Helper to create directory via strategy
                    val strategy = getStrategyForPath(parentPath)
                    
                    if (strategy != null) {
                        // Create deep structure
                        if (strategy.createDirectory(batchTrash).isSuccess) {
                            currentTrashPath = batchTrash
                            
                            // 2. Prepare Metadata
                            val fileNames = files.map { extractFileName(getSafePath(it), it.name) }
                            val metadata = com.sza.fastmediasorter.data.model.TrashMetadata(
                                originalPath = parentPath,
                                resourceId = 0, // Placeholder
                                resourceType = strategy.getProtocolName(),
                                deletedFiles = fileNames,
                                deletionTimestamp = timestamp,
                                isDirectory = files.size == 1 && files.first().isDirectory // Best guess
                            )
                            
                            // 3. Write Metadata
                            val metadataPath = "$batchTrash/metadata.json"
                            if (strategy.writeFile(metadataPath, metadata.toJson()).isSuccess) {
                                trashFolderCreated = true
                                Timber.d("executeDelete: Created trash metadata at $metadataPath")
                            } else {
                                errors.add("Failed to write metadata for ${parentPath}")
                            }
                        } else {
                             errors.add("Failed to create trash folder $batchTrash")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "executeDelete: Error preparing soft delete for $parentPath")
                    errors.add("Soft delete setup failed: ${e.message}")
                }
            }
            
            // Process files in this group
            files.forEach { file ->
                val filePath = getSafePath(file)
                val fileName = extractFileName(filePath, file.name)
                
                try {
                    val result = if (trashFolderCreated && currentTrashPath != null) {
                        // Move to trash
                        val trashFilePath = "$currentTrashPath/$fileName"
                        moveToTrash(filePath, trashFilePath, fileName)
                    } else {
                        // Hard delete (fallback or intended)
                        deleteFile(filePath)
                    }
                    
                    result.fold(
                        onSuccess = {
                            if (trashFolderCreated && currentTrashPath != null) {
                                trashedPaths.add(filePath) // Or currentTrashPath?
                                // User asked for trashed paths? Usually need connection to undo.
                                // Returning the NEW path in trash is useful for undo.
                                // But FileOperationResult usually expects original paths for "what was processed".
                                // Let's populate the resultPaths nicely later.
                            } else {
                                deletedPaths.add(filePath)
                            }
                            successCount++
                            Timber.i("executeDelete: SUCCESS - ${if(trashFolderCreated) "trashed" else "deleted"} ${file.name}")
                        },
                        onFailure = { error ->
                            val msg = "Failed to ${if(trashFolderCreated) "trash" else "delete"} ${file.name}: ${error.message}"
                            Timber.e("executeDelete: $msg")
                            errors.add(msg)
                        }
                    )
                } catch (e: Exception) {
                    val msg = "Exception deleting ${file.name}: ${e.message}"
                    Timber.e(e, "executeDelete: $msg")
                    errors.add(msg)
                }
            }
        }
        
        // For soft delete, we might want to return the trash directories created, or the original files.
        // The RestoreUseCase needs the original path or the trash path.
        // Returning `trashedPaths` (original paths) lets UI know what was removed.
        // But for Undo, we need to know WHERE it went.
        // Code snippet in previous turn returned: `trashDirs + trashedPaths`.
        // I will return trashedPaths (original) + distinct trash folders?
        // Let's stick to returning processed paths.
        
        val resultPaths = if (operation.softDelete && trashedPaths.isNotEmpty()) {
             trashedPaths // + list of trash folders?
             // UI/ViewModel uses this list to know what to remove from adapter.
             // Undo logic usually needs to scan .trash or use history.
             // If I return trashedPaths, Adapter removes them.
        } else {
            deletedPaths
        }
        
        return@withContext buildDeleteResult(successCount, operation, resultPaths, errors)
    }
    
    // ==================== Protocol-Specific Methods (Delegated to Strategies) ====================
    
    /**
     * Copy a file using the appropriate strategy.
     * Subclasses should override to add protocol-specific logic like overwrite checking.
     */
    protected open suspend fun copyFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        val sourceStrategy = getStrategyForPath(sourcePath)
        val destStrategy = getStrategyForPath(destPath)

        // If both strategies exist and are different, use cross-protocol bridging
        if (sourceStrategy != null && destStrategy != null && sourceStrategy != destStrategy) {
            return copyCrossProtocol(sourcePath, destPath, sourceStrategy, destStrategy, overwrite, progressCallback)
        }

        val strategy = sourceStrategy ?: destStrategy
        ?: return Result.failure(IllegalArgumentException("No strategy found for paths: $sourcePath -> $destPath"))
        
        return strategy.copyFile(sourcePath, destPath, overwrite, progressCallback)
    }

    private suspend fun copyCrossProtocol(
        sourcePath: String,
        destPath: String,
        sourceStrategy: FileOperationStrategy,
        destStrategy: FileOperationStrategy,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        Timber.d("copyCrossProtocol: Bridging transfer $sourcePath -> $destPath")

        // Create temp file
        val fileName = extractFileName(sourcePath, sourcePath.substringAfterLast('/'))
        val tempFile = File(context.cacheDir, "transfer_${System.currentTimeMillis()}_$fileName")

        try {
            // 1. Download source -> temp
            Timber.d("copyCrossProtocol: Step 1 - Download to temp ${tempFile.absolutePath}")
            val downloadResult = sourceStrategy.copyFile(
                sourcePath,
                tempFile.absolutePath,
                true, // Always overwrite temp
                progressCallback // Pass callback? Maybe split progress?
            )

            if (downloadResult.isFailure) {
                return Result.failure(Exception("Download failed: ${downloadResult.exceptionOrNull()?.message}"))
            }

            // 2. Upload temp -> dest
            Timber.d("copyCrossProtocol: Step 2 - Upload to dest $destPath")
            val uploadResult = destStrategy.copyFile(
                tempFile.absolutePath,
                destPath,
                overwrite,
                progressCallback // Pass callback again?
            )

            if (uploadResult.isFailure) {
                return Result.failure(Exception("Upload failed: ${uploadResult.exceptionOrNull()?.message}"))
            }

            return Result.success(destPath)

        } catch (e: Exception) {
            Timber.e(e, "copyCrossProtocol: Failed")
            return Result.failure(e)
        } finally {
            // Cleanup temp
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
    
    /**
     * Move a file using the appropriate strategy.
     * Default implementation: copy + delete.
     */
    protected open suspend fun moveFile(
        sourcePath: String,
        destPath: String,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?
    ): Result<String> {
        // Copy file
        val copyResult = copyFile(sourcePath, destPath, overwrite, progressCallback)
        if (copyResult.isFailure) {
            return copyResult
        }
        
        // Delete source
        val deleteResult = deleteFile(sourcePath)
        if (deleteResult.isFailure) {
            return Result.failure(
                Exception("File copied but failed to delete source: ${deleteResult.exceptionOrNull()?.message}")
            )
        }
        
        return copyResult
    }
    
    /**
     * Delete a file using the appropriate strategy.
     */
    protected open suspend fun deleteFile(filePath: String): Result<Unit> {
        val strategy = getStrategyForPath(filePath)
            ?: return Result.failure(IllegalArgumentException("No strategy found for path: $filePath"))
        
        return strategy.deleteFile(filePath)
    }
    
    /**
     * Move a file to trash (soft delete).
     * Default implementation uses moveFile.
     */
    protected open suspend fun moveToTrash(
        sourcePath: String,
        trashPath: String,
        fileName: String
    ): Result<Unit> {
        val strategy = getStrategyForPath(sourcePath)
            ?: return Result.failure(IllegalArgumentException("No strategy found for path: $sourcePath"))
        
        return strategy.moveFile(sourcePath, trashPath)
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Safely join two path components, avoiding double slashes.
     * Handles all path types: local, SMB, FTP, SFTP, cloud, content URIs.
     * 
     * @param basePath The base path (may or may not end with /)
     * @param component The component to append (should not start with /)
     * @return The joined path with a single separator
     */
    protected fun joinPath(basePath: String, component: String): String {
        val normalizedBase = basePath.trimEnd('/')
        val normalizedComponent = component.trimStart('/')
        return "$normalizedBase/$normalizedComponent"
    }
    
    /**
     * Extract clean filename from a path.
     * Handles content:// URIs, network paths (smb://, sftp://, etc.), cloud paths, and local paths.
     */
    protected fun extractFileName(path: String, fallbackName: String): String {
        return when {
            // Cloud paths contain folder IDs, not filenames - use fallback (actual filename)
            path.startsWith("cloud://") || path.startsWith("cloud:/") -> fallbackName
            path.startsWith("content:/") -> {
                try {
                    val decoded = Uri.decode(path)
                    decoded.substringAfterLast("/").substringAfterLast("%2F")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to decode content URI, using fallback")
                    fallbackName
                }
            }
            path.contains("/") -> path.substringAfterLast("/")
            else -> fallbackName
        }
    }
    
    /**
     * Create a trash folder for soft delete operations.
     * Returns the trash folder path, or null if creation failed.
     */
    protected open suspend fun createTrashFolder(firstFilePath: String): String? {
        // Extract parent directory
        val parentDir = if (firstFilePath.contains("/")) {
            firstFilePath.substringBeforeLast('/')
        } else {
            return null
        }
        
        val trashDirPath = "$parentDir/.trash_${System.currentTimeMillis()}"
        
        // Check if strategy exists for this path
        getStrategyForPath(trashDirPath) ?: return null
        
        // Create directory (strategies should implement this if needed)
        // For now, we'll try to use the exists check as a proxy
        // Subclasses can override this method for protocol-specific directory creation
        
        return trashDirPath
    }
    
    /**
     * Delete a content:// URI using SAF.
     */
    protected suspend fun deleteWithSaf(contentUri: String): Boolean {
        return com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(
            context, contentUri, "BaseFileOperationHandler"
        )
    }
    
    /**
     * Pre-flight check for batch delete permission on local files.
     * Throws BatchDeletePermissionRequiredException if permission needed.
     * 
     * This MUST be called BEFORE any upload/copy operation in Move flows
     * to prevent partial operations (file uploaded but not deleted).
     * 
     * @param sources List of source Files to check
     * @throws BatchDeletePermissionRequiredException if permission needed
     */
    @Throws(BatchDeletePermissionRequiredException::class)
    protected suspend fun checkBatchDeletePermissionBeforeMove(
        sources: List<File>
    ): Unit = withContext(Dispatchers.IO) {
        // Filter for local files that might need MediaStore permission
        val localPaths = sources
            .map { getSafePath(it) }
            .filter { path ->
                // Only check non-network, non-SAF paths
                !path.startsWith("smb:", ignoreCase = true) &&
                !path.startsWith("sftp:", ignoreCase = true) &&
                !path.startsWith("ftp:", ignoreCase = true) &&
                !path.startsWith("gdrive:", ignoreCase = true) &&
                !path.startsWith("onedrive:", ignoreCase = true) &&
                !path.startsWith("dropbox:", ignoreCase = true) &&
                !path.startsWith("cloud:", ignoreCase = true) &&
                !path.startsWith("content:/")  // SAF handles own permissions
            }
        
        if (localPaths.isEmpty()) {
            Timber.d("checkBatchDeletePermission: No local files to check")
            return@withContext
        }
        
        // Check if files are in MediaStore-managed shared storage (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sharedStoragePaths = localPaths.filter { path ->
                path.startsWith("/storage/emulated/0/") && 
                (path.contains("/Pictures/") || 
                 path.contains("/DCIM/") || 
                 path.contains("/Movies/") ||
                 path.contains("/Downloads/") ||
                 path.contains("/Documents/") ||
                 path.contains("/Music/") ||
                 path.contains("/Podcasts/") ||
                 path.contains("/Audiobooks/"))
            }
            
            if (sharedStoragePaths.isEmpty()) {
                Timber.d("checkBatchDeletePermission: No shared storage files to check")
                return@withContext
            }
            
            Timber.i("checkBatchDeletePermission: Found ${sharedStoragePaths.size} shared storage files, checking MediaStore URIs")
            
            // Get MediaStore URIs for these files
            val uris = mutableListOf<Uri>()
            
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )
            
            sharedStoragePaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    // Query MediaStore for this file
                    val projection = arrayOf(MediaStore.MediaColumns._ID)
                    val selection = "${MediaStore.MediaColumns.DATA} = ?"
                    val selectionArgs = arrayOf(path)
                    
                    for (collection in collections) {
                        try {
                            context.contentResolver.query(
                                collection,
                                projection,
                                selection,
                                selectionArgs,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                                    val uri = ContentUris.withAppendedId(collection, id)
                                    uris.add(uri)
                                    Timber.d("checkBatchDeletePermission: Found MediaStore URI for $path -> $uri")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "checkBatchDeletePermission: Failed to query MediaStore for $path")
                        }
                    }
                }
            }
            
            if (uris.isNotEmpty()) {
                Timber.i("checkBatchDeletePermission: Requesting write permission for ${uris.size} MediaStore files")
                
                // Use createWriteRequest (NOT createDeleteRequest!) to get permission WITHOUT auto-deleting
                // createDeleteRequest auto-deletes files after user grants permission
                // createWriteRequest grants permission to modify/delete, but does NOT auto-delete
                try {
                    val pendingIntent = MediaStore.createWriteRequest(
                        context.contentResolver,
                        uris
                    )
                    
                    // If we got here, permission is needed - throw BEFORE any upload
                    Timber.i("checkBatchDeletePermission: Write permission required, throwing exception BEFORE upload")
                    throw BatchDeletePermissionRequiredException(pendingIntent, uris)
                    
                } catch (e: BatchDeletePermissionRequiredException) {
                    // Re-throw to caller
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "checkBatchDeletePermission: Error checking permission, proceeding with operation")
                    // If check fails, proceed with operation (might fail later)
                }
            }
        }
        
        Timber.d("checkBatchDeletePermission: Pre-flight check passed")
    }
    
    /**
     * Request batch delete permission for multiple files AFTER upload completed.
     * Uses createDeleteRequest which auto-deletes files after user grants permission.
     * Call this AFTER all uploads are complete.
     * 
     * @param filePaths List of local file paths to delete
     * @throws BatchDeletePermissionRequiredException always, to show permission dialog
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @Throws(BatchDeletePermissionRequiredException::class)
    protected suspend fun requestBatchDeletePermission(filePaths: List<String>) {
        Timber.i("requestBatchDeletePermission: Requesting delete for ${filePaths.size} files")
        
        val uris = mutableListOf<Uri>()
        
        filePaths.forEach { path ->
            try {
                val file = File(path)
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                
                val collection = when {
                    mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                
                val selection = "${MediaStore.MediaColumns.DATA} = ?"
                val selectionArgs = arrayOf(path)
                
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        uris.add(contentUri)
                        Timber.d("requestBatchDeletePermission: Found URI for $path -> $contentUri")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "requestBatchDeletePermission: Failed to get URI for $path")
            }
        }
        
        if (uris.isNotEmpty()) {
            Timber.i("requestBatchDeletePermission: Creating delete request for ${uris.size} URIs")
            // Use createDeleteRequest - auto-deletes after user grants permission
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            throw BatchDeletePermissionRequiredException(pendingIntent, uris)
        } else {
            Timber.w("requestBatchDeletePermission: No URIs found for batch delete")
        }
    }

    /**
     * Get a safe path string from a File object.
     * Uses .path for network URIs to avoid prepending the local working directory (which .absolutePath does).
     * Uses .absolutePath for local files to ensure full path resolution.
     */
    protected fun getSafePath(file: File): String {
        val path = file.path
        // Check for known protocols (ignore case handled by checking logic later, but here we just want to preserve the string)
        if (path.contains("://") || 
            path.startsWith("smb:", ignoreCase = true) || 
            path.startsWith("sftp:", ignoreCase = true) || 
            path.startsWith("ftp:", ignoreCase = true) || 
            path.startsWith("cloud:", ignoreCase = true) ||
            path.startsWith("content:", ignoreCase = true)) {
            return path
        }
        return file.absolutePath
    }
    
    // ==================== Result Building Methods ====================
    
    /**
     * Build the result for a copy operation.
     */
    protected fun buildCopyResult(
        successCount: Int,
        operation: FileOperation.Copy,
        copiedPaths: List<String>,
        errors: List<String>
    ): FileOperationResult {
        return when {
            successCount == operation.sources.size -> {
                Timber.i("executeCopy: All $successCount files copied successfully")
                FileOperationResult.Success(successCount, operation, copiedPaths)
            }
            successCount > 0 -> {
                Timber.w("executeCopy: Partial success - $successCount/${operation.sources.size} files copied. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("executeCopy: All copy operations failed. Errors: $errors")
                val errorMessage = errors.joinToString("\n")
                // Fallback resolved string for compatibility, but provide resource ID for UI to resolve with correct context
                FileOperationResult.Failure(
                    error = context.getString(R.string.all_copy_operations_failed, errorMessage),
                    errorRes = R.string.all_copy_operations_failed,
                    formatArgs = listOf(errorMessage)
                )
            }
        }
    }
    
    /**
     * Build the result for a move operation.
     */
    protected fun buildMoveResult(
        successCount: Int,
        operation: FileOperation.Move,
        movedPaths: List<String>,
        errors: List<String>
    ): FileOperationResult {
        return when {
            successCount == operation.sources.size -> {
                Timber.i("executeMove: All $successCount files moved successfully")
                FileOperationResult.Success(successCount, operation, movedPaths)
            }
            successCount > 0 -> {
                Timber.w("executeMove: Partial success - $successCount/${operation.sources.size} files moved. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("executeMove: All move operations failed. Errors: $errors")
                val errorMessage = errors.joinToString("\n")
                FileOperationResult.Failure(
                    error = context.getString(R.string.all_move_operations_failed, errorMessage),
                    errorRes = R.string.all_move_operations_failed,
                    formatArgs = listOf(errorMessage)
                )
            }
        }
    }
    
    /**
     * Build the result for a delete operation.
     */
    protected fun buildDeleteResult(
        successCount: Int,
        operation: FileOperation.Delete,
        deletedPaths: List<String>,
        errors: List<String>
    ): FileOperationResult {
        return when {
            successCount == operation.files.size -> {
                Timber.i("executeDelete: All $successCount files deleted successfully")
                FileOperationResult.Success(successCount, operation, deletedPaths)
            }
            successCount > 0 -> {
                Timber.w("executeDelete: Partial success - $successCount/${operation.files.size} deleted. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("executeDelete: All delete operations failed. Errors: $errors")
                val errorMessage = errors.joinToString("; ")
                FileOperationResult.Failure(
                    error = "All delete operations failed: $errorMessage",
                    errorRes = R.string.all_delete_operations_failed,
                    formatArgs = listOf(errorMessage)
                )
            }
        }
    }
}
