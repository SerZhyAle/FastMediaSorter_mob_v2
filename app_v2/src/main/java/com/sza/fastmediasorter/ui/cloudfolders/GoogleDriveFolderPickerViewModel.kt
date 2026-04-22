package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.cloud.AuthResult
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.google.android.gms.auth.UserRecoverableAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class GoogleDriveFolderPickerState(
    val folders: List<CloudFolderItem> = emptyList(),
    val selectedFolders: Set<String> = emptySet(),  // IDs of selected folders
    val isLoading: Boolean = false,
    val currentPath: List<PathItem> = listOf(PathItem("root", "My Drive")),
    val canGoBack: Boolean = false,
    val addAsDestination: Boolean = false,  // Flag to mark resources as destinations
    val scanSubdirectories: Boolean = true  // Flag to scan subdirectories
) {
    val selectedCount: Int get() = selectedFolders.size
    val hasSelection: Boolean get() = selectedFolders.isNotEmpty()
}

data class PathItem(
    val id: String,
    val name: String
)

sealed class GoogleDriveFolderPickerEvent {
    data class ShowError(val message: String) : GoogleDriveFolderPickerEvent()
    data object FolderSelected : GoogleDriveFolderPickerEvent()
    data class RequiresReAuth(val intent: android.content.Intent) : GoogleDriveFolderPickerEvent()
}

