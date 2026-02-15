package com.sza.fastmediasorter.ui.editresource

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import com.sza.fastmediasorter.R
import java.util.Locale

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
    val scanSubdirectories: Boolean = true,
    
    // Global settings for UI visibility
    val isGlobalTextSupportEnabled: Boolean = false,
    val isGlobalPdfSupportEnabled: Boolean = false,

    val isGlobalEpubSupportEnabled: Boolean = false,
    val isGlobalAllFilesEnabled: Boolean = false,
    
    // Speed test state
    val isTestingSpeed: Boolean = false,
    val speedTestStatus: String = "",
    
    // Destination state
    val currentDestinationsCount: Int = 0,
    val maxDestinations: Int = 10,
    val canBeDestination: Boolean = false
)

sealed class EditResourceEvent {
    data class ShowError(val message: String) : EditResourceEvent()
    data class ShowMessage(val message: String) : EditResourceEvent()
    data class ShowMessageRes(val messageResId: Int, val args: List<Any> = emptyList()) : EditResourceEvent()
    data class ResourceUpdated(val requiresRescan: Boolean) : EditResourceEvent() // requiresRescan=true if file list needs reload
    data class TestResult(val success: Boolean, val message: String) : EditResourceEvent()
    data class ConfirmClearTrash(val count: Int) : EditResourceEvent()
    data class TrashCleared(val count: Int) : EditResourceEvent()
    object RequestCloudReAuthentication : EditResourceEvent() // Request user to sign in again
}

