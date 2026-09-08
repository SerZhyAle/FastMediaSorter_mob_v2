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
}
