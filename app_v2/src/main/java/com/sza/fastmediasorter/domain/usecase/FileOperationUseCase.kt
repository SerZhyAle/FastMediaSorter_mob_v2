package com.sza.fastmediasorter.domain.usecase

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.media.MediaScannerConnection
import android.provider.DocumentsContract
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.domain.transfer.FileOperationError
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

sealed class FileOperation {
    data class Copy(val sources: List<File>, val destination: File, val overwrite: Boolean, val sourceCredentialsId: String? = null) : FileOperation()
    data class Move(val sources: List<File>, val destination: File, val overwrite: Boolean, val sourceCredentialsId: String? = null) : FileOperation()
    data class Rename(val file: File, val newName: String) : FileOperation()
    data class Delete(val files: List<File>, val softDelete: Boolean = true) : FileOperation() // softDelete: move to trash instead of permanent delete
}

sealed class FileOperationResult {
    data class Success(
        val processedCount: Int, 
        val operation: FileOperation,
        val copiedFilePaths: List<String> = emptyList(), // Paths of destination files for undo
        val skippedCount: Int = 0,
        val skippedPaths: List<String> = emptyList()
    ) : FileOperationResult()
    data class PartialSuccess(
        val processedCount: Int, 
        val failedCount: Int, 
        val errors: List<String>,
        val deletedPaths: List<String> = emptyList(), // Paths of actually deleted/moved files
        val skippedCount: Int = 0,
        val skippedPaths: List<String> = emptyList()
    ) : FileOperationResult()
    data class Failure(
        val error: String,
        val errorRes: Int? = null,
        val formatArgs: List<Any> = emptyList()
    ) : FileOperationResult()
    
    /**
     * Cloud provider requires re-authentication
     * UI should prompt user to re-authenticate via AddResourceActivity
     */
    data class AuthenticationRequired(val provider: String, val message: String) : FileOperationResult()
    
    /**
     * Batch delete permission required (Android 11+)
     * Contains PendingIntent to request user permission for batch delete
     */
    data class PermissionRequired(val pendingIntent: PendingIntent, val fileUris: List<Uri>) : FileOperationResult()
}

/**
 * Progress updates for file operations
 */
sealed class FileOperationProgress {
    data class Starting(val operation: FileOperation, val totalFiles: Int) : FileOperationProgress()
    data class Processing(
        val currentFile: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val bytesTransferred: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBytesPerSecond: Long = 0L
    ) : FileOperationProgress()
    data class Completed(val result: FileOperationResult) : FileOperationProgress()
}

data class OperationHistory(
    val operation: FileOperation,
    val result: FileOperationResult,
    val timestamp: Long = System.currentTimeMillis()
)

