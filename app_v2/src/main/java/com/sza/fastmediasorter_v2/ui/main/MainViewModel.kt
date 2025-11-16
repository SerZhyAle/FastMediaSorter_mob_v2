package com.sza.fastmediasorter_v2.ui.main

import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
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
    data class ShowError(val message: String, val details: String? = null) : MainEvent()
    data class ShowInfo(val message: String, val details: String? = null) : MainEvent()
    data class ShowMessage(val message: String) : MainEvent()
    data class NavigateToBrowse(val resourceId: Long, val skipAvailabilityCheck: Boolean = false) : MainEvent()
    data class NavigateToEditResource(val resourceId: Long) : MainEvent()
    object NavigateToAddResource : MainEvent()
    object NavigateToSettings : MainEvent()
    data class ScanProgress(val scannedCount: Int, val currentFile: String?) : MainEvent()
    object ScanComplete : MainEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val mediaScannerFactory: MediaScannerFactory,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<MainState, MainEvent>() {

    override fun getInitialState() = MainState()

    init {
        observeResourcesFromDatabase()
    }

    private fun observeResourcesFromDatabase() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            getResourcesUseCase()
                .catch { e ->
                    Timber.e(e, "Error observing resources from database")
                    handleError(e)
                }
                .collect { allResources ->
                    // Apply current filters and sorting
                    val filteredResources = applyFiltersAndSorting(allResources)
                    updateState { it.copy(resources = filteredResources) }
                }
        }
    }

    private fun applyFiltersAndSorting(resources: List<MediaResource>): List<MediaResource> {
        var filtered = resources
        
        // Apply type filter
        state.value.filterByType?.let { types ->
            filtered = filtered.filter { types.contains(it.type) }
        }
        
        // Apply media type filter
        state.value.filterByMediaType?.let { mediaTypes ->
            filtered = filtered.filter { resource ->
                resource.supportedMediaTypes.any { mediaTypes.contains(it) }
            }
        }
        
        // Apply name filter
        state.value.filterByName?.let { nameFilter ->
            if (nameFilter.isNotBlank()) {
                filtered = filtered.filter { it.name.contains(nameFilter, ignoreCase = true) }
            }
        }
        
        // Apply sorting
        filtered = when (state.value.sortMode) {
            SortMode.MANUAL -> filtered.sortedBy { it.displayOrder }
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> filtered.sortedBy { it.createdDate }
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.createdDate }
            else -> filtered.sortedBy { it.displayOrder }
        }
        
        return filtered
    }

    private fun loadResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            try {
                // Use DB-level filtering and sorting for better performance
                val resources = getResourcesUseCase.getFiltered(
                    filterByType = state.value.filterByType,
                    filterByMediaType = state.value.filterByMediaType,
                    filterByName = state.value.filterByName,
                    sortMode = state.value.sortMode
                )
                updateState { it.copy(resources = resources) }
            } catch (e: Exception) {
                Timber.e(e, "Error loading resources")
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun selectResource(resource: MediaResource) {
        updateState { it.copy(selectedResource = resource) }
    }

    fun startPlayer() {
        val resource = state.value.selectedResource
        if (resource != null && resource.id != 0L) {
            viewModelScope.launch(ioDispatcher) {
                validateAndOpenResource(resource)
            }
        }
    }
    
    private suspend fun validateAndOpenResource(resource: MediaResource) {
        val settings = settingsRepository.getSettings().first()
        val showDetails = settings.showDetailedErrors
        
        // For local resources, check file count
        if (resource.type == ResourceType.LOCAL && resource.fileCount == 0) {
            sendEvent(MainEvent.ShowInfo(
                message = "No files found in '${resource.name}'",
                details = if (showDetails) {
                    "Resource: ${resource.name}\nType: ${resource.type}\nPath: ${resource.path}\n\nFile count: 0\n\nPlease check if the folder still exists and contains media files."
                } else null
            ))
            return
        }
        
        // For network resources (SMB, SFTP, FTP, CLOUD), test connection
        val isNetworkResource = resource.type in setOf(
            ResourceType.SMB,
            ResourceType.SFTP,
            ResourceType.FTP,
            ResourceType.CLOUD
        )
        
        if (isNetworkResource) {
            // Test connection before opening
            try {
                val testResult = resourceRepository.testConnection(resource)
                testResult.fold(
                    onSuccess = { message ->
                        Timber.d("Connection test OK: $message - opening Browse")
                        // Connection OK - open resource (even if empty for network)
                        sendEvent(MainEvent.NavigateToBrowse(resource.id, skipAvailabilityCheck = true))
                    },
                    onFailure = { error ->
                        Timber.e(error, "Connection test failed for ${resource.name}")
                        sendEvent(MainEvent.ShowError(
                            message = "Failed to connect to '${resource.name}'",
                            details = if (showDetails) {
                                "Resource: ${resource.name} (${resource.type})\nPath: ${resource.path}\n\nConnection error:\n${error.message ?: "Unknown error"}\n\nStack trace:\n${error.stackTraceToString()}"
                            } else null
                        ))
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception testing connection for ${resource.name}")
                sendEvent(MainEvent.ShowError(
                    message = "Failed to check resource '${resource.name}'",
                    details = if (showDetails) {
                        "Resource: ${resource.name} (${resource.type})\nPath: ${resource.path}\n\nException:\n${e.message ?: "Unknown error"}\n\nStack trace:\n${e.stackTraceToString()}"
                    } else null
                ))
            }
        } else {
            // Local resource with files - open directly
            sendEvent(MainEvent.NavigateToBrowse(resource.id, skipAvailabilityCheck = false))
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
                // Reload resources list to update UI
                loadResources()
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
                
                // Atomically swap display orders in single transaction
                resourceRepository.swapResourceDisplayOrders(resource, previousResource)
                
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
                
                // Atomically swap display orders in single transaction
                resourceRepository.swapResourceDisplayOrders(resource, nextResource)
                
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
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            try {
                // Create a copy with a new name
                val copyName = generateCopyName(selected.name)
                val copy = selected.copy(
                    id = 0, // New resource will get a new ID
                    name = copyName,
                    isDestination = false, // Reset destination flag
                    destinationOrder = null, // Reset destination order
                    destinationColor = 0xFF4CAF50.toInt() // Reset to default green
                )
                
                Timber.d("Copying resource: ${selected.name} -> $copyName")
                val result = addResourceUseCase(copy)
                
                result.onSuccess { newResourceId ->
                    Timber.d("Resource copied successfully with ID: $newResourceId")
                    // Navigate to edit the copied resource
                    sendEvent(MainEvent.NavigateToEditResource(newResourceId))
                    loadResources()
                }.onFailure { error ->
                    Timber.e(error, "Failed to copy resource: ${selected.name}")
                    sendEvent(MainEvent.ShowError("Failed to copy resource: ${error.message}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy resource: ${selected.name}")
                sendEvent(MainEvent.ShowError("Failed to copy resource: ${e.message}"))
            }
        }
    }
    
    /**
     * Generate a unique copy name by appending " (copy)" or " (copy N)"
     */
    private fun generateCopyName(originalName: String): String {
        val resources = state.value.resources
        val existingNames = resources.map { it.name }.toSet()
        
        // Try "Name (copy)" first
        var copyName = "$originalName (copy)"
        if (!existingNames.contains(copyName)) {
            return copyName
        }
        
        // If it exists, try "Name (copy 2)", "Name (copy 3)", etc.
        var counter = 2
        while (existingNames.contains("$originalName (copy $counter)")) {
            counter++
        }
        
        return "$originalName (copy $counter)"
    }
    
    /**
     * Refresh resources list from database (fast)
     */
    fun refreshResources() {
        Timber.d("Refreshing resources from database")
        loadResources()
    }
    
    /**
     * Quick check all resources: test availability and check write access.
     * Does NOT count files - only checks connectivity and permissions for UI status indicators.
     * File count is updated only when opening resource in BrowseActivity.
     */
    fun scanAllResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            try {
                val resources = getResourcesUseCase().first()
                var unavailableCount = 0
                var writableCount = 0
                var readOnlyCount = 0
                
                resources.forEach { resource ->
                    try {
                        // Test connection/availability
                        val testResult = resourceRepository.testConnection(resource)
                        
                        testResult.fold(
                            onSuccess = {
                                Timber.d("Resource available: ${resource.name}")
                                
                                val scanner = mediaScannerFactory.getScanner(resource.type)
                                
                                // Check write permission (fast)
                                val isWritable = try {
                                    scanner.isWritable(resource.path, resource.credentialsId)
                                } catch (e: Exception) {
                                    Timber.e(e, "Error checking write access for ${resource.name}")
                                    resource.isWritable
                                }
                                
                                if (isWritable) writableCount++ else readOnlyCount++
                                
                                // Update resource only if write permission changed
                                if (isWritable != resource.isWritable) {
                                    val updatedResource = resource.copy(isWritable = isWritable)
                                    updateResourceUseCase(updatedResource)
                                }
                            },
                            onFailure = { error ->
                                Timber.w("Resource unavailable: ${resource.name} - ${error.message}")
                                unavailableCount++
                            }
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Resource check failed: ${resource.name}")
                        unavailableCount++
                    }
                }
                
                val totalResources = resources.size
                val availableCount = totalResources - unavailableCount
                
                val message = when {
                    unavailableCount == 0 -> "All resources available: $writableCount writable, $readOnlyCount read-only"
                    unavailableCount == totalResources -> "All resources unavailable"
                    else -> "Resources checked: $availableCount available ($unavailableCount unavailable)"
                }
                sendEvent(MainEvent.ShowMessage(message))
            } catch (e: Exception) {
                Timber.e(e, "Error scanning resources")
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }
}
