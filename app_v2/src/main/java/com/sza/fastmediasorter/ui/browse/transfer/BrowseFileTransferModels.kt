package com.sza.fastmediasorter.ui.browse.transfer

import android.app.PendingIntent
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation

data class BrowseFileTransferRequest(
    val operationType: FileOperationType,
    val sourceResourceId: Long,
    val sourceResourceName: String,
    val sourceCredentialsId: String?,
    val currentBrowsePath: String?,
    val destinationPath: String,
    val destinationName: String,
    val overwriteFiles: Boolean,
    val sources: List<BrowseFileTransferSource>,
)

data class BrowseFileTransferSource(
    val path: String,
    val displayName: String,
    val size: Long,
    val isDirectory: Boolean,
)

data class BrowseFileTransferProgressSnapshot(
    val operationType: FileOperationType,
    val totalFiles: Int,
    val currentIndex: Int,
    val currentFile: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val completedOperationBytes: Long,
    val totalOperationBytes: Long,
)

sealed interface BrowseFileTransferTerminalEvent {
    val workId: String
    val operationType: FileOperationType

    data class Success(
        override val workId: String,
        override val operationType: FileOperationType,
        val processedCount: Int,
        val undoOperation: UndoOperation?,
    ) : BrowseFileTransferTerminalEvent

    data class PartialSuccess(
        override val workId: String,
        override val operationType: FileOperationType,
        val processedCount: Int,
        val failedCount: Int,
        val details: String?,
        val undoOperation: UndoOperation?,
    ) : BrowseFileTransferTerminalEvent

    data class Failure(
        override val workId: String,
        override val operationType: FileOperationType,
        val message: String,
        val details: String? = null,
    ) : BrowseFileTransferTerminalEvent

    data class AuthenticationRequired(
        override val workId: String,
        override val operationType: FileOperationType,
        val provider: String,
        val message: String? = null,
    ) : BrowseFileTransferTerminalEvent

    data class PermissionRequired(
        override val workId: String,
        override val operationType: FileOperationType,
        val pendingIntent: PendingIntent? = null,
    ) : BrowseFileTransferTerminalEvent

    data class Cancelled(
        override val workId: String,
        override val operationType: FileOperationType,
    ) : BrowseFileTransferTerminalEvent
}

data class BrowseFileTransferTerminalPayload(
    val kind: String,
    val workId: String,
    val operationType: String,
    val processedCount: Int = 0,
    val failedCount: Int = 0,
    val message: String? = null,
    val details: String? = null,
    val provider: String? = null,
    val undoSourceFiles: List<String> = emptyList(),
    val undoDestinationFolder: String? = null,
    val undoCopiedFiles: List<String> = emptyList(),
)

private const val KIND_SUCCESS = "success"
private const val KIND_PARTIAL = "partial"
private const val KIND_FAILURE = "failure"
private const val KIND_AUTH = "auth"
private const val KIND_PERMISSION = "permission"
private const val KIND_CANCELLED = "cancelled"

fun BrowseFileTransferTerminalEvent.toPayload(): BrowseFileTransferTerminalPayload = when (this) {
    is BrowseFileTransferTerminalEvent.Success -> BrowseFileTransferTerminalPayload(
        kind = KIND_SUCCESS,
        workId = workId,
        operationType = operationType.name,
        processedCount = processedCount,
        undoSourceFiles = undoOperation?.sourceFiles.orEmpty(),
        undoDestinationFolder = undoOperation?.destinationFolder,
        undoCopiedFiles = undoOperation?.copiedFiles.orEmpty(),
    )
    is BrowseFileTransferTerminalEvent.PartialSuccess -> BrowseFileTransferTerminalPayload(
        kind = KIND_PARTIAL,
        workId = workId,
        operationType = operationType.name,
        processedCount = processedCount,
        failedCount = failedCount,
        details = details,
        undoSourceFiles = undoOperation?.sourceFiles.orEmpty(),
        undoDestinationFolder = undoOperation?.destinationFolder,
        undoCopiedFiles = undoOperation?.copiedFiles.orEmpty(),
    )
    is BrowseFileTransferTerminalEvent.Failure -> BrowseFileTransferTerminalPayload(
        kind = KIND_FAILURE,
        workId = workId,
        operationType = operationType.name,
        message = message,
        details = details,
    )
    is BrowseFileTransferTerminalEvent.AuthenticationRequired -> BrowseFileTransferTerminalPayload(
        kind = KIND_AUTH,
        workId = workId,
        operationType = operationType.name,
        provider = provider,
        message = message,
    )
    is BrowseFileTransferTerminalEvent.PermissionRequired -> BrowseFileTransferTerminalPayload(
        kind = KIND_PERMISSION,
        workId = workId,
        operationType = operationType.name,
    )
    is BrowseFileTransferTerminalEvent.Cancelled -> BrowseFileTransferTerminalPayload(
        kind = KIND_CANCELLED,
        workId = workId,
        operationType = operationType.name,
    )
}

fun BrowseFileTransferTerminalPayload.toEvent(
    pendingIntent: PendingIntent? = null,
): BrowseFileTransferTerminalEvent {
    val opType = FileOperationType.valueOf(operationType)
    val undoOperation = buildUndoOperation(opType)
    return when (kind) {
        KIND_SUCCESS -> BrowseFileTransferTerminalEvent.Success(
            workId = workId,
            operationType = opType,
            processedCount = processedCount,
            undoOperation = undoOperation,
        )
        KIND_PARTIAL -> BrowseFileTransferTerminalEvent.PartialSuccess(
            workId = workId,
            operationType = opType,
            processedCount = processedCount,
            failedCount = failedCount,
            details = details,
            undoOperation = undoOperation,
        )
        KIND_FAILURE -> BrowseFileTransferTerminalEvent.Failure(
            workId = workId,
            operationType = opType,
            message = message ?: "",
            details = details,
        )
        KIND_AUTH -> BrowseFileTransferTerminalEvent.AuthenticationRequired(
            workId = workId,
            operationType = opType,
            provider = provider.orEmpty(),
            message = message,
        )
        KIND_PERMISSION -> BrowseFileTransferTerminalEvent.PermissionRequired(
            workId = workId,
            operationType = opType,
            pendingIntent = pendingIntent,
        )
        else -> BrowseFileTransferTerminalEvent.Cancelled(
            workId = workId,
            operationType = opType,
        )
    }
}

private fun BrowseFileTransferTerminalPayload.buildUndoOperation(
    operationType: FileOperationType,
): UndoOperation? {
    if (undoSourceFiles.isEmpty() || undoDestinationFolder.isNullOrBlank() || undoCopiedFiles.isEmpty()) {
        return null
    }
    return UndoOperation(
        type = operationType,
        sourceFiles = undoSourceFiles,
        destinationFolder = undoDestinationFolder,
        copiedFiles = undoCopiedFiles,
        oldNames = null,
        timestamp = System.currentTimeMillis(),
    )
}
