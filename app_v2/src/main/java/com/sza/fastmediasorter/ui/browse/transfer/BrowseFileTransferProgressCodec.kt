package com.sza.fastmediasorter.ui.browse.transfer

import androidx.work.Data
import androidx.work.workDataOf
import com.sza.fastmediasorter.domain.model.FileOperationType

object BrowseFileTransferProgressCodec {

    private const val KEY_KIND = "browse_transfer_kind"
    private const val KEY_WORK_ID = "browse_transfer_work_id"
    private const val KEY_OPERATION_TYPE = "browse_transfer_operation_type"
    private const val KEY_TOTAL_FILES = "browse_transfer_total_files"
    private const val KEY_CURRENT_INDEX = "browse_transfer_current_index"
    private const val KEY_CURRENT_FILE = "browse_transfer_current_file"
    private const val KEY_BYTES_TRANSFERRED = "browse_transfer_bytes_transferred"
    private const val KEY_TOTAL_BYTES = "browse_transfer_total_bytes"
    private const val KEY_SPEED = "browse_transfer_speed"
    private const val KEY_COMPLETED_BYTES = "browse_transfer_completed_bytes"
    private const val KEY_TOTAL_OPERATION_BYTES = "browse_transfer_total_operation_bytes"
    private const val KEY_PROVIDER = "browse_transfer_provider"
    private const val KEY_MESSAGE = "browse_transfer_message"

    private const val KIND_PROGRESS = "progress"
    private const val KIND_SUCCESS = "success"
    private const val KIND_PARTIAL = "partial"
    private const val KIND_FAILURE = "failure"
    private const val KIND_AUTH = "auth"
    private const val KIND_PERMISSION = "permission"
    private const val KIND_CANCELLED = "cancelled"

    fun encodeProgress(workId: String, snapshot: BrowseFileTransferProgressSnapshot): Data = workDataOf(
        KEY_KIND to KIND_PROGRESS,
        KEY_WORK_ID to workId,
        KEY_OPERATION_TYPE to snapshot.operationType.name,
        KEY_TOTAL_FILES to snapshot.totalFiles,
        KEY_CURRENT_INDEX to snapshot.currentIndex,
        KEY_CURRENT_FILE to snapshot.currentFile,
        KEY_BYTES_TRANSFERRED to snapshot.bytesTransferred,
        KEY_TOTAL_BYTES to snapshot.totalBytes,
        KEY_SPEED to snapshot.speedBytesPerSecond,
        KEY_COMPLETED_BYTES to snapshot.completedOperationBytes,
        KEY_TOTAL_OPERATION_BYTES to snapshot.totalOperationBytes,
    )

    fun decodeProgress(data: Data): BrowseFileTransferProgressSnapshot? {
        if (data.getString(KEY_KIND) != KIND_PROGRESS) return null
        val operationType = data.getString(KEY_OPERATION_TYPE)?.let(FileOperationType::valueOf) ?: return null
        return BrowseFileTransferProgressSnapshot(
            operationType = operationType,
            totalFiles = data.getInt(KEY_TOTAL_FILES, 0),
            currentIndex = data.getInt(KEY_CURRENT_INDEX, 0),
            currentFile = data.getString(KEY_CURRENT_FILE).orEmpty(),
            bytesTransferred = data.getLong(KEY_BYTES_TRANSFERRED, 0L),
            totalBytes = data.getLong(KEY_TOTAL_BYTES, 0L),
            speedBytesPerSecond = data.getLong(KEY_SPEED, 0L),
            completedOperationBytes = data.getLong(KEY_COMPLETED_BYTES, 0L),
            totalOperationBytes = data.getLong(KEY_TOTAL_OPERATION_BYTES, 0L),
        )
    }

    fun encodeTerminalFallback(event: BrowseFileTransferTerminalEvent): Data {
        val kind = when (event) {
            is BrowseFileTransferTerminalEvent.Success -> KIND_SUCCESS
            is BrowseFileTransferTerminalEvent.PartialSuccess -> KIND_PARTIAL
            is BrowseFileTransferTerminalEvent.Failure -> KIND_FAILURE
            is BrowseFileTransferTerminalEvent.AuthenticationRequired -> KIND_AUTH
            is BrowseFileTransferTerminalEvent.PermissionRequired -> KIND_PERMISSION
            is BrowseFileTransferTerminalEvent.Cancelled -> KIND_CANCELLED
        }
        return workDataOf(
            KEY_KIND to kind,
            KEY_WORK_ID to event.workId,
            KEY_OPERATION_TYPE to event.operationType.name,
            KEY_PROVIDER to (event as? BrowseFileTransferTerminalEvent.AuthenticationRequired)?.provider,
            KEY_MESSAGE to when (event) {
                is BrowseFileTransferTerminalEvent.Failure -> event.message
                is BrowseFileTransferTerminalEvent.AuthenticationRequired -> event.message
                else -> null
            },
        )
    }

    fun decodeTerminalFallback(data: Data): BrowseFileTransferTerminalEvent? {
        val workId = data.getString(KEY_WORK_ID) ?: return null
        val operationType = data.getString(KEY_OPERATION_TYPE)?.let(FileOperationType::valueOf) ?: return null
        return when (data.getString(KEY_KIND)) {
            KIND_SUCCESS -> BrowseFileTransferTerminalEvent.Success(workId, operationType, 0, null)
            KIND_PARTIAL -> BrowseFileTransferTerminalEvent.PartialSuccess(workId, operationType, 0, 0, null, null)
            KIND_FAILURE -> BrowseFileTransferTerminalEvent.Failure(
                workId = workId,
                operationType = operationType,
                message = data.getString(KEY_MESSAGE).orEmpty(),
            )
            KIND_AUTH -> BrowseFileTransferTerminalEvent.AuthenticationRequired(
                workId = workId,
                operationType = operationType,
                provider = data.getString(KEY_PROVIDER).orEmpty(),
                message = data.getString(KEY_MESSAGE),
            )
            KIND_PERMISSION -> BrowseFileTransferTerminalEvent.PermissionRequired(
                workId = workId,
                operationType = operationType,
                pendingIntent = null,
            )
            KIND_CANCELLED -> BrowseFileTransferTerminalEvent.Cancelled(workId, operationType)
            else -> null
        }
    }
}
