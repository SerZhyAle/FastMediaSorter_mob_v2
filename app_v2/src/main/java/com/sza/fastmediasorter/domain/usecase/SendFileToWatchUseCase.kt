package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.domain.model.WearFileTransferAck
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Sends a local media file to the paired watch and awaits its correlated open outcome. */
class SendFileToWatchUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val fileTransferRepository: WearFileTransferRepository
) {

    sealed interface Outcome {
        data object Opened : Outcome
        data object WatchAppNotOpen : Outcome
        data object WatchUnavailable : Outcome
        data object NoReply : Outcome
        data object UnsupportedType : Outcome
        data object TooLarge : Outcome
        data class Error(val message: String?) : Outcome
    }

    internal var ackTimeoutMs: Long = ACK_TIMEOUT_MS

    suspend operator fun invoke(
        path: String,
        displayName: String,
        mediaType: MediaType
    ): Outcome {
        return when {
            mediaType !in RENDERABLE_ON_WATCH -> Outcome.UnsupportedType
            File(path).length() > WEAR_FILE_TRANSFER_MAX_BYTES -> Outcome.TooLarge
            wearableRepository.getConnectedNodes().isEmpty() -> Outcome.WatchUnavailable
            else -> sendAndAwait(path, displayName, mediaType)
        }
    }

    private suspend fun sendAndAwait(
        path: String,
        displayName: String,
        mediaType: MediaType
    ): Outcome {
        val requestId = UUID.randomUUID().toString()
        return coroutineScope {
            // Subscribe first so a fast watch acknowledgement cannot be missed between send and collect.
            val acknowledgement = async(start = CoroutineStart.UNDISPATCHED) {
                WearSyncEvents.fileTransferAckFlow.first { it.requestId == requestId }
            }
            fileTransferRepository.enqueue(
                sourcePath = path,
                displayName = displayName,
                openNow = true,
                requestId = requestId,
                mediaType = mediaType
            )
            val transferOutcome = fileTransferRepository.awaitTransfer(requestId)
            when (transferOutcome) {
                WearFileTransferOutcome.TOO_LARGE -> {
                    acknowledgement.cancel()
                    Outcome.TooLarge
                }
                WearFileTransferOutcome.WATCH_UNREACHABLE -> {
                    acknowledgement.cancel()
                    Outcome.WatchUnavailable
                }
                WearFileTransferOutcome.SUCCEEDED -> {
                    val ack = withTimeoutOrNull(ackTimeoutMs) { acknowledgement.await() }
                    mapAck(ack)
                }
                else -> {
                    acknowledgement.cancel()
                    Outcome.Error(transferOutcome.name)
                }
            }
        }
    }

    private fun mapAck(ack: WearFileTransferAck?): Outcome = when (ack?.outcome) {
        null -> Outcome.NoReply
        WearFileTransferAck.OUTCOME_OPENED -> Outcome.Opened
        WearFileTransferAck.OUTCOME_NOT_FOREGROUND -> Outcome.WatchAppNotOpen
        WearFileTransferAck.OUTCOME_UNSUPPORTED -> Outcome.UnsupportedType
        WearFileTransferAck.OUTCOME_TOO_LARGE -> Outcome.TooLarge
        else -> Outcome.Error(ack.outcome)
    }

    private companion object {
        const val ACK_TIMEOUT_MS = 15_000L
        val RENDERABLE_ON_WATCH = setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO, MediaType.AUDIO)
    }
}
