package com.sza.fastmediasorter.ui.browse.transfer

import android.app.PendingIntent
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation

// S0957: BrowseFileTransferRequest, its nested BrowseFileTransferSource and BrowseFileTransferTerminalPayload
// are Gson field-reflection persisted to disk (active_request.json / terminal_event.json) and read back by a
// @HiltWorker that outlives the process. Without @SerializedName R8 renames the fields, so a Play auto-update
// mid-transfer (new R8 mapping between write and read) desyncs the JSON keys. Pinning every persisted field to
// an explicit wire name makes the format R8-independent and stable across app updates while keeping obfuscation.
data class BrowseFileTransferRequest(
    @SerializedName("operationType") val operationType: FileOperationType,
    @SerializedName("sourceResourceId") val sourceResourceId: Long,
    @SerializedName("sourceResourceName") val sourceResourceName: String,
    @SerializedName("sourceCredentialsId") val sourceCredentialsId: String?,
    @SerializedName("currentBrowsePath") val currentBrowsePath: String?,
    @SerializedName("destinationPath") val destinationPath: String,
    @SerializedName("destinationName") val destinationName: String,
    @SerializedName("overwriteFiles") val overwriteFiles: Boolean,
    @SerializedName("sources") val sources: List<BrowseFileTransferSource>,
    @SerializedName("softDelete") val softDelete: Boolean = false,
    // S1370: true when the sources are staging storage created for this operation (share-receive caches
    // the incoming stream before the destination is picked). The requester finishes before the transfer
    // does, so it cannot delete them itself without pulling the files out from under the running worker -
    // the worker owns the purge instead, on every terminal outcome.
    @SerializedName("sourcesOwnedByOperation") val sourcesOwnedByOperation: Boolean = false,
    @SerializedName("stagingDirectoryPath") val stagingDirectoryPath: String? = null,
)

data class BrowseFileTransferSource(
    @SerializedName("path") val path: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("size") val size: Long,
    @SerializedName("isDirectory") val isDirectory: Boolean,
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
    @SerializedName("kind") val kind: String,
    @SerializedName("workId") val workId: String,
    @SerializedName("operationType") val operationType: String,
    @SerializedName("processedCount") val processedCount: Int = 0,
    @SerializedName("failedCount") val failedCount: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("details") val details: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("undoSourceFiles") val undoSourceFiles: List<String> = emptyList(),
    @SerializedName("undoDestinationFolder") val undoDestinationFolder: String? = null,
    @SerializedName("undoCopiedFiles") val undoCopiedFiles: List<String> = emptyList(),
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
