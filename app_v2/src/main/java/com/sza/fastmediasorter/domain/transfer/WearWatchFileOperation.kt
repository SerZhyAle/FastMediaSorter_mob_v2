package com.sza.fastmediasorter.domain.transfer

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearFileTransferAck
import com.sza.fastmediasorter.domain.model.WearFileTransferItem
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.UUID

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
        // A file already on the watch is edited on the watch, by its owner. What is missing here is
        // a remote verb: the transfer channel moves bytes towards a destination and carries no
        // delete or rename the phone could send, so there is nothing to dispatch to. Refusing in
        // words beats a silent no-op the user reads as success.
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
            val error = sendOne(source, requireSavedAck = deleteSources, progressCallback = progressCallback)
            if (error == null) {
                delivered += source.absolutePath
                if (deleteSources && !source.delete()) {
                    Timber.w("Sent %s to the watch but could not remove the source", source.name)
                }
            } else {
                errors += error
            }
        }
        return resultOf(operation, sources.size, delivered, errors)
    }

    /**
     * S3360: sends one file, returning the message to report for it or null once it is delivered.
     *
     * A move may not trust [WearFileTransferOutcome.SUCCEEDED] on its own: the queue raises it when
     * the phone has finished writing the bytes into the channel, which says nothing about whether
     * the watch ever turned them into a file. The watch answers that separately, with a correlated
     * ack, so a move waits for the answer before the last remaining copy may be deleted.
     */
    private suspend fun sendOne(
        source: File,
        requireSavedAck: Boolean,
        progressCallback: ByteProgressCallback?
    ): String? = coroutineScope {
        val requestId = UUID.randomUUID().toString()
        // Subscribed before the file is queued and started UNDISPATCHED so the collector is already
        // in place: a small file is acknowledged in milliseconds, and an ack missed that way is
        // indistinguishable here from a watch that never saved anything.
        val savedAck = if (requireSavedAck) {
            async(start = CoroutineStart.UNDISPATCHED) {
                WearSyncEvents.fileTransferAckFlow.first { it.requestId == requestId }
            }
        } else {
            null
        }
        val outcome = awaitTransfer(source, requestId, progressCallback)
        val streamed = outcome == WearFileTransferOutcome.SUCCEEDED
        val ack = if (streamed && savedAck != null) {
            withTimeoutOrNull(SAVE_ACK_TIMEOUT_MS) { savedAck.await() }
        } else {
            null
        }
        savedAck?.cancel()
        when {
            !streamed -> context.getString(messageFor(outcome), source.name)
            savedAck == null -> null
            ack != null && ack.outcome in SAVED_ON_WATCH -> null
            else -> context.getString(R.string.wear_watch_transfer_unconfirmed, source.name)
        }
    }

    /**
     * Queues one file under [requestId] and suspends until the queue reports it finished.
     *
     * The queue is read rather than awaited by callback because it is the repository's only public
     * shape, and because a snapshot lets a late read still see a transfer that finished quickly.
     */
    private suspend fun awaitTransfer(
        source: File,
        requestId: String,
        progressCallback: ByteProgressCallback?
    ): WearFileTransferOutcome {
        val transferId = transferRepository.enqueue(source.absolutePath, source.name, requestId = requestId)
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

    private companion object {
        /**
         * The window the phone already gives a watch to answer a transfer it was sent
         * (SendFileToWatchUseCase); a move waits no longer for an answer of the same kind.
         */
        const val SAVE_ACK_TIMEOUT_MS = 15_000L

        /**
         * Every ack the watch sends once the bytes are a file on it. OPENED and NOT_FOREGROUND are
         * reachable only after the save succeeded, so they confirm the copy exactly as SAVED does.
         */
        val SAVED_ON_WATCH = setOf(
            WearFileTransferAck.OUTCOME_SAVED,
            WearFileTransferAck.OUTCOME_OPENED,
            WearFileTransferAck.OUTCOME_NOT_FOREGROUND
        )
    }
}
