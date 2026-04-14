package com.sza.fastmediasorter.data.wear

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableDataLayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearableDataLayerRepository {

    override suspend fun getConnectedNodes(): List<Node> = try {
        Wearable.getNodeClient(context).connectedNodes.await()
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
        Timber.d("sendMessage: $path → $nodeId")
    }
}
