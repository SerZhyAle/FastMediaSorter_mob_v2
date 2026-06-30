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
}