class FileOperationUseCase @Inject constructor(
    private val context: Context,
    private val smbFileOperationHandler: SmbFileOperationHandler,
    private val sftpFileOperationHandler: SftpFileOperationHandler,
    private val ftpFileOperationHandler: FtpFileOperationHandler,
    private val cloudFileOperationHandler: CloudFileOperationHandler
) {
    
    private var lastOperation: OperationHistory? = null
    
    /**
     * Execute file operation with progress updates emitted via Flow
     * Use this method when you need to show progress UI during long operations
     * Supports cancellation via coroutine job cancellation
     */
    fun executeWithProgress(operation: FileOperation): Flow<FileOperationProgress> = channelFlow {
        val contextElement = CorrelationContext.asContextElement(
             operation = "file-operation",
             extras = mapOf("opType" to operation.javaClass.simpleName)
        )
        
        withContext(contextElement) {
            StructuredLogger.d("START executeWithProgress")
        
        val totalFiles = when (operation) {
            is FileOperation.Copy -> operation.sources.size
            is FileOperation.Move -> operation.sources.size
            is FileOperation.Delete -> operation.files.size
            is FileOperation.Rename -> 1
        }
        
        send(FileOperationProgress.Starting(operation, totalFiles))
        
        // Update current file tracking based on operation type
        var currentFileIndex = 1
        var currentFileName = when (operation) {
            is FileOperation.Copy -> operation.sources.firstOrNull()?.name ?: ""
            is FileOperation.Move -> operation.sources.firstOrNull()?.name ?: ""
            is FileOperation.Delete -> operation.files.firstOrNull()?.name ?: ""
            is FileOperation.Rename -> operation.file.name
        }
        
        // Create progress callback that sends to channel (thread-safe)
        val progressCallback = object : ByteProgressCallback {
            override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
                // Use trySend to avoid blocking if channel is full
                trySend(FileOperationProgress.Processing(
                    currentFile = currentFileName,
                    currentIndex = currentFileIndex - 1,
                    totalFiles = totalFiles,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = speedBytesPerSecond
                ))
            }

            override suspend fun onFileStarted(index: Int, fileName: String, total: Int) {
                currentFileIndex = index
                currentFileName = fileName
                // Send an immediate Processing update so the dialog shows the new file
                trySend(FileOperationProgress.Processing(
                    currentFile = fileName,
                    currentIndex = index - 1,
                    totalFiles = total,
                    bytesTransferred = 0L,
                    totalBytes = 0L,
                    speedBytesPerSecond = 0L
                ))
            }
        }
        
        // Execute operation in separate coroutine to allow progress updates
        val resultDeferred = launch(Dispatchers.IO) {
            val result = executeInternal(operation, progressCallback)
            send(FileOperationProgress.Completed(result))
        }
        
        // Wait for completion
        resultDeferred.join()
        }
    }

    
    /**
     * Internal execution without withContext (called from flow with flowOn)
     */
    private suspend fun executeInternal(
        operation: FileOperation,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult {
        Timber.d("FileOperation: Starting operation: ${operation.javaClass.simpleName}")
        
        try {
            // Helper to check if path is network resource (use path instead of absolutePath to avoid /prefix)
            fun File.isNetworkPath(protocol: String): Boolean {
                val pathStr = this.path
                val result = pathStr.startsWith("$protocol://") || 
                             pathStr.startsWith("/$protocol://") || 
                             pathStr.startsWith("/$protocol:/") ||
                             pathStr.startsWith("$protocol:/")  // Single colon case
                Timber.d("FileOperation.isNetworkPath: path='$pathStr', protocol='$protocol', result=$result")
                return result
            }
            
            // Check if operation involves SMB or SFTP paths
            val hasSmbPath = when (operation) {
                is FileOperation.Copy -> {
                    val sourceSmbCount = operation.sources.count { it.isNetworkPath("smb") }
                    val destIsSmb = operation.destination.isNetworkPath("smb")
                    Timber.d("FileOperation.Copy: sources=$sourceSmbCount/${operation.sources.size} SMB, dest=${if (destIsSmb) "SMB" else "Local"}")
                    sourceSmbCount > 0 || destIsSmb
                }
                is FileOperation.Move -> {
                    val sourceSmbCount = operation.sources.count { it.isNetworkPath("smb") }
                    val destIsSmb = operation.destination.isNetworkPath("smb")
                    Timber.d("FileOperation.Move: sources=$sourceSmbCount/${operation.sources.size} SMB, dest=${if (destIsSmb) "SMB" else "Local"}")
                    sourceSmbCount > 0 || destIsSmb
                }
                is FileOperation.Delete -> {
                    val smbCount = operation.files.count { it.isNetworkPath("smb") }
                    Timber.d("FileOperation.Delete: $smbCount/${operation.files.size} SMB files")
                    smbCount > 0
                }
                is FileOperation.Rename -> {
                    val isSmb = operation.file.isNetworkPath("smb")
                    Timber.d("FileOperation.Rename: file=${if (isSmb) "SMB" else "Local"}")
                    isSmb
                }
            }

            val hasSftpPath = when (operation) {
                is FileOperation.Copy -> {
                    val sourceSftpCount = operation.sources.count { it.isNetworkPath("sftp") }
                    val destIsSftp = operation.destination.isNetworkPath("sftp")
                    Timber.d("FileOperation.Copy: sources=$sourceSftpCount/${operation.sources.size} SFTP, dest=${if (destIsSftp) "SFTP" else "Local"}")
                    sourceSftpCount > 0 || destIsSftp
                }
                is FileOperation.Move -> {
                    val sourceSftpCount = operation.sources.count { it.isNetworkPath("sftp") }
                    val destIsSftp = operation.destination.isNetworkPath("sftp")
                    Timber.d("FileOperation.Move: sources=$sourceSftpCount/${operation.sources.size} SFTP, dest=${if (destIsSftp) "SFTP" else "Local"}")
                    sourceSftpCount > 0 || destIsSftp
                }
                is FileOperation.Delete -> {
                    val sftpCount = operation.files.count { it.isNetworkPath("sftp") }
                    Timber.d("FileOperation.Delete: $sftpCount/${operation.files.size} SFTP files")
                    sftpCount > 0
                }
                is FileOperation.Rename -> {
                    val isSftp = operation.file.isNetworkPath("sftp")
                    Timber.d("FileOperation.Rename: file=${if (isSftp) "SFTP" else "Local"}")
                    isSftp
                }
            }

            val hasFtpPath = when (operation) {
                is FileOperation.Copy -> {
                    val sourceFtpCount = operation.sources.count { it.isNetworkPath("ftp") }
                    val destIsFtp = operation.destination.isNetworkPath("ftp")
                    Timber.d("FileOperation.Copy: sources=$sourceFtpCount/${operation.sources.size} FTP, dest=${if (destIsFtp) "FTP" else "Local"}")
                    sourceFtpCount > 0 || destIsFtp
                }
                is FileOperation.Move -> {
                    val sourceFtpCount = operation.sources.count { it.isNetworkPath("ftp") }
                    val destIsFtp = operation.destination.isNetworkPath("ftp")
                    Timber.d("FileOperation.Move: sources=$sourceFtpCount/${operation.sources.size} FTP, dest=${if (destIsFtp) "FTP" else "Local"}")
                    sourceFtpCount > 0 || destIsFtp
                }
                is FileOperation.Delete -> {
                    val ftpCount = operation.files.count { it.isNetworkPath("ftp") }
                    Timber.d("FileOperation.Delete: $ftpCount/${operation.files.size} FTP files")
                    ftpCount > 0
                }
                is FileOperation.Rename -> {
                    val isFtp = operation.file.isNetworkPath("ftp")
                    Timber.d("FileOperation.Rename: file=${if (isFtp) "FTP" else "Local"}")
                    isFtp
                }
            }

            val hasCloudPath = when (operation) {
                is FileOperation.Copy -> {
                    val sourceCloudCount = operation.sources.count { it.isNetworkPath("cloud") }
                    val destIsCloud = operation.destination.isNetworkPath("cloud")
                    Timber.d("FileOperation.Copy: sources=$sourceCloudCount/${operation.sources.size} Cloud, dest=${if (destIsCloud) "Cloud" else "Local"}")
                    sourceCloudCount > 0 || destIsCloud
                }
                is FileOperation.Move -> {
                    val sourceCloudCount = operation.sources.count { it.isNetworkPath("cloud") }
                    val destIsCloud = operation.destination.isNetworkPath("cloud")
                    Timber.d("FileOperation.Move: sources=$sourceCloudCount/${operation.sources.size} Cloud, dest=${if (destIsCloud) "Cloud" else "Local"}")
                    sourceCloudCount > 0 || destIsCloud
                }
                is FileOperation.Delete -> {
                    val cloudCount = operation.files.count { it.isNetworkPath("cloud") }
                    Timber.d("FileOperation.Delete: $cloudCount/${operation.files.size} Cloud files")
                    cloudCount > 0
                }
                is FileOperation.Rename -> {
                    val isCloud = operation.file.isNetworkPath("cloud")
                    Timber.d("FileOperation.Rename: file=${if (isCloud) "Cloud" else "Local"}")
                    isCloud
                }
            }

            val result = when {
                hasCloudPath -> {
                    Timber.d("FileOperation: Using Cloud handler")
                    // Use Cloud handler for operations involving cloud paths
                    when (operation) {
                        is FileOperation.Copy -> cloudFileOperationHandler.executeCopy(operation, progressCallback)
                        is FileOperation.Move -> cloudFileOperationHandler.executeMove(operation, progressCallback)
                        is FileOperation.Delete -> cloudFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> cloudFileOperationHandler.executeRename(operation)
                    }
                }
                hasSmbPath && hasSftpPath -> {
                    // Mixed operation SMB↔SFTP: use destination protocol as priority
                    val useSmb = when (operation) {
                        is FileOperation.Copy -> operation.destination.isNetworkPath("smb")
                        is FileOperation.Move -> operation.destination.isNetworkPath("smb")
                        else -> hasSmbPath // For Delete/Rename, use first detected protocol
                    }
                    
                    if (useSmb) {
                        Timber.d("FileOperation: Mixed SMB↔SFTP - using SMB handler (dest=SMB)")
                        when (operation) {
                            is FileOperation.Copy -> smbFileOperationHandler.executeCopy(operation, progressCallback)
                            is FileOperation.Move -> smbFileOperationHandler.executeMove(operation, progressCallback)
                            is FileOperation.Delete -> smbFileOperationHandler.executeDelete(operation)
                            is FileOperation.Rename -> smbFileOperationHandler.executeRename(operation)
                        }
                    } else {
                        Timber.d("FileOperation: Mixed SMB↔SFTP - using SFTP handler (dest=SFTP)")
                        when (operation) {
                            is FileOperation.Copy -> sftpFileOperationHandler.executeCopy(operation, progressCallback)
                            is FileOperation.Move -> sftpFileOperationHandler.executeMove(operation, progressCallback)
                            is FileOperation.Delete -> sftpFileOperationHandler.executeDelete(operation)
                            is FileOperation.Rename -> sftpFileOperationHandler.executeRename(operation)
                        }
                    }
                }
                hasSmbPath && hasFtpPath -> {
                    // Mixed operation SMB↔FTP: FTP doesn't support cross-protocol, use SMB handler to download first
                    Timber.d("FileOperation: Mixed SMB↔FTP - using SMB handler (FTP can't handle cross-protocol)")
                    when (operation) {
                        is FileOperation.Copy -> smbFileOperationHandler.executeCopy(operation, progressCallback)
                        is FileOperation.Move -> smbFileOperationHandler.executeMove(operation, progressCallback)
                        is FileOperation.Delete -> smbFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> smbFileOperationHandler.executeRename(operation)
                    }
                }
                hasSftpPath && hasFtpPath -> {
                    // Mixed operation SFTP↔FTP: FTP doesn't support cross-protocol, use SFTP handler to download first
                    Timber.d("FileOperation: Mixed SFTP↔FTP - using SFTP handler (FTP can't handle cross-protocol)")
                    when (operation) {
                        is FileOperation.Copy -> sftpFileOperationHandler.executeCopy(operation, progressCallback)
                        is FileOperation.Move -> sftpFileOperationHandler.executeMove(operation, progressCallback)
                        is FileOperation.Delete -> sftpFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> sftpFileOperationHandler.executeRename(operation)
                    }
                }
                hasFtpPath -> {
                    Timber.d("FileOperation: Using FTP handler")
                    // Use FTP handler for operations involving FTP paths (local↔FTP or FTP↔FTP)
                    when (operation) {
                        is FileOperation.Copy -> ftpFileOperationHandler.executeCopy(operation, progressCallback)
                        is FileOperation.Move -> ftpFileOperationHandler.executeMove(operation, progressCallback)
                        is FileOperation.Delete -> ftpFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> ftpFileOperationHandler.executeRename(operation)
                    }
                }
                hasSmbPath -> {
                    Timber.d("FileOperation: Using SMB handler")
                    // Use SMB handler for operations involving SMB paths
                    when (operation) {
                        is FileOperation.Copy -> smbFileOperationHandler.executeCopy(operation, progressCallback)
                        is FileOperation.Move -> smbFileOperationHandler.executeMove(operation, progressCallback)
                        is FileOperation.Delete -> smbFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> smbFileOperationHandler.executeRename(operation)
                    }
                }
                hasSftpPath -> {
                    Timber.d("FileOperation: Using SFTP handler")
                    // Use SFTP handler for operations involving SFTP paths
                    when (operation) {
                        is FileOperation.Copy -> sftpFileOperationHandler.executeCopy(operation, progressCallback)
                        is FileOperation.Move -> sftpFileOperationHandler.executeMove(operation, progressCallback)
                        is FileOperation.Delete -> sftpFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> sftpFileOperationHandler.executeRename(operation)
                    }
                }
                else -> {
                    Timber.d("FileOperation: Using local file operations")
                    // Use local file operations
                    when (operation) {
                        is FileOperation.Copy -> executeCopy(operation, progressCallback)
                        is FileOperation.Move -> executeMove(operation, progressCallback)
                        is FileOperation.Rename -> executeRename(operation)
                        is FileOperation.Delete -> executeDelete(operation)
                    }
                }
            }
            
            when (result) {
                is FileOperationResult.Success -> {
                    if (result.skippedCount > 0) StructuredLogger.i("SUCCESS (with skips)", "count" to result.processedCount, "skipped" to result.skippedCount)
                    else StructuredLogger.i("SUCCESS", "count" to result.processedCount)
                }
                is FileOperationResult.PartialSuccess -> StructuredLogger.w("PARTIAL SUCCESS", "processed" to result.processedCount, "failed" to result.failedCount, "skipped" to result.skippedCount)
                is FileOperationResult.Failure -> StructuredLogger.e("FAILURE", "error" to result.error)
                is FileOperationResult.AuthenticationRequired -> StructuredLogger.w("AUTH REQUIRED", "provider" to result.provider)
                is FileOperationResult.PermissionRequired -> StructuredLogger.i("PERMISSION REQUIRED", "uris" to result.fileUris.size)
            }
            
            lastOperation = OperationHistory(operation, result)
            return result
            
        } catch (e: BatchDeletePermissionRequiredException) {
            // Handle batch delete permission specially
            StructuredLogger.i("Batch delete permission required")
            val result = FileOperationResult.PermissionRequired(e.pendingIntent, e.uris)
            lastOperation = OperationHistory(operation, result)
            return result
        } catch (e: android.app.RecoverableSecurityException) {
            // Handle Android 10 RecoverableSecurityException
            StructuredLogger.i("RecoverableSecurityException caught")
            val result = FileOperationResult.PermissionRequired(e.userAction.actionIntent, emptyList())
            lastOperation = OperationHistory(operation, result)
            return result
        } catch (e: Exception) {
            StructuredLogger.e(e, "EXCEPTION in executeInternal")
            return FileOperationResult.Failure("${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    suspend fun execute(
        operation: FileOperation,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO + CorrelationContext.asContextElement("file-operation-sync")) {
        executeInternal(operation, progressCallback)
    }
    
    private suspend fun executeCopy(
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
                    // Handle SAF source: copy via ContentResolver
                    val normalizedUri = if (sourcePath.startsWith("content://")) sourcePath 
                                       else sourcePath.replaceFirst("content:/", "content://")
                    val uri = Uri.parse(normalizedUri)
                    
                    // Extract clean filename from URI
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
                
                // Regular file path handling
                val destFile = File(operation.destination, source.name)

                // Safety check: Don't copy file to itself
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
        val result = when {
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
        
        return result
    }

    private fun scanNewFile(path: String) {
        com.sza.fastmediasorter.utils.MediaStoreNotifier.notifyFile(context, path, "file-operation")
    }
    
    private suspend fun executeMove(
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
                    // Handle SAF source: copy via ContentResolver then delete
                    val normalizedUri = if (sourcePath.startsWith("content://")) sourcePath 
                                       else sourcePath.replaceFirst("content:/", "content://")
                    val uri = Uri.parse(normalizedUri)
                    
                    // Extract clean filename from URI
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
                    
                    // Copy from SAF to destination
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open SAF URI")
                    
                    val copyDuration = System.currentTimeMillis() - startTime
                    Timber.d("executeMove: SAF copy completed in ${copyDuration}ms, attempting delete")
                    
                    // Delete source SAF file using SafHelper (supports tree document URIs)
                    val deleted = try {
                        com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(
                            context, normalizedUri, "FileOperationUseCase.executeMove"
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "executeMove: Failed to delete SAF source")
                        false
                    }
                    
                    if (deleted) {
                        val totalDuration = System.currentTimeMillis() - startTime
                        movedPaths.add(destFile.absolutePath)
                        successCount++
                        Timber.i("executeMove: SUCCESS - SAF $fileName moved in ${totalDuration}ms")
                        scanNewFile(destFile.absolutePath)
                    } else {
                        // File was copied but not deleted - treat as partial success
                        val totalDuration = System.currentTimeMillis() - startTime
                        movedPaths.add(destFile.absolutePath)
                        successCount++
                        Timber.w("executeMove: SAF $fileName copied in ${totalDuration}ms but source delete failed - manual cleanup needed")
                    }
                    return@forEachIndexed
                }
                
                // Regular file path handling
                val destFile = File(operation.destination, source.name)

                // Safety check: Don't move file to itself
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
                
                // Try rename first (faster for same filesystem)
                // CRITICAL: renameTo silently overwrites on Android, so check first
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
                    
                    val deleted = if (isSharedStorage(source.absolutePath) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
                
            } catch (e: BatchDeletePermissionRequiredException) {
                // Re-throw permission exceptions to be handled by parent
                throw e
            } catch (e: android.app.RecoverableSecurityException) {
                // Re-throw permission exceptions to be handled by parent
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
        val result = when {
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
        
        return result
    }
    
    private fun executeRename(operation: FileOperation.Rename): FileOperationResult {
        try {
            val filePath = operation.file.path
            
            // Check if this is a SAF/content URI
            if (filePath.startsWith("content:/")) {
                val normalizedUri = if (filePath.startsWith("content://")) filePath 
                                   else filePath.replaceFirst("content:/", "content://")
                val uri = Uri.parse(normalizedUri)
                
                return try {
                    val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, operation.newName)
                    if (newUri != null) {
                        FileOperationResult.Success(1, operation, listOf(newUri.toString()))
                    } else {
                        FileOperationResult.Failure("Failed to rename SAF document: ${operation.file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SAF rename failed")
                    FileOperationResult.Failure("SAF rename error: ${e.message}")
                }
            }
            
            // Regular file path handling
            if (!operation.file.exists()) {
                return FileOperationResult.Failure("File not found: ${operation.file.name}")
            }
            
            // For network paths (SMB/S/FTP), manually construct new path
            // filePath already declared above
            val newFile = if (filePath.startsWith("smb://") || filePath.startsWith("sftp://") || filePath.startsWith("ftp://")) {
                val lastSlashIndex = filePath.lastIndexOf('/')
                val parentPath = filePath.substring(0, lastSlashIndex)
                val newPath = "$parentPath/${operation.newName}"
                object : File(newPath) {
                    override fun getPath(): String = newPath
                    override fun getAbsolutePath(): String = newPath
                }
            } else {
                File(operation.file.parent, operation.newName)
            }
            
            if (newFile.exists()) {
                return FileOperationResult.Failure(context.getString(R.string.file_already_exists, operation.newName))
            }
            
            if (operation.file.renameTo(newFile)) {
                scanNewFile(newFile.absolutePath)
                return FileOperationResult.Success(1, operation, listOf(newFile.absolutePath))
            } else {
                return FileOperationResult.Failure("Failed to rename ${operation.file.name}")
            }
            
        } catch (e: Exception) {
            return FileOperationResult.Failure("Rename error: ${e.message}")
        }
    }
    
    private suspend fun executeDelete(operation: FileOperation.Delete): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("executeDelete: START - ${operation.files.size} files, softDelete=${operation.softDelete}")
        
        // === BATCH URI COLLECTION FOR ANDROID 11+ ===
        // Collect all MediaStore URIs upfront to create ONE batch delete request
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
                    throw BatchDeletePermissionRequiredException(pendingIntent, uris)
                }
            } catch (e: BatchDeletePermissionRequiredException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "executeDelete: Batch URI collection failed, falling back to individual deletion")
            }
        }
        
        val cloudFiles = operation.files.filter { it.path.startsWith("cloud:") }
        val otherFiles = operation.files.filter { !it.path.startsWith("cloud:") }
        
        val results = mutableListOf<FileOperationResult>()
        
        // 1. Cloud Files
        if (cloudFiles.isNotEmpty()) {
            results.add(cloudFileOperationHandler.executeDelete(operation.copy(files = cloudFiles)))
        }
        
        // 2. Normal Files (Local, SMB, SFTP, FTP) - handled by SmbFileOperationHandler (which is Universal)
        // Using SmbFileOperationHandler because it integrates LocalOperationStrategy and others
        if (otherFiles.isNotEmpty()) {
            results.add(smbFileOperationHandler.executeDelete(operation.copy(files = otherFiles)))
        }
        
        // Combine results
        if (results.isEmpty()) {
            return@withContext FileOperationResult.Success(0, operation, emptyList())
        }
        
        if (results.size == 1) {
            return@withContext results.first()
        }
        
        // Merge multiple results
        var totalSuccess = 0
        var totalFailed = 0
        val allErrors = mutableListOf<String>()
        val allProcessedPaths = mutableListOf<String>() // deleted or trashed paths
        
        results.forEach { result ->
            when (result) {
                is FileOperationResult.Success -> {
                    totalSuccess += result.processedCount
                    allProcessedPaths.addAll(result.copiedFilePaths) // In delete result, this is deletedPaths/trashedPaths
                }
                is FileOperationResult.PartialSuccess -> {
                    totalSuccess += result.processedCount
                    totalFailed += result.failedCount
                    allErrors.addAll(result.errors)
                    allProcessedPaths.addAll(result.deletedPaths)
                }
                is FileOperationResult.Failure -> {
                    totalFailed += operation.files.size // Approximate, or we need to know how many in that batch
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
            val errorMsg = allErrors.joinToString("; ")
            FileOperationResult.Failure(errorMsg)
        }
    }
    
    fun getLastOperation(): OperationHistory? = lastOperation
    
    fun clearHistory() {
        lastOperation = null
    }
    
    suspend fun canUndo(): Boolean = withContext(Dispatchers.IO) {
        lastOperation != null
    }
    
    suspend fun undo(): FileOperationResult? = withContext(Dispatchers.IO) {
        val history = lastOperation ?: return@withContext null
        
        when (val op = history.operation) {
            is FileOperation.Copy -> {
                val filesToDelete = op.sources.map { File(op.destination, it.name) }
                execute(FileOperation.Delete(filesToDelete))
            }
            is FileOperation.Move -> {
                val filesToMoveBack = op.sources.mapNotNull { source ->
                    val parent = source.parentFile
                    if (parent != null) {
                        File(op.destination, source.name) to parent
                    } else {
                        null
                    }
                }.filter { it.first.exists() }
                
                if (filesToMoveBack.isEmpty()) return@withContext null
                
                execute(FileOperation.Move(
                    sources = filesToMoveBack.map { it.first },
                    destination = filesToMoveBack.first().second,
                    overwrite = true
                ))
            }
            is FileOperation.Delete -> null
            is FileOperation.Rename -> {
                // For network paths, manually construct new path
                val filePath = op.file.path
                val newFile = if (filePath.startsWith("smb://") || filePath.startsWith("sftp://") || filePath.startsWith("ftp://")) {
                    val lastSlashIndex = filePath.lastIndexOf('/')
                    val parentPath = filePath.substring(0, lastSlashIndex)
                    val newPath = "$parentPath/${op.newName}"
                    object : File(newPath) {
                        override fun getPath(): String = newPath
                        override fun getAbsolutePath(): String = newPath
                        override fun exists(): Boolean = true // Assume exists for undo
                    }
                } else {
                    File(op.file.parent, op.newName)
                }
                
                if (newFile.exists()) {
                    execute(FileOperation.Rename(newFile, op.file.name))
                } else {
                    null
                }
            }
        }
    }
    
    // Helper function for scoped storage detection
    private fun isSharedStorage(path: String): Boolean {
        return path.startsWith("/storage/emulated/0/") && 
               !path.contains("/Android/data/") &&
               !path.contains("/Android/obb/")
    }

    // Helper for MediaStore deletion
    private suspend fun deleteViaMediaStore(filePath: String): Boolean = withContext(Dispatchers.IO) {
        Timber.d("FileOperationUseCase.deleteViaMediaStore: ENTRY - filePath=$filePath, API=${android.os.Build.VERSION.SDK_INT}")
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
                Timber.d("FileOperationUseCase: Non-media file ($mimeType), skipping MediaStore")
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
            Timber.d("FileOperationUseCase: MediaStore query for $filePath found $count rows in $collection")
            
            if (cursor != null && cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val id = cursor.getLong(idColumn)
                val contentUri = android.content.ContentUris.withAppendedId(collection, id)
                cursor.close() // Close early before delete
                cursor = null
                
                Timber.d("deleteViaMediaStore: Found file in MediaStore - ID=$id, contentUri=$contentUri")
                Timber.d("deleteViaMediaStore: Attempting delete with media URI: $contentUri")
                
                // On Android 11+, use createDeleteRequest for batch delete with single user prompt
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        Timber.d("deleteViaMediaStore: Android 11+ detected - calling createDeleteRequest for file: $filePath")
                        Timber.d("deleteViaMediaStore: Creating batch delete request for URI: $contentUri")
                        // This method requests permission for batch delete with ONE dialog
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(contentUri)
                        )
                        
                        Timber.i("deleteViaMediaStore: ========================================")
                        Timber.i("deleteViaMediaStore: BATCH DELETE PERMISSION REQUEST CREATED")
                        Timber.i("deleteViaMediaStore: File: $filePath")
                        Timber.i("deleteViaMediaStore: ContentUri: $contentUri")
                        Timber.i("deleteViaMediaStore: PendingIntent: $pendingIntent")
                        Timber.i("deleteViaMediaStore: Throwing BatchDeletePermissionRequiredException...")
                        Timber.i("deleteViaMediaStore: ========================================")
                        
                        // Throw special exception with PendingIntent for UI to handle
                        throw BatchDeletePermissionRequiredException(pendingIntent, listOf(contentUri))
                    } catch (e: BatchDeletePermissionRequiredException) {
                        // Re-throw our custom exception
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "FileOperationUseCase: createDeleteRequest failed, falling back to regular delete")
                        // Fall through to try regular delete
                    }
                }
                
                // Android 10 or fallback: try direct delete (may throw RecoverableSecurityException)
                try {
                    val deletedRows = context.contentResolver.delete(contentUri, null, null)
                    Timber.d("FileOperationUseCase: MediaStore delete result: $deletedRows rows deleted")
                    
                    if (deletedRows > 0) {
                        return@withContext true
                    }
                } catch (securityException: SecurityException) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // On Android 10+, check if this is a RecoverableSecurityException
                        val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            Timber.w("FileOperationUseCase: RecoverableSecurityException for $filePath - need user permission")
                            // Re-throw to be handled by UI layer
                            throw recoverableSecurityException
                        }
                    }
                    // Non-recoverable SecurityException - log and fall through to File.delete()
                    Timber.w(securityException, "FileOperationUseCase: SecurityException (non-recoverable) for $filePath")
                }
            } else {
                Timber.w("FileOperationUseCase: File not found in MediaStore: $filePath")
            }
            
            // Fallback: If MediaStore delete failed (e.g. file not indexed), try File.delete()
            if (file.exists() && file.delete()) {
                Timber.d("FileOperationUseCase: Fallback File.delete() succeeded for $filePath")
                return@withContext true
            } else {
                Timber.w("FileOperationUseCase: Fallback File.delete() failed for $filePath (exists=${file.exists()})")
                return@withContext false
            }
        } catch (e: BatchDeletePermissionRequiredException) {
            // Re-throw our custom exception
            throw e
        } catch (e: Exception) {
            // Re-throw RecoverableSecurityException to be handled by caller/UI
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
                e is android.app.RecoverableSecurityException) {
                Timber.i("FileOperationUseCase: Propagating RecoverableSecurityException to UI layer")
                throw e
            }
            
            Timber.e(e, "FileOperationUseCase: MediaStore delete failed for: $filePath")
            
            // One last try with File.delete() for other exceptions
            try {
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
    
    /**
     * Collect MediaStore content URIs for all files in batch.
     * Returns list of URIs that need MediaStore deletion (Android 11+).
     * Used to create ONE batch delete request instead of multiple individual requests.
     */
    private suspend fun collectMediaStoreUris(files: List<File>): List<Uri> = withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()
        
        // Only relevant for Android 11+ with batch delete support
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext emptyList()
        }
        
        Timber.d("collectMediaStoreUris: Collecting URIs for ${files.size} files")
        
        for (file in files) {
            val filePath = file.absolutePath
            
            // Skip non-shared storage files
            if (!isSharedStorage(filePath)) continue
            
            try {
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                
                // Determine correct MediaStore collection
                val collection = when {
                    mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null // Non-media files
                }
                
                if (collection == null) continue
                
                // Query MediaStore for file ID
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
    
    /**
     * Custom exception to indicate batch delete permission is required.
     * Contains PendingIntent to show system permission dialog.
     */
    class BatchDeletePermissionRequiredException(
        val pendingIntent: PendingIntent,
        val uris: List<Uri>
    ) : Exception("Batch delete permission required")
}
