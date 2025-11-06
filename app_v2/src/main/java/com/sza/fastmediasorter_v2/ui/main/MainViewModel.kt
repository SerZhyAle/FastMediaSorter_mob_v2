package com.sza.fastmediasorter_v2.ui.main

import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter_v2.domain.usecase.DeleteResourceUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.MediaScanner
import com.sza.fastmediasorter_v2.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MainState(
    val resources: List<MediaResource> = emptyList(),
    val selectedResource: MediaResource? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val filterByType: Set<ResourceType>? = null,
    val filterByMediaType: Set<MediaType>? = null,
    val filterByName: String? = null
)

sealed class MainEvent {
    data class ShowError(val message: String) : MainEvent()
    data class ShowMessage(val message: String) : MainEvent()
    data class NavigateToBrowse(val resourceId: Long) : MainEvent()
    object NavigateToAddResource : MainEvent()
    object NavigateToSettings : MainEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val mediaScanner: MediaScanner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<MainState, MainEvent>() {

    override fun getInitialState() = MainState()

    init {
        loadResources()
    }

    private fun loadResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            getResourcesUseCase()
                .catch { e ->
                    Timber.e(e, "Error loading resources")
                    handleError(e)
                }
                .collect { resources ->
                    updateState { it.copy(resources = applyFiltersAndSort(resources)) }
                    setLoading(false)
                }
        }
    }

    private fun applyFiltersAndSort(resources: List<MediaResource>): List<MediaResource> {
        var filtered = resources
        
        // Filter by resource type
        state.value.filterByType?.let { types ->
            filtered = filtered.filter { it.type in types }
        }
        
        // Filter by supported media types
        state.value.filterByMediaType?.let { mediaTypes ->
            filtered = filtered.filter { resource ->
                mediaTypes.any { mediaType ->
                    resource.supportedMediaTypes.contains(mediaType)
                }
            }
        }
        
        // Filter by name substring
        state.value.filterByName?.let { name ->
            if (name.isNotBlank()) {
                filtered = filtered.filter {
                    it.name.contains(name, ignoreCase = true) ||
                    it.path.contains(name, ignoreCase = true)
                }
            }
        }
        
        // Sort
        return when (state.value.sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> filtered.sortedBy { it.createdDate }
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.createdDate }
            SortMode.SIZE_ASC -> filtered.sortedBy { it.fileCount }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { it.fileCount }
            else -> filtered
        }
    }

    fun selectResource(resource: MediaResource) {
        updateState { it.copy(selectedResource = resource) }
    }

    fun startPlayer() {
        val resourceId = state.value.selectedResource?.id
        if (resourceId != null && resourceId != 0L) {
            sendEvent(MainEvent.NavigateToBrowse(resourceId))
        }
    }

    fun addResource() {
        sendEvent(MainEvent.NavigateToAddResource)
    }

    fun deleteResource(resource: MediaResource) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            deleteResourceUseCase(resource.id).onSuccess {
                Timber.d("Resource deleted: ${resource.name}")
                sendEvent(MainEvent.ShowMessage("Resource deleted"))
                if (state.value.selectedResource?.id == resource.id) {
                    updateState { it.copy(selectedResource = null) }
                }
            }.onFailure { e ->
                Timber.e(e, "Error deleting resource")
                handleError(e)
            }
            setLoading(false)
        }
    }

    fun moveResourceUp(resource: MediaResource) {
        // TODO: Реализовать изменение порядка в БД
        Timber.d("Move up: ${resource.name}")
    }

    fun moveResourceDown(resource: MediaResource) {
        // TODO: Реализовать изменение порядка в БД
        Timber.d("Move down: ${resource.name}")
    }

    fun setSortMode(sortMode: SortMode) {
        updateState { it.copy(sortMode = sortMode) }
        loadResources()
    }

    fun setFilterByType(types: Set<ResourceType>?) {
        updateState { it.copy(filterByType = types) }
        loadResources()
    }

    fun setFilterByMediaType(mediaTypes: Set<MediaType>?) {
        updateState { it.copy(filterByMediaType = mediaTypes) }
        loadResources()
    }

    fun setFilterByName(name: String?) {
        updateState { it.copy(filterByName = name) }
        loadResources()
    }

    fun clearFilters() {
        updateState { 
            it.copy(
                filterByType = null,
                filterByMediaType = null,
                filterByName = null
            ) 
        }
        loadResources()
    }
    
    fun copySelectedResource() {
        val selected = state.value.selectedResource
        if (selected == null) {
            sendEvent(MainEvent.ShowMessage("Please select a resource to copy"))
            return
        }
        
        // TODO: Открыть диалог/экран для редактирования копии ресурса
        Timber.d("Copy resource: ${selected.name}")
        sendEvent(MainEvent.ShowMessage("Resource copy not yet implemented"))
    }
    
    fun refreshResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            try {
                getResourcesUseCase()
                    .catch { e ->
                        Timber.e(e, "Error refreshing resources")
                        handleError(e)
                    }
                    .collect { resources ->
                        val updated = resources.map { resource ->
                            val fileCount = try {
                                mediaScanner.getFileCount(resource.path, resource.supportedMediaTypes)
                            } catch (e: Exception) {
                                Timber.e(e, "Error counting files for ${resource.name}")
                                resource.fileCount
                            }
                            
                            val isWritable = try {
                                mediaScanner.isWritable(resource.path)
                            } catch (e: Exception) {
                                Timber.e(e, "Error checking write access for ${resource.name}")
                                resource.isWritable
                            }
                            
                            if (fileCount != resource.fileCount || isWritable != resource.isWritable) {
                                val updatedResource = resource.copy(
                                    fileCount = fileCount,
                                    isWritable = isWritable
                                )
                                updateResourceUseCase(updatedResource)
                                updatedResource
                            } else {
                                resource
                            }
                        }
                        
                        sendEvent(MainEvent.ShowMessage("Resources refreshed"))
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing resources")
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }
}
