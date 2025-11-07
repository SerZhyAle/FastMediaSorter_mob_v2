package com.sza.fastmediasorter_v2.ui.editresource

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class EditResourceState(
    val originalResource: MediaResource? = null,
    val currentResource: MediaResource? = null,
    val hasChanges: Boolean = false
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
            
            setLoading(false)
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
            
            updateResourceUseCase(current).onSuccess {
                Timber.d("Resource updated: ${current.name}")
                sendEvent(EditResourceEvent.ResourceUpdated)
                
                // Update original to prevent hasChanges flag after save
                updateState { 
                    it.copy(
                        originalResource = current,
                        hasChanges = false
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
