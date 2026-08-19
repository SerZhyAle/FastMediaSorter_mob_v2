package com.sza.fastmediasorter.service

import android.content.SharedPreferences
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.domain.model.WearPhoneResourcePage
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.ApplyWatchFavoritesDeltaUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ListPhoneResourcePageUseCase
import com.sza.fastmediasorter.domain.usecase.OpenPhoneResourceChannelUseCase
import com.sza.fastmediasorter.domain.usecase.PhoneResourceChannel
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    @Inject lateinit var listPhoneResourcePageUseCase: ListPhoneResourcePageUseCase

    @Inject lateinit var openPhoneResourceChannelUseCase: OpenPhoneResourceChannelUseCase

    @Inject lateinit var wearableDataLayerRepository: WearableDataLayerRepository

    @Inject lateinit var wearLogReportReceiver: WearLogReportReceiver

    @Inject lateinit var gson: Gson

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("PhoneWearListenerService: message received ${event.path}")
        when (event.path) {
            PATH_REQUEST                       -> handleSyncRequest()
            PATH_ACK                           -> handleAck(event.data)
            WearDataLayerPaths.SOURCES_EXPORT  -> handleSourcesExport(event.data)
            WearDataLayerPaths.FAVORITES_DELTA -> handleFavoritesDelta(event.data)
            WearDataLayerPaths.PHONE_RESOURCE_BROWSE_REQUEST -> handlePhoneResourceBrowse(event.data)
            WearDataLayerPaths.PHONE_RESOURCE_OPEN_REQUEST ->
                handlePhoneResourceOpen(event.sourceNodeId, event.data)
            WearDataLayerPaths.LOG_REPORT_REQUEST ->
                handleLogReport(event.sourceNodeId, event.data)
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
                WearSyncEvents.emitWatchPlaybackState(payload)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize playback state payload")
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
                WearSyncEvents.emitWatchSources(payload)
            } catch (e: Exception) {
                Timber.e(e, "failed to deserialize sources export payload")
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
                Timber.e(e, "Failed to deserialize favorites delta payload")
            }
        }
    }

    private fun handleLogReport(nodeId: String, data: ByteArray) {
        serviceScope.launch {
            wearLogReportReceiver.handle(nodeId, data)
        }
    }

    private fun handlePhoneResourceBrowse(data: ByteArray) {
        Timber.d("S1697: phone received a watch browse request")
        serviceScope.launch {
            val request = parsePhoneResourceRequest(data) ?: return@launch
            sendPhoneResourcePage(listPhoneResourcePageUseCase(request))
        }
    }

    private fun handlePhoneResourceOpen(nodeId: String, data: ByteArray) {
        Timber.d("S1697: phone received a watch open request")
        serviceScope.launch {
            val request = parsePhoneResourceRequest(data) ?: return@launch
            when (val outcome = openPhoneResourceChannelUseCase(request)) {
                is PhoneResourceChannel.Rejected -> sendPhoneResourcePage(
                    WearPhoneResourcePage(requestId = request.requestId, status = outcome.status)
                )

                is PhoneResourceChannel.Approved -> transferApprovedItem(nodeId, request, outcome)
            }
        }
    }

    /**
     * The watch is told the outcome before the bytes start, so a transfer that dies mid-flight is a
     * dropped channel on a page it already has rather than a request that never answered.
     */
    private suspend fun transferApprovedItem(
        nodeId: String,
        request: WearPhoneResourceRequest,
        approved: PhoneResourceChannel.Approved
    ) {
        sendPhoneResourcePage(
            WearPhoneResourcePage(
                requestId = request.requestId,
                status = WearPhoneResourceResponseStatus.OK,
                items = listOf(
                    WearPhoneResourceItem(
                        token = request.itemToken.orEmpty(),
                        name = approved.name,
                        sizeBytes = approved.sizeBytes,
                        isDirectory = false
                    )
                )
            )
        )

        val channelClient = Wearable.getChannelClient(applicationContext)
        val channel = try {
            channelClient.openChannel(nodeId, WearDataLayerPaths.PHONE_RESOURCE_TRANSFER).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to open phone resource channel")
            sendPhoneResourcePage(
                WearPhoneResourcePage(
                    requestId = request.requestId,
                    status = WearPhoneResourceResponseStatus.TRANSFER_REJECTED
                )
            )
            return
        }

        try {
            channelClient.getOutputStream(channel).await().use { output ->
                approved.openStream().use { input -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            // A watch that walks away cancels this scope; the channel still has to be closed below.
            Timber.w(e, "Phone resource transfer interrupted")
        } finally {
            runCatching { channelClient.close(channel).await() }
                .onFailure { Timber.w(it, "Failed to close phone resource channel") }
        }
    }

    private fun parsePhoneResourceRequest(data: ByteArray): WearPhoneResourceRequest? = try {
        gson.fromJson(data.decodeToString(), WearPhoneResourceRequest::class.java)
    } catch (e: Exception) {
        Timber.e(e, "Failed to deserialize phone resource request")
        null
    }

    private suspend fun sendPhoneResourcePage(page: WearPhoneResourcePage) {
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PHONE_RESOURCE_PAGE,
            sentAt = System.currentTimeMillis(),
            data = gson.toJson(page).toByteArray(Charsets.UTF_8)
        )
        runCatching {
            wearableDataLayerRepository.putEnvelopeDataItem(
                WearDataLayerPaths.PHONE_RESOURCE_PAGE,
                envelope
            )
        }.onFailure { Timber.e(it, "Failed to publish phone resource page") }
    }

    private fun handleSyncRequest() {
        Timber.i("Watch requested sync - sending resources")
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
            WearSyncEvents.emitAck(json)
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