@HiltViewModel
class GoogleDriveFolderPickerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val googleDriveClient: GoogleDriveRestClient,
    private val resourceRepository: ResourceRepository,
    private val addResourceUseCase: AddResourceUseCase,
    private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Account email passed from AddResourceActivity — stored as credentialsId in the resource */
    private val accountEmail: String? = savedStateHandle["extra_account_email"]

    private val _state = MutableStateFlow(GoogleDriveFolderPickerState())
    val state: StateFlow<GoogleDriveFolderPickerState> = _state.asStateFlow()

    private val _events = Channel<GoogleDriveFolderPickerEvent>()
    val events = _events.receiveAsFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // Initialize access token before making API calls
                val authResult = googleDriveClient.authenticate()
                if (authResult is AuthResult.Error) {
                    Timber.e("Authentication failed: ${authResult.message}")
                    _events.send(GoogleDriveFolderPickerEvent.ShowError("Authentication failed: ${authResult.message}"))
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                
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
            } catch (e: UserRecoverableAuthException) {
                Timber.w("User consent required for new permissions")
                val intent = e.intent
                if (intent != null) {
                    _events.send(GoogleDriveFolderPickerEvent.RequiresReAuth(intent))
                } else {
                    _events.send(GoogleDriveFolderPickerEvent.ShowError("Authentication required but no intent available"))
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error loading folders")
                _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Unknown error"))
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
                // Deduplication check: prevent adding the same folder twice
                val existingResources = resourceRepository.getAllResourcesSync()
                val duplicate = existingResources.find {
                    it.cloudProvider == CloudProvider.GOOGLE_DRIVE && it.cloudFolderId == folder.id
                }
                if (duplicate != null) {
                    Timber.w("GoogleDriveFolderPicker: folder ${folder.id} already added as '${duplicate.name}'")
                    _events.send(
                        GoogleDriveFolderPickerEvent.ShowError(
                            "Folder \"${folder.name}\" already added as resource \"${duplicate.name}\". Remove it first if you want to re-add."
                        )
                    )
                    return@launch
                }

                // Get globally enabled media types from settings
                val settings = settingsRepository.getSettings().first()
                val supportedTypes = settings.getGloballyEnabledMediaTypes()
                
                val resource = com.sza.fastmediasorter.domain.model.MediaResource(
                    id = 0,
                    type = ResourceType.CLOUD,
                    path = "cloud://google_drive/${folder.id}",
                    name = folder.name,
                    isDestination = isDestination,
                    scanSubdirectories = scanSubdirectories,
                    displayOrder = 0,
                    cloudProvider = CloudProvider.GOOGLE_DRIVE,
                    cloudFolderId = folder.id,
                    credentialsId = accountEmail, // Links resource to the authenticated Google account
                    isWritable = true, // Cloud storage is writable
                    supportedMediaTypes = supportedTypes
                )
                
                val result = addResourceUseCase.addMultiple(listOf(resource))
                
                result.onSuccess {
                    _events.send(GoogleDriveFolderPickerEvent.FolderSelected)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add folder")
                    _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Failed to add folder"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add folder")
                _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Failed to add folder"))
            }
        }
    }

    /**
     * Navigate into a folder to see its subfolders
     */
    fun navigateIntoFolder(folder: CloudFolderItem) {
        viewModelScope.launch {
            // Keep selected folders when navigating
            _state.update { it.copy(isLoading = true) }

            try {
                // Initialize access token
                val authResult = googleDriveClient.authenticate()
                if (authResult is AuthResult.Error) {
                    _events.send(GoogleDriveFolderPickerEvent.ShowError("Authentication failed"))
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }

                when (val result = googleDriveClient.listFolders(folder.id)) {
                    is CloudResult.Success -> {
                        val folders = result.data.map { cloudFile ->
                            CloudFolderItem(
                                id = cloudFile.id,
                                name = cloudFile.name,
                                mimeType = cloudFile.mimeType,
                                isSelected = false
                            )
                        }
                        _state.update { currentState ->
                            val newPath = currentState.currentPath + PathItem(folder.id, folder.name)
                            currentState.copy(
                                folders = folders,
                                isLoading = false,
                                currentPath = newPath,
                                canGoBack = true
                            )
                        }
                    }
                    is CloudResult.Error -> {
                        Timber.e("Failed to load subfolders: ${result.message}")
                        _events.send(GoogleDriveFolderPickerEvent.ShowError(result.message))
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: UserRecoverableAuthException) {
                Timber.w("User consent required for new permissions")
                val intent = e.intent
                if (intent != null) {
                    _events.send(GoogleDriveFolderPickerEvent.RequiresReAuth(intent))
                } else {
                    _events.send(GoogleDriveFolderPickerEvent.ShowError("Authentication required but no intent available"))
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error navigating into folder")
                _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Unknown error"))
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Navigate back to parent folder
     */
    fun navigateBack(): Boolean {
        val currentPath = _state.value.currentPath
        if (currentPath.size <= 1) {
            return false // Already at root
        }

        viewModelScope.launch {
            // Keep selected folders when navigating back
            _state.update { it.copy(isLoading = true) }

            val newPath = currentPath.dropLast(1)
            val parentId = newPath.last().id

            try {
                val authResult = googleDriveClient.authenticate()
                if (authResult is AuthResult.Error) {
                    _events.send(GoogleDriveFolderPickerEvent.ShowError("Authentication failed"))
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }

                val folderId = if (parentId == "root") null else parentId
                when (val result = googleDriveClient.listFolders(folderId)) {
                    is CloudResult.Success -> {
                        val folders = result.data.map { cloudFile ->
                            CloudFolderItem(
                                id = cloudFile.id,
                                name = cloudFile.name,
                                mimeType = cloudFile.mimeType,
                                isSelected = false
                            )
                        }
                        _state.update {
                            it.copy(
                                folders = folders,
                                isLoading = false,
                                currentPath = newPath,
                                canGoBack = newPath.size > 1
                            )
                        }
                    }
                    is CloudResult.Error -> {
                        _events.send(GoogleDriveFolderPickerEvent.ShowError(result.message))
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: UserRecoverableAuthException) {
                Timber.w("User consent required for new permissions")
                val intent = e.intent
                if (intent != null) {
                    _events.send(GoogleDriveFolderPickerEvent.RequiresReAuth(intent))
                } else {
                    _events.send(GoogleDriveFolderPickerEvent.ShowError("Authentication required but no intent available"))
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _events.send(GoogleDriveFolderPickerEvent.ShowError(e.message ?: "Unknown error"))
                _state.update { it.copy(isLoading = false) }
            }
        }
        return true
    }
}
