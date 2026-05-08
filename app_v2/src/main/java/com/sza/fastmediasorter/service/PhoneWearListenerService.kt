package com.sza.fastmediasorter.service

import android.content.SharedPreferences
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.domain.usecase.ApplyWatchFavoritesDeltaUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val PATH_REQUEST = "/fms/network_sources/request"
private const val PATH_ACK     = "/fms/network_sources/ack"
private const val PREFS_NAME   = "wear_sync_prefs"
private const val KEY_LAST_SYNC = "last_sync_timestamp"

@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    @Inject lateinit var sendResourcesToWatchUseCase: SendResourcesToWatchUseCase
    @Inject lateinit var importWatchSourcesUseCase: ImportWatchSourcesUseCase
    @Inject lateinit var applyWatchFavoritesDeltaUseCase: ApplyWatchFavoritesDeltaUseCase
    @Inject lateinit var gson: Gson

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("PhoneWearListenerService: message received ${event.path}")
        when (event.path) {
            PATH_REQUEST                       -> handleSyncRequest()
            PATH_ACK                           -> handleAck(event.data)
            WearDataLayerPaths.SOURCES_EXPORT  -> handleSourcesExport(event.data)
            WearDataLayerPaths.FAVORITES_DELTA -> handleFavoritesDelta(event.data)
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearDataLayerPaths.PLAYBACK_STATE
            ) {
                val payloadBytes = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.getByteArray("payload") ?: continue
                handlePlaybackState(payloadBytes)
            }
        }
        events.release()
    }

    private fun handlePlaybackState(data: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = gson.fromJson(data.decodeToString(), WearEventEnvelope::class.java)
                val payload = gson.fromJson(
                    envelope.data.decodeToString(),
                    WearPlaybackStatePayload::class.java
                )
                WearSyncEvents.watchPlaybackStateFlow.emit(payload)
            } catch (e: Exception) {
                Timber.e(e, "S0111: Failed to deserialize playback state payload")
            }
        }
    }

    private fun handleSourcesExport(data: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = gson.fromJson(data.decodeToString(), WearEventEnvelope::class.java)
                val payload = gson.fromJson(
                    envelope.data.decodeToString(),
                    WearSourcesExportPayload::class.java
                )
                WearSyncEvents.watchSourcesReceivedFlow.emit(payload)
            } catch (e: Exception) {
                Timber.e(e, "S0111: failed to deserialize sources export payload")
            }
        }
    }

    private fun handleFavoritesDelta(data: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = gson.fromJson(data.decodeToString(), WearEventEnvelope::class.java)
                val payload = gson.fromJson(
                    envelope.data.decodeToString(),
                    WearFavoritesDeltaPayload::class.java
                )
                applyWatchFavoritesDeltaUseCase(payload)
            } catch (e: Exception) {
                Timber.e(e, "S0111: Failed to deserialize favorites delta payload")
            }
        }
    }

    private fun handleSyncRequest() {
        Timber.i("Watch requested sync — sending resources")
        serviceScope.launch {
            sendResourcesToWatchUseCase().onFailure { e ->
                Timber.e(e, "Failed to send resources on watch request")
            }
        }
    }

    private fun handleAck(data: ByteArray) {
        val json = data.decodeToString()
        Timber.i("Watch ack received: $json")
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        // Broadcast result to any active WearSyncViewModel via the companion object flow
        serviceScope.launch {
            WearSyncEvents.ackFlow.emit(json)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = PREFS_NAME
        const val LAST_SYNC = KEY_LAST_SYNC
    }
}

/** Process-wide event bus for messages from the watch. */
object WearSyncEvents {
    val ackFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val watchSourcesReceivedFlow = MutableSharedFlow<WearSourcesExportPayload>(extraBufferCapacity = 1)
    val watchPlaybackStateFlow = MutableSharedFlow<WearPlaybackStatePayload?>(extraBufferCapacity = 1)
}
