package com.sza.fastmediasorter.wear.data.wear

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.usecase.ApplyWearSettingsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ImportNetworkSourcesUseCase
import com.sza.fastmediasorter.wear.domain.usecase.StoreTransferredStreamUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

import com.sza.fastmediasorter.wear.data.repository.WearFileReceiverManager
import com.google.android.gms.wearable.ChannelClient

private const val PATH_PUSH = "/fms/network_sources/push"
private const val PATH_ACK  = "/fms/network_sources/ack"

@AndroidEntryPoint
class WatchWearListenerService : WearableListenerService() {

    private val envelopeCodec = WearEventEnvelopeCodec()

    @Inject lateinit var importNetworkSourcesUseCase: ImportNetworkSourcesUseCase
    @Inject lateinit var applyWearSettingsUseCase: ApplyWearSettingsUseCase
    @Inject lateinit var wearFileReceiverManager: WearFileReceiverManager
    @Inject lateinit var storeTransferredStreamUseCase: StoreTransferredStreamUseCase
    @Inject lateinit var gson: Gson

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path.startsWith("/fms/transfer_file")) {
            val fileName = channel.path.substringAfterLast('/', "transferred_media")
            serviceScope.launch {
                wearFileReceiverManager.receiveFile(channel, fileName)
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                PATH_PUSH -> {
                    val payloadBytes = dataMap.getByteArray("payload") ?: continue
                    handlePush(payloadBytes, event.dataItem.uri.host ?: "")
                }
                WearDataLayerPaths.SETTINGS_PUSH -> {
                    val payloadBytes = dataMap.getByteArray("payload") ?: continue
                    handleSettingsPush(payloadBytes)
                }
            }
        }
        events.release()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearDataLayerPaths.PLAYBACK_CMD -> handlePlaybackCommand(event.data)
            WearDataLayerPaths.STREAM_TRANSFER ->
                handleStreamTransfer(event.sourceNodeId, event.data)
            else -> Timber.d("WatchWearListenerService: unhandled message path ${event.path}")
        }
    }

    private fun handleStreamTransfer(nodeId: String, data: ByteArray) {
        Timber.d("S1799: stream transfer message received from $nodeId")
        serviceScope.launch {
            val payload = try {
                val envelope = envelopeCodec.decode(data)
                gson.fromJson(envelope.data.decodeToString(), WearStreamTransferPayload::class.java)
            } catch (e: Exception) {
                // No parseable payload means no requestId either, so no ack can be correlated -
                // the phone reports the timeout outcome instead.
                Timber.e(e, "Failed to deserialize stream transfer payload")
                null
            } ?: return@launch
            val ack = storeTransferredStreamUseCase(payload)
            if (ack.outcome != WearStreamTransferAck.OUTCOME_ERROR) {
                vibrateSuccess()
            }
            sendStreamTransferAck(nodeId, ack)
        }
    }

    private suspend fun sendStreamTransferAck(nodeId: String, ack: WearStreamTransferAck) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(this)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.STREAM_TRANSFER_ACK,
                    gson.toJson(ack).toByteArray(Charsets.UTF_8)
                )
                .await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to send stream transfer ack")
        }
    }

    private fun handleSettingsPush(payloadBytes: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = envelopeCodec.decode(payloadBytes)
                val payload = gson.fromJson(envelope.data.decodeToString(), WearSettingsPayload::class.java)
                applyWearSettingsUseCase(payload)
            } catch (e: Exception) {
                Timber.e(e, "Failed to apply settings push")
                WatchSyncEvents.settingsErrorFlow.emit(e.message ?: "Settings apply failed")
            }
        }
    }

    private fun handlePlaybackCommand(data: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = envelopeCodec.decode(data)
                val commandName = gson.fromJson(envelope.data.decodeToString(), String::class.java)
                val command = WearPlaybackCommand.valueOf(commandName)
                WatchPlaybackCommandEvents.commandFlow.emit(command)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize playback command")
            }
        }
    }

    private fun handlePush(payloadBytes: ByteArray, senderNodeId: String) {
        serviceScope.launch {
            try {
                val json = payloadBytes.decodeToString()
                val payload = gson.fromJson(json, WearSyncPayload::class.java)
                val result = importNetworkSourcesUseCase(payload)
                Timber.i("WatchWearListenerService: import done $result")
                vibrateSuccess()
                WatchSyncEvents.importResultFlow.emit(result)
                sendAck(senderNodeId, result)
            } catch (e: Exception) {
                Timber.e(e, "Failed to process sync payload")
                WatchSyncEvents.importErrorFlow.emit(e.message ?: "Unknown error")
            }
        }
    }

    private fun vibrateSuccess() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 120), -1))
    }

    private suspend fun sendAck(nodeId: String, result: ImportResult) {
        if (nodeId.isBlank()) return
        val ackJson = """{"added":${result.added},"updated":${result.updated}}"""
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, PATH_ACK, ackJson.toByteArray())
                .await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to send ack to phone")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

/** Process-wide event bus for sync results on the watch. */
object WatchSyncEvents {
    val importResultFlow  = MutableSharedFlow<ImportResult>(extraBufferCapacity = 1)
    val importErrorFlow   = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val settingsErrorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

/** Process-wide event bus for remote playback commands received from the phone. */
object WatchPlaybackCommandEvents {
    val commandFlow = MutableSharedFlow<WearPlaybackCommand>(extraBufferCapacity = 4)
}
