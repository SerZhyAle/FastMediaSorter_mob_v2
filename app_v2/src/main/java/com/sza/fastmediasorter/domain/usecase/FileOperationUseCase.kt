package com.sza.fastmediasorter.domain.usecase

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import timber.log.Timber
import java.io.File
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
    data class Starting(val operation: FileOperation, val totalFiles: Int, val totalOperationBytes: Long = 0L) : FileOperationProgress()
    data class Processing(
        val currentFile: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val bytesTransferred: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBytesPerSecond: Long = 0L,
        val completedOperationBytes: Long = 0L
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

    private val deleteOp = LocalDeleteFileOperation(context, cloudFileOperationHandler, smbFileOperationHandler)
    private val copyOp = LocalCopyFileOperation(context) { path -> scanNewFile(path) }
    private val moveOp = LocalMoveFileOperation(
        context,
        scanNewFile = { path -> scanNewFile(path) },
        deleteViaMediaStore = { path -> deleteOp.deleteViaMediaStore(path) },
        isSharedStorage = { path -> deleteOp.isSharedStorage(path) }
    )
    private val renameOp = LocalRenameFileOperation(context) { path -> scanNewFile(path) }

    private fun scanNewFile(path: String) {
        com.sza.fastmediasorter.utils.MediaStoreNotifier.notifyFile(context, path, "file-operation")
    }
    
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

        // Pre-compute per-file sizes for overall progress; network files return 0 (acceptable)
        val fileSizes: List<Long> = when (operation) {
            is FileOperation.Copy -> operation.sources.map { it.length() }
            is FileOperation.Move -> operation.sources.map { it.length() }
            else -> emptyList()
        }
        val totalOperationBytes = fileSizes.sum()

        send(FileOperationProgress.Starting(operation, totalFiles, totalOperationBytes))

        // Update current file tracking based on operation type
        var currentFileIndex = 1
        var currentFileName = when (operation) {
            is FileOperation.Copy -> operation.sources.firstOrNull()?.name ?: ""
            is FileOperation.Move -> operation.sources.firstOrNull()?.name ?: ""
            is FileOperation.Delete -> operation.files.firstOrNull()?.name ?: ""
            is FileOperation.Rename -> operation.file.name
        }

        // Create progress callback that sends to channel (thread-safe)
        var completedFileBytes = 0L
        val progressCallback = object : ByteProgressCallback {
            override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
                // Use trySend to avoid blocking if channel is full
                trySend(FileOperationProgress.Processing(
                    currentFile = currentFileName,
                    currentIndex = currentFileIndex - 1,
                    totalFiles = totalFiles,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = speedBytesPerSecond,
                    completedOperationBytes = completedFileBytes + bytesTransferred
                ))
            }

            override suspend fun onFileStarted(index: Int, fileName: String, total: Int) {
                currentFileIndex = index
                currentFileName = fileName
                // Accumulate bytes from all files that completed before this one
                completedFileBytes = fileSizes.take(index - 1).sum()
                // Send an immediate Processing update so the dialog shows the new file
                trySend(FileOperationProgress.Processing(
                    currentFile = fileName,
                    currentIndex = index - 1,
                    totalFiles = total,
                    bytesTransferred = 0L,
                    totalBytes = 0L,
                    speedBytesPerSecond = 0L,
                    completedOperationBytes = completedFileBytes
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
                    when (operation) {
                        is FileOperation.Copy -> copyOp.execute(operation, progressCallback)
                        is FileOperation.Move -> moveOp.execute(operation, progressCallback)
                        is FileOperation.Rename -> renameOp.execute(operation)
                        is FileOperation.Delete -> deleteOp.execute(operation)
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
    
    /**
     * Custom exception to indicate batch delete permission is required.
     * Contains PendingIntent to show system permission dialog.
     */
    class BatchDeletePermissionRequiredException(
        val pendingIntent: PendingIntent,
        val uris: List<Uri>
    ) : Exception("Batch delete permission required")
}
