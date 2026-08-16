package com.sza.fastmediasorter.data.transfer

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A universal handler for file operations (Copy, Move, Delete) across any supported protocol (Local, SMB, SFTP, FTP).
 * It delegates the actual transfer logic to specific [TransferStrategy] implementations.
 */
@Singleton
class UniversalFileOperationHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val strategies: Set<@JvmSuppressWildcards TransferStrategy>,
    private val fileAccessProviders: Set<@JvmSuppressWildcards FileAccess>
) {

    suspend fun executeCopy(
        operation: FileOperation.Copy,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val successPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEach { source ->
            val result = performTransfer(
                source, 
                operation.destination, 
                operation.overwrite, 
                progressCallback,
                isMove = false,
                sourceCredentialsId = operation.sourceCredentialsId
            )
            processResult(result, successPaths, errors)?.let { successCount += it }
        }

        buildFinalResult(successCount, operation.sources.size, errors, successPaths, operation)
    }

    suspend fun executeMove(
        operation: FileOperation.Move,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val successPaths = mutableListOf<String>()
        var successCount = 0

        operation.sources.forEach { source ->
            val result = performTransfer(
                source, 
                operation.destination, 
                operation.overwrite, 
                progressCallback,
                isMove = true,
                sourceCredentialsId = operation.sourceCredentialsId
            )
            processResult(result, successPaths, errors)?.let { successCount += it }
        }

        buildFinalResult(successCount, operation.sources.size, errors, successPaths, operation)
    }

    suspend fun executeDelete(
        operation: FileOperation.Delete
    ): FileOperationResult = withContext(Dispatchers.IO) {
         val errors = mutableListOf<String>()
         var successCount = 0
         
         operation.files.forEach { source ->
             val uri = PathUtils.safeParseUri(source.path)
             val scheme = uri.scheme
             val provider = fileAccessProviders.find { it.supports(scheme) }
             
             if (provider != null) {
                 if (provider.delete(uri)) {
                     successCount++
                 } else {
                     errors.add("Failed to delete ${source.name}")
                 }
             } else {
                 errors.add("No file access provider for scheme $scheme")
             }
         }
         
         buildFinalResult(successCount, operation.files.size, errors, listOf(), operation)
    }

    private suspend fun performTransfer(
        source: File,
        destination: File,
        overwrite: Boolean,
        progressCallback: ByteProgressCallback?,
        isMove: Boolean,
        sourceCredentialsId: String?
    ): FileOperationResult {
         try {
            val sourceUri = PathUtils.safeParseUri(source.path)
            val destUri = PathUtils.safeParseUri(destination.path)
            
            // Fix destination URI construction: append filename if destination is a directory
            val fullDestUri = buildDestUri(destUri, source.name)

            val sourceScheme = sourceUri.scheme
            val destScheme = fullDestUri.scheme

            val strategy = strategies.find { it.supports(sourceScheme, destScheme) }
            
            if (strategy == null) {
                return FileOperationResult.Failure("No strategy for $sourceScheme -> $destScheme")
            }

            if (isMove) {
                try {
                    // Try optimized move
                    val moveSuccess = strategy.move(sourceUri, fullDestUri, overwrite, sourceCredentialsId, progressCallback)
                    if (moveSuccess) {
                        return FileOperationResult.Success(1, FileOperation.Move(listOf(source), destination, overwrite), listOf(fullDestUri.toString()))
                    }
                    return FileOperationResult.Failure("Move operation failed")
                } catch (e: UnsupportedOperationException) {
                    // Fallback to Copy + Delete
                }
            }

            // Copy logic (for Copy op OR Move fallback)
            val copySuccess = strategy.copy(sourceUri, fullDestUri, overwrite, sourceCredentialsId, progressCallback)
            
            if (copySuccess) {
                if (isMove) {
                    // Delete source for move operation
                    val deleteProvider = fileAccessProviders.find { it.supports(sourceScheme) }
                    if (deleteProvider?.delete(sourceUri) == true) {
                        return FileOperationResult.Success(1, FileOperation.Move(listOf(source), destination, overwrite), listOf(fullDestUri.toString()))
                    } else {
                        return FileOperationResult.PartialSuccess(1, 1, listOf("Copy successful but failed to delete source"))
                    }
                }
                return FileOperationResult.Success(1, FileOperation.Copy(listOf(source), destination, overwrite), listOf(fullDestUri.toString()))
            }
            
            return FileOperationResult.Failure("Copy operation failed")

        } catch (e: Exception) {
            return FileOperationResult.Failure("Error: ${e.message}")
        }
    }

    // Helper functions
    private fun processResult(result: FileOperationResult, paths: MutableList<String>, errors: MutableList<String>): Int? {
        return when (result) {
            is FileOperationResult.Success -> {
                paths.addAll(result.copiedFilePaths)
                result.processedCount
            }
            is FileOperationResult.Failure -> {
                errors.add(result.error)
                0
            }
             is FileOperationResult.PartialSuccess -> {
                 errors.addAll(result.errors)
                 result.processedCount
             }
             else -> 0
        }
    }
    
    private fun buildFinalResult(success: Int, total: Int, errors: List<String>, paths: List<String>, op: Any): FileOperationResult {
         return when {
            success == total -> FileOperationResult.Success(success, op as FileOperation, paths)
            success > 0 -> FileOperationResult.PartialSuccess(success, errors.size, errors)
            else -> {
                val errorMessage = errors.joinToString("\n")
                val errorRes = when (op) {
                    is FileOperation.Copy -> R.string.all_copy_operations_failed
                    is FileOperation.Move -> R.string.all_move_operations_failed
                    is FileOperation.Delete -> R.string.all_delete_operations_failed
                    else -> R.string.error_all_operations_failed
                }

                FileOperationResult.Failure(
                    error = context.getString(errorRes, errorMessage),
                    errorRes = errorRes,
                    formatArgs = listOf(errorMessage)
                )
            }
        }
    }
    
    private fun buildDestUri(folderUri: Uri, filename: String): Uri {
        val folderPath = folderUri.toString()
        val separator = if (folderPath.endsWith("/")) "" else "/"
        return PathUtils.safeParseUri(folderPath + separator + filename)
    }
}
