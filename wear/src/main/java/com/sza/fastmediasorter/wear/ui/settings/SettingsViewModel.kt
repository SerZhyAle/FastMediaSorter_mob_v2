package com.sza.fastmediasorter.wear.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Settings screen.
 * Manages loading and updating of app settings.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE.toString()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    fun reloadSettings() {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                listOf(
                    preferencesRepository.isAudioEnabled,
                    preferencesRepository.isVideoEnabled,
                    preferencesRepository.isImagesEnabled,
                    preferencesRepository.isSlideshowEnabled,
                    preferencesRepository.slideshowIntervalSeconds,
                    preferencesRepository.slideshowWaitForFinish,
                    preferencesRepository.downloadAlbumArt
                )
            ) { values ->
                val audio = values[0] as Boolean
                val video = values[1] as Boolean
                val images = values[2] as Boolean
                val slideshow = values[3] as Boolean
                val interval = values[4] as Int
                val waitForFinish = values[5] as Boolean
                val albumArt = values[6] as Boolean
                _uiState.value.copy(
                    isAudioEnabled = audio,
                    isVideoEnabled = video,
                    isImagesEnabled = images,
                    isSlideshowEnabled = slideshow,
                    slideshowIntervalSeconds = interval,
                    slideshowWaitForFinish = waitForFinish,
                    downloadAlbumArt = albumArt,
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

    fun toggleWaitForFinish() {
        viewModelScope.launch {
            preferencesRepository.setSlideshowWaitForFinish(!_uiState.value.slideshowWaitForFinish)
        }
    }

    fun toggleAlbumArt() {
        viewModelScope.launch {
            preferencesRepository.setDownloadAlbumArt(!_uiState.value.downloadAlbumArt)
        }
    }
}
