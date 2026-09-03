package com.sza.fastmediasorter.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.repository.wear.SharedPreferencesWearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSettingsFieldDiff
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.usecase.EnsureWatchResourceUseCase
import com.sza.fastmediasorter.domain.usecase.GetPairedWatchStatusUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SendWearBackgroundImageUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import timber.log.Timber
import java.io.File
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

@HiltViewModel
class WearSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outbound: WearOutboundUseCases,
    private val importWatchSourcesUseCase: ImportWatchSourcesUseCase,
    private val getPairedWatchStatusUseCase: GetPairedWatchStatusUseCase,
    private val ensureWatchResourceUseCase: EnsureWatchResourceUseCase,
    private val sendWearBackgroundImageUseCase: SendWearBackgroundImageUseCase,
    private val wearFileTransferRepository: WearFileTransferRepository,
    private val wearSettingsMirrorStore: SharedPreferencesWearSettingsMirrorStore
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
    private val _watchSettingsState = MutableStateFlow(wearSettingsMirrorStore.readSettings())
    val watchSettingsState: StateFlow<WearSettingsPayload?> = _watchSettingsState.asStateFlow()

    private val _pendingWatchSources = MutableStateFlow<WearSourcesExportPayload?>(null)
    val pendingWatchSources: StateFlow<WearSourcesExportPayload?> = _pendingWatchSources.asStateFlow()

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

    private val _backgroundPreview = MutableStateFlow(readPreparedFrame())
    val backgroundPreview: StateFlow<WearBackgroundPreview?> = _backgroundPreview.asStateFlow()

    private val _backgroundDelivery =
        MutableStateFlow<WearBackgroundDeliveryState>(WearBackgroundDeliveryState.Idle)
    val backgroundDelivery: StateFlow<WearBackgroundDeliveryState> = _backgroundDelivery.asStateFlow()

    private var backgroundTransferJob: Job? = null

    // S2093: observable rather than a plain getter - the caption beside the sync button has to change
    // when a report arrives, and a getter is read once and never again. S2460 removed the getter that
    // stood beside this flow once its only reader, the screen's second status line, was deleted.
    private val _lastSyncTimestamp = MutableStateFlow(wearSettingsMirrorStore.readLastSyncTimestamp())
    val lastSyncedAt: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    // S2461: read back from the store rather than off the merged payload, so the version on screen is
    // the one that was persisted beside the sync time and the two readouts cannot disagree.
    private val _watchAppVersion = MutableStateFlow(wearSettingsMirrorStore.readWatchAppVersion())
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
                _pendingWatchSources.value = payload
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
        val merged = withBackgroundMode(settings)
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
        rememberSettings(withBackgroundMode(settings))
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
        wearSettingsMirrorStore.writeSettings(settings)
        if (changed.isEmpty()) return
        val editedAt = System.currentTimeMillis()
        wearSettingsMirrorStore.writeFieldTimestamps(
            wearSettingsMirrorStore.readFieldTimestamps() + changed.associateWith { editedAt }
        )
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
        _lastSyncTimestamp.value = wearSettingsMirrorStore.readLastSyncTimestamp()
        _watchAppVersion.value = wearSettingsMirrorStore.readWatchAppVersion()
    }

    /**
     * S2000: the watch-settings group rebuilds the whole payload from its own controls, and the
     * background lives in no control of that group - so without merging it back in, editing any
     * neighbouring switch would erase the chosen background before it ever left the phone.
     */
    private fun withBackgroundMode(settings: WearSettingsPayload): WearSettingsPayload =
        settings.copy(backgroundMode = _backgroundMode.value)

    fun updateBackgroundMode(mode: String) {
        _backgroundMode.value = mode
        _watchSettingsState.value?.let { rememberSettings(it.copy(backgroundMode = mode)) }
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

    fun acceptWatchImport() {
        val payload = _pendingWatchSources.value ?: return
        viewModelScope.launch {
            importWatchSourcesUseCase(payload)
                .onSuccess { result ->
                    Timber.i("Watch import accepted: added=${result.added} skipped=${result.skipped}")
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
    }
}
