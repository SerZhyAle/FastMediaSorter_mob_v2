package com.sza.fastmediasorter_v2.ui.editresource

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter_v2.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class EditResourceState(
    val originalResource: MediaResource? = null,
    val currentResource: MediaResource? = null,
    val hasChanges: Boolean = false,
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
    val hasSftpCredentialsChanges: Boolean = false
)

sealed class EditResourceEvent {
    data class ShowError(val message: String) : EditResourceEvent()
    data class ShowMessage(val message: String) : EditResourceEvent()
    object ResourceUpdated : EditResourceEvent()
    data class TestResult(val success: Boolean, val message: String) : EditResourceEvent()
}

@HiltViewModel
class EditResourceViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val smbOperationsUseCase: SmbOperationsUseCase,
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
            
            updateState { 
                it.copy(
                    originalResource = resource,
                    currentResource = resource
                ) 
            }
            
            // Load SMB credentials if resource is SMB type
            if (resource.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SMB && resource.credentialsId != null) {
                loadSmbCredentials(resource.credentialsId!!)
            }
            
            // Load SFTP credentials if resource is SFTP type
            if (resource.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SFTP && resource.credentialsId != null) {
                loadSftpCredentials(resource.credentialsId!!)
            }
            
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
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            if (isDestination && current.destinationOrder == null) {
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
                
                // Find next available destination order
                val nextOrder = (currentDestinations.maxOfOrNull { res -> res.destinationOrder ?: 0 } ?: 0) + 1
                val color = com.sza.fastmediasorter_v2.core.util.DestinationColors.getColorForDestination(nextOrder)
                
                val updated = current.copy(
                    isDestination = true,
                    destinationOrder = nextOrder,
                    destinationColor = color
                )
                updateCurrentResource(updated)
                Timber.d("Added to destinations with order $nextOrder")
            } else if (!isDestination) {
                // Remove from destinations - clear order and color
                val updated = current.copy(
                    isDestination = false,
                    destinationOrder = null,
                    destinationColor = 0 // Set to 0 instead of null
                )
                updateCurrentResource(updated)
                Timber.d("Removed from destinations")
            } else {
                // Already has destinationOrder, just update flag
                val updated = current.copy(isDestination = isDestination)
                updateCurrentResource(updated)
            }
        }
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
        val hasChanges = updated != original
        updateState { 
            it.copy(
                currentResource = updated,
                hasChanges = hasChanges
            ) 
        }
    }

    fun resetToOriginal() {
        val original = state.value.originalResource ?: return
        updateState { 
            it.copy(
                currentResource = original,
                hasChanges = false
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
            if (current.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SMB && currentState.hasSmbCredentialsChanges) {
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
                    updatedResource = current.copy(credentialsId = newCredentialsId)
                }.onFailure { e ->
                    Timber.e(e, "Failed to save SMB credentials")
                    sendEvent(EditResourceEvent.ShowError("Failed to save SMB credentials: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            }
            
            // Save SFTP credentials if changed and resource is SFTP
            if (current.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SFTP && currentState.hasSftpCredentialsChanges) {
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
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            resourceRepository.testConnection(current).onSuccess { message ->
                Timber.d("Connection test successful: $message")
                sendEvent(EditResourceEvent.TestResult(true, message))
            }.onFailure { e ->
                Timber.e(e, "Connection test failed")
                sendEvent(EditResourceEvent.TestResult(false, e.message ?: "Unknown error"))
            }
            
            setLoading(false)
        }
    }
}
