package com.sza.fastmediasorter.ui.editresource

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

data class EditResourceState(
    val originalResource: MediaResource? = null,
    val currentResource: MediaResource? = null,
    val hasChanges: Boolean = false,
    val hasResourceChanges: Boolean = false, // Changes to resource properties (name, isDestination, etc.)
    val smbServer: String = "",
    val smbShareName: String = "",
    val smbUsername: String = "",
    val smbPassword: String = "",
    val smbDomain: String = "",
    val smbPort: Int = 445,
    val hasSmbCredentialsChanges: Boolean = false,
    // SFTP credentials
    val sftpHost: String = "",
    val sftpPort: Int = 22,
    val sftpUsername: String = "",
    val sftpPassword: String = "",
    val sftpPath: String = "/",
    val hasSftpCredentialsChanges: Boolean = false,
    // Trash folders
    val hasTrashFolders: Boolean = false,
    val trashFolderCount: Int = 0,
    // Scan subdirectories
    val scanSubdirectories: Boolean = true
)

sealed class EditResourceEvent {
    data class ShowError(val message: String) : EditResourceEvent()
    data class ShowMessage(val message: String) : EditResourceEvent()
    object ResourceUpdated : EditResourceEvent()
    data class TestResult(val success: Boolean, val message: String) : EditResourceEvent()
    data class ConfirmClearTrash(val count: Int) : EditResourceEvent()
    data class TrashCleared(val count: Int) : EditResourceEvent()
}

