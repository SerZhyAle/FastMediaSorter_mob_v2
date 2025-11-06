package com.sza.fastmediasorter_v2.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BrowseState(
    val resource: MediaResource? = null,
    val mediaFiles: List<MediaFile> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST
)

sealed class BrowseEvent {
    data class ShowError(val message: String) : BrowseEvent()
    data class ShowMessage(val message: String) : BrowseEvent()
    data class NavigateToPlayer(val filePath: String, val fileIndex: Int) : BrowseEvent()
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<BrowseState, BrowseEvent>() {

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId") 
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() 
        ?: 0L

    override fun getInitialState() = BrowseState()

    init {
        loadResource()
    }

    private fun loadResource() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val resource = getResourcesUseCase.getById(resourceId)
            if (resource == null) {
                sendEvent(BrowseEvent.ShowError("Resource not found"))
                setLoading(false)
                return@launch
            }
            
            updateState { 
                it.copy(
                    resource = resource,
                    sortMode = resource.sortMode,
                    displayMode = resource.displayMode
                ) 
            }
            
            loadMediaFiles()
        }
    }

    private fun loadMediaFiles() {
        val resource = state.value.resource ?: return
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            getMediaFilesUseCase(resource, state.value.sortMode)
                .catch { e ->
                    Timber.e(e, "Error loading media files")
                    handleError(e)
                }
                .collect { files ->
                    updateState { it.copy(mediaFiles = files) }
                    setLoading(false)
                }
        }
    }

    fun setSortMode(sortMode: SortMode) {
        updateState { it.copy(sortMode = sortMode) }
        loadMediaFiles()
    }

    fun toggleDisplayMode() {
        val newMode = if (state.value.displayMode == DisplayMode.LIST) {
            DisplayMode.GRID
        } else {
            DisplayMode.LIST
        }
        updateState { it.copy(displayMode = newMode) }
    }

    fun selectFile(filePath: String) {
        updateState { state ->
            val newSelected = state.selectedFiles.toMutableSet()
            if (filePath in newSelected) {
                newSelected.remove(filePath)
            } else {
                newSelected.add(filePath)
            }
            state.copy(selectedFiles = newSelected)
        }
    }

    fun clearSelection() {
        updateState { it.copy(selectedFiles = emptySet()) }
    }

    fun openFile(file: MediaFile) {
        val index = state.value.mediaFiles.indexOf(file)
        sendEvent(BrowseEvent.NavigateToPlayer(file.path, index))
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val selectedPaths = state.value.selectedFiles
            if (selectedPaths.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage("No files selected"))
                return@launch
            }
            
            // TODO: Реализовать удаление файлов
            Timber.d("Delete files: $selectedPaths")
            sendEvent(BrowseEvent.ShowMessage("Delete: ${selectedPaths.size} files"))
        }
    }
}
