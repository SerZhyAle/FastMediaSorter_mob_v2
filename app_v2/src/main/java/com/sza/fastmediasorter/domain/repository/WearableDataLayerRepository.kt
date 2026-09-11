package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearNode

interface WearableDataLayerRepository {
    /** Returns all currently connected Wear OS nodes (paired watches). */
    suspend fun getConnectedNodes(): List<WearNode>

    /** Stores a data item at [path] that survives app restarts and reconnects. */
    suspend fun putDataItem(path: String, payload: ByteArray)

    /** Sends a fire-and-forget message to a specific node. */
    suspend fun sendMessage(nodeId: String, path: String, data: ByteArray)

    /** Serializes [envelope] to JSON and stores it as a Data Item at [path]. */
    suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope)

    /**
     * S2550: asks the watch at [nodeId] to let this phone listen to its microphone.
     *
     * It starts nothing by itself. ADR-6: the watch raises a request the owner must tap, because the
     * platform refuses to create a microphone service from the background on two counts. The answer
     * arrives separately, on `WearSyncEvents.listenAckFlow`, carrying either an address or a reason.
     */
    suspend fun sendListenStart(nodeId: String, requestId: String)

    /** S2550: ends the listening session on [nodeId]. Safe to send without knowing what is running. */
    suspend fun sendListenStop(nodeId: String, requestId: String)

    /**
     * S2981: whether the owner left the Wear Companion switch on.
     *
     * Defaults to true so a bridge with no switch of its own - the stub and the test fakes - never
     * refuses a request on the switch's behalf.
     */
    suspend fun isCompanionEnabled(): Boolean = true

    /**
     * S2981: publishes a refusal at [path] even while the companion switch is off.
     *
     * The one exception to the switch silencing every send. It exists so the phone can tell a watch
     * that asked "the companion is off" instead of leaving it to time out and blame a missing phone,
     * and it must only ever carry that refusal - never a listing or any other content.
     */
    suspend fun putCompanionRefusal(path: String, payload: ByteArray) = putDataItem(path, payload)
}
