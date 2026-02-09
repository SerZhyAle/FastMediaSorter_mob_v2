package com.sza.fastmediasorter.wear.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    }

    private fun loadSettings() {
        viewModelScope.launch {
            preferencesRepository.isAudioEnabled.collect { audio ->
                _uiState.value = _uiState.value.copy(isAudioEnabled = audio, isLoading = false)
            }
        }
        viewModelScope.launch {
            preferencesRepository.isVideoEnabled.collect { video ->
                _uiState.value = _uiState.value.copy(isVideoEnabled = video)
            }
        }
        viewModelScope.launch {
            preferencesRepository.isImagesEnabled.collect { images ->
                _uiState.value = _uiState.value.copy(isImagesEnabled = images)
            }
        }
        viewModelScope.launch {
            preferencesRepository.isSlideshowEnabled.collect { slideshow ->
                _uiState.value = _uiState.value.copy(isSlideshowEnabled = slideshow)
            }
        }
        viewModelScope.launch {
            preferencesRepository.slideshowIntervalSeconds.collect { interval ->
                _uiState.value = _uiState.value.copy(slideshowIntervalSeconds = interval)
            }
        }
        viewModelScope.launch {
            preferencesRepository.slideshowWaitForFinish.collect { wait ->
                _uiState.value = _uiState.value.copy(slideshowWaitForFinish = wait)
            }
        }
        viewModelScope.launch {
            preferencesRepository.downloadAlbumArt.collect { albumArt ->
                _uiState.value = _uiState.value.copy(downloadAlbumArt = albumArt)
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
