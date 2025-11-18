package com.sza.fastmediasorter.ui.addresource

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.ScanLocalFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

data class AddResourceState(
    val resourcesToAdd: List<MediaResource> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isScanning: Boolean = false,
    val copyFromResource: MediaResource? = null
)

sealed class AddResourceEvent {
    data class ShowError(val message: String) : AddResourceEvent()
    data class ShowMessage(val message: String) : AddResourceEvent()
    data class ShowTestResult(val message: String, val isSuccess: Boolean) : AddResourceEvent()
    data class LoadResourceForCopy(val resource: MediaResource) : AddResourceEvent()
    object ResourcesAdded : AddResourceEvent()
}

@HiltViewModel
class AddResourceViewModel @Inject constructor(
    private val scanLocalFoldersUseCase: ScanLocalFoldersUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository,
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
    
    /**
     * Load resource data for copy mode
     */
    fun loadResourceForCopy(resourceId: Long) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            try {
                val resource = resourceRepository.getResourceById(resourceId)
                if (resource != null) {
                    Timber.d("Loaded resource for copy: ${resource.name}")
                    updateState { it.copy(copyFromResource = resource) }
                    sendEvent(AddResourceEvent.LoadResourceForCopy(resource))
                } else {
                    Timber.e("Resource not found for copy: $resourceId")
                    sendEvent(AddResourceEvent.ShowError("Resource not found"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load resource for copy: $resourceId")
                sendEvent(AddResourceEvent.ShowError("Failed to load resource: ${e.message}"))
            }
        }
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
                // For content:// URIs (SAF), use uri.toString() to preserve full URI
                // For file:// URIs, use uri.path for backward compatibility
                val path = if (uri.scheme == "content") {
                    uri.toString()
                } else {
                    uri.path ?: ""
                }
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
                    withTimeout(5000) { // 5 second timeout
                        scanner.isWritable(path, credentialsId = null)
                    }
                } catch (e: TimeoutCancellationException) {
                    Timber.w("Write permission check timed out for $path")
                    false
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
                    var unavailableCount = 0
                    viewModelScope.launch(ioDispatcher) {
                        resourcesWithCredentials.forEach { resource ->
                            try {
                                val scanner = mediaScannerFactory.getScanner(resource.type)
                                val supportedTypes = getSupportedMediaTypes()
                                
                                val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                                val isWritable = withTimeout(5000) { // 5 second timeout
                                    scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                                }
                                
                                // Update resource with real values
                                val updatedResource = resource.copy(
                                    fileCount = fileCount,
                                    isWritable = isWritable
                                )
                                resourceRepository.updateResource(updatedResource)
                                
                                Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to scan resource ${resource.name}")
                                unavailableCount++
                            }
                        }
                    }.join() // Wait for all scans to complete
                    
                    val message = when {
                        addResult.destinationsFull && unavailableCount > 0 -> {
                            "Added ${addResult.addedCount} SMB resources. " +
                            "Destinations are full (max 10). ${addResult.skippedDestinations} resources added without destination flag. " +
                            "$unavailableCount resource(s) are currently unavailable."
                        }
                        addResult.destinationsFull -> {
                            "Added ${addResult.addedCount} SMB resources. " +
                            "Destinations are full (max 10). ${addResult.skippedDestinations} resources added without destination flag."
                        }
                        unavailableCount > 0 -> {
                            "Added ${addResult.addedCount} SMB resources. " +
                            "$unavailableCount resource(s) are currently unavailable."
                        }
                        else -> "Added ${addResult.addedCount} SMB resources"
                    }
                    
                    if (unavailableCount > 0) {
                        sendEvent(AddResourceEvent.ShowError(message))
                    } else {
                        sendEvent(AddResourceEvent.ShowMessage(message))
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
                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { _ ->
                    Timber.d("Added manually entered SMB resource")
                    
                    // Scan resource to update fileCount and isWritable
                    var scanSuccessful = false
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val scanner = mediaScannerFactory.getScanner(resource.type)
                            val supportedTypes = getSupportedMediaTypes()
                            
                            val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                            val isWritable = withTimeout(5000) { // 5 second timeout
                                scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                            }
                            
                            // Update resource with real values
                            val updatedResource = resource.copy(
                                fileCount = fileCount,
                                isWritable = isWritable
                            )
                            resourceRepository.updateResource(updatedResource)
                            
                            Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                            scanSuccessful = true
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to scan resource ${resource.name}")
                            // Keep resource with fileCount=0, isWritable=false
                        }
                    }.join() // Wait for scan to complete
                    
                    if (scanSuccessful) {
                        sendEvent(AddResourceEvent.ShowMessage("SMB resource added successfully"))
                    } else {
                        sendEvent(AddResourceEvent.ShowError(
                            "SMB resource '$shareName' added but is currently unavailable. " +
                            "Check that the share exists and is accessible from this device."
                        ))
                    }
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
     * Test SFTP or FTP connection
     */
    fun testSftpFtpConnection(
        protocolType: ResourceType, // SFTP or FTP
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
            
            when (protocolType) {
                ResourceType.SFTP -> {
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
                }
                ResourceType.FTP -> {
                    smbOperationsUseCase.testFtpConnection(
                        host = host,
                        port = port,
                        username = username,
                        password = password
                    ).onSuccess { message ->
                        Timber.d("FTP test connection successful")
                        sendEvent(AddResourceEvent.ShowTestResult(message, isSuccess = true))
                    }.onFailure { e ->
                        Timber.e(e, "FTP test connection failed")
                        val errorMessage = "Connection failed: ${e.message}"
                        sendEvent(AddResourceEvent.ShowTestResult(errorMessage, isSuccess = false))
                    }
                }
                else -> {
                    sendEvent(AddResourceEvent.ShowError("Invalid protocol type"))
                }
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Test SFTP connection (legacy method - kept for compatibility)
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
     * Add SFTP or FTP resource
     */
    fun addSftpFtpResource(
        protocolType: ResourceType, // SFTP or FTP
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
            
            val protocolName = if (protocolType == ResourceType.SFTP) "SFTP" else "FTP"
            val protocolLower = protocolName.lowercase()
            
            // Save credentials first
            val credentialsResult = when (protocolType) {
                ResourceType.SFTP -> smbOperationsUseCase.saveSftpCredentials(
                    host = host,
                    port = port,
                    username = username,
                    password = password
                )
                ResourceType.FTP -> smbOperationsUseCase.saveFtpCredentials(
                    host = host,
                    port = port,
                    username = username,
                    password = password
                )
                else -> Result.failure(Exception("Invalid protocol type"))
            }
            
            credentialsResult.onSuccess { credentialsId ->
                Timber.d("Saved $protocolName credentials with ID: $credentialsId")
                
                // Create resource
                val path = "$protocolLower://$host:$port$remotePath"
                val resourceName = if (remotePath == "/" || remotePath.isBlank()) {
                    "$username@$host"
                } else {
                    remotePath.substringAfterLast('/')
                }
                
                val resource = MediaResource(
                    id = 0, // Ensure autoincrement
                    name = resourceName,
                    path = path,
                    type = protocolType,
                    isDestination = false,
                    credentialsId = credentialsId
                )
                
                // Add resource to database
                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { _ ->
                    Timber.d("Added $protocolName resource to DB")
                    
                    // Get the inserted resource from DB to get real ID
                    val allResources = resourceRepository.getAllResources().first()
                    Timber.d("Total resources in DB: ${allResources.size}")
                    
                    val insertedResource = allResources.find { it.path == resource.path && it.type == resource.type }
                    
                    if (insertedResource == null) {
                        Timber.e("Failed to find inserted resource. Looking for path=${resource.path}, type=${resource.type}")
                        Timber.e("Available resources: ${allResources.map { "id=${it.id}, path=${it.path}, type=${it.type}" }}")
                        sendEvent(AddResourceEvent.ShowError("Resource was added but could not be retrieved"))
                        setLoading(false)
                        return@launch
                    }
                    
                    Timber.d("Found inserted resource: id=${insertedResource.id}, credentialsId=${insertedResource.credentialsId}")
                    
                    // Scan resource to update fileCount and isWritable
                    var scanSuccessful = false
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val scanner = mediaScannerFactory.getScanner(insertedResource.type)
                            val supportedTypes = getSupportedMediaTypes()
                            
                            Timber.d("Starting scan: path=${insertedResource.path}, credentialsId=${insertedResource.credentialsId}, supportedTypes=$supportedTypes")
                            
                            val fileCount = scanner.getFileCount(insertedResource.path, supportedTypes, credentialsId = insertedResource.credentialsId)
                            val isWritable = withTimeout(5000) { // 5 second timeout
                                scanner.isWritable(insertedResource.path, credentialsId = insertedResource.credentialsId)
                            }
                            
                            Timber.d("Scan completed: fileCount=$fileCount, isWritable=$isWritable")
                            
                            // Update resource with real values
                            val updatedResource = insertedResource.copy(
                                fileCount = fileCount,
                                isWritable = isWritable
                            )
                            resourceRepository.updateResource(updatedResource)
                            
                            Timber.d("Updated resource in DB: ${insertedResource.name}")
                            scanSuccessful = true
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to scan resource ${insertedResource.name}")
                        }
                    }.join() // Wait for scan to complete
                    
                    if (scanSuccessful) {
                        sendEvent(AddResourceEvent.ShowMessage("$protocolName resource added successfully"))
                    } else {
                        sendEvent(AddResourceEvent.ShowError(
                            "$protocolName resource '$resourceName' added but is currently unavailable. " +
                            "Check that the remote path exists and is accessible."
                        ))
                    }
                    sendEvent(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add $protocolName resource")
                    sendEvent(AddResourceEvent.ShowError("Failed to add resource: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save $protocolName credentials")
                sendEvent(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Add SFTP resource (legacy method - kept for compatibility)
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
                    var scanSuccessful = false
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val scanner = mediaScannerFactory.getScanner(resource.type)
                            val supportedTypes = getSupportedMediaTypes()
                            
                            val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                            val isWritable = withTimeout(5000) { // 5 second timeout
                                scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                            }
                            
                            // Update resource with real values
                            val updatedResource = resource.copy(
                                fileCount = fileCount,
                                isWritable = isWritable
                            )
                            resourceRepository.updateResource(updatedResource)
                            
                            Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                            scanSuccessful = true
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to scan resource ${resource.name}")
                        }
                    }.join() // Wait for scan to complete
                    
                    if (scanSuccessful) {
                        sendEvent(AddResourceEvent.ShowMessage("SFTP resource added successfully"))
                    } else {
                        sendEvent(AddResourceEvent.ShowError(
                            "SFTP resource '$resourceName' added but is currently unavailable. " +
                            "Check that the remote path exists and is accessible."
                        ))
                    }
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
    
    /**
     * Test SFTP connection with SSH private key
     */
    fun testSftpConnectionWithKey(
        host: String,
        port: Int,
        username: String,
        privateKey: String,
        keyPassphrase: String?
    ) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val result = smbOperationsUseCase.testSftpConnection(
                host = host,
                port = port,
                username = username,
                password = "", // Not used for key auth
                privateKey = privateKey,
                keyPassphrase = keyPassphrase
            )
            
            result.onSuccess { message ->
                Timber.d("SFTP SSH key connection test successful: $message")
                sendEvent(AddResourceEvent.ShowTestResult(message, true))
            }.onFailure { e ->
                Timber.e(e, "SFTP SSH key connection test failed")
                val errorMessage = "Connection failed: ${e.message}"
                sendEvent(AddResourceEvent.ShowTestResult(errorMessage, false))
            }
            
            setLoading(false)
        }
    }
    
    /**
     * Add SFTP resource with SSH private key
     */
    fun addSftpResourceWithKey(
        host: String,
        port: Int,
        username: String,
        privateKey: String,
        keyPassphrase: String?,
        remotePath: String
    ) {
        if (host.isBlank()) {
            sendEvent(AddResourceEvent.ShowError("Host is required"))
            return
        }
        
        if (privateKey.isBlank()) {
            sendEvent(AddResourceEvent.ShowError("SSH private key is required"))
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Save credentials with SSH key (passphrase stored in password field)
            smbOperationsUseCase.saveSftpCredentials(
                host = host,
                port = port,
                username = username,
                password = keyPassphrase ?: "", // Passphrase stored in password field
                privateKey = privateKey
            ).onSuccess { credentialsId ->
                Timber.d("Saved SFTP SSH key credentials with ID: $credentialsId")
                
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
                    Timber.d("Added SFTP resource with SSH key")
                    
                    // Scan resource to update fileCount and isWritable
                    var scanSuccessful = false
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val scanner = mediaScannerFactory.getScanner(resource.type)
                            val supportedTypes = getSupportedMediaTypes()
                            
                            val fileCount = scanner.getFileCount(resource.path, supportedTypes, credentialsId = resource.credentialsId)
                            val isWritable = withTimeout(5000) { // 5 second timeout
                                scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                            }
                            
                            // Get the inserted resource from DB to get real ID
                            val allResources = resourceRepository.getAllResources().first()
                            val insertedResource = allResources.firstOrNull { 
                                it.path == resource.path && it.credentialsId == credentialsId 
                            }
                            
                            if (insertedResource == null) {
                                Timber.e("Failed to find inserted resource in database")
                                return@launch
                            }
                            
                            // Update resource with real values
                            val updatedResource = insertedResource.copy(
                                fileCount = fileCount,
                                isWritable = isWritable
                            )
                            resourceRepository.updateResource(updatedResource)
                            
                            Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                            scanSuccessful = true
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to scan resource ${resource.name}")
                        }
                    }.join() // Wait for scan to complete
                    
                    if (scanSuccessful) {
                        sendEvent(AddResourceEvent.ShowMessage("SFTP resource added successfully"))
                    } else {
                        sendEvent(AddResourceEvent.ShowError(
                            "SFTP resource '$resourceName' added but is currently unavailable. " +
                            "Check that the remote path exists and is accessible."
                        ))
                    }
                    sendEvent(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SFTP resource")
                    sendEvent(AddResourceEvent.ShowError("Failed to add resource: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SFTP SSH key credentials")
                sendEvent(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }
            
            setLoading(false)
        }
    }
}
