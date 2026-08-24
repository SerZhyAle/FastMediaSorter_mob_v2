package com.sza.fastmediasorter.domain.transfer

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearFileTransferItem
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File

/**
 * S1861: writes into the paired-watch resource, the branch of [FileOperationUseCase] that a
 * `wear://` destination takes.
 *
 * Same shape as the `Local*FileOperation` siblings on purpose - one call in, one
 * [FileOperationResult] out - so a watch-bound copy reports its outcome through the notification,
 * the progress dialog and the terminal message the transfer worker already owns. Goal 5 of the
 * strategic spec asks that the user see what arrived and what did not; a second surface built only
 * for the watch would answer that twice.
 */
internal class WearWatchFileOperation(
    private val context: Context,
    private val transferRepository: WearFileTransferRepository
) {

    suspend fun execute(
        operation: FileOperation,
        progressCallback: ByteProgressCallback? = null
    ): FileOperationResult {
        return dispatch(operation, progressCallback)
    }

    private suspend fun dispatch(
        operation: FileOperation,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult = when (operation) {
        is FileOperation.Copy -> send(operation.sources, operation, deleteSources = false, progressCallback)
        is FileOperation.Move -> send(operation.sources, operation, deleteSources = true, progressCallback)
        // Editing what already sits on the watch is a declared non-goal of this ticket and belongs
        // to S1863; refusing in words beats a silent no-op the user reads as success.
        is FileOperation.Delete, is FileOperation.Rename -> FileOperationResult.Failure(
            error = context.getString(R.string.wear_watch_operation_unsupported),
            errorRes = R.string.wear_watch_operation_unsupported
        )
    }

    private suspend fun send(
        sources: List<File>,
        operation: FileOperation,
        deleteSources: Boolean,
        progressCallback: ByteProgressCallback?
    ): FileOperationResult {
        val errors = mutableListOf<String>()
        val delivered = mutableListOf<String>()
        sources.forEachIndexed { index, source ->
            progressCallback?.onFileStarted(index + 1, source.name, sources.size)
            val outcome = awaitTransfer(source, progressCallback)
            if (outcome == WearFileTransferOutcome.SUCCEEDED) {
                delivered += source.absolutePath
                if (deleteSources && !source.delete()) {
                    Timber.w("Sent %s to the watch but could not remove the source", source.name)
                }
            } else {
                errors += context.getString(messageFor(outcome), source.name)
            }
        }
        return resultOf(operation, sources.size, delivered, errors)
    }

    /**
     * Queues one file and suspends until the queue reports it finished.
     *
     * The queue is read rather than awaited by callback because it is the repository's only public
     * shape, and because a snapshot lets a late read still see a transfer that finished quickly.
     */
    private suspend fun awaitTransfer(
        source: File,
        progressCallback: ByteProgressCallback?
    ): WearFileTransferOutcome {
        val transferId = transferRepository.enqueue(source.absolutePath, source.name)
        var lastReported = -1L
        val finished = transferRepository.transfers.first { state ->
            val item = state.items.firstOrNull { it.id == transferId }
            if (item != null && item.transferredBytes != lastReported) {
                lastReported = item.transferredBytes
                progressCallback?.onProgress(item.transferredBytes, item.totalBytes, 0L)
            }
            // An absent entry is the inert wearStub queue, which returns an empty id and never
            // enqueues: without this arm the flavors carrying no companion would suspend here for
            // the life of the process instead of reporting that the file did not reach a watch.
            item == null || item.outcome.isTerminal
        }
        return outcomeOf(finished.items.firstOrNull { it.id == transferId })
    }

    private fun outcomeOf(item: WearFileTransferItem?): WearFileTransferOutcome =
        item?.outcome ?: WearFileTransferOutcome.FAILED

    private fun messageFor(outcome: WearFileTransferOutcome): Int = when (outcome) {
        WearFileTransferOutcome.TOO_LARGE -> R.string.wear_watch_transfer_too_large
        WearFileTransferOutcome.WATCH_UNREACHABLE -> R.string.wear_watch_transfer_unreachable
        WearFileTransferOutcome.CANCELLED -> R.string.wear_watch_transfer_cancelled
        else -> R.string.wear_watch_transfer_failed_file
    }

    private fun resultOf(
        operation: FileOperation,
        total: Int,
        delivered: List<String>,
        errors: List<String>
    ): FileOperationResult = when {
        errors.isEmpty() -> FileOperationResult.Success(
            processedCount = delivered.size,
            operation = operation,
            copiedFilePaths = delivered
        )
        delivered.isEmpty() -> FileOperationResult.Failure(error = errors.joinToString("\n"))
        else -> FileOperationResult.PartialSuccess(
            processedCount = delivered.size,
            failedCount = total - delivered.size,
            errors = errors,
            deletedPaths = delivered
        )
    }
}
