package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.domain.model.WearClipboardTextAck
import com.sza.fastmediasorter.domain.model.WearFileTransferAck
import com.sza.fastmediasorter.domain.model.WearListenAckPayload
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearScreenshotRequestAck
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.model.WearStreamTransferAck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus for messages received from the watch companion.
 *
 * Lives in `src/main` (GMS-free, pure coroutines) so the main-flavor `WearSyncViewModel` collector
 * compiles for every flavor, while the GMS-backed `PhoneWearListenerService` emitter (S0403
 * `wearGms` source set) publishes into it. Non-Wear flavors keep these flows inert - nothing emits.
 *
 * S1031: the mutable backing flows are private; collectors read the read-only [SharedFlow] views and
 * the emitter publishes through the explicit `emit*` methods, so a single owner controls emission.
 */
object WearSyncEvents {
    private val _ackFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ackFlow: SharedFlow<String> = _ackFlow.asSharedFlow()

    private val _watchSourcesReceivedFlow =
        MutableSharedFlow<WearSourcesExportPayload>(extraBufferCapacity = 1)
    val watchSourcesReceivedFlow: SharedFlow<WearSourcesExportPayload> =
        _watchSourcesReceivedFlow.asSharedFlow()

    private val _watchPlaybackStateFlow =
        MutableSharedFlow<WearPlaybackStatePayload?>(extraBufferCapacity = 1)
    val watchPlaybackStateFlow: SharedFlow<WearPlaybackStatePayload?> =
        _watchPlaybackStateFlow.asSharedFlow()

    // S1799: typed, request-correlated ack channel for single-stream transfers. Kept apart from the
    // legacy untyped ackFlow, whose only discriminator is "a Sending state exists somewhere".
    private val _streamTransferAckFlow =
        MutableSharedFlow<WearStreamTransferAck>(extraBufferCapacity = 4)
    val streamTransferAckFlow: SharedFlow<WearStreamTransferAck> = _streamTransferAckFlow.asSharedFlow()

    /**
     * S2093: the merged watch settings, after a `SETTINGS_REPORT` was reconciled with the mirror.
     *
     * Replays the last one, because the companion sheet is opened long after the exchange that
     * produced it and would otherwise show the merge only if it happened to be on screen at the time -
     * the sheet's ViewModel dies with its dialog.
     */
    private val _watchSettingsMergedFlow =
        MutableSharedFlow<WearSettingsPayload>(replay = 1, extraBufferCapacity = 1)
    val watchSettingsMergedFlow: SharedFlow<WearSettingsPayload> = _watchSettingsMergedFlow.asSharedFlow()

    private val _fileTransferAckFlow =
        MutableSharedFlow<WearFileTransferAck>(replay = 1, extraBufferCapacity = 4)
    val fileTransferAckFlow: SharedFlow<WearFileTransferAck> = _fileTransferAckFlow.asSharedFlow()

    /**
     * S2550: the watch's answer to a listen command - an address to play, or a reason it refused.
     *
     * Replays the last one because the answer waits on a tap on the watch and may arrive minutes
     * later, by which time the screen that asked can have been recreated by a rotation; without the
     * replay the address would be delivered to a collector that no longer exists and the session
     * would look like the watch never answered.
     */
    private val _listenAckFlow =
        MutableSharedFlow<WearListenAckPayload>(replay = 1, extraBufferCapacity = 4)
    val listenAckFlow: SharedFlow<WearListenAckPayload> = _listenAckFlow.asSharedFlow()

    /**
     * S3109: the watch's answer to a clipboard this phone pushed - taken, or refused with a reason.
     *
     * No replay, unlike [listenAckFlow]: this answer follows a message the watch handles without any
     * human in the loop, so it arrives within seconds of the request and a replayed one would be a
     * previous send's verdict shown against a fresh tap.
     */
    private val _clipboardTextAckFlow =
        MutableSharedFlow<WearClipboardTextAck>(extraBufferCapacity = 4)
    val clipboardTextAckFlow: SharedFlow<WearClipboardTextAck> = _clipboardTextAckFlow.asSharedFlow()

    /**
     * S3110: the watch's verdict on a screenshot this phone asked for - captured, or refused.
     *
     * No replay, for [clipboardTextAckFlow]'s reason: it answers a request nobody on the watch has to
     * approve, so it arrives on its own and a replayed one would be a previous ask's verdict shown
     * against a fresh tap.
     */
    private val _screenshotAckFlow =
        MutableSharedFlow<WearScreenshotRequestAck>(extraBufferCapacity = 4)
    val screenshotAckFlow: SharedFlow<WearScreenshotRequestAck> = _screenshotAckFlow.asSharedFlow()

    suspend fun emitAck(json: String) = _ackFlow.emit(json)

    suspend fun emitClipboardTextAck(ack: WearClipboardTextAck) = _clipboardTextAckFlow.emit(ack)

    suspend fun emitScreenshotAck(ack: WearScreenshotRequestAck) = _screenshotAckFlow.emit(ack)

    suspend fun emitStreamTransferAck(ack: WearStreamTransferAck) = _streamTransferAckFlow.emit(ack)

    suspend fun emitFileTransferAck(ack: WearFileTransferAck) = _fileTransferAckFlow.emit(ack)

    suspend fun emitListenAck(ack: WearListenAckPayload) = _listenAckFlow.emit(ack)

    suspend fun emitWatchSources(payload: WearSourcesExportPayload) =
        _watchSourcesReceivedFlow.emit(payload)

    suspend fun emitWatchPlaybackState(state: WearPlaybackStatePayload?) =
        _watchPlaybackStateFlow.emit(state)

    suspend fun emitWatchSettingsMerged(settings: WearSettingsPayload) =
        _watchSettingsMergedFlow.emit(settings)
}
