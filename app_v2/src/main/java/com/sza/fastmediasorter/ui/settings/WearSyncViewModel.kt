package com.sza.fastmediasorter.ui.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.data.repository.wear.SharedPreferencesWearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.model.WearListenAckPayload
import com.sza.fastmediasorter.domain.model.WearListenRefusal
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSettingsFieldDiff
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.model.WearSyncLeg
import com.sza.fastmediasorter.domain.model.WearSyncLegResult
import com.sza.fastmediasorter.domain.model.WearSyncOutcome
import com.sza.fastmediasorter.domain.model.streamUrl
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.usecase.EnsureWatchResourceUseCase
import com.sza.fastmediasorter.domain.usecase.GetPairedWatchStatusUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SendWearBackgroundImageUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed class WearSyncUiState {
    data object Idle : WearSyncUiState()
    data object Sending : WearSyncUiState()
    data class Success(val sent: Int, val skipped: Int) : WearSyncUiState()

    /** S1781: the owner has marked no resources for the watch, so nothing was sent - not a failure. */
    data object NothingSelected : WearSyncUiState()
    data object SettingsPushed : WearSyncUiState()
    data class Error(val message: String) : WearSyncUiState()
}

/**
 * S2484: the state of one unified exchange, kept apart from [WearSyncUiState].
 *
 * A separate flow rather than three more cases on the shared state, because both legacy controls
 * write [WearSyncUiState] without marking which of them wrote it. Sequential taps hid that; a
 * fan-out exposes it at once, with one leg's completion overwriting the other's progress.
 */
sealed class UnifiedSyncState {
    data object Idle : UnifiedSyncState()
    data object Running : UnifiedSyncState()
    data class Finished(val outcome: WearSyncOutcome) : UnifiedSyncState()
}

/**
 * S2000: how far the chosen background picture has got on its way to the watch.
 *
 * The picture reports its own outcome because it travels the byte channel rather than the settings
 * contract, which acknowledges nothing per field (strategic §2.8, ADR-1). The failing values stay
 * apart because an unreachable watch is fixed by walking closer and a rejected picture by picking
 * another one.
 */
sealed class WearBackgroundDeliveryState {
    data object Idle : WearBackgroundDeliveryState()
    data object Sending : WearBackgroundDeliveryState()
    data object Sent : WearBackgroundDeliveryState()
    data object WatchUnreachable : WearBackgroundDeliveryState()
    data object Failed : WearBackgroundDeliveryState()
}

/**
 * S2000: the prepared frame as the window shows it back to the owner.
 *
 * [stamp] rides along because every delivery overwrites the same path, so the path alone is an
 * unchanged value that neither this flow nor an image cache would treat as new content.
 */
data class WearBackgroundPreview(val path: String, val stamp: Long)

/**
 * S2034: what the add-or-open button did, told once rather than held as state.
 *
 * The two outcomes are separate because they need different answers from the host: a row that was
 * just created is announced and left alone, while an existing one is opened. Collapsing them into
 * "open it either way" would drop the reader into an empty browser, since a freshly created watch
 * resource has never been scanned.
 */
sealed class WearWatchResourceEvent {
    data class Created(val name: String) : WearWatchResourceEvent()
    data class Open(val resourceId: Long) : WearWatchResourceEvent()
    data object Failed : WearWatchResourceEvent()
}

/**
 * S2550: the listening control's three appearances, and no fourth.
 *
 * What ended the last attempt rides on [Idle] rather than living in a state of its own, because a
 * refusal is not a fourth thing the control can be doing - it is idle with something to say, and the
 * start action must stay reachable in exactly that moment.
 */
sealed class WearListenState {

    /** @param messageRes why the last attempt ended, or null when nothing has been tried yet. */
    data class Idle(@param:StringRes val messageRes: Int? = null) : WearListenState()

    /** The command is with the watch and the owner has not yet tapped the request it raised. */
    data object Awaiting : WearListenState()

    /** The watch's microphone is being played on this phone. */
    data object Listening : WearListenState()
}

