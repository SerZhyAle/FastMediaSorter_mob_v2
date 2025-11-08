package com.sza.fastmediasorter_v2.ui.addresource

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.core.util.DestinationColors
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
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
    data class ShowTestResult(val message: String, val isSuccess: Boolean) : AddResourceEvent()
    object ResourcesAdded : AddResourceEvent()
}

@HiltViewModel
class AddResourceViewModel @Inject constructor(
    private val scanLocalFoldersUseCase: ScanLocalFoldersUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: com.sza.fastmediasorter_v2.domain.repository.ResourceRepository,
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
                    scanner.getFileCount(path, supportedTypes, credentialsId = null)
                } catch (e: Exception) {
                    Timber.e(e, "Error counting files in $path")
                    0
                }
                
                val isWritable = try {
                    scanner.isWritable(path, credentialsId = null)
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
                sendEvent(AddResourceEvent.ShowTestResult(message, isSuccess = true))
            }.onFailure { e ->
                Timber.e(e, "SMB connection test failed")
                val errorMessage = "Connection failed:\n\n${e.message}"
                sendEvent(AddResourceEvent.ShowTestResult(errorMessage, isSuccess = false))
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
                
                // Show message with warning if only few shares found
                val message = if (shares.size > 0 && shares.size < 3) {
                    "Found ${shares.size} share(s). Note: SMBJ library can only detect shares with common names. " +
                    "If you have more shares with custom names, please add them manually using 'Add This Resource' button."
                } else if (shares.size >= 3) {
                    "Found ${shares.size} shares. If you have more shares with custom names, add them manually."
                } else {
                    "No shares found. Your shares may have custom names. Please use 'Add This Resource' button."
                }
                
                sendEvent(AddResourceEvent.ShowMessage(message))
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
                    
                    // Scan each added resource to update fileCount and isWritable
                    viewModelScope.launch(ioDispatcher) {
                        resourcesWithCredentials.forEach { resource ->
                            try {
                                val scanner = mediaScannerFactory.getScanner(resource.type)
                                val supportedTypes = getSupportedMediaTypes()
                                
                                val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                                val isWritable = scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                                
                                // Update resource with real values
                                val updatedResource = resource.copy(
                                    fileCount = fileCount,
                                    isWritable = isWritable
                                )
                                resourceRepository.updateResource(updatedResource)
                                
                                Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to scan resource ${resource.name}")
                            }
                        }
                    }
                    
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
    
    /**
     * Add manually entered SMB resource (without scanning)
     */
    fun addSmbResourceManually(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        port: Int,
        addToDestinations: Boolean = false
    ) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
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
                
                // Determine destination settings if needed
                val (isDestination, destinationOrder, destinationColor) = if (addToDestinations) {
                    val allResources = resourceRepository.getAllResources().first()
                    val destinations = allResources.filter { it.isDestination }
                    
                    if (destinations.size >= 10) {
                        sendEvent(AddResourceEvent.ShowError("Maximum 10 destinations allowed"))
                        setLoading(false)
                        return@launch
                    }
                    
                    val maxOrder = destinations.mapNotNull { it.destinationOrder }.maxOrNull() ?: 0
                    val nextOrder = maxOrder + 1
                    val color = DestinationColors.getColorForDestination(nextOrder)
                    Triple(true, nextOrder, color)
                } else {
                    Triple(false, 0, 0)
                }
                
                // Create resource
                val path = "smb://$server/$shareName"
                val resource = MediaResource(
                    id = 0, // Ensure autoincrement
                    name = shareName,
                    path = path,
                    type = ResourceType.SMB,
                    isDestination = isDestination,
                    destinationOrder = destinationOrder,
                    destinationColor = destinationColor,
                    credentialsId = credentialsId
                )
                
                // Add resource to database
                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { addResult ->
                    Timber.d("Added manually entered SMB resource")
                    
                    // Scan resource to update fileCount and isWritable
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val scanner = mediaScannerFactory.getScanner(resource.type)
                            val supportedTypes = getSupportedMediaTypes()
                            
                            val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                            val isWritable = scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                            
                            // Update resource with real values
                            val updatedResource = resource.copy(
                                fileCount = fileCount,
                                isWritable = isWritable
                            )
                            resourceRepository.updateResource(updatedResource)
                            
                            Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to scan resource ${resource.name}")
                        }
                    }
                    
                    sendEvent(AddResourceEvent.ShowMessage("SMB resource added successfully"))
                    sendEvent(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SMB resource")
                    sendEvent(AddResourceEvent.ShowError("Failed to add resource: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SMB credentials")
                sendEvent(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Get current app settings (for showing detailed errors)
     */
    suspend fun getSettings() = settingsRepository.getSettings().first()
    
    // ========== SFTP Operations ==========
    
    /**
     * Test SFTP connection
     */
    fun testSftpConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        if (host.isBlank()) {
            sendEvent(AddResourceEvent.ShowError("Host is required"))
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            smbOperationsUseCase.testSftpConnection(
                host = host,
                port = port,
                username = username,
                password = password
            ).onSuccess { message ->
                Timber.d("SFTP test connection successful")
                sendEvent(AddResourceEvent.ShowTestResult(message, isSuccess = true))
            }.onFailure { e ->
                Timber.e(e, "SFTP test connection failed")
                val errorMessage = "Connection failed: ${e.message}"
                sendEvent(AddResourceEvent.ShowTestResult(errorMessage, isSuccess = false))
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Add SFTP resource
     */
    fun addSftpResource(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ) {
        if (host.isBlank()) {
            sendEvent(AddResourceEvent.ShowError("Host is required"))
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Save credentials first
            smbOperationsUseCase.saveSftpCredentials(
                host = host,
                port = port,
                username = username,
                password = password
            ).onSuccess { credentialsId ->
                Timber.d("Saved SFTP credentials with ID: $credentialsId")
                
                // Create resource
                val path = "sftp://$host:$port$remotePath"
                val resourceName = if (remotePath == "/" || remotePath.isBlank()) {
                    "$username@$host"
                } else {
                    remotePath.substringAfterLast('/')
                }
                
                val resource = MediaResource(
                    id = 0, // Ensure autoincrement
                    name = resourceName,
                    path = path,
                    type = ResourceType.SFTP,
                    isDestination = false,
                    credentialsId = credentialsId
                )
                
                // Add resource to database
                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { _ ->
                    Timber.d("Added SFTP resource")
                    
                    // Scan resource to update fileCount and isWritable
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val scanner = mediaScannerFactory.getScanner(resource.type)
                            val supportedTypes = getSupportedMediaTypes()
                            
                            val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                            val isWritable = scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                            
                            // Update resource with real values
                            val updatedResource = resource.copy(
                                fileCount = fileCount,
                                isWritable = isWritable
                            )
                            resourceRepository.updateResource(updatedResource)
                            
                            Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to scan resource ${resource.name}")
                        }
                    }
                    
                    sendEvent(AddResourceEvent.ShowMessage("SFTP resource added successfully"))
                    sendEvent(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SFTP resource")
                    sendEvent(AddResourceEvent.ShowError("Failed to add resource: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SFTP credentials")
                sendEvent(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }
            
            setLoading(false)
        }
    }
}
