package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.domain.model.WearBroadcastHandoffPayload
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.streams.BuildWatchBroadcastHandoffUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

        /** S1944: stored and playing - the watch app was on screen and navigated to its player. */
        data object Opened : Outcome

        /**
         * S1944: stored, but not opened, because the watch app was not on screen. Kept apart from
         * [Error] deliberately: the platform forbids waking the watch app from a Data Layer message
         * (ADR-1), so this is the ordinary ending of a normal request, and the user is told what to
         * do rather than that something broke.
         */
        data object WatchAppNotOpen : Outcome

        /** S4261: the watch has no endpoint of the broadcast it plays, or it is protected without its pass. */
        data object Unplayable : Outcome

        /** S4261: silence to the descriptor form, which a watch build older than the form never answers. */
        data object NoReplyToBroadcast : Outcome
    }

    // Test seam: production keeps the default; unit tests shrink it so NoReply cases finish fast.
    internal var ackTimeoutMs: Long = ACK_TIMEOUT_MS

    suspend operator fun invoke(
        title: String,
        url: String,
        mediaKind: String,
        openNow: Boolean = false,
        handoff: BuildWatchBroadcastHandoffUseCase.Result.Handoff? = null,
    ): Outcome {
        val nodes = wearableRepository.getConnectedNodes()
        if (nodes.isEmpty()) return Outcome.WatchUnavailable

        val requestId = UUID.randomUUID().toString()
        // Each form is serialized at its own constructor so the wire type stays visible to the Gson contract gate.
        val json = if (handoff == null) {
            gson.toJson(
                WearStreamTransferPayload(
                    requestId = requestId,
                    name = title,
                    url = url,
                    mediaKind = mediaKind,
                    // S1944: the same transfer, asked to end in a player rather than in a list. The default
                    // keeps every existing caller sending exactly what it sent before.
                    openNow = openNow
                )
            )
        } else {
            gson.toJson(
                WearBroadcastHandoffPayload(
                    requestId = requestId,
                    name = title,
                    url = url,
                    mediaKind = handoff.mediaKind,
                    openNow = openNow,
                    broadcastId = handoff.broadcastId,
                    descriptor = handoff.descriptor,
                    pass = handoff.pass
                )
            )
        }
        // S4261: the descriptor form goes on its own path, so an older watch stays silent (LIVE-BROADCAST item P).
        val path = if (handoff == null) WearDataLayerPaths.STREAM_TRANSFER else WearDataLayerPaths.STREAM_BROADCAST_PASS
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_STREAM_TRANSFER,
            sentAt = System.currentTimeMillis(),
            data = json.toByteArray(Charsets.UTF_8)
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
            val accepted = nodes.count { node ->
                runCatching {
                    wearableRepository.sendMessage(node.id, path, bytes)
                }.onFailure { it.warnUnlessCancellation("Failed to send stream transfer to node ${node.id}") }
                    .isSuccess
            }
            // A watch that left the Data Layer after getConnectedNodes() refuses every send; waiting out
            // the ack timeout would report that as NoReply 15 s later instead of WatchUnavailable now.
            if (accepted == 0) {
                ack.cancel()
                Outcome.WatchUnavailable
            } else {
                mapAck(ack.await(), handoff != null)
            }
        }
    }

    private fun mapAck(ack: WearStreamTransferAck?, isHandoff: Boolean): Outcome = when (ack?.outcome) {
        null -> if (isHandoff) Outcome.NoReplyToBroadcast else Outcome.NoReply
        WearStreamTransferAck.OUTCOME_UNPLAYABLE -> Outcome.Unplayable
        WearStreamTransferAck.OUTCOME_STORED -> Outcome.Delivered(updated = false)
        WearStreamTransferAck.OUTCOME_UPDATED -> Outcome.Delivered(updated = true)
        WearStreamTransferAck.OUTCOME_OPENED -> Outcome.Opened
        WearStreamTransferAck.OUTCOME_NOT_FOREGROUND -> Outcome.WatchAppNotOpen
        else -> Outcome.Error(ack.message)
    }

    companion object {
        private const val ACK_TIMEOUT_MS = 15_000L
    }
}
