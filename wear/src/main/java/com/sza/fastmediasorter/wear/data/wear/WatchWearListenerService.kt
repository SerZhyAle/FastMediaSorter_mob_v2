package com.sza.fastmediasorter.wear.data.wear

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.wear.data.repository.WearPhonePinsRepository
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.data.wear.helpers.WearTransferOutcomeCoordinator
import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveResult
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferMetadata
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiversPayload
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamPinsPayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.repository.WearFileReceiverRepository
import com.sza.fastmediasorter.wear.domain.usecase.ApplyWearSettingsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.DrainPendingVoiceNotesUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ImportNetworkSourcesUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ReportWearSettingsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.StoreTransferredStreamUseCase
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import com.sza.fastmediasorter.wear.util.rethrowIfCancellation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

private const val PATH_PUSH = "/fms/network_sources/push"
private const val PATH_ACK = "/fms/network_sources/ack"

/** Used when the phone opened the channel without a trailing name segment. */
private const val DEFAULT_INCOMING_FILE_NAME = "transferred_media"

/**
 * S1862: the capability the phone companion advertises, and the only thing this service watches to
 * learn that the phone is back. Repeated as a literal in the CAPABILITY_CHANGED filter of
 * `wear/src/main/AndroidManifest.xml`, because a manifest cannot reference a Kotlin constant, and it
 * must equal the name the phone declares in its own `res/values/wear.xml`.
 */
private const val PHONE_COMPANION_CAPABILITY = "fms_phone_companion"

@AndroidEntryPoint
class WatchWearListenerService : WearableListenerService() {

    private val envelopeCodec = WearEventEnvelopeCodec()

    @Inject lateinit var importNetworkSourcesUseCase: ImportNetworkSourcesUseCase

    @Inject lateinit var applyWearSettingsUseCase: ApplyWearSettingsUseCase

    @Inject lateinit var reportWearSettingsUseCase: ReportWearSettingsUseCase

    @Inject lateinit var wearFileReceiverRepository: WearFileReceiverRepository

    @Inject lateinit var storeTransferredStreamUseCase: StoreTransferredStreamUseCase

    @Inject lateinit var drainPendingVoiceNotesUseCase: DrainPendingVoiceNotesUseCase

    // S2431: what an arrived file or stream should be answered with. This service only dispatches the
    // event and puts the answer on the wire.
    @Inject lateinit var transferOutcomeCoordinator: WearTransferOutcomeCoordinator

    @Inject lateinit var uploadOutcomeNotifier: com.sza.fastmediasorter.wear.core.notification.WearUploadOutcomeNotifier

    @Inject lateinit var gson: Gson

    // S2149: the phone's pinned-stream set. Kept apart from the watch's own favourites so the star
    // still means "I marked this here" and the phone can withdraw only what the phone sent.
    @Inject lateinit var wearPhonePinsRepository: WearPhonePinsRepository

