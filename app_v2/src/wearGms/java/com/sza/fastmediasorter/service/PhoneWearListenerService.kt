package com.sza.fastmediasorter.service

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.repository.wear.SharedPreferencesWearSettingsMirrorStore
import com.sza.fastmediasorter.data.wear.OpenOnPhoneNotifier
import com.sza.fastmediasorter.data.wear.WearIncomingFileRegistry
import com.sza.fastmediasorter.data.wear.WearSendToNotifier
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.domain.model.WearFileReceiveAck
import com.sza.fastmediasorter.domain.model.WearFileTransferAck
import com.sza.fastmediasorter.domain.model.WearFileTransferMetadata
import com.sza.fastmediasorter.domain.model.WearOpenOnPhoneAck
import com.sza.fastmediasorter.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.domain.model.WearPhoneResourcePage
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequestKind
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.ApplyWatchFavoritesDeltaUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ListPhoneResourcePageUseCase
import com.sza.fastmediasorter.domain.usecase.MergeWearSettingsReportUseCase
import com.sza.fastmediasorter.domain.usecase.OpenPhoneResourceChannelUseCase
import com.sza.fastmediasorter.domain.usecase.PhoneResourceChannel
import com.sza.fastmediasorter.domain.usecase.ReceiveWatchFileUseCase
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import com.sza.fastmediasorter.ui.player.dispatch.StandalonePlayerDispatcherActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val PATH_REQUEST = "/fms/network_sources/request"
private const val PATH_ACK = "/fms/network_sources/ack"

/** Used when the watch opened the channel without a trailing name segment. */
private const val DEFAULT_INCOMING_FILE_NAME = "watch_file"

