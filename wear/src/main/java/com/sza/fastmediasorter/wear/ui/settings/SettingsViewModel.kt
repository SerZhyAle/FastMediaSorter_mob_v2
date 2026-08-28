package com.sza.fastmediasorter.wear.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents
import com.sza.fastmediasorter.wear.data.wear.WearLogReportClient
import com.sza.fastmediasorter.wear.data.wear.WearLogReportOutcome
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ReportWearSettingsUseCase
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

/**
 * ViewModel for Settings screen.
 * Manages loading and updating of app settings.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository,
    private val logReportClient: WearLogReportClient,
    private val reportWearSettingsUseCase: ReportWearSettingsUseCase
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

    private fun loadSettings() {
        viewModelScope.launch {
            val hasAccelerometer = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
            // listOf is typed: the sources are Boolean, Int and WearViewMode, and letting the
            // compiler infer a reified intersection of those raises a warning that becomes an error
            // in a future Kotlin release.
            combine(
                listOf<Flow<Any>>(
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
                    preferencesRepository.isAnimationsDisabled
                )
            ) { values ->
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
                _uiState.value.copy(
                    backgroundMode = background,
                    lastSyncedAtEpochMillis = lastSync,
                    isAudioEnabled = audio,
                    isVideoEnabled = video,
                    isImagesEnabled = images,
                    isDocumentsEnabled = documents,
                    isSlideshowEnabled = slideshow,
                    slideshowIntervalSeconds = interval,
                    downloadAlbumArt = albumArt,
                    viewMode = viewMode,
                    streamsSectionEnabled = streamsSection,
                    keepScreenAwakeOutsidePlayers = keepAwake,
                    fileListViewMode = fileListView,
                    isAutoRotationEnabled = autoRotation,
                    hasAutoRotationSensor = hasAccelerometer,
                    voiceNoteSendPolicy = sendPolicy,
                    isAnimationsDisabled = disableAnimations,
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
            preferencesRepository.setStreamsSectionEnabled(!_uiState.value.streamsSectionEnabled)
        }
    }

    fun toggleDisableAnimations() {
        viewModelScope.launch {
            preferencesRepository.setAnimationsDisabled(!_uiState.value.isAnimationsDisabled)
        }
    }

    fun toggleKeepScreenAwakeOutsidePlayers() {
        viewModelScope.launch {
            preferencesRepository.setKeepScreenAwakeOutsidePlayers(
                !_uiState.value.keepScreenAwakeOutsidePlayers
            )
        }
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
        Timber.d("S2093: watch sync button pressed, lastSync=${_uiState.value.lastSyncedAtEpochMillis}")
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