    // S2142: the phone's «Send to..» list. Its own store rather than a watch setting - it is a
    // derivative of the owner's settings, and the settings mirrors are gated on parity of six files.
    @Inject lateinit var wearSendToReceiversRepository: WearSendToReceiversRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * S1862: the phone coming back into reach is what releases the notes taken while it was gone.
     *
     * This service is the alarm clock rather than a screen because the platform starts it by itself:
     * a drain wired to the recorder screen would only run while the user is already looking at the
     * list, which is exactly when the note is not forgotten. An empty node set is the phone leaving,
     * and there is nothing to do then - the notes are already pending.
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val phoneIsBack = capabilityInfo.name == PHONE_COMPANION_CAPABILITY &&
            capabilityInfo.nodes.isNotEmpty()
        if (phoneIsBack) {
            serviceScope.launch { drainPendingVoiceNotesUseCase() }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path.startsWith(WearDataLayerPaths.FILE_TRANSFER)) {
            val fileName = channel.path.substringAfterLast('/', DEFAULT_INCOMING_FILE_NAME)
            serviceScope.launch {
                val result = wearFileReceiverRepository.receiveFile(channel, fileName)
                Timber.i("Incoming file %s ended as %s", fileName, result.outcome)
                answerFileTransfer(channel.nodeId, result)
            }
        }
    }

    /**
     * S1884: tells the phone what became of the file it sent, when it asked to be told.
     *
     * A blank request id is the shipped S1861 sorting transfer, which was written before this path
     * existed and waits for nothing - answering it would put a message on the wire that no one reads.
     */
    private suspend fun answerFileTransfer(nodeId: String, result: WearFileReceiveResult) {
        val declaration = result.declaration ?: return
        if (declaration.requestId.isBlank()) return

        val outcome = transferOutcomeCoordinator.fileOutcome(result, declaration)
        sendFileTransferAck(nodeId, WearFileTransferAck(declaration.requestId, outcome))
    }

    private suspend fun sendFileTransferAck(nodeId: String, ack: WearFileTransferAck) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(this)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.FILE_TRANSFER_ACK,
                    gson.toJson(ack).toByteArray(Charsets.UTF_8)
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to send file transfer ack")
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val payloadBytes = dataMap.getByteArray("payload") ?: continue
            dispatchDataItem(event.dataItem.uri, payloadBytes)
        }
        events.release()
    }

    /**
     * Split out of [onDataChanged] because the loop plus one branch per path reached detekt's
     * complexity ceiling when S2142 added the fifth path. The payload is read once above rather than
     * per branch: every path carries it under the same key, and a data item without one is nothing
     * any branch could act on.
     */
    private fun dispatchDataItem(uri: android.net.Uri, payloadBytes: ByteArray) {
        when (uri.path) {
            PATH_PUSH -> handlePush(payloadBytes, uri.host ?: "")
            WearDataLayerPaths.SETTINGS_PUSH -> handleSettingsPush(payloadBytes)
            WearDataLayerPaths.FILE_UPLOAD_OUTCOME -> handleFileUploadOutcome(payloadBytes, uri)
            WearDataLayerPaths.STREAM_PINS -> handleStreamPinsPush(payloadBytes)
            WearDataLayerPaths.SEND_TO_RECEIVERS -> handleSendToReceiversPush(payloadBytes)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleFileUploadOutcome(payloadBytes: ByteArray, uri: android.net.Uri) {
        serviceScope.launch {
            try {
                val outcome = gson.fromJson(
                    payloadBytes.decodeToString(),
                    com.sza.fastmediasorter.wear.domain.model.WearFileUploadOutcome::class.java
                )
                Timber.i(
                    "WearFileUploadOutcome received: %s, succeeded=%b, dest=%s",
                    outcome.fileName,
                    outcome.succeeded,
                    outcome.destination
                )
                if (!outcome.succeeded) {
                    uploadOutcomeNotifier.notifyUploadFailed(outcome.fileName, outcome.destination)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to process WearFileUploadOutcome")
            } finally {
                runCatching {
                    Wearable.getDataClient(this@WatchWearListenerService).deleteDataItems(uri).await()
                }.onFailure { Timber.w(it, "Failed to delete consumed WearFileUploadOutcome data item") }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearDataLayerPaths.PLAYBACK_CMD -> handlePlaybackCommand(event.data)
            WearDataLayerPaths.STREAM_TRANSFER ->
                handleStreamTransfer(event.sourceNodeId, event.data)
            WearDataLayerPaths.FILE_TRANSFER_META -> handleFileTransferMeta(event.data)
            else -> Timber.d("WatchWearListenerService: unhandled message path ${event.path}")
        }
    }

    /**
     * The phone announcing a file before it opens the channel. Parsed on the caller's thread because
     * it is a few dozen bytes of JSON and the declaration must be in place before the channel event,
     * which GMS may deliver immediately afterwards.
     */
    private fun handleFileTransferMeta(data: ByteArray) {
        val metadata = try {
            gson.fromJson(data.decodeToString(), WearFileTransferMetadata::class.java)
        } catch (e: JsonSyntaxException) {
            // Nothing to correlate without a name, so the channel simply arrives undeclared and is
            // capped at the ceiling instead of at the declared size.
            Timber.w(e, "Failed to parse an incoming file declaration")
            null
        }
        if (metadata != null) {
            wearFileReceiverRepository.declare(metadata)
        }
    }

    private fun handleStreamTransfer(nodeId: String, data: ByteArray) {
        serviceScope.launch {
            val payload = try {
                val envelope = envelopeCodec.decode(data)
                gson.fromJson(envelope.data.decodeToString(), WearStreamTransferPayload::class.java)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                // No parseable payload means no requestId either, so no ack can be correlated -
                // the phone reports the timeout outcome instead.
                Timber.e(e, "Failed to deserialize stream transfer payload")
                null
            } ?: return@launch
            val result = storeTransferredStreamUseCase(payload)
            if (result.ack.outcome != WearStreamTransferAck.OUTCOME_ERROR) {
                vibrateSuccess()
            }
            val ack = if (payload.openNow && result.channel != null) {
                transferOutcomeCoordinator.streamAck(result.channel, payload.requestId)
            } else {
                result.ack
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
            e.errorUnlessCancellation("Failed to send stream transfer ack")
        }
    }

    private fun handleSettingsPush(payloadBytes: ByteArray) {
        val receivedAt = System.currentTimeMillis()
        serviceScope.launch {
            try {
                val envelope = envelopeCodec.decode(payloadBytes)
                val payload = gson.fromJson(envelope.data.decodeToString(), WearSettingsPayload::class.java)
                applyWearSettingsUseCase(payload, envelope.sentAt, receivedAt)
                // S2093: a push is answered with what the watch ended up holding, so one press on the
                // phone completes the exchange in both directions rather than only sending.
                reportWearSettingsUseCase()
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to apply settings push")
                WatchSyncEvents.settingsErrorFlow.emit(e.message ?: "Settings apply failed")
            }
        }
    }

    /**
     * S2149: stores the phone's pinned-stream set so the streams list can raise those channels.
     *
     * A payload that fails to parse is dropped rather than applied as an empty set: losing one bad
     * message must not silently clear a set the owner can only restore by re-pinning on the phone.
     */
    private fun handleStreamPinsPush(payloadBytes: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = envelopeCodec.decode(payloadBytes)
                val payload = gson.fromJson(envelope.data.decodeToString(), WearStreamPinsPayload::class.java)
                wearPhonePinsRepository.replaceAll(payload.identities)
            } catch (e: CancellationException) {
                // The service scope was torn down; swallowing this would leave the coroutine machinery
                // believing the job is still live (S1363/S1889/S1910).
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to apply stream pins push - keeping the previously stored set")
            }
        }
    }

    /**
     * S2142: stores the «Send to..» receivers the phone offers, so the file menu can list them.
     *
     * A payload that fails to parse is dropped rather than applied as an empty list, the same rule
     * as [handleStreamPinsPush] and for the same reason: losing one bad message must not silently
     * clear a list the owner can only restore by toggling receivers again on the phone.
     */
    private fun handleSendToReceiversPush(payloadBytes: ByteArray) {
        serviceScope.launch {
            try {
                val envelope = envelopeCodec.decode(payloadBytes)
                val json = envelope.data.decodeToString()
                val payload = gson.fromJson(json, WearSendToReceiversPayload::class.java)
                wearSendToReceiversRepository.replaceAll(payload.receivers)
            } catch (e: CancellationException) {
                // The service scope was torn down; swallowing this would leave the coroutine
                // machinery believing the job is still live (S1363/S1889/S1910).
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to apply send-to receivers push - keeping the stored list")
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
                e.errorUnlessCancellation("Failed to deserialize playback command")
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
                e.errorUnlessCancellation("Failed to process sync payload")
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
        Timber.d("S2278: catalog sync ack serialized via Gson to $nodeId")
        val ackJson = gson.toJson(SyncAck(added = result.added, updated = result.updated))
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, PATH_ACK, ackJson.toByteArray())
                .await()
        } catch (e: Exception) {
            e.errorUnlessCancellation("Failed to send ack to phone")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

/**
 * S2278: the sync ack went out as a raw string template while the two transfer acks in this file
 * already used the injected [com.google.gson.Gson]. Nothing escaped the values, and a third
 * serialization idiom in one file is one the next reader has to notice.
 */
private data class SyncAck(val added: Int, val updated: Int)

/** Process-wide event bus for sync results on the watch. */
object WatchSyncEvents {
    val importResultFlow = MutableSharedFlow<ImportResult>(extraBufferCapacity = 1)
    val importErrorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val settingsErrorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

/** Process-wide event bus for remote playback commands received from the phone. */
object WatchPlaybackCommandEvents {
    val commandFlow = MutableSharedFlow<WearPlaybackCommand>(extraBufferCapacity = 4)
}

/**
 * S1944: the phone asking the watch to open a channel, and the host saying it did.
 *
 * Same shape as [WatchPlaybackCommandEvents] and for the same reason: this service holds no reference
 * to the Activity or its navigation and cannot acquire one. The confirmation half exists because the
 * answer sent back to the phone depends on whether a screen was actually there to listen.
 */
object WatchStreamOpenEvents {
    val requestFlow = MutableSharedFlow<WearStreamChannel>(extraBufferCapacity = 4)
    val openedFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
}

/**
 * S1884: the phone asking the watch to show a file it just delivered, and the host saying it did.
 *
 * Deliberately the same pair as [WatchStreamOpenEvents] rather than a generalisation of it: the two
 * requests carry different payloads, and merging them would mean a screen collecting one kind of
 * open request also waking for the other. The echo is the landed path, which is unique per arrival
 * because the preview directory holds one file at a time.
 */
object WatchFileOpenEvents {
    val requestFlow = MutableSharedFlow<WearFileOpenRequest>(extraBufferCapacity = 4)
    val openedFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
