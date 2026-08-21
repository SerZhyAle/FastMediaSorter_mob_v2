package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.model.WearStreamTransferAck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus for messages received from the watch companion.
 *
 * Lives in `src/main` (GMS-free, pure coroutines) so the main-flavor `WearSyncViewModel` collector
 * compiles for every flavor, while the GMS-backed `PhoneWearListenerService` emitter (S0403
 * `wearGms` source set) publishes into it. Non-Wear flavors keep these flows inert - nothing emits.
 *
 * S1031: the mutable backing flows are private; collectors read the read-only [SharedFlow] views and
 * the emitter publishes through the explicit `emit*` methods, so a single owner controls emission.
 */
object WearSyncEvents {
    private val _ackFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ackFlow: SharedFlow<String> = _ackFlow.asSharedFlow()

    private val _watchSourcesReceivedFlow =
        MutableSharedFlow<WearSourcesExportPayload>(extraBufferCapacity = 1)
    val watchSourcesReceivedFlow: SharedFlow<WearSourcesExportPayload> =
        _watchSourcesReceivedFlow.asSharedFlow()

    private val _watchPlaybackStateFlow =
        MutableSharedFlow<WearPlaybackStatePayload?>(extraBufferCapacity = 1)
    val watchPlaybackStateFlow: SharedFlow<WearPlaybackStatePayload?> =
        _watchPlaybackStateFlow.asSharedFlow()

    // S1799: typed, request-correlated ack channel for single-stream transfers. Kept apart from the
    // legacy untyped ackFlow, whose only discriminator is "a Sending state exists somewhere".
    private val _streamTransferAckFlow =
        MutableSharedFlow<WearStreamTransferAck>(extraBufferCapacity = 4)
    val streamTransferAckFlow: SharedFlow<WearStreamTransferAck> = _streamTransferAckFlow.asSharedFlow()

    suspend fun emitAck(json: String) = _ackFlow.emit(json)

    suspend fun emitStreamTransferAck(ack: WearStreamTransferAck) = _streamTransferAckFlow.emit(ack)

    suspend fun emitWatchSources(payload: WearSourcesExportPayload) =
        _watchSourcesReceivedFlow.emit(payload)

    suspend fun emitWatchPlaybackState(state: WearPlaybackStatePayload?) =
        _watchPlaybackStateFlow.emit(state)
}