@HiltViewModel
class EditResourceViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val mediaScannerFactory: com.sza.fastmediasorter.domain.usecase.MediaScannerFactory,
    private val smbClient: com.sza.fastmediasorter.data.network.SmbClient,
    savedStateHandle: SavedStateHandle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<EditResourceState, EditResourceEvent>() {

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId") 
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() 
        ?: 0L

    override fun getInitialState() = EditResourceState()

    init {
        loadResource()
    }

    private fun loadResource() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val resource = getResourcesUseCase.getById(resourceId)
            if (resource == null) {
                sendEvent(EditResourceEvent.ShowError("Resource not found"))
                setLoading(false)
                return@launch
            }
            
            Timber.d("EditResourceViewModel.loadResource: id=${resource.id}, name=${resource.name}, lastBrowseDate=${resource.lastBrowseDate}")
            
            // Reset SMB client before loading SMB resource to ensure fresh connection
            if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB) {
                Timber.d("Resetting SMB client before editing resource: ${resource.name}")
                smbClient.resetClients()
            }
            
            updateState { 
                it.copy(
                    originalResource = resource,
                    currentResource = resource
                ) 
            }
            
            // Load SMB credentials if resource is SMB type
            if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && resource.credentialsId != null) {
                loadSmbCredentials(resource.credentialsId)
            }
            
            // Load SFTP credentials if resource is SFTP type
            if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP && resource.credentialsId != null) {
                loadSftpCredentials(resource.credentialsId)
            }
            
            // Check for trash folders
            checkTrashFolders()
            
            setLoading(false)
        }
    }
    
    private suspend fun loadSmbCredentials(credentialsId: String) {
        smbOperationsUseCase.getConnectionInfo(credentialsId).onSuccess { connectionInfo ->
            Timber.d("Loaded SMB credentials for resource")
            updateState { state ->
                state.copy(
                    smbServer = connectionInfo.server,
                    smbShareName = connectionInfo.shareName,
                    smbUsername = connectionInfo.username,
                    smbPassword = connectionInfo.password,
                    smbDomain = connectionInfo.domain,
                    smbPort = connectionInfo.port
                )
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to load SMB credentials")
        }
    }
    
    private suspend fun loadSftpCredentials(credentialsId: String) {
        smbOperationsUseCase.getSftpCredentials(credentialsId).onSuccess { credentials ->
            Timber.d("Loaded SFTP credentials for resource")
            updateState { state ->
                state.copy(
                    sftpHost = credentials.server,
                    sftpPort = credentials.port,
                    sftpUsername = credentials.username,
                    sftpPassword = credentials.password,
                    sftpPath = "/" // Default, actual path stored in resource.path
                )
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to load SFTP credentials")
        }
    }

    fun updateName(name: String) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(name = name)
        updateCurrentResource(updated)
    }

    fun updateSlideshowInterval(interval: Int) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(slideshowInterval = interval)
        updateCurrentResource(updated)
    }

    fun updateSupportedMediaTypes(types: Set<MediaType>) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(supportedMediaTypes = types)
        updateCurrentResource(updated)
    }

    fun updateIsDestination(isDestination: Boolean) {
        val current = state.value.currentResource ?: return
        
        // Exit early if no actual change (prevents unnecessary updates)
        if (current.isDestination == isDestination) {
            Timber.d("updateIsDestination: no change, already $isDestination")
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            if (isDestination && current.destinationOrder == null) {
                // Check if resource is writable before adding to destinations
                if (!current.isWritable) {
                    sendEvent(EditResourceEvent.ShowError(
                        "Cannot set as destination: resource is not writable (read-only). " +
                        "Only writable resources can be destinations."
                    ))
                    return@launch
                }
                
                // Need to assign destinationOrder - check if destinations are full
                val allResources = getResourcesUseCase().first()
                val currentDestinations = allResources.filter { res -> res.isDestination }
                val maxDestinations = 10
                
                if (currentDestinations.size >= maxDestinations) {
                    sendEvent(EditResourceEvent.ShowError(
                        "Cannot add to destinations: maximum $maxDestinations destinations allowed. " +
                        "Remove a destination first."
                    ))
                    return@launch
                }
                
                // Find next available destination order (0-9)
                val usedOrders = currentDestinations.mapNotNull { it.destinationOrder }.toSet()
                val nextOrder = (0 until 10).firstOrNull { it !in usedOrders } ?: -1
                
                if (nextOrder == -1) {
                    sendEvent(EditResourceEvent.ShowError(
                        "Cannot add to destinations: no available order slots. " +
                        "Remove a destination first."
                    ))
                    return@launch
                }
                
                val color = com.sza.fastmediasorter.core.util.DestinationColors.getColorForDestination(nextOrder)
                
                val updated = current.copy(
                    isDestination = true,
                    destinationOrder = nextOrder,
                    destinationColor = color
                )
                updateCurrentResource(updated)
                Timber.d("Added to destinations with order $nextOrder")
            } else if (isDestination && current.destinationOrder != null) {
                // Already has destinationOrder - validate it's in valid range (0-9)
                val currentOrder = current.destinationOrder
                if (currentOrder !in 0..9) {
                    // Invalid order - need to reassign
                    Timber.w("Destination order $currentOrder is out of range 0-9, reassigning")
                    
                    val allResources = getResourcesUseCase().first()
                    val usedOrders = allResources
                        .filter { res -> res.isDestination && res.id != current.id }
                        .mapNotNull { it.destinationOrder }
                        .toSet()
                    
                    val nextOrder = (0 until 10).firstOrNull { it !in usedOrders } ?: -1
                    
                    if (nextOrder == -1) {
                        sendEvent(EditResourceEvent.ShowError(
                            "Cannot fix destination order: no available slots. Remove a destination first."
                        ))
                        return@launch
                    }
                    
                    val color = com.sza.fastmediasorter.core.util.DestinationColors.getColorForDestination(nextOrder)
                    val updated = current.copy(
                        isDestination = true,
                        destinationOrder = nextOrder,
                        destinationColor = color
                    )
                    updateCurrentResource(updated)
                    Timber.d("Fixed destination order from $currentOrder to $nextOrder")
                } else {
                    // Valid order, just ensure flag is set
                    val updated = current.copy(isDestination = true)
                    updateCurrentResource(updated)
                    Timber.d("Re-enabled destination with existing order $currentOrder")
                }
            } else if (!isDestination) {
                // Remove from destinations - clear order and color
                val updated = current.copy(
                    isDestination = false,
                    destinationOrder = null,
                    destinationColor = 0 // Set to 0 instead of null
                )
                updateCurrentResource(updated)
                Timber.d("Removed from destinations")
            }
        }
    }
    
    fun updateScanSubdirectories(enabled: Boolean) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(scanSubdirectories = enabled)
        updateCurrentResource(updated)
    }
    
    fun updateDisableThumbnails(enabled: Boolean) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(disableThumbnails = enabled)
        updateCurrentResource(updated)
    }
    
    // SMB Credentials updates
    fun updateSmbServer(server: String) {
        updateState { it.copy(smbServer = server, hasSmbCredentialsChanges = true) }
    }
    
    fun updateSmbShareName(shareName: String) {
        updateState { it.copy(smbShareName = shareName, hasSmbCredentialsChanges = true) }
    }
    
    fun updateSmbUsername(username: String) {
        updateState { it.copy(smbUsername = username, hasSmbCredentialsChanges = true) }
    }
    
    fun updateSmbPassword(password: String) {
        updateState { it.copy(smbPassword = password, hasSmbCredentialsChanges = true) }
    }
    
    fun updateSmbDomain(domain: String) {
        updateState { it.copy(smbDomain = domain, hasSmbCredentialsChanges = true) }
    }
    
    fun updateSmbPort(port: Int) {
        updateState { it.copy(smbPort = port, hasSmbCredentialsChanges = true) }
    }
    
    // SFTP credential update methods
    fun updateSftpHost(host: String) {
        updateState { it.copy(sftpHost = host, hasSftpCredentialsChanges = true) }
    }
    
    fun updateSftpPort(port: Int) {
        updateState { it.copy(sftpPort = port, hasSftpCredentialsChanges = true) }
    }
    
    fun updateSftpUsername(username: String) {
        updateState { it.copy(sftpUsername = username, hasSftpCredentialsChanges = true) }
    }
    
    fun updateSftpPassword(password: String) {
        updateState { it.copy(sftpPassword = password, hasSftpCredentialsChanges = true) }
    }
    
    fun updateSftpPath(path: String) {
        updateState { it.copy(sftpPath = path, hasSftpCredentialsChanges = true) }
    }

    private fun updateCurrentResource(updated: MediaResource) {
        val original = state.value.originalResource ?: return
        val previous = state.value.currentResource
        val hasResourceChanges = updated != original
        Timber.d("updateCurrentResource: hasResourceChanges=$hasResourceChanges, prev.isDest=${previous?.isDestination}, updated.isDest=${updated.isDestination}, orig.isDest=${original.isDestination}")
        updateState { 
            it.copy(
                currentResource = updated,
                hasChanges = hasResourceChanges,
                hasResourceChanges = hasResourceChanges
            ) 
        }
    }

    fun resetToOriginal() {
        val original = state.value.originalResource ?: return
        updateState { 
            it.copy(
                currentResource = original,
                hasChanges = false,
                hasResourceChanges = false
            ) 
        }
        sendEvent(EditResourceEvent.ShowMessage("Changes reset"))
    }

    fun saveChanges() {
        val current = state.value.currentResource ?: return
        val currentState = state.value
        
        if (current.name.isBlank()) {
            sendEvent(EditResourceEvent.ShowError("Resource name cannot be empty"))
            return
        }
        
        if (current.supportedMediaTypes.isEmpty()) {
            sendEvent(EditResourceEvent.ShowError("At least one media type must be selected"))
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            var updatedResource = current
            
            // Save SMB credentials if changed and resource is SMB
            if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && currentState.hasSmbCredentialsChanges) {
                if (currentState.smbServer.isBlank() || currentState.smbShareName.isBlank()) {
                    sendEvent(EditResourceEvent.ShowError("Server and Share Name are required for SMB resources"))
                    setLoading(false)
                    return@launch
                }
                
                // Save new credentials
                smbOperationsUseCase.saveCredentials(
                    server = currentState.smbServer,
                    shareName = currentState.smbShareName,
                    username = currentState.smbUsername,
                    password = currentState.smbPassword,
                    domain = currentState.smbDomain,
                    port = currentState.smbPort
                ).onSuccess { newCredentialsId ->
                    Timber.d("Saved new SMB credentials: $newCredentialsId")
                    // Update resource path with new server and share
                    val newPath = "smb://${currentState.smbServer}/${currentState.smbShareName}"
                    updatedResource = current.copy(
                        credentialsId = newCredentialsId,
                        path = newPath
                    )
                }.onFailure { e ->
                    Timber.e(e, "Failed to save SMB credentials")
                    sendEvent(EditResourceEvent.ShowError("Failed to save SMB credentials: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            }
            
            // Save SFTP credentials if changed and resource is SFTP
            if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP && currentState.hasSftpCredentialsChanges) {
                if (currentState.sftpHost.isBlank()) {
                    sendEvent(EditResourceEvent.ShowError("Host is required for SFTP resources"))
                    setLoading(false)
                    return@launch
                }
                
                // Save new SFTP credentials
                smbOperationsUseCase.saveSftpCredentials(
                    host = currentState.sftpHost,
                    port = currentState.sftpPort,
                    username = currentState.sftpUsername,
                    password = currentState.sftpPassword
                ).onSuccess { newCredentialsId ->
                    Timber.d("Saved new SFTP credentials: $newCredentialsId")
                    // Update resource path with new remote path
                    val newPath = "sftp://${currentState.sftpHost}:${currentState.sftpPort}${currentState.sftpPath}"
                    updatedResource = current.copy(
                        credentialsId = newCredentialsId,
                        path = newPath
                    )
                }.onFailure { e ->
                    Timber.e(e, "Failed to save SFTP credentials")
                    sendEvent(EditResourceEvent.ShowError("Failed to save SFTP credentials: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            }
            
            // Check if resource is still writable after credential changes
            val scanner = mediaScannerFactory.getScanner(updatedResource.type)
            val isWritable = try {
                withTimeout(5000) { // 5 second timeout for write permission check
                    scanner.isWritable(updatedResource.path, updatedResource.credentialsId)
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("Write permission check timed out after 5 seconds - resource may be unavailable")
                false
            } catch (e: Exception) {
                Timber.e(e, "Failed to check write permissions")
                false
            }
            
            // Update isWritable flag (but keep destination status even if temporarily unavailable)
            updatedResource = updatedResource.copy(isWritable = isWritable)
            
            if (!isWritable && updatedResource.isDestination) {
                // Warn user but don't remove from destinations - they may just be outside network
                Timber.w("Resource ${updatedResource.name} appears unavailable but keeping as destination")
                sendEvent(EditResourceEvent.ShowError(
                    "Warning: Unable to verify write access. " +
                    "Resource may be temporarily unavailable (e.g., outside home network). " +
                    "Destination status preserved - resource will work when available again."
                ))
            }
            
            updateResourceUseCase(updatedResource).onSuccess {
                Timber.d("Resource updated: ${updatedResource.name}")
                sendEvent(EditResourceEvent.ResourceUpdated)
                
                // Update original to prevent hasChanges flag after save
                updateState { 
                    it.copy(
                        originalResource = updatedResource,
                        hasChanges = false,
                        hasSmbCredentialsChanges = false,
                        hasSftpCredentialsChanges = false
                    ) 
                }
            }.onFailure { e ->
                Timber.e(e, "Error updating resource")
                handleError(e)
            }
            
            setLoading(false)
        }
    }

    fun testConnection() {
        val current = state.value.currentResource ?: return
        val currentState = state.value
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // For SMB resources with unsaved credential changes, test with state credentials
            if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && currentState.hasSmbCredentialsChanges) {
                if (currentState.smbServer.isBlank() || currentState.smbShareName.isBlank()) {
                    sendEvent(EditResourceEvent.TestResult(false, "Server and Share Name are required"))
                    setLoading(false)
                    return@launch
                }
                
                Timber.d("Testing SMB connection with unsaved credentials from state")
                smbOperationsUseCase.testConnection(
                    server = currentState.smbServer,
                    shareName = currentState.smbShareName,
                    username = currentState.smbUsername,
                    password = currentState.smbPassword,
                    domain = currentState.smbDomain,
                    port = currentState.smbPort
                ).onSuccess { message ->
                    Timber.d("Connection test successful: $message")
                    sendEvent(EditResourceEvent.TestResult(true, message))
                }.onFailure { e ->
                    Timber.e(e, "Connection test failed")
                    sendEvent(EditResourceEvent.TestResult(false, e.message ?: "Unknown error"))
                }
            } else if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP && currentState.hasSftpCredentialsChanges) {
                // For SFTP resources with unsaved credential changes, test with state credentials
                if (currentState.sftpHost.isBlank()) {
                    sendEvent(EditResourceEvent.TestResult(false, "Host is required"))
                    setLoading(false)
                    return@launch
                }
                
                Timber.d("Testing SFTP connection with unsaved credentials from state")
                smbOperationsUseCase.testSftpConnection(
                    host = currentState.sftpHost,
                    port = currentState.sftpPort,
                    username = currentState.sftpUsername,
                    password = currentState.sftpPassword
                ).onSuccess { message ->
                    Timber.d("Connection test successful: $message")
                    sendEvent(EditResourceEvent.TestResult(true, message))
                }.onFailure { e ->
                    Timber.e(e, "Connection test failed")
                    sendEvent(EditResourceEvent.TestResult(false, e.message ?: "Unknown error"))
                }
            } else {
                // Test with saved credentials from database
                Timber.d("Testing connection with saved credentials from database")
                resourceRepository.testConnection(current).onSuccess { message ->
                    Timber.d("Connection test successful: $message")
                    sendEvent(EditResourceEvent.TestResult(true, message))
                }.onFailure { e ->
                    Timber.e(e, "Connection test failed")
                    sendEvent(EditResourceEvent.TestResult(false, e.message ?: "Unknown error"))
                }
            }

            setLoading(false)
        }
    }
    
    /**
     * Check for trash folders in the resource
     */
    fun checkTrashFolders() {
        val current = state.value.currentResource ?: return
        
        // Only check network resources
        if (current.type !in listOf(
            com.sza.fastmediasorter.domain.model.ResourceType.SMB,
            com.sza.fastmediasorter.domain.model.ResourceType.SFTP,
            com.sza.fastmediasorter.domain.model.ResourceType.FTP
        )) {
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val credentialsId = current.credentialsId ?: return@launch
            val result = smbOperationsUseCase.checkTrashFolders(current.type, credentialsId, current.path).getOrNull()
            val (hasTrash, trashFolders) = result ?: (false to emptyList())
            
            updateState { 
                it.copy(
                    hasTrashFolders = hasTrash,
                    trashFolderCount = trashFolders.size
                )
            }
            
            Timber.d("Trash check: hasTrash=$hasTrash, count=${trashFolders.size}, folders=$trashFolders")
        }
    }
    
    /**
     * Request confirmation to clear trash
     */
    fun requestClearTrash() {
        val count = state.value.trashFolderCount
        if (count > 0) {
            sendEvent(EditResourceEvent.ConfirmClearTrash(count))
        }
    }
    
    /**
     * Clear all trash folders in the resource
     */
    fun clearTrash() {
        val current = state.value.currentResource ?: return
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val credentialsId = current.credentialsId ?: run {
                Timber.e("Cannot clear trash: missing credentialsId")
                sendEvent(EditResourceEvent.ShowError("Invalid credentials"))
                setLoading(false)
                return@launch
            }
            
            smbOperationsUseCase.cleanupTrash(current.type, credentialsId, current.path)
                .onSuccess { deletedCount ->
                    Timber.i("Successfully cleared $deletedCount trash folders")
                    sendEvent(EditResourceEvent.TrashCleared(deletedCount))
                    
                    // Re-check trash status
                    updateState { it.copy(hasTrashFolders = false, trashFolderCount = 0) }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to clear trash")
                    sendEvent(EditResourceEvent.ShowError(e.message ?: "Failed to clear trash"))
                }

            setLoading(false)
        }
    }
}