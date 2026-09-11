package com.sza.fastmediasorter.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.repository.wear.SharedPreferencesWearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSettingsFieldDiff
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.model.WearSyncLeg
import com.sza.fastmediasorter.domain.model.WearSyncLegResult
import com.sza.fastmediasorter.domain.model.WearSyncOutcome
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.usecase.EnsureWatchResourceUseCase
import com.sza.fastmediasorter.domain.usecase.GetPairedWatchStatusUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ObserveUnitSystemUseCase
import com.sza.fastmediasorter.domain.usecase.SendWearBackgroundImageUseCase
import com.sza.fastmediasorter.service.WatchListenSessionManager
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearListenState
import com.sza.fastmediasorter.service.WearSyncEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed class WearSyncUiState {
    data object Idle : WearSyncUiState()
    data object Sending : WearSyncUiState()
    data class Success(val sent: Int, val skipped: Int, val removed: Int = 0) : WearSyncUiState()

    /**
     * S1781: the owner has marked no resources for the watch, so nothing was sent - not a failure.
     *
     * S2926: "nothing was sent" means no DataItem was written, which is what `SendResult.dispatched`
     * states. A batch of pure deletions carries no sources and withdraws nothing yet still travels,
     * and it belongs in [Sending] until the watch answers, not here.
     */
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
 * S2916: one-shot feedback for the settings push, delivered outside the beam dialog.
 *
 * The resources push shows its outcome inside [BeamAnimationDialog], but the settings push does not open
 * that dialog, so its timeout and local failure need their own channel. A [SharedFlow] mirrors the
 * existing [WearWatchResourceEvent] pattern collected by the host fragment as a toast.
 */
sealed class SettingsPushEvent {
    data class Timeout(val message: String) : SettingsPushEvent()
    data class Failed(val message: String) : SettingsPushEvent()
}

// Every parameter is a distinct collaborator this screen needs (sync legs, watch resource ops,
// listening, settings mirror) - S2731 added settingsRepository for the one read-only unitSystem value.
@Suppress("LongParameterList")
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
    private val observeUnitSystemUseCase: ObserveUnitSystemUseCase,
    // S2881: the session itself lives in the process, not here - this screen is one of its readers.
    private val watchListenSessionManager: WatchListenSessionManager,
    // S2515 (ADR-4): mirror writes outlive this ViewModel on purpose - see rememberSettings.
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    /**
     * S2731: the phone's own measurement system, so the companion window's `payload()` builder can
     * send it without a dedicated UI row (no `companionRowTag` - see `WearSettingsRegistry`).
     */
    val unitSystem: StateFlow<UnitSystem> = observeUnitSystemUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.DEFAULT)

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

    // S2916: tracks the settings push's ack wait, parallel to ackTimeoutJob for resources.
    private var settingsAckTimeoutJob: Job? = null
    private var settingsPushInFlight = false

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

    // S2862: in-memory mirror of WearSettingsMirrorStore's field timestamps. readFieldTimestamps() is
    // suspend (IO) and rememberSettings captures editedAt synchronously (S2515/ADR-4: the stamp is the
    // moment of the edit, not the moment the coroutine runs), so the outgoing payload reads this cache
    // rather than re-reading the store and racing the async write in applicationScope.
    private var fieldTimestampsCache: Map<String, Long> = emptyMap()

    init {
        // Observe ack events emitted by PhoneWearListenerService
        viewModelScope.launch {
            WearSyncEvents.ackFlow.collect { ackJson ->
                val current = _uiState.value
                if (current is WearSyncUiState.Sending) {
                    ackTimeoutJob?.cancel()
                    // S2882: the push dialog reports what the watch actually did, not a sum that calls
                    // a removal a send. `sent` is what travelled onto the watch; `removed` is what the
                    // phone declared unwanted and the watch deleted. A removal-only batch now reads
                    // "Removed N source(s) from watch" instead of "Sent N source(s) to watch".
                    val sent = parseIntField(ackJson, "added") + parseIntField(ackJson, "updated")
                    val removed = parseIntField(ackJson, "removed")
                    Timber.d("S2882: companion reports sent=$sent removed=$removed")
                    _uiState.value = WearSyncUiState.Success(sent, 0, removed)
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
                    fieldTimestamps = wearSettingsMirrorStore.readFieldTimestamps()
                )
            }
            _watchSettingsState.value = persisted.settings
            _backgroundMode.value = persisted.settings?.backgroundMode
                ?: WearSettingsPayload.BACKGROUND_MODE_BRANDED_ANIMATION
            _colorScheme.value = persisted.settings?.colorScheme ?: WearSettingsPayload.COLOR_SCHEME_DARK
            _backgroundPreview.value = persisted.preview
            _lastSyncTimestamp.value = persisted.lastSyncTimestamp
            _watchAppVersion.value = persisted.watchAppVersion
            fieldTimestampsCache = persisted.fieldTimestamps
        }
    }

    // S2034: one-shot, so rotating the window does not re-open the browser or repeat the toast.
    private val _watchResourceEvents = MutableSharedFlow<WearWatchResourceEvent>(extraBufferCapacity = 1)
    val watchResourceEvents: SharedFlow<WearWatchResourceEvent> = _watchResourceEvents.asSharedFlow()

    // S2916: one-shot toast channel for settings push timeout and local failure.
    private val _settingsPushEvent = MutableSharedFlow<SettingsPushEvent>(extraBufferCapacity = 1)
    val settingsPushEvent: SharedFlow<SettingsPushEvent> = _settingsPushEvent.asSharedFlow()

    /**
     * S2034: the companion window's add-or-open button - strategic 2 goals 1-3.
     *
     * S2868: a created row is named after the watch on the link (the same human name the settings
     * row shows), so the main-screen tile reads like the settings row does.
     *
     * @param defaultName used only when the bridge cannot name the watch; the host supplies it
     *   because the name is a resource string and this view model must not reach for one on the
     *   domain's behalf.
     */
    fun addOrOpenWatchResource(defaultName: String) {
        viewModelScope.launch {
            val name = (getPairedWatchStatusUseCase() as? PairedWatchStatus.Connected)
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?: defaultName
            Timber.d("S2868: add-or-open names the watch resource '%s'", name)
            val outcome = ensureWatchResourceUseCase(name).getOrElse { e ->
                Timber.e(e, "Could not ensure the watch resource")
                _watchResourceEvents.emit(WearWatchResourceEvent.Failed)
                return@launch
            }
            _watchResourceEvents.emit(
                if (outcome.created) {
                    WearWatchResourceEvent.Created(name)
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
                    Timber.d("S2926: push returned dispatched=${result.dispatched}")
                    if (!result.dispatched) {
                        // S1781: nothing left the phone, so no ack can ever arrive - waiting out the
                        // timeout would report a watch failure for an empty selection instead.
                        // S2882: a batch that withdrew resources DID leave the phone and will be
                        // acknowledged, so only a batch that did neither takes this branch.
                        // S2926: the use case now states that departure instead of leaving it to be
                        // inferred from the counters - a batch of pure deletions has both of them at
                        // zero and was landing here, dropping the ack the watch does send.
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
            val acked = awaitResourcesAck(outbound.syncEverything(stampedForWire(merged)))
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

    /**
     * S2916: the settings push's counterpart to [startAckTimeout]. Waits for the watch's merge report
     * to arrive via [WearSyncEvents.watchSettingsMergedFlow]; if it does not, tells the owner via a toast
     * rather than leaving the old timestamp and version on screen in silence.
     */
    private fun startSettingsAckTimeout() {
        settingsAckTimeoutJob = viewModelScope.launch {
            delay(ACK_TIMEOUT_MS)
            if (settingsPushInFlight) {
                settingsPushInFlight = false
                Timber.w("Watch did not report the settings merge within $ACK_TIMEOUT_MS ms")
                Timber.d("S2916: settings push timed out - no merge report")
                _uiState.value = WearSyncUiState.Idle
                _settingsPushEvent.tryEmit(
                    SettingsPushEvent.Timeout(context.getString(R.string.wear_sync_settings_no_ack))
                )
            }
        }
    }

    fun reset() {
        ackTimeoutJob?.cancel()
        settingsAckTimeoutJob?.cancel()
        settingsPushInFlight = false
        _uiState.value = WearSyncUiState.Idle
    }

    fun pushSettings(settings: WearSettingsPayload) {
        val merged = withScreenChoices(settings)
        rememberSettings(merged)
        settingsAckTimeoutJob?.cancel()
        _uiState.value = WearSyncUiState.Sending
        viewModelScope.launch {
            outbound.pushSettings(stampedForWire(merged))
                .onSuccess {
                    // S2916: Play Services accepting the Data Item means "handed to the Data Layer",
                    // not "the watch answered". Stay in Sending and wait for the merge report, mirroring
                    // the resources path's startAckTimeout. The report arrives via
                    // watchSettingsMergedFlow and completes the push in adoptMergedSettings.
                    settingsPushInFlight = true
                    startSettingsAckTimeout()
                    Timber.d("S2916: settings push waiting for merge report")
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to push watch settings")
                    _uiState.value = WearSyncUiState.Idle
                    _settingsPushEvent.tryEmit(
                        SettingsPushEvent.Failed(context.getString(R.string.wear_push_settings_failed))
                    )
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
        // S2862: the cache is updated synchronously so the outgoing payload built in the same edit
        // carries the fresh stamp without racing the async write below. The same map is then persisted,
        // keeping the in-memory and on-disk copies in step.
        if (changed.isNotEmpty()) {
            fieldTimestampsCache = fieldTimestampsCache + changed.associateWith { editedAt }
        }
        // S2515 (ADR-4): the application scope, not viewModelScope - this sheet is a
        // BottomSheetDialogFragment and is routinely closed in the same gesture that edits a setting,
        // which would cancel a viewModelScope write and lose exactly what the mirror exists to keep.
        applicationScope.launch {
            wearSettingsMirrorStore.writeSettings(settings)
            if (changed.isNotEmpty()) {
                wearSettingsMirrorStore.writeFieldTimestamps(fieldTimestampsCache)
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
        // S2862: the merge wrote new stamps to the store, so the in-memory cache must follow them -
        // otherwise the next push would carry the pre-merge stamps and the watch's just-accepted edit
        // could lose to its own prior value. readFieldTimestamps() is suspend, so the refresh is async.
        viewModelScope.launch { fieldTimestampsCache = wearSettingsMirrorStore.readFieldTimestamps() }
        // S2916: the merge report is the watch's answer. When a settings push is waiting for it,
        // this arrival completes the exchange - the timestamp and version above are already updated.
        if (settingsPushInFlight) {
            settingsPushInFlight = false
            settingsAckTimeoutJob?.cancel()
            _uiState.value = WearSyncUiState.SettingsPushed
            Timber.d("S2916: settings push completed by merge report")
        }
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

    /**
     * S2862: attaches the phone's field-edit timestamps and an empty capability map to a payload
     * about to leave for the watch.
     *
     * Before this the builder in [WearWatchSettingsGroup.payload] left both fields null, so the watch's
     * [com.sza.fastmediasorter.wear.domain.model.WearSettingsMergeResolver] read `incomingStamps == null`
     * and applied every phone value unconditionally - the Last-Write-Wins policy (S2485) was dead in the
     * phone-to-watch direction. The watch never reads phone-side `capabilities`, but an empty map (rather
     * than null) satisfies the decoder contract so the exchange no longer reports a spurious `MISSING`
     * divergence for it.
     */
    private fun stampedForWire(settings: WearSettingsPayload): WearSettingsPayload {
        val stamped = settings.copy(fieldTimestamps = fieldTimestampsCache, capabilities = emptyMap())
        Timber.d("S2862: outbound settings payload carries ${fieldTimestampsCache.size} field stamp(s)")
        return stamped
    }

    fun updateBackgroundMode(mode: String) {
        _backgroundMode.value = mode
        _watchSettingsState.value?.let { rememberSettings(it.copy(backgroundMode = mode)) }
    }

    fun updateColorScheme(scheme: String) {
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
        val fieldTimestamps: Map<String, Long> = emptyMap(),
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

    /**
     * S2881: the session lives in [WatchListenSessionManager] for the life of the process, so this
     * screen renders it rather than owns it.
     *
     * Closing this window no longer ends a running session, which is a deliberate reversal of the
     * S2550 behaviour: a session started from the widget or a launcher shortcut must not die because
     * the owner happened to open and close the companion card, and something outside this screen can
     * now stop it. The watch's own indicator and stop path are unchanged.
     */
    val listenState: StateFlow<WearListenState> = watchListenSessionManager.listenState

    /** The start action on the card. The record variant has its own route and does not pass here. */
    fun startListening() {
        watchListenSessionManager.start(record = false)
    }

    /** The stop action on the card. */
    fun stopListening() {
        watchListenSessionManager.stop()
    }

    // S1682: the watch reports two numbers, `added` and `updated`. Reading only `added` showed
    // "0 resources" after a sync that in fact refreshed every existing one, which reads as a failure.
    // S2882 adds the third: a watch that only deleted withdrawn sources changed as much as one that
    // added them, and a phone older than that field simply scores it zero.
    private fun parseAppliedCount(json: String): Int =
        parseIntField(json, "added") + parseIntField(json, "updated") + parseIntField(json, "removed")

    private fun parseIntField(json: String, field: String): Int = try {
        json.substringAfter("\"$field\":", "").substringBefore(",").substringBefore("}")
            .trim().toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    companion object {
        // Long enough for a Bluetooth-linked watch to wake and apply the payload, short enough that
        // a user staring at the dialog is not left guessing. The verified round trip of 2026-08-15
        // acked well inside this window.
        private const val ACK_TIMEOUT_MS = 15_000L
    }
}