@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    private val envelopeCodec = WearEventEnvelopeCodec()

    @Inject lateinit var sendResourcesToWatchUseCase: SendResourcesToWatchUseCase

    @Inject lateinit var importWatchSourcesUseCase: ImportWatchSourcesUseCase

    @Inject lateinit var applyWatchFavoritesDeltaUseCase: ApplyWatchFavoritesDeltaUseCase

    @Inject lateinit var listPhoneResourcePageUseCase: ListPhoneResourcePageUseCase

    @Inject lateinit var openPhoneResourceChannelUseCase: OpenPhoneResourceChannelUseCase

    @Inject lateinit var wearableDataLayerRepository: WearableDataLayerRepository

    @Inject lateinit var wearLogReportReceiver: WearLogReportReceiver

    @Inject lateinit var openOnPhoneNotifier: OpenOnPhoneNotifier

    @Inject lateinit var receiveWatchFileUseCase: ReceiveWatchFileUseCase

    @Inject lateinit var wearIncomingFileRegistry: WearIncomingFileRegistry

    @Inject lateinit var wearSendToNotifier: WearSendToNotifier

    @Inject lateinit var gson: Gson

    @Inject lateinit var wearSettingsMirrorStore: SharedPreferencesWearSettingsMirrorStore

    @Inject lateinit var mergeWearSettingsReportUseCase: MergeWearSettingsReportUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_REQUEST -> handleSyncRequest()
            PATH_ACK -> handleAck(event.data)
            WearDataLayerPaths.SOURCES_EXPORT -> handleSourcesExport(event.data)
            WearDataLayerPaths.FAVORITES_DELTA -> handleFavoritesDelta(event.data)
            WearDataLayerPaths.PHONE_RESOURCE_BROWSE_REQUEST -> handlePhoneResourceBrowse(event.data)
            WearDataLayerPaths.PHONE_RESOURCE_OPEN_REQUEST ->
                handlePhoneResourceOpen(event.sourceNodeId, event.data)
            WearDataLayerPaths.OPEN_ON_PHONE_REQUEST ->
                handleOpenOnPhone(event.sourceNodeId, event.data)
            WearDataLayerPaths.LOG_REPORT_REQUEST ->
                handleLogReport(event.sourceNodeId, event.data)
            WearDataLayerPaths.STREAM_TRANSFER_ACK -> handleStreamTransferAck(event.data)
            WearDataLayerPaths.FILE_TRANSFER_ACK -> handleFileTransferAck(event.data)
            WearDataLayerPaths.FILE_TRANSFER_META -> handleFileTransferMeta(event.data)
        }
    }

    /**
     * The watch announcing a file before it opens the channel. Parsed on the caller's thread: it is a
     * few dozen bytes of JSON, and the declaration must be recorded before the channel event, which
     * GMS may deliver immediately afterwards.
     */
    private fun handleFileTransferMeta(data: ByteArray) {
        val metadata = try {
            gson.fromJson(data.decodeToString(), WearFileTransferMetadata::class.java)
        } catch (e: JsonSyntaxException) {
            // Without a name there is nothing to correlate, so the channel arrives undeclared and is
            // capped at the ceiling rather than at the declared size.
            Timber.w(e, "Failed to parse an incoming watch file declaration")
            null
        }
        if (metadata != null) {
            wearIncomingFileRegistry.declare(metadata)
        }
    }

    /**
     * S1861: the reverse direction - the watch is the initiator and the phone drains the channel.
     *
     * The stream is opened here rather than inside the use case so the GMS types never leave this
     * source set; the use case decides where the bytes land and enforces the ceiling.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (!channel.path.startsWith(WearDataLayerPaths.FILE_TRANSFER)) {
            return
        }
        val fileName = channel.path.substringAfterLast('/', DEFAULT_INCOMING_FILE_NAME)
        val declaration = wearIncomingFileRegistry.take(fileName)
        val declaredBytes = declaration?.size ?: 0L
        applicationScope.launch {
            val channelClient = Wearable.getChannelClient(applicationContext)
            try {
                val result = channelClient.getInputStream(channel).await().use { input ->
                    receiveWatchFileUseCase(fileName, declaredBytes, input)
                }
                Timber.i("Incoming watch file %s ended as %s", fileName, result.outcome)
                val ack = WearFileReceiveAck(
                    fileName = fileName,
                    outcome = errandOutcome(fileName, declaration, result) ?: when (result.outcome) {
                        com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.SAVED ->
                            WearFileReceiveAck.OUTCOME_SAVED
                        com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.QUEUED_FOR_UPLOAD ->
                            WearFileReceiveAck.OUTCOME_QUEUED
                        com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.NO_DESTINATION ->
                            WearFileReceiveAck.OUTCOME_NO_DESTINATION
                        com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.REFUSED_TOO_LARGE ->
                            WearFileReceiveAck.OUTCOME_TOO_LARGE
                        com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.FAILED ->
                            WearFileReceiveAck.OUTCOME_FAILED
                    },
                    destination = result.destinationName.orEmpty()
                )
                runCatching {
                    wearableDataLayerRepository.sendMessage(
                        channel.nodeId,
                        WearDataLayerPaths.FILE_RECEIVE_ACK,
                        gson.toJson(ack).toByteArray(Charsets.UTF_8)
                    )
                }.onFailure { Timber.w(it, "Failed to send FILE_RECEIVE_ACK to watch") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Expected when the watch leaves range before the stream is handed over.
                Timber.w(e, "Failed to open the incoming watch file channel")
                val failureAck = WearFileReceiveAck(
                    fileName = fileName,
                    outcome = WearFileReceiveAck.OUTCOME_FAILED,
                    destination = ""
                )
                runCatching {
                    wearableDataLayerRepository.sendMessage(
                        channel.nodeId,
                        WearDataLayerPaths.FILE_RECEIVE_ACK,
                        gson.toJson(failureAck).toByteArray(Charsets.UTF_8)
                    )
                }.onFailure { Timber.w(it, "Failed to send FILE_RECEIVE_ACK to watch") }
            } finally {
                withContext(NonCancellable) {
                    runCatching { channelClient.close(channel).await() }
                        .onFailure { Timber.w(it, "Failed to close the incoming watch file channel") }
                }
            }
        }
    }

    /**
     * S2142: the ack for a transfer that arrived carrying an errand, or null when it carried none.
     *
     * Null rather than a default so the ordinary transfer's own answer stays untouched below - an
     * errand is a different question, and answering it with "saved" would tell the watch the file
     * was filed away when what it asked for was a receiver.
     *
     * A transfer with no local path (a remote destination the phone only queued an upload to) cannot
     * be handed to a receiver at all: the bytes are not on this phone yet, so the errand is reported
     * failed rather than silently downgraded to the upload the watch never asked for.
     */
    private fun errandOutcome(
        fileName: String,
        declaration: WearFileTransferMetadata?,
        result: com.sza.fastmediasorter.domain.model.WearFileReceiveResult
    ): String? {
        val receiverId = declaration?.sendToReceiverId?.takeIf { it.isNotBlank() } ?: return null
        val savedPath = result.savedPath
        if (result.outcome != com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.SAVED ||
            savedPath.isNullOrEmpty()
        ) {
            Timber.w("Send to from watch: %s landed as %s, no local file to hand on", fileName, result.outcome)
            return WearFileReceiveAck.OUTCOME_FAILED
        }
        return if (wearSendToNotifier.notifyPendingSend(fileName, savedPath, receiverId)) {
            WearFileReceiveAck.OUTCOME_AWAITING_SEND_TO
        } else {
            WearFileReceiveAck.OUTCOME_NOTIFICATIONS_OFF
        }
    }

    private fun handleStreamTransferAck(data: ByteArray) {
        applicationScope.launch {
            try {
                val ack = gson.fromJson(data.decodeToString(), WearStreamTransferAck::class.java)
                WearSyncEvents.emitStreamTransferAck(ack)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize stream transfer ack")
            }
        }
    }

    private fun handleFileTransferAck(data: ByteArray) {
        applicationScope.launch {
            try {
                val ack = gson.fromJson(data.decodeToString(), WearFileTransferAck::class.java)
                WearSyncEvents.emitFileTransferAck(ack)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize file transfer ack")
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val receivedAt = System.currentTimeMillis()
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            dispatchDataEvent(event, receivedAt)
        }
        events.release()
    }

    // The per-event work is a function rather than the loop body so that skipping a payload-less
    // event is a return here instead of a second continue in the walk above.
    private fun dispatchDataEvent(event: DataEvent, receivedAtEpochMillis: Long) {
        val payloadBytes = DataMapItem.fromDataItem(event.dataItem)
            .dataMap.getByteArray("payload") ?: return
        when (event.dataItem.uri.path) {
            WearDataLayerPaths.PLAYBACK_STATE -> handlePlaybackState(payloadBytes)
            WearDataLayerPaths.SETTINGS_REPORT -> handleSettingsReport(payloadBytes, receivedAtEpochMillis)
        }
    }

    // S2093: the reverse leg of the settings exchange terminates here, beside every other
    // watch-originated path. The arrival time is read once for the whole buffer and handed down,
    // because the merge measures the clock skew from it against the envelope's own sentAt.
    private fun handleSettingsReport(data: ByteArray, receivedAtEpochMillis: Long) {
        applicationScope.launch {
            try {
                val envelope = envelopeCodec.decode(data)
                val payload = gson.fromJson(
                    envelope.data.decodeToString(),
                    WearSettingsPayload::class.java
                )
                val merged = mergeWearSettingsReportUseCase(payload, envelope.sentAt, receivedAtEpochMillis)
                WearSyncEvents.emitWatchSettingsMerged(merged)
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge watch settings report")
            }
        }
    }

    private fun handlePlaybackState(data: ByteArray) {
        applicationScope.launch {
            try {
                val envelope = envelopeCodec.decode(data)
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
        applicationScope.launch {
            try {
                val envelope = envelopeCodec.decode(data)
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
        applicationScope.launch {
            try {
                val envelope = envelopeCodec.decode(data)
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
        applicationScope.launch {
            wearLogReportReceiver.handle(nodeId, data)
        }
    }

    private fun handlePhoneResourceBrowse(data: ByteArray) {
        applicationScope.launch {
            val request = parsePhoneResourceRequest(data) ?: return@launch
            sendPhoneResourcePage(listPhoneResourcePageUseCase(request))
        }
    }

    private fun handlePhoneResourceOpen(nodeId: String, data: ByteArray) {
        applicationScope.launch {
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
        } catch (e: CancellationException) {
            // S1927: same reason as S1889 and S1911 - a scope going away is not an open that failed,
            // and reporting it at E made a routine teardown read as an app error.
            throw e
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
                approved.file.inputStream().use { input -> input.copyTo(output) }
            }
        } catch (e: CancellationException) {
            // S1927: matches sendPhoneResourcePage below - a scope going away is not a transfer that
            // failed, so it propagates. The finally still runs on the way out.
            throw e
        } catch (e: Exception) {
            // S1927: a watch that walks away raises IOException on the copy, not cancellation -
            // applicationScope is a never-cancelled SupervisorJob. The old comment claimed the
            // opposite and was the reason S1911 left this arm alone.
            Timber.w(e, "Phone resource transfer interrupted")
        } finally {
            // S1927: close() asks GMS to close before await() can suspend, so the channel is released
            // either way; NonCancellable keeps the confirmation if this scope ever becomes cancellable.
            withContext(NonCancellable) {
                runCatching { channelClient.close(channel).await() }
                    .onFailure { Timber.w(it, "Failed to close phone resource channel") }
            }
        }
    }

    /**
     * S2004: the twelfth path - the watch asks this phone to show one of the phone's own files.
     *
     * The item is resolved by the very use case that listed it for the watch, so the two sides cannot
     * disagree about what a token names, and only a file that use case already approved can reach
     * here at all - the watch offers the action for its downloaded copies alone.
     */
    private fun handleOpenOnPhone(nodeId: String, data: ByteArray) {
        applicationScope.launch {
            val request = parseOpenOnPhoneRequest(data) ?: return@launch
            val approved = openPhoneResourceChannelUseCase(openRequestFor(request.token))
            val outcome = if (approved is PhoneResourceChannel.Approved) {
                showOrAnnounce(request, approved)
            } else {
                WearOpenOnPhoneOutcome.NOT_FOUND
            }
            answerOpenOnPhone(nodeId, request.token, outcome)
        }
    }

    /**
     * Foreground goes straight to the viewer, background goes to a notification.
     *
     * ADR-3: a `WearableListenerService` is a background process, and a background process has not
     * been allowed to start an activity since Android 10 - so the direct launch is guarded by the
     * app's own lifecycle state rather than attempted and left to fail silently.
     */
    private fun showOrAnnounce(
        request: WearOpenOnPhoneRequest,
        approved: PhoneResourceChannel.Approved
    ): WearOpenOnPhoneOutcome {
        val target = contentUriFor(approved.file) ?: return WearOpenOnPhoneOutcome.NOT_FOUND
        return when {
            isAppInForeground() && startViewer(target) -> WearOpenOnPhoneOutcome.SHOWN
            // Not in front, or in front and the launch was refused anyway - one answer either way.
            else -> announce(request.token, approved.name, target)
        }
    }

    private fun announce(token: String, displayName: String, target: Uri): WearOpenOnPhoneOutcome =
        if (openOnPhoneNotifier.notifyPendingOpen(token, displayName, target)) {
            WearOpenOnPhoneOutcome.NOTIFIED
        } else {
            WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION
        }

    /**
     * Read off the process's own lifecycle rather than by asking the system what is running: the task
     * queries were deprecated for exactly this use and answer about other apps as well as this one.
     */
    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    @Suppress("TooGenericExceptionCaught")
    private fun startViewer(target: Uri): Boolean = try {
        val view = Intent(applicationContext, StandalonePlayerDispatcherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = target
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        applicationContext.startActivity(view)
        true
    } catch (e: Exception) {
        // Every way this can fail - a background-start refusal, a disabled component - means the same
        // thing to the caller: the file is not on screen, so announce it instead.
        Timber.w(e, "Open on phone: direct launch refused, falling back to a notification")
        false
    }

    /**
     * A provider URI, never a `file://` one: the receiving activity is addressed through an intent
     * that leaves this process, and a raw file URI is refused there since Android 7.
     */
    private fun contentUriFor(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.fileprovider", file)
    }.onFailure { Timber.w(it, "Open on phone: %s is outside every shared provider path", file.name) }
        .getOrNull()

    /** The item request the browse protocol speaks, built for a token the watch already holds. */
    private fun openRequestFor(token: String) = WearPhoneResourceRequest(
        requestId = token,
        kind = WearPhoneResourceRequestKind.OPEN,
        itemToken = token
    )

    private fun parseOpenOnPhoneRequest(data: ByteArray): WearOpenOnPhoneRequest? = try {
        gson.fromJson(data.decodeToString(), WearOpenOnPhoneRequest::class.java)
    } catch (e: JsonSyntaxException) {
        // Without a token there is nothing to resolve and nothing to correlate an answer with.
        Timber.e(e, "Failed to deserialize an open-on-phone request")
        null
    }

    private suspend fun answerOpenOnPhone(nodeId: String, token: String, outcome: WearOpenOnPhoneOutcome) {
        val ack = WearOpenOnPhoneAck(token = token, outcome = outcome)
        runCatching {
            wearableDataLayerRepository.sendMessage(
                nodeId,
                WearDataLayerPaths.OPEN_ON_PHONE_ACK,
                gson.toJson(ack).toByteArray(Charsets.UTF_8)
            )
        }.onFailure { Timber.w(it, "Open on phone: acknowledgement could not be sent") }
    }

    private fun parsePhoneResourceRequest(data: ByteArray): WearPhoneResourceRequest? = try {
        gson.fromJson(data.decodeToString(), WearPhoneResourceRequest::class.java)
    } catch (e: Exception) {
        Timber.e(e, "Failed to deserialize phone resource request")
        null
    }

    // S1911: a publish failure has exactly one answer whatever GMS raised, so the broad arm is the
    // contract. Cancellation is rethrown as the first arm above it rather than folded in.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendPhoneResourcePage(page: WearPhoneResourcePage) {
        val envelopeBytes = withinWireLimit(page)
        try {
            wearableDataLayerRepository.putDataItem(
                WearDataLayerPaths.PHONE_RESOURCE_PAGE,
                envelopeBytes
            )
        } catch (e: CancellationException) {
            // S1911: this scope going away is why the page cannot be published - not a publish that
            // failed. Logging it at E made a routine teardown read as an app error.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to publish phone resource page")
        }
    }

    private fun encodePage(page: WearPhoneResourcePage): ByteArray {
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PHONE_RESOURCE_PAGE,
            sentAt = System.currentTimeMillis(),
            data = gson.toJson(page).toByteArray(Charsets.UTF_8)
        )
        return envelopeCodec.encode(envelope)
    }

    /**
     * S1860: last line of defence before the transport refuses the page outright.
     *
     * GMS caps one data item at 100 KB and answers anything larger with `DATA_ITEM_TOO_LARGE` - a
     * refusal the watch cannot see, so it waits out its ten seconds and reports the phone as
     * unreachable. The page's own picture budget aims well under that, but a folder of long names
     * can still push a full page over, and the pictures are the only part worth dropping: a listing
     * without them still lists, and an item with no picture is a state the watch already draws.
     */
    private fun withinWireLimit(page: WearPhoneResourcePage): ByteArray {
        val withPictures = encodePage(page)
        if (withPictures.size <= MAX_DATA_ITEM_BYTES) return withPictures

        return encodePage(page.copy(items = page.items.map { it.withoutThumbnail() }))
    }

    private fun WearPhoneResourceItem.withoutThumbnail(): WearPhoneResourceItem =
        if (thumbnailBase64 == null) this else copy(thumbnailBase64 = null)

    private fun handleSyncRequest() {
        Timber.i("Watch requested sync - sending resources")
        applicationScope.launch {
            sendResourcesToWatchUseCase().onFailure { e ->
                Timber.e(e, "Failed to send resources on watch request")
            }
        }
    }

    private fun handleAck(data: ByteArray) {
        val json = data.decodeToString()
        Timber.i("Watch ack received: $json")
        wearSettingsMirrorStore.markSynced(System.currentTimeMillis())
        // Broadcast result to any active WearSyncViewModel via the companion object flow
        applicationScope.launch {
            WearSyncEvents.emitAck(json)
        }
    }

    companion object {
        /** S1860: what GMS accepts in one data item; anything larger comes back DATA_ITEM_TOO_LARGE. */
        private const val MAX_DATA_ITEM_BYTES = 100 * 1024
    }
}
