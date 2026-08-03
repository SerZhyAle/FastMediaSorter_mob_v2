package com.sza.fastmediasorter.domain.usecase

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.stats.FileOpAction
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.transfer.TransferProgressReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
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
        val skippedPaths: List<String> = emptyList(),
        val softDeleteFallbackPaths: List<String> = emptyList()
    ) : FileOperationResult()
    data class PartialSuccess(
        val processedCount: Int, 
        val failedCount: Int, 
        val errors: List<String>,
        val deletedPaths: List<String> = emptyList(), // Paths of actually deleted/moved files
        val skippedCount: Int = 0,
        val skippedPaths: List<String> = emptyList(),
        val softDeleteFallbackPaths: List<String> = emptyList()
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
    private val cloudFileOperationHandler: CloudFileOperationHandler,
    private val localOperationStrategy: LocalOperationStrategy,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops entirely when collection is disabled.
    private val statsSink: StatsSink,
    // S1025: single pre-flight probe of the network destination before the batch loop.
    private val hostReachabilityChecker: HostReachabilityChecker,
    private val transferProgressReporter: TransferProgressReporter,
) {

    private var lastOperation: OperationHistory? = null

    private val deleteOp = LocalDeleteFileOperation(context, cloudFileOperationHandler, localOperationStrategy)
    private val copyOp = LocalCopyFileOperation(context) { path -> scanNewFile(path) }
    private val moveOp = LocalMoveFileOperation(
        context,
        scanNewFile = { path -> scanNewFile(path) },
        deleteViaMediaStore = { path -> localOperationStrategy.deleteViaMediaStore(path) },
        isSharedStorage = { path -> localOperationStrategy.isSharedStoragePath(path) }
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
        val progressOperationId = UUID.randomUUID().toString()

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
                val completedOperationBytes = completedFileBytes + bytesTransferred
                val report = transferProgressReporter.report(
                    operationId = progressOperationId,
                    bytesTransferred = completedOperationBytes,
                    totalBytes = totalOperationBytes,
                    consumerKey = IN_PROCESS_CONSUMER,
                    minimumPublishIntervalMs = NO_THROTTLE_MS,
                    forcePublish = true,
                )
                // Use trySend to avoid blocking if channel is full
                trySend(FileOperationProgress.Processing(
                    currentFile = currentFileName,
                    currentIndex = currentFileIndex - 1,
                    totalFiles = totalFiles,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = report.speedBytesPerSecond,
                    completedOperationBytes = completedOperationBytes
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
        transferProgressReporter.clear(progressOperationId)
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

        // S0473: a successful Delete result carries no freed-bytes total, so sum the source sizes
        // before the delete runs (we are already on the IO dispatcher here). Copy/Move read sizes
        // post-success from the still-present sources, so they need no pre-capture.
        val preDeleteBytes: Long = if (operation is FileOperation.Delete) {
            operation.files.sumOf { runCatching { it.length() }.getOrDefault(0L) }
        } else 0L

        try {
            // S1028: File-mangling-tolerant protocol match now lives in PathUtils; this thin
            // extension keeps the call ergonomics. S1027: no per-call log here - it fired for every
            // source x every protocol probe (~1500 lines on a 369-file transfer).
            fun File.isNetworkPath(protocol: String): Boolean =
                PathUtils.fileMatchesProtocol(this.path, protocol)

            // S1027: one helper over source+destination replaces four near-identical when-blocks
            // that each also logged per branch (16 lines/operation). Copy/Move look at sources and
            // destination; Delete at the file list; Rename at the single file.
            fun hasProtocol(protocol: String): Boolean {
                val result = when (operation) {
                    is FileOperation.Copy ->
                        operation.sources.any { it.isNetworkPath(protocol) } ||
                            operation.destination.isNetworkPath(protocol)
                    is FileOperation.Move ->
                        operation.sources.any { it.isNetworkPath(protocol) } ||
                            operation.destination.isNetworkPath(protocol)
                    is FileOperation.Delete -> operation.files.any { it.isNetworkPath(protocol) }
                    is FileOperation.Rename -> operation.file.isNetworkPath(protocol)
                }
                Timber.d("S1028: network-path classified proto=$protocol net=$result")
                return result
            }

            val hasSmbPath = hasProtocol("smb")
            val hasSftpPath = hasProtocol("sftp")
            val hasFtpPath = hasProtocol("ftp")
            val hasCloudPath = hasProtocol("cloud")

            // S1027: single per-operation classification summary (replaced the 16 per-branch lines).
            Timber.d(
                "FileOperation.${operation.javaClass.simpleName}: " +
                    "smb=$hasSmbPath sftp=$hasSftpPath ftp=$hasFtpPath cloud=$hasCloudPath",
            )

            // S1025: one destination-reachability probe before entering any per-file loop. Keys off
            // the DESTINATION scheme only (smb/sftp/ftp Copy/Move); Cloud keeps its own auth gate and
            // Delete/Rename/local yield no endpoint, so they skip the probe. On failure abort the
            // whole batch - the in-loop per-file precheck/retry stays for transient errors.
            resolveDestinationEndpoint(operation)?.let { endpoint ->
                val reachable = hostReachabilityChecker.isReachable(
                    endpoint.host, endpoint.port, DESTINATION_PROBE_TIMEOUT_MS,
                )
                Timber.d("S1025: preflight destination probe host=${endpoint.host} reachable=$reachable")
                if (!reachable) {
                    return FileOperationResult.Failure(
                        error = context.getString(R.string.transfer_destination_unreachable),
                        errorRes = R.string.transfer_destination_unreachable,
                    )
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
            recordFileOpStats(operation, result, preDeleteBytes)
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
            e.rethrowIfCancellation()
            StructuredLogger.e(e, "EXCEPTION in executeInternal")
            return FileOperationResult.Failure("${e.javaClass.simpleName}: ${e.message}")
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            t.rethrowIfCancellation()
            // S1021: catch(Exception) above never sees an Error (OOM/StackOverflow/..) - it would
            // otherwise escape this use case, the Worker's CancellationException-only catch, and
            // doWork() itself uncaught, silent to Timber and visible only in WorkManager's own log.
            Timber.e(t, "Throwable escaped FileOperationUseCase.executeInternal")
            return FileOperationResult.Failure("${t.javaClass.simpleName}: ${t.message}")
        }
    }

    suspend fun execute(
        operation: FileOperation,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult = withContext(Dispatchers.IO + CorrelationContext.asContextElement("file-operation-sync")) {
        executeInternal(operation, progressCallback)
    }
    
    /**
     * S0473: emit a per-type [StatsEvent.FileOp] for a completed Copy/Move/Delete/Rename. Files are
     * bucketed by [StatsMediaType] so the dashboard can break operations down by media kind; one
     * event is emitted per bucket. Rename (S0654) carries a plain count - the fold ignores its bytes
     * and matrix. Network-only results without a processed count are not counted. The sink no-ops
     * when collection is disabled.
     */
    private fun recordFileOpStats(
        operation: FileOperation,
        result: FileOperationResult,
        preDeleteBytes: Long
    ) {
        val processedCount = when (result) {
            is FileOperationResult.Success -> result.processedCount
            is FileOperationResult.PartialSuccess -> result.processedCount
            else -> return
        }
        if (processedCount <= 0) return

        val action: FileOpAction
        val files: List<File>
        when (operation) {
            is FileOperation.Copy -> { action = FileOpAction.COPY; files = operation.sources }
            is FileOperation.Move -> { action = FileOpAction.MOVE; files = operation.sources }
            is FileOperation.Delete -> { action = FileOpAction.DELETE; files = operation.files }
            is FileOperation.Rename -> { action = FileOpAction.RENAME; files = listOf(operation.file) }
        }

        // Bucket processed files by media type. Bytes: Copy/Move sum live source sizes; Delete uses
        // the pre-delete total, apportioned across buckets by the captured size of each source.
        val countByType = mutableMapOf<StatsMediaType, Long>()
        val bytesByType = mutableMapOf<StatsMediaType, Long>()
        files.forEach { file ->
            val type = file.name.toStatsMediaType()
            countByType[type] = (countByType[type] ?: 0L) + 1L
            val bytes = runCatching { file.length() }.getOrDefault(0L)
            bytesByType[type] = (bytesByType[type] ?: 0L) + bytes
        }
        // For Delete the sources are already gone, so live length()==0; fall back to the pre-delete
        // total assigned to the dominant (first) bucket rather than losing the freed-bytes figure.
        if (action == FileOpAction.DELETE && bytesByType.values.all { it == 0L } && preDeleteBytes > 0L) {
            countByType.keys.firstOrNull()?.let { bytesByType[it] = preDeleteBytes }
        }

        countByType.forEach { (type, count) ->
            statsSink.record(
                StatsEvent.FileOp(
                    action = action,
                    type = type,
                    count = count,
                    bytes = bytesByType[type] ?: 0L
                )
            )
        }
    }

    private fun String.toStatsMediaType(): StatsMediaType = when (MediaTypeUtils.getMediaType(this)) {
        MediaType.IMAGE, MediaType.GIF -> StatsMediaType.IMAGE
        MediaType.VIDEO -> StatsMediaType.VIDEO
        MediaType.AUDIO -> StatsMediaType.AUDIO
        MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT, MediaType.TEXT -> StatsMediaType.DOCUMENT
        else -> StatsMediaType.OTHER
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

        val undoResult = when (val op = history.operation) {
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
        // Count a completed player-side undo. The reverse op above records its own FileOp stats;
        // this is the separate "undo happened" counter (S0654).
        if (undoResult != null) statsSink.record(StatsEvent.UndoPerformed)
        undoResult
    }

    /**
     * S1025: derive the network endpoint of a Copy/Move destination for the pre-flight probe.
     * Returns null for non-Copy/Move ops and for cloud/local destinations (probe skipped). The raw
     * path may be slash-mangled by File(..) (`smb:/host`), so scheme detection reuses the tolerant
     * [PathUtils.fileMatchesProtocol] and the authority is taken after the scheme, ignoring leading
     * slashes.
     */
    private fun resolveDestinationEndpoint(operation: FileOperation): NetworkEndpoint? {
        val destPath = when (operation) {
            is FileOperation.Copy -> operation.destination.path
            is FileOperation.Move -> operation.destination.path
            else -> return null
        }
        val (scheme, defaultPort) = when {
            PathUtils.fileMatchesProtocol(destPath, "smb") -> "smb" to SMB_DEFAULT_PORT
            PathUtils.fileMatchesProtocol(destPath, "sftp") -> "sftp" to SFTP_DEFAULT_PORT
            PathUtils.fileMatchesProtocol(destPath, "ftp") -> "ftp" to FTP_DEFAULT_PORT
            else -> return null
        }
        val authority = destPath.substringAfter("$scheme:").trimStart('/').substringBefore("/")
        if (authority.isBlank()) return null
        val host = authority.substringBefore(":")
        val port = authority.substringAfter(":", defaultPort.toString()).toIntOrNull() ?: defaultPort
        return NetworkEndpoint(host, port)
    }

    private data class NetworkEndpoint(val host: String, val port: Int)

    /**
     * Custom exception to indicate batch delete permission is required.
     * Contains PendingIntent to show system permission dialog.
     */
    class BatchDeletePermissionRequiredException(
        val pendingIntent: PendingIntent,
        val uris: List<Uri>
    ) : Exception("Batch delete permission required")

    private companion object {
        const val IN_PROCESS_CONSUMER = "file-operation"
        const val NO_THROTTLE_MS = 0L
        const val SMB_DEFAULT_PORT = 445
        const val SFTP_DEFAULT_PORT = 22
        const val FTP_DEFAULT_PORT = 21
        const val DESTINATION_PROBE_TIMEOUT_MS = 3000
    }
}
