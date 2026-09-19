package com.sza.fastmediasorter.domain.usecase.wear

import android.os.Build
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.PhoneClipboardSendOutcome
import com.sza.fastmediasorter.domain.model.WearClipboardTextAck
import com.sza.fastmediasorter.domain.model.WearClipboardTextCodec
import com.sza.fastmediasorter.domain.model.WearClipboardTextPayload
import com.sza.fastmediasorter.domain.model.WearClipboardTextRefusalReasons
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S3109: carries this phone's text clipboard to the paired watch and waits for its answer.
 *
 * It never reads the clipboard itself. ADR-1: only the foreground app may read its own clipboard
 * since Android 10, so the read belongs to the screen the owner is looking at, and a use case doing
 * it would run wherever it happened to be called from.
 */
class SendClipboardTextToWatchUseCase @Inject constructor(
    private val dataLayerRepository: WearableDataLayerRepository,
    private val gson: Gson
) {

    suspend operator fun invoke(text: String): PhoneClipboardSendOutcome = when {
        text.isBlank() -> PhoneClipboardSendOutcome.NothingToSend

        // Refused here rather than on the watch: an oversized text would spend a Data Layer message
        // and a whole timeout to be told what this build already knows.
        text.length > WearClipboardTextPayload.MAX_TEXT_LENGTH ->
            PhoneClipboardSendOutcome.WatchRefused(WearClipboardTextRefusalReasons.TOO_LONG)

        else -> roundTrip(text, dataLayerRepository.getConnectedNodes())
    }

    private suspend fun roundTrip(text: String, nodes: List<WearNode>): PhoneClipboardSendOutcome {
        if (nodes.isEmpty()) {
            return PhoneClipboardSendOutcome.NoConnectedWatch
        }

        val requestId = UUID.randomUUID().toString()
        val bytes = WearClipboardTextCodec.serialize(payload(requestId, text), gson)
        var sent = true

        // onSubscription, not a plain collect after the send: the ack flow keeps no replay, so a
        // watch answering faster than this collector is registered would have its answer dropped and
        // this phone would wait out the whole timeout for a text that in fact arrived.
        val ack = withTimeoutOrNull(ACK_TIMEOUT_MS) {
            WearSyncEvents.clipboardTextAckFlow
                .onSubscription { sent = sendToAll(nodes, bytes) }
                .first { answer -> answer.requestId == requestId }
        }
        return if (!sent) PhoneClipboardSendOutcome.NoConnectedWatch else answered(ack)
    }

    /** True when at least one node accepted the text. */
    private suspend fun sendToAll(nodes: List<WearNode>, bytes: ByteArray): Boolean =
        nodes.map { node ->
            // map before any: any() short-circuits, and the text is meant to reach every connected
            // watch, not merely the first one that accepts it.
            runCatching {
                dataLayerRepository.sendMessage(
                    node.id,
                    WearDataLayerPaths.CLIPBOARD_TEXT_FROM_PHONE,
                    bytes
                )
            }
                .onFailure { Timber.w(it, "Phone clipboard: send to ${node.id} failed") }
                .isSuccess
        }.any { it }

    private fun answered(ack: WearClipboardTextAck?) = when {
        ack == null -> PhoneClipboardSendOutcome.WatchDidNotAnswer
        ack.accepted -> PhoneClipboardSendOutcome.Delivered
        else -> PhoneClipboardSendOutcome.WatchRefused(ack.reason.orEmpty())
    }

    private fun payload(requestId: String, text: String) = WearClipboardTextPayload(
        requestId = requestId,
        sourceDeviceModel = Build.MODEL,
        capturedAtEpochMillis = System.currentTimeMillis(),
        text = text
    )

    private companion object {
        /** The same wait the watch gives this phone, so neither side calls the other slow first. */
        const val ACK_TIMEOUT_MS = 15_000L
    }
}
