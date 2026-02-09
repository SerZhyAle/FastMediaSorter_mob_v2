package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.cloud.AuthResult
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DropboxFolderPickerState(
    val folders: List<CloudFolderItem> = emptyList(),
    val selectedFolders: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val currentPath: List<PathItem> = listOf(PathItem("", "Dropbox")),
    val canGoBack: Boolean = false,
    val addAsDestination: Boolean = false,
    val scanSubdirectories: Boolean = true
) {
    val selectedCount: Int get() = selectedFolders.size
    val hasSelection: Boolean get() = selectedFolders.isNotEmpty()
}

sealed class DropboxFolderPickerEvent {
    data class ShowError(val message: String) : DropboxFolderPickerEvent()
    data object FolderSelected : DropboxFolderPickerEvent()
}

@HiltViewModel
class DropboxFolderPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dropboxClient: DropboxClient,
    private val resourceRepository: ResourceRepository,
    private val addResourceUseCase: AddResourceUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DropboxFolderPickerState())
    val state: StateFlow<DropboxFolderPickerState> = _state.asStateFlow()

    private val _events = Channel<DropboxFolderPickerEvent>()
    val events = _events.receiveAsFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val authResult = dropboxClient.authenticate()
                if (authResult is AuthResult.Error) {
                    Timber.e("Dropbox authentication failed: ${authResult.message}")
                    _events.send(DropboxFolderPickerEvent.ShowError("Authentication failed: ${authResult.message}"))
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                val currentFolderId = _state.value.currentPath.lastOrNull()?.id
                when (val result = dropboxClient.listFolders(currentFolderId)) {
                    is CloudResult.Success -> {
                        val folders = result.data.map { cloudFile ->
                            CloudFolderItem(
                                id = cloudFile.id,
                                name = cloudFile.name,
                                mimeType = cloudFile.mimeType,
                                isSelected = cloudFile.id in _state.value.selectedFolders
                            )
                        }
                        _state.update { it.copy(folders = folders, isLoading = false) }
                    }
                    is CloudResult.Error -> {
                        Timber.e("Failed to load Dropbox folders: ${result.message}")
                        _events.send(DropboxFolderPickerEvent.ShowError(result.message))
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading Dropbox folders")
                _events.send(DropboxFolderPickerEvent.ShowError(e.message ?: "Unknown error"))
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleDestinationFlag() {
        _state.update { it.copy(addAsDestination = !it.addAsDestination) }
    }
    
    fun toggleScanSubdirectoriesFlag() {
        _state.update { it.copy(scanSubdirectories = !it.scanSubdirectories) }
    }
    
    fun selectFolder(folder: CloudFolderItem) {
        viewModelScope.launch {
            val isDestination = _state.value.addAsDestination
            val scanSubdirectories = _state.value.scanSubdirectories

            try {
                val resource = com.sza.fastmediasorter.domain.model.MediaResource(
                    id = 0,
                    type = ResourceType.CLOUD,
                    path = "cloud://dropbox${folder.id}",
                    name = folder.name,
                    isDestination = isDestination,
                    scanSubdirectories = scanSubdirectories,
                    displayOrder = 0,
                    cloudProvider = CloudProvider.DROPBOX,
                    cloudFolderId = folder.id,
                    isWritable = true // Cloud storage is writable
                )
                
                val result = addResourceUseCase.addMultiple(listOf(resource))
                
                result.onSuccess {
                    _events.send(DropboxFolderPickerEvent.FolderSelected)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add folder")
                    _events.send(DropboxFolderPickerEvent.ShowError(e.message ?: "Failed to add folder"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add folder")
                _events.send(DropboxFolderPickerEvent.ShowError(e.message ?: "Failed to add folder"))
            }
        }
    }

    fun navigateIntoFolder(folder: CloudFolderItem) {
        viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    currentPath = currentState.currentPath + PathItem(folder.id, folder.name),
                    canGoBack = true
                )
            }
            loadFolders()
        }
    }

    fun navigateBack(): Boolean {
        val currentPath = _state.value.currentPath
        return if (currentPath.size > 1) {
            _state.update { it.copy(
                currentPath = currentPath.dropLast(1),
                canGoBack = currentPath.size > 2
            ) }
            loadFolders()
            true
        } else {
            false
        }
    }
}
