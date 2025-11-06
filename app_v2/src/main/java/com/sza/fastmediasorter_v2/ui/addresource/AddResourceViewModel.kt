package com.sza.fastmediasorter_v2.ui.addresource

import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter_v2.domain.usecase.ScanLocalFoldersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AddResourceState(
    val resourcesToAdd: List<MediaResource> = emptyList(),
    val isScanning: Boolean = false
)

sealed class AddResourceEvent {
    data class ShowError(val message: String) : AddResourceEvent()
    data class ShowMessage(val message: String) : AddResourceEvent()
    object ResourcesAdded : AddResourceEvent()
}

@HiltViewModel
class AddResourceViewModel @Inject constructor(
    private val scanLocalFoldersUseCase: ScanLocalFoldersUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<AddResourceState, AddResourceEvent>() {

    override fun getInitialState() = AddResourceState()

    fun scanLocalFolders() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateState { it.copy(isScanning = true) }
            setLoading(true)
            
            scanLocalFoldersUseCase().onSuccess { resources ->
                updateState { 
                    it.copy(
                        resourcesToAdd = resources,
                        isScanning = false
                    ) 
                }
                sendEvent(AddResourceEvent.ShowMessage("Found ${resources.size} folders"))
            }.onFailure { e ->
                Timber.e(e, "Error scanning local folders")
                handleError(e)
                updateState { it.copy(isScanning = false) }
            }
            
            setLoading(false)
        }
    }

    fun toggleResourceSelection(resource: MediaResource, selected: Boolean) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) {
                    r.copy(id = if (selected) 1 else 0) // Используем id для отметки выбранных
                } else r
            }
            state.copy(resourcesToAdd = updated)
        }
    }

    fun updateResourceName(resource: MediaResource, newName: String) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) {
                    r.copy(name = newName)
                } else r
            }
            state.copy(resourcesToAdd = updated)
        }
    }

    fun toggleDestination(resource: MediaResource, isDestination: Boolean) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) {
                    r.copy(isDestination = isDestination)
                } else r
            }
            state.copy(resourcesToAdd = updated)
        }
    }

    fun addSelectedResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val selectedResources = state.value.resourcesToAdd.filter { it.id > 0 }
            if (selectedResources.isEmpty()) {
                sendEvent(AddResourceEvent.ShowMessage("No resources selected"))
                setLoading(false)
                return@launch
            }
            
            addResourceUseCase.addMultiple(selectedResources).onSuccess { ids ->
                Timber.d("Added ${ids.size} resources")
                sendEvent(AddResourceEvent.ShowMessage("Added ${ids.size} resources"))
                sendEvent(AddResourceEvent.ResourcesAdded)
            }.onFailure { e ->
                Timber.e(e, "Error adding resources")
                handleError(e)
            }
            
            setLoading(false)
        }
    }

    fun addManualResource(resource: MediaResource) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            addResourceUseCase(resource).onSuccess { id ->
                Timber.d("Added resource with id: $id")
                sendEvent(AddResourceEvent.ShowMessage("Resource added"))
                sendEvent(AddResourceEvent.ResourcesAdded)
            }.onFailure { e ->
                Timber.e(e, "Error adding resource")
                handleError(e)
            }
            
            setLoading(false)
        }
    }
}
