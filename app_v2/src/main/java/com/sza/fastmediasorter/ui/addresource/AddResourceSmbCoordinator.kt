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
                bridge.emit(AddResourceEvent.ShowTestResult(
                    context.getString(R.string.addresource_connection_failed),
                    isSuccess = false
                ))
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

            // S3735: slot before credentials - a refused add must not orphan a credentials row.
            val destSlot = finalizer.allocateDestinationSlot(addToDestinations, isReadOnly)
                ?: return@launch
            val (isDestination, destinationOrder, destinationColor) = destSlot

            smbOperationsUseCase.saveCredentials(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            ).onSuccess { credentialsId ->
                Timber.d("Saved SMB credentials with ID: $credentialsId")

                // SMBJ quirk: some clients pass share with backslashes - normalize once here
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

                addResourceUseCase.addMultiple(listOf(resource)).onSuccess { addResult ->
                    Timber.d("Added manually entered SMB resource")

                    // Skip write-test when the user marked it read-only - avoids spurious
                    // ACCESS_DENIED → SMB auto-reset → toast. Only trigger speed test when
                    // writable (read-only resource can't create .speedtest_*.tmp).
                    val scanSuccessful = finalizer.scanInsertedResource(
                        resource = resource,
                        createdId = addResult.createdResourceIds.firstOrNull(),
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
                    bridge.emit(AddResourceEvent.ResourcesAdded(addResult.createdResourceIds))
                }.onFailure { e ->
                    Timber.e(e, "Failed to add SMB resource")
                    bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_add_failed)))
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to save SMB credentials")
                bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_save_credentials_failed)))
            }

            bridge.markLoading(false)
        }
    }
}
