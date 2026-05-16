package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Build
import com.sza.fastmediasorter.data.transfer.BaseFileOperationHandler
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal class LocalDeleteFileOperation(
    private val context: Context,
    private val cloudFileOperationHandler: com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler,
    private val localOperationStrategy: LocalOperationStrategy,
) {
    private val localDeleteHandler = object : BaseFileOperationHandler(context) {
        override fun getStrategies(): List<FileOperationStrategy> = listOf(localOperationStrategy)
    }

    suspend fun collectMediaStoreUris(files: List<File>): List<Uri> = withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext emptyList()
        }

        Timber.d("collectMediaStoreUris: Collecting URIs for ${files.size} files")

        for (file in files) {
            val filePath = file.absolutePath

            if (!localOperationStrategy.isSharedStoragePath(filePath)) continue

            try {
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

                val collection = when {
                    mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }

                if (collection == null) continue

                val selection = "${android.provider.MediaStore.MediaColumns.DATA} = ?"
                val selectionArgs = arrayOf(filePath)

                context.contentResolver.query(
                    collection,
                    arrayOf(android.provider.MediaStore.MediaColumns._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                        val contentUri = android.content.ContentUris.withAppendedId(collection, id)
                        uris.add(contentUri)
                        Timber.d("collectMediaStoreUris: Found URI for ${file.name}: $contentUri")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "collectMediaStoreUris: Failed to get URI for ${file.name}")
            }
        }

        Timber.i("collectMediaStoreUris: Collected ${uris.size} URIs from ${files.size} files")
        uris
    }

    suspend fun execute(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("executeDelete: START - ${operation.files.size} files, softDelete=${operation.softDelete}")

        if (!operation.softDelete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uris = collectMediaStoreUris(operation.files)
                if (uris.isNotEmpty()) {
                    Timber.i("executeDelete: Creating batch delete request for ${uris.size} URIs")
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        context.contentResolver,
                        uris
                    )
                    Timber.i("executeDelete: Batch delete PendingIntent created - throwing exception for UI handling")
                    throw FileOperationUseCase.BatchDeletePermissionRequiredException(pendingIntent, uris)
                }
            } catch (e: FileOperationUseCase.BatchDeletePermissionRequiredException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "executeDelete: Batch URI collection failed, falling back to individual deletion")
            }
        }

        val cloudFiles = operation.files.filter { it.path.startsWith("cloud:") }
        val otherFiles = operation.files.filter { !it.path.startsWith("cloud:") }

        val results = mutableListOf<FileOperationResult>()

        if (cloudFiles.isNotEmpty()) {
            results.add(cloudFileOperationHandler.executeDelete(operation.copy(files = cloudFiles)))
        }

        if (otherFiles.isNotEmpty()) {
            results.add(localDeleteHandler.executeDelete(operation.copy(files = otherFiles)))
        }

        if (results.isEmpty()) {
            return@withContext FileOperationResult.Success(0, operation, emptyList())
        }

        if (results.size == 1) {
            return@withContext results.first()
        }

        var totalSuccess = 0
        var totalFailed = 0
        val allErrors = mutableListOf<String>()
        val allProcessedPaths = mutableListOf<String>()
        val allSoftDeleteFallbackPaths = mutableListOf<String>()

        results.forEach { result ->
            when (result) {
                is FileOperationResult.Success -> {
                    totalSuccess += result.processedCount
                    allProcessedPaths.addAll(result.copiedFilePaths)
                    allSoftDeleteFallbackPaths.addAll(result.softDeleteFallbackPaths)
                }
                is FileOperationResult.PartialSuccess -> {
                    totalSuccess += result.processedCount
                    totalFailed += result.failedCount
                    allErrors.addAll(result.errors)
                    allProcessedPaths.addAll(result.deletedPaths)
                    allSoftDeleteFallbackPaths.addAll(result.softDeleteFallbackPaths)
                }
                is FileOperationResult.Failure -> {
                    totalFailed += operation.files.size
                    allErrors.add(result.error)
                }
                else -> {}
            }
        }

        return@withContext if (totalFailed == 0) {
            FileOperationResult.Success(
                totalSuccess,
                operation,
                allProcessedPaths,
                softDeleteFallbackPaths = allSoftDeleteFallbackPaths,
            )
        } else if (totalSuccess > 0) {
            FileOperationResult.PartialSuccess(
                totalSuccess,
                totalFailed,
                allErrors,
                allProcessedPaths,
                softDeleteFallbackPaths = allSoftDeleteFallbackPaths,
            )
        } else {
            FileOperationResult.Failure(allErrors.joinToString("; "))
        }
    }
}
