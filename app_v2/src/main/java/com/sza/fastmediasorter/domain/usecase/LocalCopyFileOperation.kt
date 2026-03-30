package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import timber.log.Timber
import java.io.File
import java.io.IOException

internal class LocalCopyFileOperation(
    private val context: Context,
    private val scanNewFile: (String) -> Unit
) {

    suspend fun execute(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult {
        Timber.d("executeCopy: Starting local copy of ${operation.sources.size} files to ${operation.destination.absolutePath}")

        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0
        var skippedCount = 0
        val skippedPaths = mutableListOf<String>()
        val total = operation.sources.size

        operation.sources.forEachIndexed { index, source ->
            Timber.d("executeCopy: [${index + 1}/$total] Processing ${source.name}")
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
                        Timber.i("executeCopy: SKIPPED SAF - $fileName (already exists in $destinationName)")
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

                    val duration = System.currentTimeMillis() - startTime
                    copiedPaths.add(destFile.absolutePath)
                    successCount++
                    Timber.i("executeCopy: SUCCESS - SAF $fileName copied in ${duration}ms")
                    scanNewFile(destFile.absolutePath)
                    return@forEachIndexed
                }

                val destFile = File(operation.destination, source.name)

                if (source.absolutePath == destFile.absolutePath) {
                    Timber.w("executeCopy: Source and destination are the same file - skipping ${source.name}")
                    successCount++
                    copiedPaths.add(destFile.absolutePath)
                    return@forEachIndexed
                }

                if (!source.exists()) {
                    val error = "${source.name}\n  Source: ${source.absolutePath}\n  Error: File not found"
                    Timber.e("executeCopy: $error")
                    errors.add(error)
                    return@forEachIndexed
                }

                Timber.d("executeCopy: Target: ${destFile.absolutePath}, size=${source.length()} bytes")

                if (destFile.exists() && !operation.overwrite) {
                    val destinationName = destFile.parentFile?.name ?: operation.destination.name
                    Timber.i("executeCopy: SKIPPED - ${source.name} (already exists in $destinationName)")
                    skippedCount++
                    skippedPaths.add(destFile.absolutePath)
                    return@forEachIndexed
                }

                val startTime = System.currentTimeMillis()
                source.copyTo(destFile, operation.overwrite)
                val duration = System.currentTimeMillis() - startTime

                copiedPaths.add(destFile.absolutePath)
                successCount++
                Timber.i("executeCopy: SUCCESS - ${source.name} copied in ${duration}ms")
                scanNewFile(destFile.absolutePath)

            } catch (e: Exception) {
                val error = FileOperationError.formatTransferError(
                    source.name,
                    source.absolutePath,
                    operation.destination.absolutePath,
                    FileOperationError.extractErrorMessage(e)
                )
                Timber.e(e, "executeCopy: ERROR - $error")
                errors.add(error)
            }
        }

        val totalProcessed = successCount + skippedCount
        return when {
            totalProcessed == operation.sources.size -> {
                Timber.i("executeCopy: All ${operation.sources.size} files processed (copied: $successCount, skipped: $skippedCount)")
                FileOperationResult.Success(successCount, operation, copiedPaths, skippedCount, skippedPaths)
            }
            totalProcessed > 0 -> {
                Timber.w("executeCopy: Partial success - $totalProcessed/${operation.sources.size} processed. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors, copiedPaths, skippedCount, skippedPaths)
            }
            else -> {
                Timber.e("executeCopy: All copy operations failed. Errors: $errors")
                val errorMessage = errors.joinToString("\n")
                FileOperationResult.Failure(
                    error = context.getString(R.string.all_copy_operations_failed, errorMessage),
                    errorRes = R.string.all_copy_operations_failed,
                    formatArgs = listOf(errorMessage)
                )
            }
        }
    }
}
