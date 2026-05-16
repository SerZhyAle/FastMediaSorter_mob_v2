package com.sza.fastmediasorter.data.transfer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.data.transfer.strategy.TrashRenameUnavailableException
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
 * Base class for file operation handlers. Provides protocol-agnostic copy/move/delete loops,
 * error aggregation, result building, and SAF/MediaStore utilities. Subclasses supply strategies.
 */
abstract class BaseFileOperationHandler(
    protected val context: Context
) {
    protected abstract fun getStrategies(): List<FileOperationStrategy>
    protected fun getStrategyForPath(path: String): FileOperationStrategy? = getStrategies().firstOrNull { it.supportsProtocol(path) }

    open suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val destinationPath = getSafePath(operation.destination)
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0
        var skippedCount = 0
        val skippedPaths = mutableListOf<String>()

        operation.sources.forEachIndexed { index, source ->
            val total = operation.sources.size
            progressCallback?.onFileStarted(index + 1, source.name, total)
            try {
                val sourcePath = getSafePath(source)
                val fileName = extractFileName(sourcePath, source.name)
                val destPath = joinPath(destinationPath, fileName)
                copyFile(sourcePath, destPath, operation.overwrite, progressCallback).fold(
                    onSuccess = { resultPath -> copiedPaths.add(resultPath); successCount++ },
                    onFailure = { error ->
                        if (error is FileExistsException && !operation.overwrite) { skippedCount++; skippedPaths.add(destPath) }
                        else errors.add(if (error is FileExistsException) context.getString(R.string.error_file_exists_copy, error.fileName, error.destinationPath)
                            else FileOperationError.formatTransferError(source.name, sourcePath, destPath, error.message ?: "Unknown error"))
                    }
                )
            } catch (e: Exception) {
                if (e is FileExistsException && !operation.overwrite) {
                    val destPath = joinPath(destinationPath, extractFileName(getSafePath(source), source.name))
                    skippedCount++; skippedPaths.add(destPath)
                } else errors.add(if (e is FileExistsException) context.getString(R.string.error_file_exists_copy, e.fileName, e.destinationPath)
                    else FileOperationError.formatTransferError(source.name, getSafePath(source), joinPath(destinationPath, source.name), FileOperationError.extractErrorMessage(e)))
            }
        }

        return@withContext buildCopyResult(successCount, operation, copiedPaths, errors, skippedCount, skippedPaths)
    }

    open suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val destinationPath = getSafePath(operation.destination)
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0
        var skippedCount = 0
        val skippedPaths = mutableListOf<String>()

        operation.sources.forEachIndexed { index, source ->
            val total = operation.sources.size
            progressCallback?.onFileStarted(index + 1, source.name, total)
            try {
                val sourcePath = getSafePath(source)
                val fileName = extractFileName(sourcePath, source.name)
                val destPath = joinPath(destinationPath, fileName)
                moveFile(sourcePath, destPath, operation.overwrite, progressCallback).fold(
                    onSuccess = { resultPath -> movedPaths.add(resultPath); successCount++ },
                    onFailure = { error ->
                        if (error is FileExistsException && !operation.overwrite) { skippedCount++; skippedPaths.add(destPath) }
                        else errors.add(if (error is FileExistsException) context.getString(R.string.error_file_exists_move, error.fileName, error.destinationPath)
                            else FileOperationError.formatTransferError(source.name, sourcePath, destPath, error.message ?: "Unknown error"))
                    }
                )
            } catch (e: Exception) {
                if (e is FileExistsException && !operation.overwrite) {
                    val destPath = joinPath(destinationPath, extractFileName(getSafePath(source), source.name))
                    skippedCount++; skippedPaths.add(destPath)
                } else errors.add(if (e is FileExistsException) context.getString(R.string.error_file_exists_move, e.fileName, e.destinationPath)
                    else FileOperationError.formatTransferError(source.name, getSafePath(source), joinPath(destinationPath, source.name), FileOperationError.extractErrorMessage(e)))
            }
        }

        return@withContext buildMoveResult(successCount, operation, movedPaths, errors, skippedCount, skippedPaths)
    }

    open suspend fun executeDelete(
        operation: FileOperation.Delete
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val trashedPaths = mutableListOf<String>()
        val softDeleteFallbackPaths = mutableListOf<String>()
        var successCount = 0

        val filesByParent = operation.files.groupBy { file ->
            val safePath = getSafePath(file)
            if (safePath.contains("/")) safePath.substringBeforeLast('/') else ""
        }

        filesByParent.forEach { (parentPath, files) ->
            if (parentPath.isEmpty()) {
                files.forEach { file ->
                    if (deleteFile(getSafePath(file)).isSuccess) { deletedPaths.add(getSafePath(file)); successCount++ }
                    else errors.add("Failed to delete ${file.name}")
                }
                return@forEach
            }

            var trashFolderCreated = false
            var currentTrashPath: String? = null

            if (operation.softDelete) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val batchTrash = TrashFolderContract.buildSnapshotPath(parentPath, timestamp)
                    val strategy = getStrategyForPath(parentPath)
                    if (strategy != null) {
                        if (strategy.createDirectory(batchTrash).isSuccess) {
                            currentTrashPath = batchTrash
                            val fileNames = files.map { extractFileName(getSafePath(it), it.name) }
                            val metadata = com.sza.fastmediasorter.data.model.TrashMetadata(
                                originalPath = parentPath,
                                resourceId = 0,
                                resourceType = strategy.getProtocolName(),
                                deletedFiles = fileNames,
                                deletionTimestamp = timestamp,
                                isDirectory = files.size == 1 && files.first().isDirectory
                            )
                            val metadataPath = "$batchTrash/metadata.json"
                            if (strategy.writeFile(metadataPath, metadata.toJson()).isSuccess) trashFolderCreated = true
                            else errors.add("Failed to write metadata for $parentPath")
                        } else errors.add("Failed to create trash folder $batchTrash")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "executeDelete: Error preparing soft delete for $parentPath")
                    errors.add("Soft delete setup failed: ${e.message}")
                }
            }

            files.forEach { file ->
                val filePath = getSafePath(file)
                val fileName = extractFileName(filePath, file.name)
                try {
                    val softDeleteAttempted = trashFolderCreated && currentTrashPath != null
                    var hardDeleteFallbackUsed = false
                    val result = if (softDeleteAttempted) {
                        try {
                            moveToTrash(filePath, "$currentTrashPath/$fileName", fileName)
                        } catch (e: TrashRenameUnavailableException) {
                            hardDeleteFallbackUsed = true
                            deleteFile(filePath)
                        }
                    } else {
                        deleteFile(filePath)
                    }
                    result.fold(
                        onSuccess = {
                            if (softDeleteAttempted && !hardDeleteFallbackUsed) {
                                trashedPaths.add(filePath)
                            } else {
                                deletedPaths.add(filePath)
                            }
                            if (hardDeleteFallbackUsed) {
                                softDeleteFallbackPaths.add(filePath)
                            }
                            successCount++
                        },
                        onFailure = { error ->
                            val action = if (softDeleteAttempted && !hardDeleteFallbackUsed) "trash" else "delete"
                            errors.add("Failed to $action ${file.name}: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "executeDelete: Exception deleting ${file.name}")
                    errors.add("Exception deleting ${file.name}: ${e.message}")
                }
            }
        }

        val resultPaths = if (operation.softDelete && trashedPaths.isNotEmpty()) trashedPaths else deletedPaths
        return@withContext buildDeleteResult(successCount, operation, resultPaths, errors, softDeleteFallbackPaths)
    }
    
    protected open suspend fun copyFile(sourcePath: String, destPath: String, overwrite: Boolean, progressCallback: ByteProgressCallback?): Result<String> {
        val sourceStrategy = getStrategyForPath(sourcePath)
        val destStrategy = getStrategyForPath(destPath)
        if (sourceStrategy != null && destStrategy != null && sourceStrategy != destStrategy)
            return copyCrossProtocol(sourcePath, destPath, sourceStrategy, destStrategy, overwrite, progressCallback)
        val strategy = sourceStrategy ?: destStrategy
            ?: return Result.failure(IllegalArgumentException("No strategy found for paths: $sourcePath -> $destPath"))
        return strategy.copyFile(sourcePath, destPath, overwrite, progressCallback)
    }

    private suspend fun copyCrossProtocol(sourcePath: String, destPath: String, sourceStrategy: FileOperationStrategy, destStrategy: FileOperationStrategy, overwrite: Boolean, progressCallback: ByteProgressCallback?): Result<String> {
        if (!overwrite && destStrategy.exists(destPath).getOrNull() == true)
            return Result.failure(FileExistsException(destPath.substringAfterLast('/'), destPath))
        val fileName = extractFileName(sourcePath, sourcePath.substringAfterLast('/'))
        val tempFile = File(context.cacheDir, "transfer_${System.currentTimeMillis()}_$fileName")
        try {
            val downloadResult = sourceStrategy.copyFile(sourcePath, tempFile.absolutePath, true, progressCallback)
            if (downloadResult.isFailure) return Result.failure(Exception("Download failed: ${downloadResult.exceptionOrNull()?.message}"))
            val uploadResult = destStrategy.copyFile(tempFile.absolutePath, destPath, overwrite, progressCallback)
            return if (uploadResult.isSuccess) Result.success(destPath)
                   else Result.failure(Exception("Upload failed: ${uploadResult.exceptionOrNull()?.message}"))
        } catch (e: Exception) {
            Timber.e(e, "copyCrossProtocol: Failed")
            return Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    protected open suspend fun moveFile(sourcePath: String, destPath: String, overwrite: Boolean, progressCallback: ByteProgressCallback?): Result<String> {
        val copyResult = copyFile(sourcePath, destPath, overwrite, progressCallback)
        if (copyResult.isFailure) return copyResult
        val deleteResult = deleteFile(sourcePath)
        return if (deleteResult.isSuccess) copyResult
               else Result.failure(Exception("File copied but failed to delete source: ${deleteResult.exceptionOrNull()?.message}"))
    }

    open suspend fun deleteFile(filePath: String): Result<Unit> {
        val strategy = getStrategyForPath(filePath)
            ?: return Result.failure(IllegalArgumentException("No strategy found for path: $filePath"))
        return strategy.deleteFile(filePath)
    }

    protected open suspend fun moveToTrash(sourcePath: String, trashPath: String, fileName: String): Result<Unit> {
        val strategy = getStrategyForPath(sourcePath)
            ?: return Result.failure(IllegalArgumentException("No strategy found for path: $sourcePath"))
        return strategy.moveFile(sourcePath, trashPath)
    }
    
    protected fun joinPath(basePath: String, component: String): String =
        "${basePath.trimEnd('/')}/${component.trimStart('/')}"

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
    
    protected suspend fun deleteWithSaf(contentUri: String): Boolean {
        return com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(
            context, contentUri, "BaseFileOperationHandler"
        )
    }
    
    /** Pre-flight permission check for batch delete BEFORE upload in Move flows. Throws if MediaStore write permission required. */
    @Throws(BatchDeletePermissionRequiredException::class)
    protected suspend fun checkBatchDeletePermissionBeforeMove(sources: List<File>): Unit = withContext(Dispatchers.IO) {
        val localPaths = sources.map { getSafePath(it) }.filter { path ->
            !path.startsWith("smb:", ignoreCase = true) && !path.startsWith("sftp:", ignoreCase = true) &&
            !path.startsWith("ftp:", ignoreCase = true) && !path.startsWith("gdrive:", ignoreCase = true) &&
            !path.startsWith("onedrive:", ignoreCase = true) && !path.startsWith("dropbox:", ignoreCase = true) &&
            !path.startsWith("cloud:", ignoreCase = true) && !path.startsWith("content:/")
        }
        if (localPaths.isEmpty()) return@withContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sharedStoragePaths = localPaths.filter { path ->
                path.startsWith("/storage/emulated/0/") &&
                (path.contains("/Pictures/") || path.contains("/DCIM/") || path.contains("/Movies/") ||
                 path.contains("/Downloads/") || path.contains("/Documents/") || path.contains("/Music/") ||
                 path.contains("/Podcasts/") || path.contains("/Audiobooks/"))
            }
            if (sharedStoragePaths.isEmpty()) return@withContext

            val uris = mutableListOf<Uri>()
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )
            
            sharedStoragePaths.forEach { path ->
                if (File(path).exists()) {
                    val selection = "${MediaStore.MediaColumns.DATA} = ?"
                    for (collection in collections) {
                        try {
                            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf(path), null)?.use { cursor ->
                                if (cursor.moveToFirst())
                                    uris.add(ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))))
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "checkBatchDeletePermission: Failed to query MediaStore for $path")
                        }
                    }
                }
            }

            if (uris.isNotEmpty()) {
                // Use createWriteRequest (NOT createDeleteRequest) to get permission WITHOUT auto-deleting
                try {
                    throw BatchDeletePermissionRequiredException(MediaStore.createWriteRequest(context.contentResolver, uris), uris)
                } catch (e: BatchDeletePermissionRequiredException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "checkBatchDeletePermission: Error checking permission, proceeding")
                }
            }
        }
    }
    
    /** Requests batch delete permission AFTER upload. Uses createDeleteRequest (auto-deletes on grant). Always throws. */
    @RequiresApi(Build.VERSION_CODES.R)
    @Throws(BatchDeletePermissionRequiredException::class)
    protected suspend fun requestBatchDeletePermission(filePaths: List<String>) {
        val uris = mutableListOf<Uri>()
        filePaths.forEach { path ->
            try {
                val file = File(path)
                val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                val collection = when {
                    mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null)?.use { cursor ->
                    if (cursor.moveToFirst())
                        uris.add(ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))))
                }
            } catch (e: Exception) {
                Timber.w(e, "requestBatchDeletePermission: Failed to get URI for $path")
            }
        }
        if (uris.isNotEmpty()) {
            // Use createDeleteRequest - auto-deletes after user grants permission
            throw BatchDeletePermissionRequiredException(MediaStore.createDeleteRequest(context.contentResolver, uris), uris)
        }
    }

    protected fun getSafePath(file: File): String {
        val path = file.path
        if (path.contains("://") || path.startsWith("smb:", ignoreCase = true) ||
            path.startsWith("sftp:", ignoreCase = true) || path.startsWith("ftp:", ignoreCase = true) ||
            path.startsWith("cloud:", ignoreCase = true) || path.startsWith("content:", ignoreCase = true))
            return path
        return file.absolutePath
    }

    protected fun buildCopyResult(successCount: Int, operation: FileOperation.Copy, copiedPaths: List<String>, errors: List<String>, skippedCount: Int = 0, skippedPaths: List<String> = emptyList()): FileOperationResult {
        val totalProcessed = successCount + skippedCount
        return when {
            totalProcessed == operation.sources.size -> FileOperationResult.Success(successCount, operation, copiedPaths, skippedCount, skippedPaths)
            totalProcessed > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors, emptyList(), skippedCount, skippedPaths)
            else -> {
                val errorMessage = errors.joinToString("\n")
                FileOperationResult.Failure(error = context.getString(R.string.all_copy_operations_failed, errorMessage), errorRes = R.string.all_copy_operations_failed, formatArgs = listOf(errorMessage))
            }
        }
    }

    protected fun buildMoveResult(successCount: Int, operation: FileOperation.Move, movedPaths: List<String>, errors: List<String>, skippedCount: Int = 0, skippedPaths: List<String> = emptyList()): FileOperationResult {
        val totalProcessed = successCount + skippedCount
        return when {
            totalProcessed == operation.sources.size -> FileOperationResult.Success(successCount, operation, movedPaths, skippedCount, skippedPaths)
            totalProcessed > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors, movedPaths, skippedCount, skippedPaths)
            else -> {
                val errorMessage = errors.joinToString("\n")
                FileOperationResult.Failure(error = context.getString(R.string.all_move_operations_failed, errorMessage), errorRes = R.string.all_move_operations_failed, formatArgs = listOf(errorMessage))
            }
        }
    }

    protected fun buildDeleteResult(
        successCount: Int,
        operation: FileOperation.Delete,
        deletedPaths: List<String>,
        errors: List<String>,
        softDeleteFallbackPaths: List<String> = emptyList(),
    ): FileOperationResult {
        return when {
            successCount == operation.files.size -> FileOperationResult.Success(
                successCount,
                operation,
                deletedPaths,
                softDeleteFallbackPaths = softDeleteFallbackPaths,
            )
            successCount > 0 -> FileOperationResult.PartialSuccess(
                successCount,
                errors.size,
                errors,
                softDeleteFallbackPaths = softDeleteFallbackPaths,
            )
            else -> {
                FileOperationResult.Failure(
                    error = context.getString(R.string.all_delete_operations_failed),
                    errorRes = R.string.all_delete_operations_failed,
                    formatArgs = emptyList()
                )
            }
        }
    }

    open suspend fun listFiles(directoryPath: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val strategy = getStrategyForPath(directoryPath)
            ?: return@withContext Result.failure(IllegalArgumentException("No strategy supports path: $directoryPath"))
        strategy.listFiles(directoryPath)
    }
}
