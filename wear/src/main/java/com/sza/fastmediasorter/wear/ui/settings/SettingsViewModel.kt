package com.sza.fastmediasorter.wear.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents
import com.sza.fastmediasorter.wear.data.wear.WearLogReportClient
import com.sza.fastmediasorter.wear.data.wear.WearLogReportOutcome
import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.domain.model.WearOpenUrlOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPortalLinks
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearOpenUrlOnPhoneRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ObserveWearGeometryModeUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ReportWearSettingsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.SetStreamsSectionEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// combine() over a flow list hands back positional values; naming the positions keeps a new flow
// from silently shifting the reads below it.
private const val INDEX_AUDIO = 0
private const val INDEX_VIDEO = 1
private const val INDEX_IMAGES = 2
private const val INDEX_SLIDESHOW = 3
private const val INDEX_INTERVAL = 4
private const val INDEX_ALBUM_ART = 5
private const val INDEX_VIEW_MODE = 6
private const val INDEX_STREAMS_SECTION = 7
private const val INDEX_KEEP_AWAKE = 8
private const val INDEX_FILE_LIST_VIEW_MODE = 9
private const val INDEX_AUTO_ROTATION = 10
private const val INDEX_VOICE_NOTE_POLICY = 11
private const val INDEX_BACKGROUND_MODE = 12
private const val INDEX_LAST_SYNC = 13
private const val INDEX_DOCUMENTS = 14
private const val INDEX_DISABLE_ANIMATIONS = 15
private const val INDEX_BACKGROUND_PLAYBACK = 16
private const val INDEX_PANEL_AUTO_HIDE = 17
private const val INDEX_POWER_SAVING_TRIGGER = 18

// S2522: appended rather than inserted. Every index above names a position in the combine list, and the
// casts below are unchecked, so renumbering would silently re-map settings onto each other's flows.
private const val INDEX_COLOR_SCHEME = 19

/** S2773: appended for the reason stated directly above, which holds for every index added later. */
private const val INDEX_GEOMETRY_MODE = 20

/** S3256: appended to combine list for dim clock and status overlay preference. */
private const val INDEX_DIM_CLOCK_OVERLAY = 21