@HiltViewModel
@Deprecated(
    message = "Legacy ViewModel. Use ResourceFormViewModel + ResourceEditorUseCase for new flows.",
    replaceWith = ReplaceWith("com.sza.fastmediasorter.ui.resourceeditor.ResourceFormViewModel")
)
class EditResourceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val mediaScannerFactory: com.sza.fastmediasorter.domain.usecase.MediaScannerFactory,
    private val smbClient: com.sza.fastmediasorter.data.network.SmbClient,
    private val networkSpeedTestUseCase: NetworkSpeedTestUseCase,
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
            
            // Get global settings
            val settings = settingsRepository.getSettings().first()
            val textEnabled = settings.supportText
            val pdfEnabled = settings.supportPdf
            val epubEnabled = settings.supportEpub
            val allFilesEnabled = settings.allFiles
            
            // Note: Do NOT reset SMB client here! Connection pool handles lifecycle automatically.
            // Resetting kills active connections and forces unnecessary reconnects.
            
            // Prepare initial state values
            var smbServer = ""
            var smbShareName = ""
            var smbUsername = ""
            var smbPassword = ""
            var smbDomain = ""
            var smbPort = 445
            
            var sftpHost = ""
            var sftpPort = 22
            var sftpUsername = ""
            var sftpPassword = ""
            var sftpPath = "/"
            
            // Load SMB credentials if resource is SMB type
            if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && resource.credentialsId != null) {
                smbOperationsUseCase.getConnectionInfo(resource.credentialsId).onSuccess { connectionInfo ->
                    Timber.d("Loaded SMB credentials for resource")
                    
                    // Extract share name (with subfolders) from resource.path (format: smb://server/share/subfolder)
                    val resourcePath = resource.path
                    val extractedShareName = if (resourcePath.startsWith("smb://")) {
                        // Normalize path: replace backslashes with forward slashes
                        val normalizedPath = resourcePath.replace('\\', '/')
                        val withoutProtocol = normalizedPath.substring(6) // Remove "smb://"
                        val firstSlash = withoutProtocol.indexOf('/')
                        if (firstSlash > 0) {
                            // Extract everything after server (share + subfolders)
                            withoutProtocol.substring(firstSlash + 1)
                        } else {
                            connectionInfo.shareName // Fallback to credentials if no path
                        }
                    } else {
                        connectionInfo.shareName
                    }
                    
                    smbServer = connectionInfo.server
                    smbShareName = extractedShareName
                    smbUsername = connectionInfo.username
                    smbPassword = connectionInfo.password
                    smbDomain = connectionInfo.domain
                    smbPort = connectionInfo.port
                }.onFailure { e ->
                    Timber.e(e, "Failed to load SMB credentials")
                }
            }
            
            // Load SFTP credentials if resource is SFTP type
            if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP && resource.credentialsId != null) {
                smbOperationsUseCase.getSftpCredentials(resource.credentialsId).onSuccess { credentials ->
                    Timber.d("Loaded SFTP credentials for resource")
                    
                    // Extract path from resource.path (format: sftp://host:port/path)
                    val resourcePath = resource.path
                    val extractedPath = if (resourcePath.startsWith("sftp://")) {
                        val withoutProtocol = resourcePath.substring(7) // Remove "sftp://"
                        val firstSlash = withoutProtocol.indexOf('/')
                        if (firstSlash > 0) {
                            withoutProtocol.substring(firstSlash) // Keep leading /
                        } else {
                            "/"
                        }
                    } else {
                        "/"
                    }
                    
                    sftpHost = credentials.server
                    sftpPort = credentials.port
                    sftpUsername = credentials.username
                    sftpPassword = credentials.password
                    sftpPath = extractedPath
                }.onFailure { e ->
                    Timber.e(e, "Failed to load SFTP credentials")
                }
            }
            
            // Load FTP credentials if resource is FTP type
            if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.FTP && resource.credentialsId != null) {
                smbOperationsUseCase.getFtpCredentials(resource.credentialsId).onSuccess { credentials ->
                    Timber.d("Loaded FTP credentials for resource")
                    
                    // Extract path from resource.path (format: ftp://host:port/path)
                    val resourcePath = resource.path
                    val extractedPath = if (resourcePath.startsWith("ftp://")) {
                        val withoutProtocol = resourcePath.substring(6) // Remove "ftp://"
                        val firstSlash = withoutProtocol.indexOf('/')
                        if (firstSlash > 0) {
                            withoutProtocol.substring(firstSlash) // Keep leading /
                        } else {
                            "/"
                        }
                    } else {
                        "/"
                    }
                    
                    sftpHost = credentials.server
                    sftpPort = credentials.port
                    sftpUsername = credentials.username
                    sftpPassword = credentials.password
                    sftpPath = extractedPath
                }.onFailure { e ->
                    Timber.e(e, "Failed to load FTP credentials")
                }
            }
            
            // Update state with all loaded data at once to prevent UI flickering
            updateState { 
                it.copy(
                    originalResource = resource,
                    currentResource = resource,
                    // SMB
                    smbServer = smbServer,
                    smbShareName = smbShareName,
                    smbUsername = smbUsername,
                    smbPassword = smbPassword,
                    smbDomain = smbDomain,
                    smbPort = smbPort,
                    // SFTP/FTP
                    sftpHost = sftpHost,
                    sftpPort = sftpPort,
                    sftpUsername = sftpUsername,
                    sftpPassword = sftpPassword,
                    sftpPath = sftpPath,
                    // Global settings
                    isGlobalTextSupportEnabled = textEnabled,
                    isGlobalPdfSupportEnabled = pdfEnabled,
                    isGlobalEpubSupportEnabled = epubEnabled,
                    isGlobalAllFilesEnabled = allFilesEnabled
                ) 
            }
            
            // Load max destinations from settings and count current destinations
            val settingsFlow = settingsRepository.getSettings()
            val settingsData = settingsFlow.first()
            val allResources = resourceRepository.getFilteredResources(
                filterByType = null,
                filterByMediaType = null,
                filterByName = null,
                sortMode = com.sza.fastmediasorter.domain.model.SortMode.NAME_ASC
            )
            val destinationsCount = allResources.count { it.isDestination }
            
            // Compute canBeDestination
            val canBeDestination = computeCanBeDestination(
                resource = resource,
                destinationsCount = destinationsCount,
                maxDestinations = settingsData.maxRecipients
            )
            
            updateState {
                it.copy(
                    currentDestinationsCount = destinationsCount,
                    maxDestinations = settingsData.maxRecipients,
                    canBeDestination = canBeDestination
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

    fun updateComment(comment: String) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(comment = comment)
        updateCurrentResource(updated)
    }

    fun updateAccessPin(pin: String?) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(accessPin = pin)
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
                
                // Set default supported media types if empty
                val supportedTypes = if (current.supportedMediaTypes.isEmpty()) {
                    setOf(
                        com.sza.fastmediasorter.domain.model.MediaType.IMAGE,
                        com.sza.fastmediasorter.domain.model.MediaType.VIDEO,
                        com.sza.fastmediasorter.domain.model.MediaType.AUDIO,
                        com.sza.fastmediasorter.domain.model.MediaType.GIF
                    )
                } else {
                    current.supportedMediaTypes
                }
                
                val updated = current.copy(
                    isDestination = true,
                    destinationOrder = nextOrder,
                    destinationColor = color,
                    supportedMediaTypes = supportedTypes
                )
                updateCurrentResource(updated)
                Timber.d("Added to destinations with order $nextOrder and default media types")
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
        Timber.d("EditResourceVM.updateScanSubdirectories: $enabled (was ${current.scanSubdirectories})")
        val updated = current.copy(scanSubdirectories = enabled)
        updateCurrentResource(updated)
    }
    
    fun updateShowSubfoldersAsItems(value: Boolean) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(showSubfoldersAsItems = value)
        updateCurrentResource(updated)
    }
    
    fun updateDisableThumbnails(enabled: Boolean) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(disableThumbnails = enabled)
        updateCurrentResource(updated)
    }

    fun updateReadOnlyMode(enabled: Boolean) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(isReadOnly = enabled)
        
        // Recalculate canBeDestination when read-only changes
        val currentState = state.value
        val canBeDestination = computeCanBeDestination(
            resource = updated,
            destinationsCount = currentState.currentDestinationsCount,
            maxDestinations = currentState.maxDestinations
        )
        
        updateState {
            it.copy(
                currentResource = updated,
                canBeDestination = canBeDestination,
                hasChanges = true,
                hasResourceChanges = true
            )
        }
    }
    
    /**
     * Helper function to compute if resource can be set as destination
     * @param resource The resource to check
     * @param destinationsCount Current count of destinations
     * @param maxDestinations Maximum allowed destinations from settings
     * @return true if resource can be set as destination
     */
    private fun computeCanBeDestination(
        resource: MediaResource,
        destinationsCount: Int,
        maxDestinations: Int
    ): Boolean {
        // If resource is already a destination, it doesn't count toward the limit
        val isLimitReached = !resource.isDestination && destinationsCount >= maxDestinations
        return resource.isWritable && !resource.isReadOnly && !isLimitReached
    }
    
    fun updateAllFilesMode(enabled: Boolean) {
        val current = state.value.currentResource ?: return
        val updated = current.copy(allFiles = enabled)
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
    
    /**
     * Determines if changes require file list rescan.
     * Returns true only if properties affecting file visibility changed:
     * - supportedMediaTypes (VIDEO, IMAGE, etc)
     * - scanSubdirectories
     * - showSubfoldersAsItems
     * - allFiles
     * - showHiddenFiles
     * 
     * Returns false for metadata changes (name, comment, pin, displayMode, etc)
     */
    private fun requiresFileListRescan(original: MediaResource, updated: MediaResource): Boolean {
        return original.supportedMediaTypes != updated.supportedMediaTypes ||
               original.scanSubdirectories != updated.scanSubdirectories ||
               original.showSubfoldersAsItems != updated.showSubfoldersAsItems ||
               original.allFiles != updated.allFiles ||
               original.showHiddenFiles != updated.showHiddenFiles
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
        
        Timber.d("EditResourceVM.saveChanges: scanSubdirectories=${current.scanSubdirectories}, showSubfoldersAsItems=${current.showSubfoldersAsItems}, name=${current.name}")
        Timber.i("╔═══ SAVING RESOURCE ═══")
        Timber.i("║ Name: ${current.name}")
        Timber.i("║ supportedMediaTypes: ${current.supportedMediaTypes.map { it.name }}")
        Timber.i("║ Size: ${current.supportedMediaTypes.size}/7")
        Timber.i("║ Contains VIDEO: ${current.supportedMediaTypes.contains(MediaType.VIDEO)}")
        Timber.i("╚════════════════════════")
        
        if (current.name.isBlank()) {
            sendEvent(EditResourceEvent.ShowError("Resource name cannot be empty"))
            return
        }
        
        if (current.supportedMediaTypes.isEmpty()) {
            sendEvent(EditResourceEvent.ShowError(context.getString(R.string.at_least_one_media_type_required)))
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
                    // Normalize path: remove trailing slash unless it's root "/"
                    val normalizedPath = if (currentState.sftpPath != "/" && currentState.sftpPath.endsWith("/")) {
                        currentState.sftpPath.dropLast(1)
                    } else {
                        currentState.sftpPath
                    }
                    val newPath = "sftp://${currentState.sftpHost}:${currentState.sftpPort}$normalizedPath"
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
            
            // Save FTP credentials if changed and resource is FTP
            if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.FTP && currentState.hasSftpCredentialsChanges) {
                if (currentState.sftpHost.isBlank()) {
                    sendEvent(EditResourceEvent.ShowError("Host is required for FTP resources"))
                    setLoading(false)
                    return@launch
                }
                
                // Save new FTP credentials
                smbOperationsUseCase.saveFtpCredentials(
                    host = currentState.sftpHost,
                    port = currentState.sftpPort,
                    username = currentState.sftpUsername,
                    password = currentState.sftpPassword
                ).onSuccess { newCredentialsId ->
                    Timber.d("Saved new FTP credentials: $newCredentialsId")
                    // Update resource path with new remote path
                    // Normalize path: remove trailing slash unless it's root "/"
                    val normalizedPath = if (currentState.sftpPath != "/" && currentState.sftpPath.endsWith("/")) {
                        currentState.sftpPath.dropLast(1)
                    } else {
                        currentState.sftpPath
                    }
                    val newPath = "ftp://${currentState.sftpHost}:${currentState.sftpPort}$normalizedPath"
                    updatedResource = current.copy(
                        credentialsId = newCredentialsId,
                        path = newPath
                    )
                }.onFailure { e ->
                    Timber.e(e, "Failed to save FTP credentials")
                    sendEvent(EditResourceEvent.ShowError("Failed to save FTP credentials: ${e.message}"))
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
                
                // Determine if changes require rescan (file list reload)
                val requiresRescan = requiresFileListRescan(
                    original = state.value.originalResource!!,
                    updated = updatedResource
                )
                Timber.d("Resource update - requiresRescan=$requiresRescan")
                
                sendEvent(EditResourceEvent.ResourceUpdated(requiresRescan = requiresRescan))
                
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
        
        // For CLOUD resources, check auth but allow speed test
        if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.CLOUD) {
            Timber.d("Cloud resource - attempting speed test. Current type: CLOUD")
             // Note: testCloudSpeed in use case will handle auth/re-auth if needed
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // For LOCAL resources: Run Speed Test!
            if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.LOCAL) {
                Timber.d("Local resource: running speed test as 'Test Connection'")
                try {
                    // Start speed test flow
                    networkSpeedTestUseCase.runSpeedTest(current).collect { status ->
                         when (status) {
                             is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Progress -> {
                                 val msg = context.getString(status.messageResId)
                                 updateState { it.copy(speedTestStatus = msg, isTestingSpeed = true) }
                             }
                             is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Complete -> {
                                 val result = status.result
                                 val readSpeed = String.format(Locale.getDefault(), "%.2f", result.readSpeedMbps)
                                 val writeSpeed = String.format(Locale.getDefault(), "%.2f", result.writeSpeedMbps)
                                 val message = context.getString(
                                     R.string.speed_test_complete,
                                     readSpeed, writeSpeed, result.recommendedThreads
                                 )
                                 sendEvent(EditResourceEvent.TestResult(success = true, message = message))
                                 loadResource()
                                 updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                             }
                             is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Error -> {
                                 sendEvent(EditResourceEvent.TestResult(success = false, message = "Speed test failed: ${status.message}"))
                                 updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                             }
                         }
                    }
                } catch (e: Exception) {
                     Timber.e(e, "Local speed test failed")
                     sendEvent(EditResourceEvent.TestResult(success = false, message = "Test failed: ${e.message}"))
                } finally {
                    updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                    setLoading(false)
                }
                return@launch
            }
            
            // For SMB/SFTP resources
            var connectionSuccess = false
            var successMessage = ""
            
            // 1. Test Availability (Connection)
            if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && currentState.hasSmbCredentialsChanges) {
                if (currentState.smbServer.isBlank() || currentState.smbShareName.isBlank()) {
                    sendEvent(EditResourceEvent.ShowError("Server and Share Name are required"))
                    setLoading(false)
                    return@launch
                }
                smbOperationsUseCase.testConnection(
                    server = currentState.smbServer,
                    shareName = currentState.smbShareName,
                    username = currentState.smbUsername,
                    password = currentState.smbPassword,
                    domain = currentState.smbDomain,
                    port = currentState.smbPort
                ).onSuccess { message ->
                    connectionSuccess = true
                    successMessage = message
                }.onFailure { e ->
                    sendEvent(EditResourceEvent.TestResult(success = false, message = "Connection failed: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            } else if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP && currentState.hasSftpCredentialsChanges) {
                 if (currentState.sftpHost.isBlank()) {
                    sendEvent(EditResourceEvent.ShowError("Host is required"))
                    setLoading(false)
                    return@launch
                }
                smbOperationsUseCase.testSftpConnection(
                    host = currentState.sftpHost,
                    port = currentState.sftpPort,
                    username = currentState.sftpUsername,
                    password = currentState.sftpPassword
                ).onSuccess { message ->
                    connectionSuccess = true
                    successMessage = message
                }.onFailure { e ->
                    sendEvent(EditResourceEvent.TestResult(success = false, message = "Connection failed: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            } else if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.FTP && currentState.hasSftpCredentialsChanges) {
                 if (currentState.sftpHost.isBlank()) {
                    sendEvent(EditResourceEvent.ShowError("Host is required"))
                    setLoading(false)
                    return@launch
                }
                smbOperationsUseCase.testFtpConnection(
                    host = currentState.sftpHost,
                    port = currentState.sftpPort,
                    username = currentState.sftpUsername,
                    password = currentState.sftpPassword
                ).onSuccess { message ->
                    connectionSuccess = true
                    successMessage = message
                }.onFailure { e ->
                    sendEvent(EditResourceEvent.TestResult(success = false, message = "Connection failed: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            } else {
                // Test with saved credentials
                resourceRepository.testConnection(current).onSuccess { message ->
                    connectionSuccess = true
                    successMessage = message
                }.onFailure { e ->
                    sendEvent(EditResourceEvent.TestResult(success = false, message = "Connection failed: ${e.message}"))
                    setLoading(false)
                    return@launch
                }
            }
            
            // 2. If Connection Success -> Show dialog, then run Speed Test (Auto-save if needed)
            if (connectionSuccess) {
                Timber.d("Connection verified: $successMessage. Proceeding to Speed Test.")
                // Show success dialog first
                sendEvent(EditResourceEvent.TestResult(success = true, message = successMessage))
                sendEvent(EditResourceEvent.ShowMessageRes(R.string.connection_ok_starting_speed_test))
                
                var resourceToTest = current
                
                // Save credentials if they were changed and verified safe
                if ((current.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && currentState.hasSmbCredentialsChanges) ||
                    (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP && currentState.hasSftpCredentialsChanges) ||
                    (current.type == com.sza.fastmediasorter.domain.model.ResourceType.FTP && currentState.hasSftpCredentialsChanges)) {
                    
                     Timber.d("Auto-saving verified credentials before speed test...")
                     // Re-use logic from saveChanges (simplified) or trigger save?
                     // It's safer to just replicate the credential save part here to get the new ID.
                     
                     val saveResult = if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB) {
                        smbOperationsUseCase.saveCredentials(
                            server = currentState.smbServer,
                            shareName = currentState.smbShareName,
                            username = currentState.smbUsername,
                            password = currentState.smbPassword,
                            domain = currentState.smbDomain,
                            port = currentState.smbPort
                        )
                     } else if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
                        smbOperationsUseCase.saveSftpCredentials(
                            host = currentState.sftpHost,
                            port = currentState.sftpPort,
                            username = currentState.sftpUsername,
                            password = currentState.sftpPassword
                        )
                     } else { // FTP
                        smbOperationsUseCase.saveFtpCredentials(
                            host = currentState.sftpHost,
                            port = currentState.sftpPort,
                            username = currentState.sftpUsername,
                            password = currentState.sftpPassword
                        )
                     }
                     
                     saveResult.onSuccess { newId ->
                         // Update resource with new ID for the test
                         val newPath = if (current.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB) {
                             "smb://${currentState.smbServer}/${currentState.smbShareName}"
                         } else {
                              // Normalize path logic logic duplicated from saveChanges... 
                              val p = if (currentState.sftpPath != "/" && currentState.sftpPath.endsWith("/")) currentState.sftpPath.dropLast(1) else currentState.sftpPath
                              "sftp://${currentState.sftpHost}:${currentState.sftpPort}$p"
                         }
                         
                         resourceToTest = current.copy(credentialsId = newId, path = newPath)
                         
                         // Update state to reflect saved status
                         updateState { 
                            it.copy(
                                smbServer = currentState.smbServer, // Ensure consistent
                                hasSmbCredentialsChanges = false,
                                hasSftpCredentialsChanges = false,
                                currentResource = resourceToTest
                            )
                         }
                         // Also persist to DB? Yes, we saved credentials, we should update resource text fields too?
                         // Actually networkSpeedTestUseCase updates the resource with stats, so it will update the whole object.
                         // But we need to make sure the resource in DB has the new credential ID.
                         // Wait, simply saving credentials doesn't update the Resource record in DB to point to it?
                         // Yes, we need to update the Resource record too.
                         
                         updateResourceUseCase(resourceToTest)
                     }.onFailure { e ->
                         Timber.e(e, "Failed to auto-save credentials")
                         sendEvent(EditResourceEvent.ShowError("Connection OK, but failed to save credentials for speed test."))
                         setLoading(false)
                         return@launch
                     }
                }
                
                // 3. Run Speed Test
                try {
                    networkSpeedTestUseCase.runSpeedTest(resourceToTest).collect { status ->
                         when (status) {
                             is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Progress -> {
                                 val msg = context.getString(status.messageResId)
                                 updateState { it.copy(speedTestStatus = msg, isTestingSpeed = true) }
                             }
                             is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Complete -> {
                                 val result = status.result
                                 // Pass raw values to View for localization
                                 val readSpeed = String.format(Locale.getDefault(), "%.2f", result.readSpeedMbps)
                                 val writeSpeed = String.format(Locale.getDefault(), "%.2f", result.writeSpeedMbps)
                                 
                                 val message = context.getString(
                                     R.string.speed_test_complete,
                                     readSpeed, writeSpeed, result.recommendedThreads
                                 )
                                 sendEvent(EditResourceEvent.TestResult(success = true, message = message))
                                 loadResource() // Refresh UI with new stats
                                 updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                             }
                             is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Error -> {
                                 val errorMsg = status.message
                                 if (resourceToTest.type == com.sza.fastmediasorter.domain.model.ResourceType.CLOUD && 
                                     (errorMsg.contains("not authenticated", ignoreCase = true) || errorMsg.contains("No Google Drive credentials", ignoreCase = true))) {
                                     sendEvent(EditResourceEvent.RequestCloudReAuthentication)
                                 } else {
                                     sendEvent(EditResourceEvent.TestResult(success = false, message = "Speed test failed: $errorMsg"))
                                 }
                                 updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                             }
                         }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Speed test failed")
                    sendEvent(EditResourceEvent.TestResult(success = false, message = "Speed test error: ${e.message}"))
                    updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                } finally {
                    // Ensure state is cleared if flow completes without verified completion (e.g. cancellation)
                    // But we handle it in Complete/Error above, so this might be redundant but safe
                    // updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                }
            }
            
            setLoading(false)
        }
    }
    
    fun runSpeedTest() {
        val current = state.value.currentResource ?: return
        
        // Only run for network resources
        if (current.type !in listOf(
            com.sza.fastmediasorter.domain.model.ResourceType.LOCAL,
            com.sza.fastmediasorter.domain.model.ResourceType.SMB,
            com.sza.fastmediasorter.domain.model.ResourceType.SFTP,
            com.sza.fastmediasorter.domain.model.ResourceType.FTP,
            com.sza.fastmediasorter.domain.model.ResourceType.CLOUD
        )) {
            sendEvent(EditResourceEvent.ShowMessage("Speed test only available for network resources"))
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateState { it.copy(isTestingSpeed = true, speedTestStatus = "Preparing...") }
            
            try {
                networkSpeedTestUseCase.runSpeedTest(current).collect { status ->
                     when (status) {
                         is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Progress -> {
                             val msg = context.getString(status.messageResId)
                             updateState { it.copy(speedTestStatus = msg) }
                         }
                         is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Complete -> {
                             // Final result
                             updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                             val result = status.result
                             val readSpeed = String.format(Locale.getDefault(), "%.2f", result.readSpeedMbps)
                             val writeSpeed = String.format(Locale.getDefault(), "%.2f", result.writeSpeedMbps)
                             sendEvent(EditResourceEvent.ShowMessageRes(
                                 R.string.speed_test_complete,
                                 listOf(readSpeed, writeSpeed, result.recommendedThreads)
                             ))
                             // Reload resource to show updated stats
                             loadResource()
                         }
                         is com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase.SpeedTestStatus.Error -> {
                             updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                             sendEvent(EditResourceEvent.ShowError("Speed test failed: ${status.message}"))
                         }
                     }
                }
            } catch (e: Exception) {
                Timber.e(e, "Speed test failed")
                updateState { it.copy(isTestingSpeed = false, speedTestStatus = "") }
                sendEvent(EditResourceEvent.ShowError("Speed test failed: ${e.message}"))
            }
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