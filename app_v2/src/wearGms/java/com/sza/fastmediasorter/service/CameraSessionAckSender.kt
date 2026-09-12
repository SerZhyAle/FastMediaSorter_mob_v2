package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.broadcast.BroadcastCameraLenses
import com.sza.fastmediasorter.domain.model.WearCameraAckPayload
import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import com.sza.fastmediasorter.domain.model.WearCameraSessionPayloadCodec
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2551: the one place this phone answers a camera command, mirroring the watch's `ListenAckSender`.
 *
 * Every outcome leaves through here rather than from wherever it was decided, so "the watch is never
 * left waiting" is a property of one class instead of a habit spread over the branches above it - the
 * same reason the mirror direction gave for its own sender.
 *
 * A send that fails is logged and swallowed: the watch times the request out on its own, and taking
 * the process down from inside a `WearableListenerService` would cost far more than the lost answer.
 */
@Singleton
class CameraSessionAckSender @Inject constructor(
    private val repository: WearableDataLayerRepository,
    private val codec: WearCameraSessionPayloadCodec
) {

    /** Tells [nodeId] that the session it asked for is not happening, and why. */
    suspend fun answerRefusal(nodeId: String, requestId: String, refusal: WearCameraRefusal) {
        send(nodeId, WearCameraAckPayload.refused(requestId, refusal), "refusal=$refusal")
    }

    /**
     * Tells [nodeId] where to watch, and what else it could ask to watch.
     *
     * [lenses] is the phone's own enumeration rather than anything the watch guessed, which is what
     * makes the picker on the watch describe this phone instead of a fixed pair. [url] must be a
     * finished address: an ack with no reason and no address is read on the watch as an unnameable
     * refusal, by the decoder's own rule.
     */
    suspend fun answerServing(
        nodeId: String,
        requestId: String,
        url: String,
        lenses: BroadcastCameraLenses
    ) {
        val payload = WearCameraAckPayload.serving(
            requestId = requestId,
            url = url,
            lenses = lenses.lenses,
            activeLensId = lenses.activeLensId
        )
        send(nodeId, payload, "lenses=${lenses.lenses.size}")
    }

    private suspend fun send(nodeId: String, payload: WearCameraAckPayload, outcome: String) {
        runCatching {
            repository.sendMessage(
                nodeId,
                WearDataLayerPaths.CAMERA_VIEW_ACK,
                codec.encodeAck(payload)
            )
            Timber.i("Answered a camera command: %s", outcome)
        }.onFailure { Timber.w(it, "Failed to answer a camera command") }
    }
}
