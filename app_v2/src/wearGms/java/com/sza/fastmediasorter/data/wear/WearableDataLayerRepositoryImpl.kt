package com.sza.fastmediasorter.data.wear

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GMS-backed Wear Data Layer bridge (S0403 `wearGms` source set).
 *
 * Mounted only into the flavors whose `sourceSets` block adds `src/wearGms/java` - standard and
 * noLegal, and only those two since S1951 narrowed the list. Maps the GMS [Node] type to
 * the domain [WearNode] at this boundary so the `WearableDataLayerRepository` interface in
 * `src/main` stays free of `com.google.android.gms`. FOSS / non-Wear flavors bind the no-op in
 * `src/wearStub` instead.
 */
@Singleton
class WearableDataLayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearableDataLayerRepository {

    private val envelopeCodec = WearEventEnvelopeCodec()

    override suspend fun getConnectedNodes(): List<WearNode> = try {
        Wearable.getNodeClient(context).connectedNodes.await()
            .map { node -> WearNode(id = node.id, displayName = node.displayName) }
    } catch (e: Exception) {
        Timber.e(e, "Failed to get connected nodes")
        emptyList()
    }

    override suspend fun putDataItem(path: String, payload: ByteArray) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putByteArray("payload", payload)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request).await()
        Timber.d("putDataItem: $path (${payload.size} bytes)")
    }

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) {
        Wearable.getMessageClient(context).sendMessage(nodeId, path, data).await()
        Timber.d("sendMessage: $path -> $nodeId")
    }

    override suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope) {
        val bytes = envelopeCodec.encode(envelope)
        putDataItem(path, bytes)
    }
}
