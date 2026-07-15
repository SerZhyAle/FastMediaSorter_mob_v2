package com.sza.fastmediasorter.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.CloudFileHandle
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationProgress
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferProgressCodec
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferProgressSnapshot
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequest
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequestStore
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferSource
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferTerminalEvent
import com.sza.fastmediasorter.ui.browse.transfer.toPayload
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@HiltWorker
class BrowseFileTransferWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileOperationUseCase: FileOperationUseCase,
    private val directoryOperationHandler: UnifiedFileOperationHandler,
    private val requestStore: BrowseFileTransferRequestStore,
    private val coordinator: BrowseFileTransferCoordinator,
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val request = requestStore.readActiveRequest()
        ensureChannel()
        return buildForegroundInfo(
            request = request,
            contentText = context.getString(R.string.browse_transfer_notif_text_preparing),
            progressPercent = null,
        )
    }

    override suspend fun doWork(): Result {
        val request = requestStore.readActiveRequest() ?: run {
            Timber.e("BrowseFileTransferWorker: no active request stored")
            return Result.failure()
        }
        ensureChannel()

        return try {
            runTransfer(request)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            withContext(NonCancellable) {
                val event = BrowseFileTransferTerminalEvent.Cancelled(
                    workId = id.toString(),
                    operationType = request.operationType,
                )
                persistAndPublish(event)
            }
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            // S1021: backstop - a Throwable that escapes runTransfer/executeInternal despite
            // their own catches would otherwise vanish into WorkManager's internal (non-Timber)
            // failure logging, invisible to the app's own log file.
            Timber.e(t, "BrowseFileTransferWorker.doWork caught unexpected Throwable")
            Result.failure()
        } finally {
            requestStore.clearActiveRequest()
        }
    }

    private suspend fun runTransfer(request: BrowseFileTransferRequest): Result {
        var latestTotalOperationBytes = 0L
        var terminalResult: FileOperationResult? = null

        if (request.sources.any { !it.isDirectory }) {
            fileOperationUseCase.executeWithProgress(request.toFileOperation()).collect { progress ->
                currentCoroutineContext().ensureActive()
                when (progress) {
                    is FileOperationProgress.Starting -> {
                        latestTotalOperationBytes = progress.totalOperationBytes
                    }
                    is FileOperationProgress.Processing -> {
                        val snapshot = BrowseFileTransferProgressSnapshot(
                            operationType = request.operationType,
                            totalFiles = progress.totalFiles,
                            currentIndex = progress.currentIndex,
                            currentFile = progress.currentFile,
                            bytesTransferred = progress.bytesTransferred,
                            totalBytes = progress.totalBytes,
                            speedBytesPerSecond = progress.speedBytesPerSecond,
                            completedOperationBytes = progress.completedOperationBytes,
                            totalOperationBytes = latestTotalOperationBytes,
                        )
                        runCatching {
                            setForeground(
                                buildForegroundInfo(
                                    request,
                                    buildProgressText(snapshot),
                                    snapshot.percentOrNull(),
                                ),
                            )
                        }.onFailure { Timber.w(it, "BrowseFileTransferWorker: setForeground failed") }
                        runCatching {
                            setProgressAsync(BrowseFileTransferProgressCodec.encodeProgress(id.toString(), snapshot))
                        }
                    }
                    is FileOperationProgress.Completed -> {
                        terminalResult = progress.result
                    }
                }
            }
        } else {
            terminalResult = FileOperationResult.Success(
                processedCount = 0,
                operation = request.toFileOperation(),
            )
        }

        val event = buildTerminalEvent(
            request = request,
            fileResult = terminalResult ?: FileOperationResult.Failure("Unknown result"),
        )
        persistAndPublish(event)
        postResultNotification(request, event)
        return when (event) {
            is BrowseFileTransferTerminalEvent.Success,
            is BrowseFileTransferTerminalEvent.PartialSuccess,
            is BrowseFileTransferTerminalEvent.AuthenticationRequired,
            is BrowseFileTransferTerminalEvent.PermissionRequired -> {
                Result.success(BrowseFileTransferProgressCodec.encodeTerminalFallback(event))
            }
            is BrowseFileTransferTerminalEvent.Failure -> {
                Result.failure(BrowseFileTransferProgressCodec.encodeTerminalFallback(event))
            }
            is BrowseFileTransferTerminalEvent.Cancelled -> {
                Result.failure(BrowseFileTransferProgressCodec.encodeTerminalFallback(event))
            }
        }
    }

    private suspend fun buildTerminalEvent(
        request: BrowseFileTransferRequest,
        fileResult: FileOperationResult,
    ): BrowseFileTransferTerminalEvent {
        return when (fileResult) {
            is FileOperationResult.Success -> {
                val dirOutcome = runDirectoryOperations(request)
                if (dirOutcome.failedCount > 0) {
                    BrowseFileTransferTerminalEvent.PartialSuccess(
                        workId = id.toString(),
                        operationType = request.operationType,
                        processedCount = fileResult.processedCount + dirOutcome.succeededCount,
                        failedCount = dirOutcome.failedCount,
                        details = dirOutcome.details,
                        undoOperation = buildUndoOperation(request, fileResult.copiedFilePaths),
                    )
                } else {
                    BrowseFileTransferTerminalEvent.Success(
                        workId = id.toString(),
                        operationType = request.operationType,
                        processedCount = fileResult.processedCount + dirOutcome.succeededCount,
                        undoOperation = buildUndoOperation(request, fileResult.copiedFilePaths),
                    )
                }
            }
            is FileOperationResult.PartialSuccess -> BrowseFileTransferTerminalEvent.PartialSuccess(
                workId = id.toString(),
                operationType = request.operationType,
                processedCount = fileResult.processedCount,
                failedCount = fileResult.failedCount,
                details = fileResult.errors.take(MAX_ERROR_DETAILS).joinToString("\n").ifBlank { null },
                undoOperation = null,
            )
            is FileOperationResult.Failure -> BrowseFileTransferTerminalEvent.Failure(
                workId = id.toString(),
                operationType = request.operationType,
                message = fileResult.error,
                details = fileResult.formatArgs.firstOrNull()?.toString(),
            )
            is FileOperationResult.AuthenticationRequired -> BrowseFileTransferTerminalEvent.AuthenticationRequired(
                workId = id.toString(),
                operationType = request.operationType,
                provider = fileResult.provider,
                message = fileResult.message,
            )
            is FileOperationResult.PermissionRequired -> BrowseFileTransferTerminalEvent.PermissionRequired(
                workId = id.toString(),
                operationType = request.operationType,
                pendingIntent = fileResult.pendingIntent,
            )
        }
    }

    private suspend fun runDirectoryOperations(request: BrowseFileTransferRequest): DirectoryOutcome {
        val directorySources = request.sources.filter { it.isDirectory }
        if (directorySources.isEmpty()) return DirectoryOutcome()

        var succeeded = 0
        val errors = mutableListOf<String>()
        directorySources.forEach { source ->
            currentCoroutineContext().ensureActive()
            val result = when (request.operationType) {
                FileOperationType.COPY ->
                    directoryOperationHandler.executeCopyDirectory(source.path, request.destinationPath)
                FileOperationType.MOVE ->
                    directoryOperationHandler.executeMoveDirectory(source.path, request.destinationPath)
                else -> kotlin.Result.failure(IllegalArgumentException("Unsupported op=${request.operationType}"))
            }
            result.onSuccess { succeeded++ }
                .onFailure { errors += (it.message ?: source.displayName) }
        }
        return DirectoryOutcome(
            succeededCount = succeeded,
            failedCount = errors.size,
            details = errors.take(MAX_ERROR_DETAILS).joinToString("\n").ifBlank { null },
        )
    }

    private fun buildUndoOperation(
        request: BrowseFileTransferRequest,
        copiedFilePaths: List<String>,
    ): UndoOperation? {
        val fileSources = request.sources.filterNot { it.isDirectory }
        if (fileSources.isEmpty() || copiedFilePaths.isEmpty()) return null
        return UndoOperation(
            type = request.operationType,
            sourceFiles = fileSources.map { it.path },
            destinationFolder = request.destinationPath,
            copiedFiles = copiedFilePaths,
            oldNames = null,
            timestamp = System.currentTimeMillis(),
        )
    }

    private suspend fun persistAndPublish(event: BrowseFileTransferTerminalEvent) {
        requestStore.writeTerminalEvent(event.toPayload())
        coordinator.publishTerminalEvent(event)
    }

    private fun BrowseFileTransferRequest.toFileOperation(): FileOperation {
        val sources = sources.filterNot { it.isDirectory }.map { it.toFile() }
        val destination = if (REMOTE_OR_CONTENT_PREFIXES.any { destinationPath.startsWith(it) }) {
            object : File(destinationPath) {
                override fun getAbsolutePath(): String = destinationPath
                override fun getPath(): String = destinationPath
            }
        } else {
            File(destinationPath)
        }
        return when (operationType) {
            FileOperationType.COPY -> FileOperation.Copy(
                sources = sources,
                destination = destination,
                overwrite = overwriteFiles,
                sourceCredentialsId = sourceCredentialsId,
            )
            FileOperationType.MOVE -> FileOperation.Move(
                sources = sources,
                destination = destination,
                overwrite = overwriteFiles,
                sourceCredentialsId = sourceCredentialsId,
            )
            else -> throw IllegalArgumentException("Unsupported browse background op: $operationType")
        }
    }

    private fun BrowseFileTransferSource.toFile(): File = when {
        path.startsWith("cloud://") -> CloudFileHandle(
            cloudPath = path,
            displayName = displayName,
            size = size,
        )
        path.startsWith("smb://") || path.startsWith("sftp://") || path.startsWith("ftp://") -> {
            // Capture the source fields into locals first: inside `object : File(..)` the bare name
            // `path` resolves to the File.getPath() member being overridden - not this receiver's
            // property - so returning `path` from getPath()/getAbsolutePath() recurses forever
            // (StackOverflowError, silent until it hit executeInternal's later catch). S1021.
            val sourcePath = path
            val sourceName = displayName
            val sourceSize = size
            object : File(sourcePath) {
                override fun getAbsolutePath(): String = sourcePath
                override fun getPath(): String = sourcePath
                override fun getName(): String = sourceName
                override fun length(): Long = sourceSize
            }
        }
        else -> File(path)
    }

    private fun buildForegroundInfo(
        request: BrowseFileTransferRequest?,
        contentText: String,
        progressPercent: Int?,
    ): ForegroundInfo {
        val notification = buildProgressNotification(request, contentText, progressPercent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID_PROGRESS, notification)
        }
    }

    private fun buildProgressNotification(
        request: BrowseFileTransferRequest?,
        contentText: String,
        progressPercent: Int?,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cloud_download)
            .setContentTitle(notificationTitle(request?.operationType))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildBrowsePendingIntent(request))
            .addAction(buildCancelAction())

        if (progressPercent != null) {
            builder.setProgress(PROGRESS_PERCENT_MAX, progressPercent.coerceIn(0, PROGRESS_PERCENT_MAX), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun postResultNotification(
        request: BrowseFileTransferRequest,
        event: BrowseFileTransferTerminalEvent,
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cloud_download)
            .setAutoCancel(true)
            .setContentIntent(buildBrowsePendingIntent(request))
            .setTimeoutAfter(RESULT_TIMEOUT_MS)

        when (event) {
            is BrowseFileTransferTerminalEvent.Success -> {
                builder.setContentTitle(context.getString(R.string.browse_transfer_notif_title_done))
                builder.setContentText(context.getString(doneMessageRes(event.operationType), event.processedCount))
            }
            is BrowseFileTransferTerminalEvent.PartialSuccess -> {
                builder.setContentTitle(context.getString(R.string.browse_transfer_notif_title_done))
                builder.setContentText(
                    context.getString(
                        R.string.error_some_operations_failed,
                        event.failedCount,
                        event.processedCount + event.failedCount,
                    ),
                )
            }
            is BrowseFileTransferTerminalEvent.AuthenticationRequired -> {
                builder.setContentTitle(context.getString(R.string.browse_transfer_notif_title_auth_required))
                builder.setContentText(context.getString(R.string.browse_transfer_notif_text_auth_required))
            }
            is BrowseFileTransferTerminalEvent.PermissionRequired -> {
                builder.setContentTitle(context.getString(R.string.browse_transfer_notif_title_permission_required))
                builder.setContentText(context.getString(R.string.browse_transfer_notif_text_permission_required))
            }
            is BrowseFileTransferTerminalEvent.Failure -> {
                builder.setContentTitle(context.getString(R.string.browse_transfer_notif_title_failed))
                builder.setContentText(context.getString(R.string.browse_transfer_notif_text_failed))
            }
            is BrowseFileTransferTerminalEvent.Cancelled -> {
                return
            }
        }

        notificationManager.notify(
            NOTIF_ID_RESULT_BASE + Math.floorMod(id.hashCode(), RESULT_ID_MODULO),
            builder.build(),
        )
    }

    private fun buildBrowsePendingIntent(request: BrowseFileTransferRequest?): PendingIntent? {
        request ?: return null
        val intent = BrowseActivity.createIntent(
            context = context,
            resourceId = request.sourceResourceId,
            skipAvailabilityCheck = false,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BrowseActivity.EXTRA_REATTACH_TRANSFER, true)
        }
        return PendingIntent.getActivity(
            context,
            Math.floorMod(request.sourceResourceId.toInt(), REQUEST_CODE_MODULO),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildCancelAction(): NotificationCompat.Action {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        return NotificationCompat.Action(
            R.drawable.ic_delete,
            context.getString(R.string.browse_transfer_notif_action_cancel),
            cancelIntent,
        )
    }

    private fun notificationTitle(operationType: FileOperationType?): String = when (operationType) {
        FileOperationType.MOVE -> context.getString(R.string.browse_transfer_notif_title_move)
        else -> context.getString(R.string.browse_transfer_notif_title_copy)
    }

    private fun doneMessageRes(operationType: FileOperationType): Int = when (operationType) {
        FileOperationType.MOVE -> R.string.moved_n_files
        else -> R.string.copied_n_files
    }

    private fun buildProgressText(snapshot: BrowseFileTransferProgressSnapshot): String {
        val percent = snapshot.percentOrNull()
        return if (percent != null) {
            context.getString(R.string.browse_transfer_notif_text_progress, percent, snapshot.currentFile)
        } else {
            context.getString(R.string.browse_transfer_notif_text_preparing)
        }
    }

    private fun BrowseFileTransferProgressSnapshot.percentOrNull(): Int? {
        return if (totalOperationBytes > 0L) {
            ((completedOperationBytes * PERCENT_SCALE) / totalOperationBytes)
                .toInt().coerceIn(0, PROGRESS_PERCENT_CAP)
        } else {
            null
        }
    }

    private fun ensureChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.browse_transfer_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.browse_transfer_notif_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private data class DirectoryOutcome(
        val succeededCount: Int = 0,
        val failedCount: Int = 0,
        val details: String? = null,
    )

    companion object {
        private const val CHANNEL_ID = "browse_file_transfer_channel"
        private const val NOTIF_ID_PROGRESS = 7300
        private const val NOTIF_ID_RESULT_BASE = 7400
        private const val RESULT_TIMEOUT_MS = 20 * 60 * 1000L
        private const val MAX_ERROR_DETAILS = 5
        private const val PROGRESS_PERCENT_MAX = 100
        private const val PROGRESS_PERCENT_CAP = 99
        private const val PERCENT_SCALE = 100L
        private const val RESULT_ID_MODULO = 100
        private const val REQUEST_CODE_MODULO = 10_000
        private val REMOTE_OR_CONTENT_PREFIXES =
            listOf("smb://", "sftp://", "ftp://", "cloud://", "content://")
    }
}