/**
 * ViewModel for Settings screen.
 * Manages loading and updating of app settings.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository,
    private val logReportClient: WearLogReportClient,
    private val reportWearSettingsUseCase: ReportWearSettingsUseCase,
    private val openUrlOnPhoneRepository: WearOpenUrlOnPhoneRepository,
    private val setStreamsSectionEnabled: SetStreamsSectionEnabledUseCase,
    private val observeGeometryMode: ObserveWearGeometryModeUseCase,
    private val geometryDefaults: WearGeometryDefaults
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE.toString()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _logReportState = MutableStateFlow<WearLogReportState>(WearLogReportState.Idle)

    /** State of the "send logs" action, separate from the settings values the screen renders. */
    val logReportState: StateFlow<WearLogReportState> = _logReportState.asStateFlow()

    private val _watchPortalState = MutableStateFlow<WearPortalLinkState>(WearPortalLinkState.Idle)

    /** S2496: outcome of the last attempt to open the portal in the watch's own browser. */
    val watchPortalState: StateFlow<WearPortalLinkState> = _watchPortalState.asStateFlow()

    private val _phonePortalState = MutableStateFlow<WearPortalLinkState>(WearPortalLinkState.Idle)

    /** S2496: outcome of the last attempt to open the portal on the paired phone. */
    val phonePortalState: StateFlow<WearPortalLinkState> = _phonePortalState.asStateFlow()

    init {
        loadSettings()
        observeSettingsErrors()
    }

    private fun observeSettingsErrors() {
        viewModelScope.launch {
            WatchSyncEvents.settingsErrorFlow.collect { error ->
                Timber.e("SettingsViewModel: remote settings apply error - $error")
            }
        }
    }

    /**
     * Every flow the settings screen reads, in the order the `INDEX_*` constants name.
     *
     * Its own function so a source added later does not push [loadSettings] past detekt's length
     * ceiling and force an unrelated rewrite of the state assembly (S2773). The list is typed: the
     * sources are Boolean, Int and enums, and letting the compiler infer a reified intersection of
     * those raises a warning that becomes an error in a future Kotlin release. APPEND here - the reads
     * below are positional and unchecked, so inserting would silently re-map settings onto each other.
     */
    private fun settingsSources(): List<Flow<Any>> = listOf(
        preferencesRepository.isAudioEnabled,
        preferencesRepository.isVideoEnabled,
        preferencesRepository.isImagesEnabled,
        preferencesRepository.isSlideshowEnabled,
        preferencesRepository.slideshowIntervalSeconds,
        preferencesRepository.downloadAlbumArt,
        preferencesRepository.viewMode,
        preferencesRepository.streamsSectionEnabled,
        preferencesRepository.keepScreenAwakeOutsidePlayers,
        preferencesRepository.fileListViewMode,
        preferencesRepository.isAutoRotationEnabled,
        preferencesRepository.voiceNoteSendPolicy,
        preferencesRepository.backgroundMode,
        preferencesRepository.lastSettingsSyncAt,
        preferencesRepository.isDocumentsEnabled,
        preferencesRepository.isAnimationsDisabled,
        preferencesRepository.backgroundPlaybackEnabled,
        preferencesRepository.panelAutoHideSeconds,
        preferencesRepository.powerSavingTrigger,
        preferencesRepository.colorScheme,
        // S2773: the RESOLVED view, not the stored choice - the row has to show what the watch is laid
        // out with from the moment it is installed, and the stored choice is null until first touched.
        observeGeometryMode(),
        preferencesRepository.dimClockOverlayEnabled
    )

    private fun loadSettings() {
        viewModelScope.launch {
            val hasAccelerometer = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
            combine(settingsSources()) { values ->
                val audio = values[INDEX_AUDIO] as Boolean
                val video = values[INDEX_VIDEO] as Boolean
                val images = values[INDEX_IMAGES] as Boolean
                val slideshow = values[INDEX_SLIDESHOW] as Boolean
                val interval = values[INDEX_INTERVAL] as Int
                val albumArt = values[INDEX_ALBUM_ART] as Boolean
                val viewMode = values[INDEX_VIEW_MODE] as WearViewMode
                val streamsSection = values[INDEX_STREAMS_SECTION] as Boolean
                val keepAwake = values[INDEX_KEEP_AWAKE] as Boolean
                val fileListView = values[INDEX_FILE_LIST_VIEW_MODE] as WearViewMode
                val autoRotation = values[INDEX_AUTO_ROTATION] as Boolean
                val sendPolicy = values[INDEX_VOICE_NOTE_POLICY] as VoiceNoteSendPolicy
                val background = values[INDEX_BACKGROUND_MODE] as WearBackgroundMode
                val lastSync = values[INDEX_LAST_SYNC] as Long
                val documents = values[INDEX_DOCUMENTS] as Boolean
                val disableAnimations = values[INDEX_DISABLE_ANIMATIONS] as Boolean
                val backgroundPlayback = values[INDEX_BACKGROUND_PLAYBACK] as Boolean
                val panelAutoHide = values[INDEX_PANEL_AUTO_HIDE] as Int
                val powerSaving = values[INDEX_POWER_SAVING_TRIGGER] as PowerSavingTrigger
                val colorScheme = values[INDEX_COLOR_SCHEME] as WearColorScheme
                val geometryMode = values[INDEX_GEOMETRY_MODE] as WearGeometryMode
                val dimClockOverlay = values[INDEX_DIM_CLOCK_OVERLAY] as Boolean
                _uiState.value.copy(
                    backgroundMode = background,
                    colorScheme = colorScheme,
                    lastSyncedAtEpochMillis = lastSync,
                    isAudioEnabled = audio,
                    isVideoEnabled = video,
                    isImagesEnabled = images,
                    isDocumentsEnabled = documents,
                    isSlideshowEnabled = slideshow,
                    slideshowIntervalSeconds = interval,
                    panelAutoHideSeconds = panelAutoHide,
                    downloadAlbumArt = albumArt,
                    viewMode = viewMode,
                    streamsSectionEnabled = streamsSection,
                    keepScreenAwakeOutsidePlayers = keepAwake,
                    fileListViewMode = fileListView,
                    isAutoRotationEnabled = autoRotation,
                    hasAutoRotationSensor = hasAccelerometer,
                    voiceNoteSendPolicy = sendPolicy,
                    isAnimationsDisabled = disableAnimations,
                    powerSavingTrigger = powerSaving,
                    backgroundPlaybackEnabled = backgroundPlayback,
                    geometryMode = geometryMode,
                    offersGeometryModeSwitch = geometryDefaults.offersModeSwitch,
                    dimClockOverlayEnabled = dimClockOverlay,
                    isLoading = false
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    fun toggleAudio() {
        viewModelScope.launch {
            preferencesRepository.setAudioEnabled(!_uiState.value.isAudioEnabled)
        }
    }

    fun toggleVideo() {
        viewModelScope.launch {
            preferencesRepository.setVideoEnabled(!_uiState.value.isVideoEnabled)
        }
    }

    fun toggleImages() {
        viewModelScope.launch {
            preferencesRepository.setImagesEnabled(!_uiState.value.isImagesEnabled)
        }
    }

    /**
     * Flip whatever switch governs [type].
     *
     * S2130: the screen enumerates the catalog's disableable types, so it names a type and not a
     * method. A type with no switch is ignored rather than defaulted to one of the others, because
     * silently toggling the wrong row is worse than a tap that does nothing.
     */
    fun toggleType(type: WearContentType) {
        when (type) {
            WearContentType.MUSIC -> toggleAudio()
            WearContentType.VIDEO -> toggleVideo()
            WearContentType.IMAGE -> toggleImages()
            WearContentType.DOCUMENT -> toggleDocuments()
            else -> Unit
        }
    }

    fun toggleDocuments() {
        viewModelScope.launch {
            preferencesRepository.setDocumentsEnabled(!_uiState.value.isDocumentsEnabled)
        }
    }

    fun toggleSlideshow() {
        viewModelScope.launch {
            preferencesRepository.setSlideshowEnabled(!_uiState.value.isSlideshowEnabled)
        }
    }

    fun setSlideshowInterval(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setSlideshowIntervalSeconds(seconds)
        }
    }

    fun setPanelAutoHideSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setPanelAutoHideSeconds(seconds)
        }
    }

    fun setViewMode(mode: WearViewMode) {
        viewModelScope.launch {
            preferencesRepository.setViewMode(mode)
        }
    }

    fun setFileListViewMode(mode: WearViewMode) {
        viewModelScope.launch {
            preferencesRepository.setFileListViewMode(mode)
        }
    }

    fun toggleStreamsSection() {
        viewModelScope.launch {
            // S2511: through the use case, which also asks the system to redraw the sections tile - the
            // tile lists Streams exactly while this switch is on, and nothing else invalidates it.
            setStreamsSectionEnabled(!_uiState.value.streamsSectionEnabled)
        }
    }

    fun toggleDisableAnimations() {
        viewModelScope.launch {
            preferencesRepository.setAnimationsDisabled(!_uiState.value.isAnimationsDisabled)
        }
    }

    /**
     * S2536: the stepper works in charge percentages, so the enum is resolved from the percentage
     * here rather than in the screen - zero is the off end of that scale and maps to
     * [PowerSavingTrigger.OFF]. An unmatched percentage would mean the screen and the enum disagreed,
     * so it resolves to OFF rather than silently picking a neighbour.
     */
    fun setPowerSavingThreshold(percent: Int) {
        val trigger = PowerSavingTrigger.entries.firstOrNull { it.thresholdPercent == percent }
            ?: PowerSavingTrigger.OFF
        viewModelScope.launch {
            preferencesRepository.setPowerSavingTrigger(trigger)
        }
    }

    fun toggleKeepScreenAwakeOutsidePlayers() {
        viewModelScope.launch {
            preferencesRepository.setKeepScreenAwakeOutsidePlayers(
                !_uiState.value.keepScreenAwakeOutsidePlayers
            )
        }
    }

    fun toggleDimClockOverlayEnabled() {
        viewModelScope.launch {
            preferencesRepository.setDimClockOverlayEnabled(
                !_uiState.value.dimClockOverlayEnabled
            )
        }
    }

    /**
     * S2773: writes the OPPOSITE of the view in force, which on a watch that has never been switched
     * is the opposite of the build variant's own starting view. That is what makes the first tap do
     * something visible rather than store the value already in effect.
     */
    fun toggleGeometryMode() {
        viewModelScope.launch {
            val next = if (_uiState.value.geometryMode == WearGeometryMode.ORIGINAL) {
                WearGeometryMode.STORE
            } else {
                WearGeometryMode.ORIGINAL
            }
            preferencesRepository.setGeometryMode(next)
        }
    }

    fun toggleBackgroundPlayback() {
        viewModelScope.launch {
            preferencesRepository.setBackgroundPlaybackEnabled(
                !_uiState.value.backgroundPlaybackEnabled
            )
            _uiState.value = _uiState.value.copy(backgroundPlaybackNeedsNotifications = false)
        }
    }

    /**
     * S2166 (strategic criterion 9): a denied notification permission leaves the setting off and
     * says so, because the service's only control surface is its notification - enabling it anyway
     * would give the owner sound they cannot stop without reopening the app.
     */
    fun onBackgroundPlaybackPermissionResult(granted: Boolean) {
        if (granted) {
            toggleBackgroundPlayback()
            return
        }
        _uiState.value = _uiState.value.copy(backgroundPlaybackNeedsNotifications = true)
    }

    /**
     * Sends the watch log to the paired phone.
     *
     * A second press while [WearLogReportState.Sending] is ignored here as well as in the row, so an
     * impatient user cannot queue several identical reports even if the row's guard is ever lost.
     */
    fun sendLogReport() {
        if (_logReportState.value is WearLogReportState.Sending) {
            return
        }
        _logReportState.value = WearLogReportState.Sending
        viewModelScope.launch {
            _logReportState.value = WearLogReportState.Finished(logReportClient.send())
        }
    }

    /**
     * S2496: records what the watch's own browser did with the portal address.
     *
     * The screen owns the intent because only a Composable holds an Activity context, but it does not
     * own the verdict: a refusal used to end in a Timber line and nothing on screen, which is exactly
     * the silent press strategic section 1 calls the screen's second break.
     */
    fun onWatchPortalOpened(launched: Boolean) {
        _watchPortalState.value = if (launched) {
            WearPortalLinkState.Idle
        } else {
            WearPortalLinkState.Finished(WearPortalLinkOutcome.NO_WATCH_BROWSER)
        }
    }

    /**
     * S2496: asks the paired phone to open the portal.
     *
     * A second press while one request is in flight is refused for the same reason [sendLogReport]
     * refuses it - the two would carry the same address and the later could only overwrite the
     * outcome of the earlier.
     */
    fun openPortalOnPhone() {
        if (_phonePortalState.value is WearPortalLinkState.Busy) {
            return
        }
        _phonePortalState.value = WearPortalLinkState.Busy
        viewModelScope.launch {
            val outcome = openUrlOnPhoneRepository.openOnPhone(WearPortalLinks.WEB_PORTAL_URL)
            _phonePortalState.value = WearPortalLinkState.Finished(outcome.toLinkOutcome())
        }
    }

    fun toggleAlbumArt() {
        viewModelScope.launch {
            preferencesRepository.setDownloadAlbumArt(!_uiState.value.downloadAlbumArt)
        }
    }

    fun toggleAutoRotation() {
        viewModelScope.launch {
            preferencesRepository.setAutoRotationEnabled(!_uiState.value.isAutoRotationEnabled)
        }
    }

    /**
     * S2093 / ADR-3: the watch chooses the background mode, never the picture.
     */
    fun setBackgroundMode(mode: WearBackgroundMode) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundMode(mode)
        }
    }

    /** S2522: the colour scheme every screen is drawn in. */
    fun setColorScheme(scheme: WearColorScheme) {
        viewModelScope.launch {
            preferencesRepository.setColorScheme(scheme)
        }
    }

    /**
     * S2093 / ADR-1: sends this watch's whole set to the phone, which merges it field by field and
     * keeps whichever side changed each field later.
     *
     * A second press while one exchange is in flight is refused rather than queued: the two would
     * carry the same set and the later one could only overwrite the outcome of the earlier.
     */
    fun syncSettings() {
        if (_uiState.value.isSyncing) return
        _uiState.value = _uiState.value.copy(isSyncing = true)
        viewModelScope.launch {
            reportWearSettingsUseCase()
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }

    /**
     * S1862: a setter rather than a toggle. The setting is a choice between two named models, and a
     * `toggle` would have to invent which one "off" means - the very confusion section 6 item 1
     * refused when it asked for a setting that can actually stop the automatic path.
     */
    fun setVoiceNoteSendPolicy(policy: VoiceNoteSendPolicy) {
        viewModelScope.launch {
            preferencesRepository.setVoiceNoteSendPolicy(policy)
        }
    }
}

