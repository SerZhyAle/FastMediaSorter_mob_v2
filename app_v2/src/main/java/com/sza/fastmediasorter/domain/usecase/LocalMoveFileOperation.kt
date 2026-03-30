package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.os.Build
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import timber.log.Timber
import java.io.File
import java.io.IOException

internal class LocalMoveFileOperation(
    private val context: Context,
    private val scanNewFile: (String) -> Unit,
    private val deleteViaMediaStore: suspend (String) -> Boolean,
    private val isSharedStorage: (String) -> Boolean
) {

    suspend fun execute(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult {
        Timber.d("executeMove: Starting local move of ${operation.sources.size} files to ${operation.destination.absolutePath}")

        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0
        var skippedCount = 0
        val skippedPaths = mutableListOf<String>()
        val total = operation.sources.size

        operation.sources.forEachIndexed { index, source ->
            Timber.d("executeMove: [${index + 1}/$total] Processing ${source.name}")
            progressCallback?.onFileStarted(index + 1, source.name, total)

            val sourcePath = source.path
            val isContentUri = sourcePath.startsWith("content:/")

            try {
                if (isContentUri) {
                    val normalizedUri = if (sourcePath.startsWith("content://")) sourcePath
                                       else sourcePath.replaceFirst("content:/", "content://")
                    val uri = Uri.parse(normalizedUri)

                    val fileName = try {
                        val decoded = Uri.decode(sourcePath)
                        decoded.substringAfterLast("/").substringAfterLast("%2F")
                    } catch (e: Exception) {
                        source.name
                    }

                    val destFile = File(operation.destination, fileName)

                    if (destFile.exists() && !operation.overwrite) {
                        val destinationName = destFile.parentFile?.name ?: operation.destination.name
                        Timber.i("executeMove: SKIPPED SAF - $fileName (already exists in $destinationName)")
                        skippedCount++
                        skippedPaths.add(destFile.absolutePath)
                        return@forEachIndexed
                    }

                    val startTime = System.currentTimeMillis()

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open SAF URI")

                    val copyDuration = System.currentTimeMillis() - startTime
                    Timber.d("executeMove: SAF copy completed in ${copyDuration}ms, attempting delete")

                    val deleted = try {
                        com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(
                            context, normalizedUri, "FileOperationUseCase.executeMove"
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "executeMove: Failed to delete SAF source")
                        false
                    }

                    val totalDuration = System.currentTimeMillis() - startTime
                    movedPaths.add(destFile.absolutePath)
                    successCount++
                    if (deleted) {
                        Timber.i("executeMove: SUCCESS - SAF $fileName moved in ${totalDuration}ms")
                    } else {
                        Timber.w("executeMove: SAF $fileName copied in ${totalDuration}ms but source delete failed - manual cleanup needed")
                    }
                    scanNewFile(destFile.absolutePath)
                    return@forEachIndexed
                }

                val destFile = File(operation.destination, source.name)

                if (source.absolutePath == destFile.absolutePath) {
                    Timber.w("executeMove: Source and destination are the same file - skipping ${source.name}")
                    successCount++
                    movedPaths.add(destFile.absolutePath)
                    return@forEachIndexed
                }

                if (!source.exists()) {
                    val error = "${source.name}\n  Source: ${source.absolutePath}\n  Error: File not found"
                    Timber.e("executeMove: $error")
                    errors.add(error)
                    return@forEachIndexed
                }

                Timber.d("executeMove: Moving ${source.absolutePath} to ${destFile.absolutePath}")

                if (destFile.exists() && !operation.overwrite) {
                    val destinationName = destFile.parentFile?.name ?: operation.destination.name
                    Timber.i("executeMove: SKIPPED - ${source.name} (already exists in $destinationName)")
                    skippedCount++
                    skippedPaths.add(destFile.absolutePath)
                    return@forEachIndexed
                }

                val startTime = System.currentTimeMillis()

                if (!destFile.exists() && source.renameTo(destFile)) {
                    val duration = System.currentTimeMillis() - startTime
                    movedPaths.add(destFile.absolutePath)
                    successCount++
                    Timber.i("executeMove: SUCCESS via rename - ${source.name} moved in ${duration}ms")
                    scanNewFile(destFile.absolutePath)
                } else {
                    Timber.d("executeMove: Rename failed, trying copy+delete for ${source.name}")

                    source.copyTo(destFile, operation.overwrite)
                    val copyDuration = System.currentTimeMillis() - startTime
                    Timber.d("executeMove: Copy completed in ${copyDuration}ms, attempting delete")

                    val deleted = if (isSharedStorage(source.absolutePath) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        deleteViaMediaStore(source.absolutePath)
                    } else {
                        source.delete()
                    }

                    if (deleted) {
                        val totalDuration = System.currentTimeMillis() - startTime
                        movedPaths.add(destFile.absolutePath)
                        successCount++
                        Timber.i("executeMove: SUCCESS via copy+delete - ${source.name} moved in ${totalDuration}ms")
                        scanNewFile(destFile.absolutePath)
                    } else {
                        val error = FileOperationError.formatTransferError(
                            source.name,
                            source.absolutePath,
                            destFile.absolutePath,
                            "Failed to delete source after copy"
                        )
                        Timber.e("executeMove: $error - copied file remains at ${destFile.absolutePath}")
                        errors.add(error)
                    }
                }

            } catch (e: FileOperationUseCase.BatchDeletePermissionRequiredException) {
                throw e
            } catch (e: android.app.RecoverableSecurityException) {
                throw e
            } catch (e: Exception) {
                val error = FileOperationError.formatTransferError(
                    source.name,
                    source.absolutePath,
                    File(operation.destination, source.name).absolutePath,
                    FileOperationError.extractErrorMessage(e)
                )
                Timber.e(e, "executeMove: ERROR - $error")
                errors.add(error)
            }
        }

        val totalProcessed = successCount + skippedCount
        return when {
            totalProcessed == operation.sources.size -> {
                Timber.i("executeMove: All ${operation.sources.size} files processed (moved: $successCount, skipped: $skippedCount)")
                FileOperationResult.Success(successCount, operation, movedPaths, skippedCount, skippedPaths)
            }
            totalProcessed > 0 -> {
                Timber.w("executeMove: Partial success - $totalProcessed/${operation.sources.size} processed. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors, movedPaths, skippedCount, skippedPaths)
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
}
