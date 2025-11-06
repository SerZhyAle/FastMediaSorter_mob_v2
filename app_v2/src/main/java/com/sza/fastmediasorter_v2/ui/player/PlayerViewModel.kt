package com.sza.fastmediasorter_v2.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase
) : BaseViewModel<PlayerViewModel.PlayerState, PlayerViewModel.PlayerEvent>() {

    data class PlayerState(
        val files: List<MediaFile> = emptyList(),
        val currentIndex: Int = 0,
        val isSlideShowActive: Boolean = false,
        val slideShowInterval: Long = 3000,
        val showControls: Boolean = true,
        val isPaused: Boolean = false
    ) {
        val currentFile: MediaFile? get() = files.getOrNull(currentIndex)
        val hasPrevious: Boolean get() = currentIndex > 0
        val hasNext: Boolean get() = currentIndex < files.size - 1
    }

    sealed class PlayerEvent {
        data class ShowError(val message: String) : PlayerEvent()
        object FinishActivity : PlayerEvent()
    }

    override fun getInitialState(): PlayerState {
        return PlayerState(currentIndex = initialIndex)
    }

    private val resourceId = savedStateHandle.get<Long>("resourceId")
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() ?: 0L
    private val initialIndex = savedStateHandle.get<Int>("initialIndex")
        ?: savedStateHandle.get<String>("initialIndex")?.toIntOrNull() ?: 0

    init {
        loadMediaFiles()
    }

    private fun loadMediaFiles() {
        viewModelScope.launch {
            setLoading(true)
            try {
                val resource = getResourcesUseCase.getById(resourceId)
                if (resource == null) {
                    sendEvent(PlayerEvent.ShowError("Resource not found"))
                    sendEvent(PlayerEvent.FinishActivity)
                    setLoading(false)
                    return@launch
                }

                val files = getMediaFilesUseCase(resource).first()
                if (files.isEmpty()) {
                    sendEvent(PlayerEvent.ShowError("No media files found"))
                    sendEvent(PlayerEvent.FinishActivity)
                } else {
                    val safeIndex = initialIndex.coerceIn(0, files.size - 1)
                    updateState { it.copy(files = files, currentIndex = safeIndex) }
                }
                setLoading(false)
            } catch (e: Exception) {
                sendEvent(PlayerEvent.ShowError(e.message ?: "Failed to load media files"))
                sendEvent(PlayerEvent.FinishActivity)
                setLoading(false)
            }
        }
    }

    fun nextFile() {
        val currentState = state.value
        if (currentState.hasNext) {
            updateState { it.copy(currentIndex = it.currentIndex + 1) }
        }
    }

    fun previousFile() {
        val currentState = state.value
        if (currentState.hasPrevious) {
            updateState { it.copy(currentIndex = it.currentIndex - 1) }
        }
    }

    fun toggleSlideShow() {
        updateState { it.copy(isSlideShowActive = !it.isSlideShowActive) }
    }

    fun setSlideShowInterval(interval: Long) {
        updateState { it.copy(slideShowInterval = interval) }
    }

    fun toggleControls() {
        updateState { it.copy(showControls = !it.showControls) }
    }

    fun togglePause() {
        updateState { it.copy(isPaused = !it.isPaused) }
    }
}
