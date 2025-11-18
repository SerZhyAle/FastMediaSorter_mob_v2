package com.sza.fastmediasorter.ui.cloudfolders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveClient
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.model.ResourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class GoogleDriveFolderPickerState(
    val folders: List<CloudFolderItem> = emptyList(),
    val selectedFolder: CloudFolderItem? = null,
    val isLoading: Boolean = false
)

data class CloudFolderItem(
    val id: String,
    val name: String,
    val mimeType: String?,
    val isSelected: Boolean = false
)

sealed class GoogleDriveFolderPickerEvent {
    data class ShowError(val message: String) : GoogleDriveFolderPickerEvent()
    data class FolderAdded(val folderId: String, val folderName: String) : GoogleDriveFolderPickerEvent()
}

@HiltViewModel
class GoogleDriveFolderPickerViewModel @Inject constructor(
    private val googleDriveClient: GoogleDriveClient,
    private val resourceRepository: ResourceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GoogleDriveFolderPickerState())
    val state: StateFlow<GoogleDriveFolderPickerState> = _state.asStateFlow()

    private val _events = Channel<GoogleDriveFolderPickerEvent>()
    val events = _events.receiveAsFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                when (val result = googleDriveClient.listFolders(null)) {
                    is CloudResult.Success -> {
                        val folders = result.data.map { cloudFile ->
                            CloudFolderItem(
                                id = cloudFile.id,
                                name = cloudFile.name,
                                mimeType = cloudFile.mimeType,
                                isSelected = false
                            )
                        }
                        _state.update { it.copy(folders = folders, isLoading = false) }
                    }
                    is CloudResult.Error -> {
                        Timber.e("Failed to load folders: ${result.message}")
                        _events.send(GoogleDriveFolderPickerEvent.ShowError(result.message))
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading folders")
                _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Unknown error"))
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectFolder(folder: CloudFolderItem) {
        _state.update { currentState ->
            currentState.copy(
                folders = currentState.folders.map { 
                    it.copy(isSelected = it.id == folder.id) 
                },
                selectedFolder = folder
            )
        }
    }

    fun addSelectedFolder() {
        viewModelScope.launch {
            val selectedFolder = _state.value.selectedFolder ?: return@launch

            try {
                val resource = com.sza.fastmediasorter.domain.model.MediaResource(
                    id = 0,
                    type = ResourceType.CLOUD,
                    path = selectedFolder.id,
                    name = selectedFolder.name,
                    isDestination = false,
                    displayOrder = 0,
                    cloudProvider = CloudProvider.GOOGLE_DRIVE,
                    cloudFolderId = selectedFolder.id
                )
                
                resourceRepository.addResource(resource)

                _events.send(
                    GoogleDriveFolderPickerEvent.FolderAdded(
                        folderId = selectedFolder.id,
                        folderName = selectedFolder.name
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to add folder")
                _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Failed to add folder"))
            }
        }
    }
}
