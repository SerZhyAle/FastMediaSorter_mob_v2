package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal class LocalDeleteFileOperation(
    private val context: Context,
    private val cloudFileOperationHandler: com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler,
    private val smbFileOperationHandler: com.sza.fastmediasorter.data.network.SmbFileOperationHandler
) {

    fun isSharedStorage(path: String): Boolean {
        return path.startsWith("/storage/emulated/0/") &&
               !path.contains("/Android/data/") &&
               !path.contains("/Android/obb/")
    }

    suspend fun deleteViaMediaStore(filePath: String): Boolean = withContext(Dispatchers.IO) {
        Timber.d("FileOperationUseCase.deleteViaMediaStore: ENTRY - filePath=$filePath, API=${Build.VERSION.SDK_INT}")
        var cursor: android.database.Cursor? = null
        try {
            val file = File(filePath)
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

            val collection = when {
                mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> null
            }

            if (collection == null) {
                Timber.d("FileOperationUseCase: Non-media file ($mimeType), skipping MediaStore")
                return@withContext file.delete()
            }

            val selection = "${android.provider.MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)

            cursor = context.contentResolver.query(
                collection,
                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                selection,
                selectionArgs,
                null
            )

            val count = cursor?.count ?: 0
            Timber.d("FileOperationUseCase: MediaStore query for $filePath found $count rows in $collection")

            if (cursor != null && cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idColumn)
                val contentUri = android.content.ContentUris.withAppendedId(collection, id)
                cursor.close()
                cursor = null

                Timber.d("deleteViaMediaStore: Found file in MediaStore - ID=$id, contentUri=$contentUri")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        Timber.d("deleteViaMediaStore: Android 11+ detected - calling createDeleteRequest for file: $filePath")
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(contentUri)
                        )

                        Timber.i("deleteViaMediaStore: BATCH DELETE PERMISSION REQUEST CREATED - File: $filePath")
                        throw FileOperationUseCase.BatchDeletePermissionRequiredException(pendingIntent, listOf(contentUri))
                    } catch (e: FileOperationUseCase.BatchDeletePermissionRequiredException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "FileOperationUseCase: createDeleteRequest failed, falling back to regular delete")
                    }
                }

                try {
                    val deletedRows = context.contentResolver.delete(contentUri, null, null)
                    Timber.d("FileOperationUseCase: MediaStore delete result: $deletedRows rows deleted")

                    if (deletedRows > 0) {
                        return@withContext true
                    }
                } catch (securityException: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            Timber.w("FileOperationUseCase: RecoverableSecurityException for $filePath - need user permission")
                            throw recoverableSecurityException
                        }
                    }
                    Timber.w(securityException, "FileOperationUseCase: SecurityException (non-recoverable) for $filePath")
                }
            } else {
                Timber.w("FileOperationUseCase: File not found in MediaStore: $filePath")
            }

            if (file.exists() && file.delete()) {
                Timber.d("FileOperationUseCase: Fallback File.delete() succeeded for $filePath")
                return@withContext true
            } else {
                Timber.w("FileOperationUseCase: Fallback File.delete() failed for $filePath (exists=${file.exists()})")
                return@withContext false
            }
        } catch (e: FileOperationUseCase.BatchDeletePermissionRequiredException) {
            throw e
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                e is android.app.RecoverableSecurityException) {
                Timber.i("FileOperationUseCase: Propagating RecoverableSecurityException to UI layer")
                throw e
            }

            Timber.e(e, "FileOperationUseCase: MediaStore delete failed for: $filePath")

            return@withContext try {
                val file = File(filePath)
                if (file.exists() && file.delete()) {
                    Timber.d("FileOperationUseCase: Exception fallback File.delete() succeeded for $filePath")
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

    suspend fun collectMediaStoreUris(files: List<File>): List<Uri> = withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext emptyList()
        }

        Timber.d("collectMediaStoreUris: Collecting URIs for ${files.size} files")

        for (file in files) {
            val filePath = file.absolutePath

            if (!isSharedStorage(filePath)) continue

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
            results.add(smbFileOperationHandler.executeDelete(operation.copy(files = otherFiles)))
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

        results.forEach { result ->
            when (result) {
                is FileOperationResult.Success -> {
                    totalSuccess += result.processedCount
                    allProcessedPaths.addAll(result.copiedFilePaths)
                }
                is FileOperationResult.PartialSuccess -> {
                    totalSuccess += result.processedCount
                    totalFailed += result.failedCount
                    allErrors.addAll(result.errors)
                    allProcessedPaths.addAll(result.deletedPaths)
                }
                is FileOperationResult.Failure -> {
                    totalFailed += operation.files.size
                    allErrors.add(result.error)
                }
                else -> {}
            }
        }

        return@withContext if (totalFailed == 0) {
            FileOperationResult.Success(totalSuccess, operation, allProcessedPaths)
        } else if (totalSuccess > 0) {
            FileOperationResult.PartialSuccess(totalSuccess, totalFailed, allErrors, allProcessedPaths)
        } else {
            FileOperationResult.Failure(allErrors.joinToString("; "))
        }
    }
}