/** What the "send logs" action is doing right now. */
sealed interface WearLogReportState {

    /** Nothing sent yet in this screen visit. */
    data object Idle : WearLogReportState

    /** A report is on its way; the row shows progress and refuses a second press. */
    data object Sending : WearLogReportState

    /** The round trip ended, carrying the outcome the row turns into a message. */
    data class Finished(val outcome: WearLogReportOutcome) : WearLogReportState
}

/** S2496: what a portal link row is doing right now. */
sealed interface WearPortalLinkState {

    /** Nothing pressed yet, or the press succeeded and needs no words. */
    data object Idle : WearPortalLinkState

    /** A request is in flight; the row refuses a second press. */
    data object Busy : WearPortalLinkState

    /** The attempt ended, carrying the outcome the row turns into a message. */
    data class Finished(val outcome: WearPortalLinkOutcome) : WearPortalLinkState
}

/**
 * S2496: what a portal link row tells the user.
 *
 * One type for both rows even though the two failures arise on different sides: the row renders a
 * message and nothing else, so a second enum would only duplicate the rendering.
 */
enum class WearPortalLinkOutcome {

    /** The phone is showing the portal. */
    OPENED_ON_PHONE,

    /** The watch has no application willing to open an https address. */
    NO_WATCH_BROWSER,

    /** No phone was reachable over the bridge. */
    NO_CONNECTED_PHONE,

    /** A phone was reachable but did not take the address. */
    PHONE_FAILED
}

private fun WearOpenUrlOnPhoneOutcome.toLinkOutcome(): WearPortalLinkOutcome = when (this) {
    WearOpenUrlOnPhoneOutcome.OPENED -> WearPortalLinkOutcome.OPENED_ON_PHONE
    WearOpenUrlOnPhoneOutcome.NO_CONNECTED_PHONE -> WearPortalLinkOutcome.NO_CONNECTED_PHONE
    WearOpenUrlOnPhoneOutcome.FAILED -> WearPortalLinkOutcome.PHONE_FAILED
}