@HiltViewModel
class WearSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outbound: WearOutboundUseCases,
    private val importWatchSourcesUseCase: ImportWatchSourcesUseCase,
    private val getPairedWatchStatusUseCase: GetPairedWatchStatusUseCase,
    private val ensureWatchResourceUseCase: EnsureWatchResourceUseCase,
    private val sendWearBackgroundImageUseCase: SendWearBackgroundImageUseCase,
    private val wearFileTransferRepository: WearFileTransferRepository,
    private val wearSettingsMirrorStore: SharedPreferencesWearSettingsMirrorStore,
    // S2515 (ADR-4): mirror writes outlive this ViewModel on purpose - see rememberSettings.
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    // S1885: seeded Unknown so the settings row starts neutral instead of claiming a watch is
    // absent before the bridge has been asked.
    private val _pairedWatchStatus = MutableStateFlow<PairedWatchStatus>(PairedWatchStatus.Unknown)
    val pairedWatchStatus: StateFlow<PairedWatchStatus> = _pairedWatchStatus.asStateFlow()

    /**
     * S1885: asked when the Wear group becomes visible and when the companion switch flips, never on
     * a timer - the bridge call costs a round trip and the row is read by someone looking at it.
     */
    fun refreshPairedWatchStatus() {
        viewModelScope.launch {
            _pairedWatchStatus.value = getPairedWatchStatusUseCase()
        }
    }

    private val _uiState = MutableStateFlow<WearSyncUiState>(WearSyncUiState.Idle)
    val uiState: StateFlow<WearSyncUiState> = _uiState.asStateFlow()

    private var ackTimeoutJob: Job? = null

    // The sheet that shows these values is a BottomSheetDialogFragment, so this ViewModel dies with
    // it and an in-memory-only mirror lost every edit the moment the sheet closed - a picked GRID_3
    // read back as the LIST default on the next open. S2093: the watch now does report its own set
    // back, and the merged result is written to the same mirror, so this restores the last agreed
    // state rather than only the phone's last send.
    private val _watchSettingsState = MutableStateFlow<WearSettingsPayload?>(null)
    val watchSettingsState: StateFlow<WearSettingsPayload?> = _watchSettingsState.asStateFlow()

    private val _pendingWatchSources = MutableStateFlow<WearSourcesExportPayload?>(null)
    val pendingWatchSources: StateFlow<WearSourcesExportPayload?> = _pendingWatchSources.asStateFlow()

    private val _unifiedSyncState = MutableStateFlow<UnifiedSyncState>(UnifiedSyncState.Idle)
    val unifiedSyncState: StateFlow<UnifiedSyncState> = _unifiedSyncState.asStateFlow()

    private val _watchPlaybackState = MutableStateFlow<WearPlaybackStatePayload?>(null)
    val watchPlaybackState: StateFlow<WearPlaybackStatePayload?> = _watchPlaybackState.asStateFlow()

    // S2000: the chosen background is a field of the mirrored payload like every other watch
    // setting, but it is also read on its own by the group that offers the two options, so it is
    // surfaced separately rather than making that group unpack the whole payload.
    private val _backgroundMode = MutableStateFlow(
        _watchSettingsState.value?.backgroundMode
            ?: WearSettingsPayload.BACKGROUND_MODE_BRANDED_ANIMATION
    )
    val backgroundMode: StateFlow<String> = _backgroundMode.asStateFlow()

    // S2522: surfaced separately for the same reason as the background mode above - the group that
    // offers the eight schemes reads this one value rather than unpacking the whole payload. The dark
    // fallback matches the watch's own default, so the window agrees with the watch before any
    // exchange has happened.
    private val _colorScheme = MutableStateFlow(
        _watchSettingsState.value?.colorScheme ?: WearSettingsPayload.COLOR_SCHEME_DARK
    )
    val colorScheme: StateFlow<String> = _colorScheme.asStateFlow()

    private val _backgroundPreview = MutableStateFlow<WearBackgroundPreview?>(null)
    val backgroundPreview: StateFlow<WearBackgroundPreview?> = _backgroundPreview.asStateFlow()

    private val _backgroundDelivery =
        MutableStateFlow<WearBackgroundDeliveryState>(WearBackgroundDeliveryState.Idle)
    val backgroundDelivery: StateFlow<WearBackgroundDeliveryState> = _backgroundDelivery.asStateFlow()

    private var backgroundTransferJob: Job? = null

    // S2093: observable rather than a plain getter - the caption beside the sync button has to change
    // when a report arrives, and a getter is read once and never again. S2460 removed the getter that
    // stood beside this flow once its only reader, the screen's second status line, was deleted.
    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncedAt: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    // S2461: read back from the store rather than off the merged payload, so the version on screen is
    // the one that was persisted beside the sync time and the two readouts cannot disagree.
    private val _watchAppVersion = MutableStateFlow<String?>(null)
    val watchAppVersion: StateFlow<String?> = _watchAppVersion.asStateFlow()

    init {
        // Observe ack events emitted by PhoneWearListenerService
        viewModelScope.launch {
            WearSyncEvents.ackFlow.collect { ackJson ->
                val current = _uiState.value
                if (current is WearSyncUiState.Sending) {
                    ackTimeoutJob?.cancel()
                    val applied = parseAppliedCount(ackJson)
                    _uiState.value = WearSyncUiState.Success(applied, 0)
                    Timber.i("Wear sync ack received: $ackJson")
                }
            }
        }
        viewModelScope.launch {
            WearSyncEvents.watchSourcesReceivedFlow.collect { payload ->
                // S2484 ADR-5: the accept card guards against data the phone did not ask for. Inside
                // an exchange the owner started that condition does not hold, so asking again would
                // turn the one promised tap into two.
                if (_unifiedSyncState.value is UnifiedSyncState.Running) {
                    applyWatchSourcesDuringUnifiedSync(payload)
                } else {
                    _pendingWatchSources.value = payload
                }
            }
        }
        viewModelScope.launch {
            WearSyncEvents.watchPlaybackStateFlow.collect { state ->
                _watchPlaybackState.value = state
            }
        }
        viewModelScope.launch {
            WearSyncEvents.watchSettingsMergedFlow.collect { merged ->
                adoptMergedSettings(merged)
            }
        }
        // S2550: one collector for the whole session rather than a one-shot await, because the watch
        // also answers after the session is running - a stop given on the wrist arrives here.
        viewModelScope.launch {
            WearSyncEvents.listenAckFlow.collect { ack ->
                if (ack.requestId == listenRequestId) {
                    onListenAck(ack)
                }
            }
        }
        loadPersistedState()
    }

    private fun loadPersistedState() {
        viewModelScope.launch {
            val persisted = withContext(Dispatchers.IO) {
                PersistedWearState(
                    settings = wearSettingsMirrorStore.readSettings(),
                    preview = readPreparedFrame(),
                    lastSyncTimestamp = wearSettingsMirrorStore.readLastSyncTimestamp(),
                    watchAppVersion = wearSettingsMirrorStore.readWatchAppVersion(),
                )
            }
            _watchSettingsState.value = persisted.settings
            _backgroundMode.value = persisted.settings?.backgroundMode
                ?: WearSettingsPayload.BACKGROUND_MODE_BRANDED_ANIMATION
            _colorScheme.value = persisted.settings?.colorScheme ?: WearSettingsPayload.COLOR_SCHEME_DARK
            _backgroundPreview.value = persisted.preview
            _lastSyncTimestamp.value = persisted.lastSyncTimestamp
            _watchAppVersion.value = persisted.watchAppVersion
        }
    }

    // S2034: one-shot, so rotating the window does not re-open the browser or repeat the toast.
    private val _watchResourceEvents = MutableSharedFlow<WearWatchResourceEvent>(extraBufferCapacity = 1)
    val watchResourceEvents: SharedFlow<WearWatchResourceEvent> = _watchResourceEvents.asSharedFlow()

    /**
     * S2034: the companion window's add-or-open button - strategic 2 goals 1-3.
     *
     * @param defaultName used only when the row has to be created; the host supplies it because the
     *   name is a resource string and this view model must not reach for one on the domain's behalf.
     */
    fun addOrOpenWatchResource(defaultName: String) {
        viewModelScope.launch {
            val outcome = ensureWatchResourceUseCase(defaultName).getOrElse { e ->
                Timber.e(e, "Could not ensure the watch resource")
                _watchResourceEvents.emit(WearWatchResourceEvent.Failed)
                return@launch
            }
            _watchResourceEvents.emit(
                if (outcome.created) {
                    WearWatchResourceEvent.Created(defaultName)
                } else {
                    WearWatchResourceEvent.Open(outcome.resourceId)
                }
            )
        }
    }

    fun startPush() {
        ackTimeoutJob?.cancel()
        _uiState.value = WearSyncUiState.Sending
        viewModelScope.launch {
            outbound.sendResources()
                .onSuccess { result ->
                    if (result.sent == 0) {
                        // S1781: nothing left the phone, so no ack can ever arrive - waiting out the
                        // timeout would report a watch failure for an empty selection instead.
                        _uiState.value = WearSyncUiState.NothingSelected
                    } else {
                        // S1682: the use case returns as soon as Play Services accepts the bytes, which is
                        // necessarily before any watch ack can travel back. Declaring Success here used to
                        // win that race every time, so the ack collector above could never fire and the
                        // green check meant "accepted locally", never "the watch applied them". Stay in
                        // Sending and let the ack decide; a watch that never answers ends in an error the
                        // user can act on rather than in a check mark that is not true.
                        startAckTimeout()
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Wear sync failed")
                    _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_sync_failed))
                }
        }
    }

    /**
     * S2484: the whole exchange behind one control - strategic 2 goal 1.
     *
     * The resources leg is folded only after the watch acknowledges it, because the use case returns
     * as soon as Play Services accepts the bytes; reporting success there would repeat the S1682
     * defect inside the combined outcome. The settings leg needs no such wait - it travels as a Data
     * Item the watch applies and answers on its own.
     */
    fun syncEverything(settings: WearSettingsPayload) {
        if (_unifiedSyncState.value is UnifiedSyncState.Running) {
            return
        }
        val merged = withScreenChoices(settings)
        rememberSettings(merged)
        inboundResourcesLeg = null
        _unifiedSyncState.value = UnifiedSyncState.Running
        viewModelScope.launch {
            val acked = awaitResourcesAck(outbound.syncEverything(merged))
            val inbound = inboundResourcesLeg
            val complete = if (inbound == null) {
                acked
            } else {
                acked.withLeg(WearSyncLeg.RESOURCES_IN, inbound)
            }
            _unifiedSyncState.value = UnifiedSyncState.Finished(complete)
        }
    }

    fun resetUnifiedSync() {
        _unifiedSyncState.value = UnifiedSyncState.Idle
    }

    /**
     * Turns "the bytes were accepted locally" into "the watch applied them", or into a failure when
     * no acknowledgement arrives. A leg that sent nothing is passed through untouched - no ack can
     * ever arrive for it, so waiting would report a watch failure for an empty selection (S1781).
     */
    private suspend fun awaitResourcesAck(outcome: WearSyncOutcome): WearSyncOutcome {
        if (outcome.legs[WearSyncLeg.RESOURCES_OUT] !is WearSyncLegResult.Succeeded) {
            return outcome
        }
        val ack = withTimeoutOrNull(ACK_TIMEOUT_MS) { WearSyncEvents.ackFlow.first() }
        val applied = if (ack == null) {
            Timber.w("Unified sync: the watch did not acknowledge the resources within $ACK_TIMEOUT_MS ms")
            WearSyncLegResult.Failed(context.getString(R.string.wear_sync_no_ack))
        } else {
            WearSyncLegResult.Succeeded(parseAppliedCount(ack))
        }
        return outcome.withLeg(WearSyncLeg.RESOURCES_OUT, applied)
    }

    /**
     * S2484 ADR-5: resources arriving from the watch inside a user-started exchange are applied at
     * once, and the pending card is left for the unsolicited case that still needs it.
     */
    private suspend fun applyWatchSourcesDuringUnifiedSync(payload: WearSourcesExportPayload) {
        val result = importWatchSourcesUseCase(payload).fold(
            // S2502: a record the watch edited later now comes back as `updated` rather than `added`,
            // and counting only the additions would report 0 for a leg that did change resources -
            // the exact silence this ticket exists to end. The leg's count is records it changed.
            onSuccess = { WearSyncLegResult.Succeeded(it.added + it.updated) },
            onFailure = { error ->
                Timber.e(error, "Unified sync: the inbound resources leg failed")
                WearSyncLegResult.Failed(context.getString(R.string.wear_sync_failed))
            }
        )
        val current = _unifiedSyncState.value
        if (current is UnifiedSyncState.Finished) {
            _unifiedSyncState.value = UnifiedSyncState.Finished(
                current.outcome.withLeg(WearSyncLeg.RESOURCES_IN, result)
            )
        } else {
            inboundResourcesLeg = result
        }
    }

    // Holds the inbound leg's verdict when it lands before the outbound legs have finished, so the
    // outcome can carry it rather than dropping whichever half arrived first.
    private var inboundResourcesLeg: WearSyncLegResult? = null

    private fun startAckTimeout() {
        ackTimeoutJob = viewModelScope.launch {
            delay(ACK_TIMEOUT_MS)
            if (_uiState.value is WearSyncUiState.Sending) {
                Timber.w("Watch did not acknowledge the sync within $ACK_TIMEOUT_MS ms")
                _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_sync_no_ack))
            }
        }
    }

    fun reset() {
        ackTimeoutJob?.cancel()
        _uiState.value = WearSyncUiState.Idle
    }

    fun pushSettings(settings: WearSettingsPayload) {
        val merged = withScreenChoices(settings)
        rememberSettings(merged)
        _uiState.value = WearSyncUiState.Sending
        viewModelScope.launch {
            outbound.pushSettings(merged)
                .onSuccess {
                    _uiState.value = WearSyncUiState.SettingsPushed
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to push watch settings")
                    _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_push_settings_failed))
                }
        }
    }

    fun updateWatchSettingsLocally(settings: WearSettingsPayload) {
        rememberSettings(withScreenChoices(settings))
    }

    /**
     * S2093: the edit is stamped as well as stored, so the next exchange can tell it apart from an
     * edit made on the watch.
     *
     * The changed fields are derived by comparing rather than reported by the control that moved: the
     * group rebuilds the whole payload on every edit, and a stamping call wired per control would be
     * missed by the next control someone adds - the exact failure this ticket removes.
     */
    private fun rememberSettings(settings: WearSettingsPayload) {
        val changed = WearSettingsFieldDiff.changedFields(_watchSettingsState.value, settings)
        _watchSettingsState.value = settings
        // S2515 (ADR-4): taken here rather than inside the launch. The stamp must be the moment the
        // owner edited, not the moment the coroutine happened to run - the merge ranks this value
        // against the watch's clock, so a later time would let a phone edit beat a newer watch edit.
        val editedAt = System.currentTimeMillis()
        // S2515 (ADR-4): the application scope, not viewModelScope - this sheet is a
        // BottomSheetDialogFragment and is routinely closed in the same gesture that edits a setting,
        // which would cancel a viewModelScope write and lose exactly what the mirror exists to keep.
        applicationScope.launch {
            Timber.d("S2515: mirror write on ${Thread.currentThread().name}")
            wearSettingsMirrorStore.writeSettings(settings)
            if (changed.isNotEmpty()) {
                wearSettingsMirrorStore.writeFieldTimestamps(
                    wearSettingsMirrorStore.readFieldTimestamps() + changed.associateWith { editedAt }
                )
            }
        }
    }

    /**
     * S2093: adopts what the watch reported after the merge, so an edit made on the watch is visible
     * here without reopening the sheet.
     *
     * Written straight into the state rather than through [rememberSettings]: the merge has already
     * stored the payload and its stamps, and re-stamping them here would mark the watch's edit as a
     * phone edit made now, which would then beat the watch on the next exchange.
     */
    private fun adoptMergedSettings(settings: WearSettingsPayload) {
        _watchSettingsState.value = settings
        settings.backgroundMode?.let { _backgroundMode.value = it }
        settings.colorScheme?.let { _colorScheme.value = it }
        _lastSyncTimestamp.value = wearSettingsMirrorStore.readLastSyncTimestamp()
        _watchAppVersion.value = wearSettingsMirrorStore.readWatchAppVersion()
    }

    /**
     * S2000 / S2522: the watch-settings group rebuilds the whole payload from its own controls, and
     * neither the background nor the colour scheme lives in a control of that group - so without
     * merging them back in, editing any neighbouring switch would erase both before they ever left
     * the phone. Named for the pair rather than for the background alone, which is what it carried
     * until the scheme joined it.
     */
    private fun withScreenChoices(settings: WearSettingsPayload): WearSettingsPayload =
        settings.copy(backgroundMode = _backgroundMode.value, colorScheme = _colorScheme.value)

    fun updateBackgroundMode(mode: String) {
        _backgroundMode.value = mode
        _watchSettingsState.value?.let { rememberSettings(it.copy(backgroundMode = mode)) }
    }

    fun updateColorScheme(scheme: String) {
        Timber.d("S2522: companion updateColorScheme scheme=%s", scheme)
        _colorScheme.value = scheme
        _watchSettingsState.value?.let { rememberSettings(it.copy(colorScheme = scheme)) }
    }

    /**
     * The frame is prepared and queued here, but the outcome is read back off the transfer queue:
     * the use case returns as soon as the queue accepts the file, which is necessarily before any
     * byte reaches the watch (strategic §2.8).
     */
    fun sendBackgroundImage(uri: Uri) {
        backgroundTransferJob?.cancel()
        _backgroundDelivery.value = WearBackgroundDeliveryState.Sending
        backgroundTransferJob = viewModelScope.launch {
            sendWearBackgroundImageUseCase(uri)
                .onSuccess { transferId ->
                    // The frame exists on disk by the time the queue accepts it, and that file is
                    // what a reopened window reads back - so the preview follows the picked picture
                    // rather than the delivery, and the line below says whether it arrived.
                    _backgroundPreview.value = readPreparedFrame()
                    awaitBackgroundTransfer(transferId)
                    if (_backgroundDelivery.value == WearBackgroundDeliveryState.Sent) {
                        updateBackgroundMode(WearSettingsPayload.BACKGROUND_MODE_IMAGE)
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to prepare the watch background frame")
                    _backgroundDelivery.value = WearBackgroundDeliveryState.Failed
                }
        }
    }

    /**
     * Every queued transfer reaches a terminal outcome - an unreachable watch and a refused file are
     * outcomes, not silence - and finished entries are never dropped from the snapshot, so this waits
     * on an event that always comes rather than needing a timeout of its own.
     */
    private suspend fun awaitBackgroundTransfer(transferId: String) {
        val finished = wearFileTransferRepository.transfers
            .mapNotNull { snapshot -> snapshot.items.firstOrNull { it.id == transferId } }
            .first { it.outcome.isTerminal }

        _backgroundDelivery.value = deliveryStateOf(finished.outcome)
    }

    private fun deliveryStateOf(outcome: WearFileTransferOutcome): WearBackgroundDeliveryState =
        when (outcome) {
            WearFileTransferOutcome.SUCCEEDED -> WearBackgroundDeliveryState.Sent
            WearFileTransferOutcome.WATCH_UNREACHABLE -> WearBackgroundDeliveryState.WatchUnreachable
            WearFileTransferOutcome.QUEUED,
            WearFileTransferOutcome.RUNNING -> WearBackgroundDeliveryState.Sending
            WearFileTransferOutcome.CANCELLED,
            WearFileTransferOutcome.TOO_LARGE,
            WearFileTransferOutcome.FAILED -> WearBackgroundDeliveryState.Failed
        }

    /**
     * Read off disk rather than remembered in this instance: the window is a short-lived screen and
     * this view model dies with it, while the prepared frame outlives both, so a reopened window
     * shows the picture the watch was last given instead of an empty slot.
     */
    private fun readPreparedFrame(): WearBackgroundPreview? =
        File(context.cacheDir, WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME)
            .takeIf { it.exists() }
            ?.let { WearBackgroundPreview(it.absolutePath, it.lastModified()) }

    private data class PersistedWearState(
        val settings: WearSettingsPayload?,
        val preview: WearBackgroundPreview?,
        val lastSyncTimestamp: Long,
        val watchAppVersion: String?,
    )

    fun acceptWatchImport() {
        val payload = _pendingWatchSources.value ?: return
        viewModelScope.launch {
            importWatchSourcesUseCase(payload)
                .onSuccess { result ->
                    Timber.i(
                        "Watch import accepted: added=${result.added} " +
                            "updated=${result.updated} skipped=${result.skipped}"
                    )
                }
                .onFailure { e ->
                    Timber.e(e, "Watch import failed")
                }
            _pendingWatchSources.value = null
        }
    }

    fun dismissWatchImport() {
        _pendingWatchSources.value = null
    }

    fun sendPlaybackCommand(command: WearPlaybackCommand) {
        viewModelScope.launch {
            outbound.sendPlaybackCommand(command).onFailure { e ->
                Timber.e(e, "Failed to send playback command $command")
            }
        }
    }

    private val _listenState = MutableStateFlow<WearListenState>(WearListenState.Idle())
    val listenState: StateFlow<WearListenState> = _listenState.asStateFlow()

    /**
     * S2550: null except while this screen is waiting for, or holding, an answer of its own.
     *
     * `listenAckFlow` replays its last value, so a collector recreated by a rotation is handed the
     * previous session's answer at once. Matching this id is what tells that answer apart from the
     * one the owner is waiting on - without it a stale refusal would close a live session, and a
     * stale address would be opened in the player.
     */
    private var listenRequestId: String? = null

    private var listenTimeoutJob: Job? = null

    /**
     * ADR-5: the shipped playback path, reached through the same controller every audio screen uses.
     * Built lazily because most instances of this view model never listen to anything.
     */
    private val audioController by lazy { AudioServiceController(context) }

    private val listenErrorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            onListenStreamDropped(error)
        }
    }

    /**
     * S2550 §3.1: the phone initiates, and this is where it does.
     *
     * The command opens no microphone. ADR-6 puts the owner's tap on the watch between this and any
     * capture, so what leaves here is a request the watch raises as a notification and nothing else.
     */
    fun startListening() {
        if (_listenState.value !is WearListenState.Idle) {
            return
        }
        Timber.d("S2550: phone requested a watch listening session")
        val requestId = UUID.randomUUID().toString()
        listenRequestId = requestId
        _listenState.value = WearListenState.Awaiting
        // Written before the command leaves, and with commit rather than apply: the address can come
        // back fast enough that the audio service is created - and its load control built - before a
        // flag written afterwards would have reached disk.
        RadioStreamBufferConfig.syncLiveSessionMirror(context, true)
        startListenTimeout(requestId)
        viewModelScope.launch {
            outbound.startListening(requestId).onFailure { e ->
                Timber.i(e, "Could not ask the watch to listen")
                endListenSession(R.string.wear_listen_send_failed)
            }
        }
    }

    /** The stop action on the control. */
    fun stopListening() {
        endListenSession(messageRes = null)
    }

    /**
     * The watch answers every outcome of its own accord, its own two-minute expiry included, so this
     * bound is only for the case where nothing on the other side is alive to answer at all. It sits
     * above that expiry rather than competing with it: firing first would report "no answer" for a
     * request the owner is still looking at.
     */
    private fun startListenTimeout(requestId: String) {
        listenTimeoutJob?.cancel()
        listenTimeoutJob = viewModelScope.launch {
            delay(LISTEN_ANSWER_TIMEOUT_MS)
            if (listenRequestId == requestId && _listenState.value is WearListenState.Awaiting) {
                Timber.w("The watch did not answer a listen command within $LISTEN_ANSWER_TIMEOUT_MS ms")
                endListenSession(R.string.wear_listen_no_answer)
            }
        }
    }

    private fun onListenAck(ack: WearListenAckPayload) {
        listenTimeoutJob?.cancel()
        val refusal = ack.refusal
        if (refusal == null) {
            Timber.i("The watch is serving a listening session")
            playListenStream(ack.streamUrl())
        } else {
            Timber.i("The watch is not serving a listening session: %s", refusal)
            endListenSession(messageFor(refusal))
        }
    }

    /**
     * ADR-4 and ADR-5 together: an ordinary HTTP address, opened exactly as internet radio is.
     *
     * No player, no media-source factory and no custom source are built here. The watch serves
     * self-delimiting ADTS AAC frames, which is what makes the shipped path enough - a source of this
     * project's own exists only where the transport is not HTTP, as with SMB, SFTP and FTP.
     */
    private fun playListenStream(url: String) {
        audioController.playAudio(Uri.parse(url), mimeType = LISTEN_MIME_TYPE) { player ->
            player.addListener(listenErrorListener)
            _listenState.value = WearListenState.Listening
        }
    }

    /**
     * S2550 §6.6, the explicit form: the media connection dropped mid-session.
     *
     * It delegates to [endListenSession], which stops playback, sends the stop over the control
     * channel that outlived the media one, and only then returns the control to idle - carrying the
     * one instruction that helps, that the watch left Wi-Fi.
     *
     * **§6.6 is an owner decision still open.** The owner may instead prefer a silent pause with
     * automatic recovery, on the grounds that a watch stepping out of coverage for a moment is not
     * the same event as a session ending. If that is the answer, this handler is the single place
     * that changes: nothing above it distinguishes a drop from a stop, and the watch's teardown is
     * Phase 04's idempotent stop path either way.
     */
    private fun onListenStreamDropped(error: PlaybackException) {
        if (_listenState.value !is WearListenState.Listening) {
            return
        }
        Timber.i(error, "The watch's audio stream dropped; ending the listening session")
        endListenSession(R.string.wear_listen_wifi_lost)
    }

    /**
     * The one exit from a listening session, in the order acceptance criterion 2 needs: playback
     * stops first so nothing is still pulling on the watch's server, the stop command goes out so the
     * watch extinguishes its microphone indicator, and only then does the control return to idle.
     *
     * The stop rides the application scope because it must also survive [onCleared], where
     * `viewModelScope` is already cancelled - and that is the one path where dropping it would leave
     * the watch's microphone open with nothing left able to close it.
     *
     * @param messageRes what to say about why it ended, or null when the owner ended it themselves.
     */
    private fun endListenSession(@StringRes messageRes: Int?) {
        val requestId = listenRequestId
        listenRequestId = null
        listenTimeoutJob?.cancel()
        stopListenPlayback()
        if (requestId != null) {
            applicationScope.launch {
                outbound.stopListening(requestId).onFailure { e ->
                    Timber.i(e, "Could not tell the watch its listening session ended")
                }
            }
        }
        _listenState.value = WearListenState.Idle(messageRes)
    }

    private fun stopListenPlayback() {
        audioController.player?.let { player ->
            player.removeListener(listenErrorListener)
            player.stop()
            player.clearMediaItems()
        }
        audioController.release()
        RadioStreamBufferConfig.syncLiveSessionMirror(context, false)
    }

    /** Each value exists because it needs different words; a shared one would be a silent screen. */
    @StringRes
    private fun messageFor(refusal: WearListenRefusal): Int = when (refusal) {
        WearListenRefusal.NOT_ASKED -> R.string.wear_listen_refusal_not_asked
        WearListenRefusal.DECLINED -> R.string.wear_listen_refusal_declined
        WearListenRefusal.EXPIRED -> R.string.wear_listen_refusal_expired
        WearListenRefusal.CAPTURE_FAILED -> R.string.wear_listen_refusal_capture_failed
        WearListenRefusal.NO_NETWORK -> R.string.wear_listen_refusal_no_network
        WearListenRefusal.NOT_ON_WIFI -> R.string.wear_listen_refusal_no_network
        WearListenRefusal.STOPPED -> R.string.wear_listen_refusal_stopped
        WearListenRefusal.BUSY -> R.string.wear_listen_refusal_busy
        WearListenRefusal.UNKNOWN -> R.string.wear_listen_refusal_unknown
    }

    /**
     * The control on this screen is the only way to end a session, so a window that goes away for
     * good takes the session with it - a microphone left open on the watch with nothing able to close
     * it is the covert recording the Non-goals put outside the product.
     */
    override fun onCleared() {
        if (_listenState.value !is WearListenState.Idle) {
            endListenSession(messageRes = null)
        }
        super.onCleared()
    }

    // S1682: the watch reports two numbers, `added` and `updated`. Reading only `added` showed
    // "0 resources" after a sync that in fact refreshed every existing one, which reads as a failure.
    private fun parseAppliedCount(json: String): Int =
        parseIntField(json, "added") + parseIntField(json, "updated")

    private fun parseIntField(json: String, field: String): Int = try {
        json.substringAfter("\"$field\":", "").substringBefore(",").substringBefore("}")
            .trim().toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    companion object {
        // Long enough for a Bluetooth-linked watch to wake and apply the payload, short enough that
        // a user staring at the dialog is not left guessing. The verified round trip of 2026-08-15
        // acked well inside this window.
        private const val ACK_TIMEOUT_MS = 15_000L

        // S2550: above the watch's own two-minute request expiry, which answers on its own - this
        // covers only a watch that cannot answer at all.
        private const val LISTEN_ANSWER_TIMEOUT_MS = 150_000L

        // ADR-4: the watch serves ADTS AAC, and naming it spares the extractor a sniff on a live
        // stream that has no container to read the type from.
        private const val LISTEN_MIME_TYPE = "audio/aac"
    }
}
