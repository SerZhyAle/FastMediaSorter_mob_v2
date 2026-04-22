package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns SMB-specific flows: connection test, share listing, bulk add from scan results,
 * and manual single-share add. State/event mutation is delegated through [AddResourceBridge].
 */
internal class AddResourceSmbCoordinator(
    private val context: Context,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val finalizer: AddResourceFinalizer,
    private val bridge: AddResourceBridge
) {

    fun testSmbConnection(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            smbOperationsUseCase.testConnection(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { message ->
                Timber.d("SMB connection test successful: $message")
                bridge.emit(AddResourceEvent.ShowTestResult(message, isSuccess = true))
            }.onFailure { e ->
                Timber.e(e, "SMB connection test failed")
                bridge.emit(AddResourceEvent.ShowTestResult("Connection failed:\n\n${e.message}", isSuccess = false))
            }
            bridge.markLoading(false)
        }
    }

    fun scanSmbShares(
        server: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.mutate { it.copy(isScanning = true) }
            bridge.markLoading(true)

            smbOperationsUseCase.listShares(
                server = server,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { shares ->
                Timber.d("Found ${shares.size} SMB shares: $shares")

                val supportedTypes = bridge.supportedMediaTypes()
                val settings = settingsRepository.getSettings().first()
                val displayMode = if (settings.defaultGridMode) DisplayMode.GRID else DisplayMode.LIST

                val resources = shares.map { shareName ->
                    MediaResource(
                        id = 0,
                        name = shareName,
                        path = "smb://$server/$shareName",
                        type = ResourceType.SMB,
                        supportedMediaTypes = supportedTypes,
                        createdDate = System.currentTimeMillis(),
                        fileCount = 0,
                        isDestination = false,
                        destinationOrder = null,
                        // assume writable; actual value populated by post-add rescan
                        isWritable = true,
                        slideshowInterval = settings.slideshowInterval,
                        displayMode = displayMode,
                        sortMode = settings.defaultSortMode,
                        allFiles = settings.allFiles
                    )
                }

                bridge.mutate {
                    it.copy(
                        resourcesToAdd = it.resourcesToAdd + resources,
                        isScanning = false
                    )
                }

                // SMBJ detects only common share names — when results look thin,
                // remind users that custom-named shares need to be added manually.
                val message = when {
                    shares.size in 1..2 ->
                        "Found ${shares.size} share(s). Note: SMBJ library can only detect shares with common names. " +
                        "If you have more shares with custom names, please add them manually using 'Add This Resource' button."
                    shares.size >= 3 ->
                        "Found ${shares.size} shares. If you have more shares with custom names, add them manually."
                    else ->
                        "No shares found. Your shares may have custom names. Please use 'Add This Resource' button."
                }
                bridge.emit(AddResourceEvent.ShowMessage(message))
            }.onFailure { e ->
                Timber.e(e, "Failed to scan SMB shares")
                bridge.emit(AddResourceEvent.ShowError("Scan failed: ${e.message}"))
                bridge.mutate { it.copy(isScanning = false) }
            }

            bridge.markLoading(false)
        }
    }

    fun addSmbResources(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            val current = bridge.stateValue
            val selectedResources = current.resourcesToAdd
                .filter { it.path in current.selectedPaths && it.type == ResourceType.SMB }

            if (selectedResources.isEmpty()) {
                bridge.emit(AddResourceEvent.ShowMessage("No SMB resources selected"))
                bridge.markLoading(false)
                return@launch
            }

            smbOperationsUseCase.saveCredentials(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { credentialsId ->
                Timber.d("Saved SMB credentials with ID: $credentialsId")

                val resourcesWithCredentials = selectedResources.map { r ->
                    r.copy(id = 0, credentialsId = credentialsId)
                }

                addResourceUseCase.addMultiple(resourcesWithCredentials).onSuccess { addResult ->
                    Timber.d("Added ${addResult.addedCount} SMB resources")

                    val unavailableCount = finalizer.scanInsertedResources(
                        resources = resourcesWithCredentials,
                        credentialsId = credentialsId
                    )

                    val message = when {
                        addResult.destinationsFull && unavailableCount > 0 ->
                            "Added ${addResult.addedCount} SMB resources. " +
                            "Destinations are full (max 10). ${addResult.skippedDestinations} resources added without destination flag. " +
                            "$unavailableCount resource(s) are currently unavailable."
                        addResult.destinationsFull ->
                            "Added ${addResult.addedCount} SMB resources. " +
                            "Destinations are full (max 10). ${addResult.skippedDestinations} resources added without destination flag."
                        unavailableCount > 0 ->
                            "Added ${addResult.addedCount} SMB resources. " +
                            "$unavailableCount resource(s) are currently unavailable."
                        else -> "Added ${addResult.addedCount} SMB resources"
                    }

                    if (unavailableCount > 0) {
                        bridge.emit(AddResourceEvent.ShowError(message))
                    } else {
                        bridge.emit(AddResourceEvent.ShowMessage(message))
                    }
                    bridge.emit(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SMB resources")
                    bridge.emit(AddResourceEvent.ShowError("Failed to add resources: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SMB credentials")
                bridge.emit(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }

            bridge.markLoading(false)
        }
    }

    fun addSmbResourceManually(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String,
        port: Int,
        resourceName: String? = null,
        comment: String? = null,
        addToDestinations: Boolean = false,
        supportedTypes: Set<MediaType> = emptySet(),
        isReadOnly: Boolean = false,
        allFiles: Boolean = false,
        scanSubdirectories: Boolean = false,
        rememberFileList: Boolean = false,
        disableThumbnails: Boolean = false,
        showSubfoldersAsItems: Boolean = false,
        accessPin: String? = null,
        profile: ResourceProfile = ResourceProfile.NONE
    ) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)

            smbOperationsUseCase.saveCredentials(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { credentialsId ->
                Timber.d("Saved SMB credentials with ID: $credentialsId")

                val destSlot = finalizer.allocateDestinationSlot(addToDestinations, isReadOnly)
                    ?: return@onSuccess
                val (isDestination, destinationOrder, destinationColor) = destSlot

                // SMBJ quirk: some clients pass share with backslashes — normalize once here
                val normalizedShareName = shareName.replace('\\', '/')
                val path = "smb://$server/$normalizedShareName"
                val settings = settingsRepository.getSettings().first()
                val displayMode = if (settings.defaultGridMode) DisplayMode.GRID else DisplayMode.LIST
                val finalSupportedTypes = if (supportedTypes.isEmpty()) bridge.supportedMediaTypes() else supportedTypes
                val finalName = if (!resourceName.isNullOrBlank()) resourceName else normalizedShareName

                val resource = MediaResource(
                    id = 0,
                    name = finalName,
                    path = path,
                    type = ResourceType.SMB,
                    isDestination = isDestination,
                    destinationOrder = destinationOrder,
                    destinationColor = destinationColor,
                    credentialsId = credentialsId,
                    comment = comment,
                    displayMode = displayMode,
                    sortMode = settings.defaultSortMode,
                    slideshowInterval = settings.slideshowInterval,
                    supportedMediaTypes = finalSupportedTypes,
                    isReadOnly = isReadOnly,
                    allFiles = allFiles,
                    scanSubdirectories = scanSubdirectories,
                    rememberFileList = rememberFileList,
                    disableThumbnails = disableThumbnails,
                    showSubfoldersAsItems = showSubfoldersAsItems,
                    accessPin = accessPin?.ifBlank { null },
                    profile = profile
                )

                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { _ ->
                    Timber.d("Added manually entered SMB resource")

                    // Skip write-test when the user marked it read-only — avoids spurious
                    // ACCESS_DENIED → SMB auto-reset → toast. Only trigger speed test when
                    // writable (read-only resource can't create .speedtest_*.tmp).
                    val scanSuccessful = finalizer.scanInsertedResource(
                        resource = resource,
                        credentialsId = credentialsId,
                        skipWriteTest = isReadOnly,
                        onlyTestIfWritable = true
                    )

                    if (scanSuccessful) {
                        val msg = if (isReadOnly) {
                            context.getString(R.string.smb_resource_added_readonly)
                        } else {
                            context.getString(R.string.smb_resource_added_success)
                        }
                        bridge.emit(AddResourceEvent.ShowMessage(msg))
                    } else {
                        bridge.emit(AddResourceEvent.ShowError(
                            context.getString(R.string.smb_resource_added_unavailable, shareName)
                        ))
                    }
                    bridge.emit(AddResourceEvent.ResourcesAdded)
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SMB resource")
                    bridge.emit(AddResourceEvent.ShowError("Failed to add resource: ${e.message}"))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SMB credentials")
                bridge.emit(AddResourceEvent.ShowError("Failed to save credentials: ${e.message}"))
            }

            bridge.markLoading(false)
        }
    }
}
