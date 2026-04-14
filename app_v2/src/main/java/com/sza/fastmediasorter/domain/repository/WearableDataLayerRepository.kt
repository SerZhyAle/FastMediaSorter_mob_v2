package com.sza.fastmediasorter.domain.repository

import com.google.android.gms.wearable.Node

interface WearableDataLayerRepository {
    /** Returns all currently connected Wear OS nodes (paired watches). */
    suspend fun getConnectedNodes(): List<Node>

    /** Stores a data item at [path] that survives app restarts and reconnects. */
    suspend fun putDataItem(path: String, payload: ByteArray)

    /** Sends a fire-and-forget message to a specific node. */
    suspend fun sendMessage(nodeId: String, path: String, data: ByteArray)
}
