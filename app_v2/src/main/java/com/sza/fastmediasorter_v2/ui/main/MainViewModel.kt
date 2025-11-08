package com.sza.fastmediasorter_v2.ui.main

import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter_v2.domain.usecase.DeleteResourceUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter_v2.domain.usecase.SizeFilter
import com.sza.fastmediasorter_v2.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MainState(
    val resources: List<MediaResource> = emptyList(),
    val selectedResource: MediaResource? = null,
    val sortMode: SortMode = SortMode.MANUAL,
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
    private val mediaScannerFactory: MediaScannerFactory,
    private val settingsRepository: SettingsRepository,
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
            SortMode.MANUAL -> filtered.sortedBy { it.displayOrder }
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
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val currentList = state.value.resources
            val currentIndex = currentList.indexOfFirst { it.id == resource.id }
            
            if (currentIndex > 0) {
                val previousResource = currentList[currentIndex - 1]
                
                // Swap display orders
                val updatedResource = resource.copy(displayOrder = previousResource.displayOrder)
                val updatedPrevious = previousResource.copy(displayOrder = resource.displayOrder)
                
                updateResourceUseCase(updatedResource)
                updateResourceUseCase(updatedPrevious)
                
                // Switch to manual sort mode to preserve user's ordering
                updateState { it.copy(sortMode = SortMode.MANUAL) }
                loadResources()
            }
        }
    }

    fun moveResourceDown(resource: MediaResource) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val currentList = state.value.resources
            val currentIndex = currentList.indexOfFirst { it.id == resource.id }
            
            if (currentIndex >= 0 && currentIndex < currentList.size - 1) {
                val nextResource = currentList[currentIndex + 1]
                
                // Swap display orders
                val updatedResource = resource.copy(displayOrder = nextResource.displayOrder)
                val updatedNext = nextResource.copy(displayOrder = resource.displayOrder)
                
                updateResourceUseCase(updatedResource)
                updateResourceUseCase(updatedNext)
                
                // Switch to manual sort mode to preserve user's ordering
                updateState { it.copy(sortMode = SortMode.MANUAL) }
                loadResources()
            }
        }
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
                // Get current settings for size filters
                val settings = settingsRepository.getSettings().first()
                val sizeFilter = SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )
                
                val resources = getResourcesUseCase().first()
                resources.forEach { resource ->
                    val scanner = mediaScannerFactory.getScanner(resource.type)
                    val fileCount = try {
                        scanner.getFileCount(resource.path, resource.supportedMediaTypes, sizeFilter)
                    } catch (e: Exception) {
                        Timber.e(e, "Error counting files for ${resource.name}")
                        resource.fileCount
                    }
                    
                    val isWritable = try {
                        scanner.isWritable(resource.path)
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
                    }
                }
                
                sendEvent(MainEvent.ShowMessage("Resources refreshed"))
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing resources")
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }
}
