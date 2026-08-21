package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S1799: sends one stream description to the watch and resolves the correlated outcome the streams
 * screen shows the user. "No watch", "watch silent" and "watch refused" are distinct answers -
 * criterion 4 of the spec is that the phone never reports success it cannot prove.
 */
class SendStreamToWatchUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson
) {

    private val envelopeCodec = WearEventEnvelopeCodec()

    sealed interface Outcome {
        data class Delivered(val updated: Boolean) : Outcome
        data object WatchUnavailable : Outcome
        data object NoReply : Outcome
        data class Error(val message: String?) : Outcome
    }

    // Test seam: production keeps the default; unit tests shrink it so NoReply cases finish fast.
    internal var ackTimeoutMs: Long = ACK_TIMEOUT_MS

    suspend operator fun invoke(title: String, url: String, mediaKind: String): Outcome {
        val nodes = wearableRepository.getConnectedNodes()
        if (nodes.isEmpty()) return Outcome.WatchUnavailable

        val requestId = UUID.randomUUID().toString()
        val payload = WearStreamTransferPayload(
            requestId = requestId,
            name = title,
            url = url,
            mediaKind = mediaKind
        )
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_STREAM_TRANSFER,
            sentAt = System.currentTimeMillis(),
            data = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        )
        val bytes = envelopeCodec.encode(envelope)

        return coroutineScope {
            // UNDISPATCHED: the collector must be subscribed before the first send, or a fast ack
            // lands between send and subscribe and the transfer misreports as NoReply.
            val ack = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(ackTimeoutMs) {
                    WearSyncEvents.streamTransferAckFlow.first { it.requestId == requestId }
                }
            }
            nodes.forEach { node ->
                runCatching {
                    wearableRepository.sendMessage(node.id, WearDataLayerPaths.STREAM_TRANSFER, bytes)
                }.onFailure { Timber.w(it, "Failed to send stream transfer to node ${node.id}") }
            }
            mapAck(ack.await())
        }
    }

    private fun mapAck(ack: WearStreamTransferAck?): Outcome = when (ack?.outcome) {
        null -> Outcome.NoReply
        WearStreamTransferAck.OUTCOME_STORED -> Outcome.Delivered(updated = false)
        WearStreamTransferAck.OUTCOME_UPDATED -> Outcome.Delivered(updated = true)
        else -> Outcome.Error(ack.message)
    }

    companion object {
        private const val ACK_TIMEOUT_MS = 15_000L
    }
}
