package com.sza.fastmediasorter_v2.ui.addresource

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter_v2.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter_v2.domain.usecase.ScanLocalFoldersUseCase
import com.sza.fastmediasorter_v2.domain.usecase.SmbOperationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AddResourceState(
    val resourcesToAdd: List<MediaResource> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
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
    private val mediaScannerFactory: MediaScannerFactory,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<AddResourceState, AddResourceEvent>() {

    override fun getInitialState() = AddResourceState()
    
    /**
     * Get supported media types based on current settings
     */
    private suspend fun getSupportedMediaTypes(): Set<MediaType> {
        val settings = settingsRepository.getSettings().first()
        val types = mutableSetOf<MediaType>()
        
        if (settings.supportImages) types.add(MediaType.IMAGE)
        if (settings.supportVideos) types.add(MediaType.VIDEO)
        if (settings.supportAudio) types.add(MediaType.AUDIO)
        if (settings.supportGifs) types.add(MediaType.GIF)
        
        return types
    }

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
            val newSelectedPaths = if (selected) {
                state.selectedPaths + resource.path
            } else {
                state.selectedPaths - resource.path
            }
            state.copy(selectedPaths = newSelectedPaths)
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
            
            val currentState = state.value
            val selectedResources = currentState.resourcesToAdd.filter { 
                it.path in currentState.selectedPaths 
            }.map { it.copy(id = 0) } // Ensure id=0 for autoincrement
            
            if (selectedResources.isEmpty()) {
                sendEvent(AddResourceEvent.ShowMessage("No resources selected"))
                setLoading(false)
                return@launch
            }
            
            val result = addResourceUseCase.addMultiple(selectedResources)
            result.onSuccess { addResult ->
                Timber.d("Added ${addResult.addedCount} resources")
                
                if (addResult.destinationsFull) {
                    sendEvent(AddResourceEvent.ShowMessage(
                        "Added ${addResult.addedCount} resources. " +
                        "Destinations are full (max 10). ${addResult.skippedDestinations} resources added without destination flag."
                    ))
                } else {
                    sendEvent(AddResourceEvent.ShowMessage("Added ${addResult.addedCount} resources"))
                }
                
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
    
    fun addManualFolder(uri: Uri) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            try {
                val path = uri.path ?: ""
                val name = uri.lastPathSegment ?: "Unknown"
                
                val supportedTypes = getSupportedMediaTypes()
                
                val scanner = mediaScannerFactory.getScanner(ResourceType.LOCAL)
                val fileCount = try {
                    scanner.getFileCount(path, supportedTypes)
                } catch (e: Exception) {
                    Timber.e(e, "Error counting files in $path")
                    0
                }
                
                val isWritable = try {
                    scanner.isWritable(path)
                } catch (e: Exception) {
                    Timber.e(e, "Error checking write access for $path")
                    false
                }
                
                val settings = settingsRepository.getSettings().first()
                
                val resource = MediaResource(
                    id = 1,
                    name = name,
                    path = path,
                    type = ResourceType.LOCAL,
                    supportedMediaTypes = supportedTypes,
                    createdDate = System.currentTimeMillis(),
                    fileCount = fileCount,
                    isDestination = false,
                    destinationOrder = null,
                    isWritable = isWritable,
                    slideshowInterval = settings.slideshowInterval
                )
                
                updateState { state ->
                    state.copy(resourcesToAdd = state.resourcesToAdd + resource)
                }
                
                sendEvent(AddResourceEvent.ShowMessage("Folder added to list"))
            } catch (e: Exception) {
                Timber.e(e, "Error adding manual folder")
                handleError(e)
            }
            
            setLoading(false)
        }
    }
    
    // ==================== SMB Network Operations ====================
    
    /**
     * Test SMB connection with provided credentials
     */
    fun testSmbConnection(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            smbOperationsUseCase.testConnection(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { message ->
                Timber.d("SMB connection test successful: $message")
                sendEvent(AddResourceEvent.ShowMessage("Connection successful: $message"))
            }.onFailure { e ->
                Timber.e(e, "SMB connection test failed")
                sendEvent(AddResourceEvent.ShowError("Connection failed: ${e.message}"))
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Scan SMB server for available shares
     */
    fun scanSmbShares(
        server: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateState { it.copy(isScanning = true) }
            setLoading(true)
            
            smbOperationsUseCase.listShares(
                server = server,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { shares ->
                Timber.d("Found ${shares.size} SMB shares: $shares")
                
                val supportedTypes = getSupportedMediaTypes()
                val settings = settingsRepository.getSettings().first()
                
                // Create MediaResource for each share
                val resources = shares.map { shareName ->
                    MediaResource(
                        id = 0,
                        name = "$server\\$shareName",
                        path = "smb://$server/$shareName",
                        type = ResourceType.SMB,
                        supportedMediaTypes = supportedTypes,
                        createdDate = System.currentTimeMillis(),
                        fileCount = 0, // Will be determined when scanning
                        isDestination = false,
                        destinationOrder = null,
                        isWritable = true, // Assume writable, will verify on file operations
                        slideshowInterval = settings.slideshowInterval
                    )
                }
                
                updateState { 
                    it.copy(
                        resourcesToAdd = it.resourcesToAdd + resources,
                        isScanning = false
                    ) 
                }
                
                sendEvent(AddResourceEvent.ShowMessage("Found ${shares.size} shares"))
            }.onFailure { e ->
                Timber.e(e, "Failed to scan SMB shares")
                sendEvent(AddResourceEvent.ShowError("Scan failed: ${e.message}"))
                updateState { it.copy(isScanning = false) }
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Add SMB resources with credentials to database
     */
    fun addSmbResources(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val currentState = state.value
            val selectedResources = currentState.resourcesToAdd.filter { 
                it.path in currentState.selectedPaths && it.type == ResourceType.SMB
            }
            
            if (selectedResources.isEmpty()) {
                sendEvent(AddResourceEvent.ShowMessage("No SMB resources selected"))
                setLoading(false)
                return@launch
            }
            
            // Save credentials first
            smbOperationsUseCase.saveCredentials(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { credentialsId ->
                Timber.d("Saved SMB credentials with ID: $credentialsId")
                
                // Update resources with credentials ID
                val resourcesWithCredentials = selectedResources.map { resource ->
                    resource.copy(
                        id = 0, // Ensure autoincrement
                        credentialsId = credentialsId
                    )
                }
                
                // Add resources to database
                addResourceUseCase.addMultiple(resourcesWithCredentials).onSuccess { addResult ->
                    Timber.d("Added ${addResult.addedCount} SMB resources")
                    
                    if (addResult.destinationsFull) {
                        sendEvent(AddResourceEvent.ShowMessage(
                            "Added ${addResult.addedCount} SMB resources. " +
                            "Destinations are full (max 10). ${addResult.skippedDestinations} resources added without destination flag."
                        ))
                    } else {
                        sendEvent(AddResourceEvent.ShowMessage("Added ${addResult.addedCount} SMB resources"))
                    }
                    
                    sendEvent(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SMB resources")
                    sendEvent(AddResourceEvent.ShowError("Failed to add resources: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SMB credentials")
                sendEvent(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }
            
            setLoading(false)
        }
    }
}
