package com.sza.fastmediasorter.data.wear

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
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
    @ApplicationContext private val context: Context,
    private val settingsRepository: Provider<SettingsRepository>
) : WearableDataLayerRepository {

    private val envelopeCodec = WearEventEnvelopeCodec()

    private suspend fun isWearCompanionEnabled(): Boolean = runCatching {
        settingsRepository.get().getSettings().first().enableWearCompanion
    }.getOrDefault(false)

    override suspend fun getConnectedNodes(): List<WearNode> {
        if (!isWearCompanionEnabled()) {
            return emptyList()
        }
        return try {
            Wearable.getNodeClient(context).connectedNodes.await()
                .map { node -> WearNode(id = node.id, displayName = node.displayName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Expected whenever Play Services Wearable is absent or the pairing is gone - a bridge
            // we cannot ask is a bridge with no nodes. INFO, not ERROR (S2365): a normal device
            // state logged at ERROR paints every debug run on a Wear-less device red through the
            // debug notification tree. Mirrors isWatchReachable() in WearWatchMediaScannerImpl.
            Timber.i(e, "Connected-nodes query failed, treating the watch as absent")
            Timber.d("S2365: connectedNodes query fell to the absence branch - logged at info")
            emptyList()
        }
    }

    override suspend fun putDataItem(path: String, payload: ByteArray) {
        if (!isWearCompanionEnabled()) {
            Timber.d("Wear companion disabled in settings; skipping putDataItem $path")
            return
        }
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putByteArray("payload", payload)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request).await()
        Timber.d("putDataItem: $path (${payload.size} bytes)")
    }

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) {
        if (!isWearCompanionEnabled()) {
            Timber.d("Wear companion disabled in settings; skipping sendMessage $path -> $nodeId")
            return
        }
        Wearable.getMessageClient(context).sendMessage(nodeId, path, data).await()
        Timber.d("sendMessage: $path -> $nodeId")
    }

    override suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope) {
        val bytes = envelopeCodec.encode(envelope)
        putDataItem(path, bytes)
    }
}
