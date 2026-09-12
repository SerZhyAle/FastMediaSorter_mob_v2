package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.domain.listen.ListenRequestRegistry
import com.sza.fastmediasorter.wear.domain.model.ListenAckPayload
import com.sza.fastmediasorter.wear.domain.model.ListenRefusal
import com.sza.fastmediasorter.wear.domain.model.ListenSessionPayloadCodec
import com.sza.fastmediasorter.wear.domain.model.LiveAudioEndpoint
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2550 ADR-2: the one place the watch answers a listen command, with an address or with a reason.
 *
 * Every outcome goes out through here rather than from wherever it happened, so "the phone is never
 * left waiting" is a property of one class instead of a habit spread over four. The requester comes
 * from [ListenRequestRegistry], so a caller only has to know what happened, never who asked.
 *
 * **It sends on its own scope, and that is the whole point of the class.** Every answer is owed by
 * something shorter-lived than the exchange: the request expires two minutes after the Data Layer
 * listener that received it was destroyed, and the decline is given by a ViewModel that finishes its
 * window in the same frame. An answer launched on either of those scopes is dropped exactly in the
 * cases acceptance criterion 1a is about - so the callers hand over a fact and this class, which the
 * application owns, is what puts it on the wire.
 *
 * A refusal takes the requester rather than reading it, because it ends the exchange; a served
 * address only reads it, because the stop that follows must reach the same phone.
 */
@Singleton
class ListenAckSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: ListenRequestRegistry,
    private val codec: ListenSessionPayloadCodec
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The watch is serving at [endpoint]. */
    fun answerServing(endpoint: LiveAudioEndpoint) {
        val requester = registry.peek() ?: return
        send(
            requester.nodeId,
            ListenAckPayload.serving(requester.requestId, endpoint.host, endpoint.port)
        )
    }

    /**
     * The watch is not serving, and [refusal] says why. Ends the exchange, and does so exactly once:
     * a decline and an expiry racing each other both call this, and only the one that takes the
     * requester speaks.
     */
    fun answerRefusal(refusal: ListenRefusal) {
        val requester = registry.take() ?: return
        send(requester.nodeId, ListenAckPayload.refused(requester.requestId, refusal))
    }

    /**
     * Refuses a command that never became the pending request - a second phone, or a second start
     * arriving over a live session. It does not touch the registry: answering the newcomer must not
     * take the answer away from whoever is already waiting.
     */
    fun answerRefusalTo(nodeId: String, requestId: String, refusal: ListenRefusal) {
        send(nodeId, ListenAckPayload.refused(requestId, refusal))
    }

    private fun send(nodeId: String, payload: ListenAckPayload) {
        if (nodeId.isBlank()) {
            return
        }
        scope.launch {
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(nodeId, WearDataLayerPaths.LISTEN_ACK, codec.encodeAck(payload))
                    .await()
                Timber.i("Answered a listen command: refusal=%s", payload.refusal)
            }.onFailure { it.errorUnlessCancellation("Failed to answer a listen command") }
        }
    }
}
