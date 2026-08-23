package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inert Wear Data Layer bridge for the `wearStub` source set (S0403).
 *
 * Mounted into non-Wear flavors (lite, photos, legacy, vr) and the FOSS flavor so the Hilt graph that
 * injects [WearableDataLayerRepository] resolves without the proprietary Play Services Wearable SDK
 * on the classpath. No watch is ever reachable here: [getConnectedNodes] is empty and the send/put
 * operations are no-ops.
 */
@Singleton
class NoOpWearableDataLayerRepository @Inject constructor() : WearableDataLayerRepository {

    override suspend fun getConnectedNodes(): List<WearNode> = emptyList()

    override suspend fun putDataItem(path: String, payload: ByteArray) = Unit

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) = Unit

    override suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope) = Unit
}
