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
    val hasSmbCredentialsChanges: Boolean = false
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
        val updated = current.copy(isDestination = isDestination)
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
            
            updateResourceUseCase(updatedResource).onSuccess {
                Timber.d("Resource updated: ${updatedResource.name}")
                sendEvent(EditResourceEvent.ResourceUpdated)
                
                // Update original to prevent hasChanges flag after save
                updateState { 
                    it.copy(
                        originalResource = updatedResource,
                        hasChanges = false,
                        hasSmbCredentialsChanges = false
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
