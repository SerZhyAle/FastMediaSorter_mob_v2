package com.sza.fastmediasorter.ui.addresource

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.DiscoverNetworkResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.GetStorageVolumesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.NetworkHost
import com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase
import com.sza.fastmediasorter.domain.usecase.ScanLocalFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Live progress snapshot for the host-discovery scan. */
data class ScanProgress(val subnet: String, val done: Int, val total: Int)

data class AddResourceState(
    val resourcesToAdd: List<MediaResource> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val copyFromResource: MediaResource? = null,
    val foundNetworkHosts: List<NetworkHost> = emptyList(),
    val foundShares: List<String> = emptyList(),
    val isScanningShares: Boolean = false
)

sealed class AddResourceEvent {
    data class ShowError(val message: String) : AddResourceEvent()
    data class ShowMessage(val message: String) : AddResourceEvent()
    data class ShowTestResult(val message: String, val isSuccess: Boolean) : AddResourceEvent()
    data class LoadResourceForCopy(
        val resource: MediaResource,
        val username: String? = null,
        val password: String? = null,
        val domain: String? = null,
        val sshKey: String? = null,
        val sshPassphrase: String? = null
    ) : AddResourceEvent()
    data class ShowAccountPicker(
        val providerName: String,
        val accounts: List<String>
    ) : AddResourceEvent()
    /** Missing ACCESS_LOCAL_NETWORK permission - UI must show rationale dialog before retrying. */
    data object ShowLocalNetworkPermission : AddResourceEvent()
    /** Emitted after a successful share scan; UI should show a picker and fill the share name field. */
    data class ShowSharePicker(
        val server: String,
        val shares: List<String>,
        /** S0064: previously used / manually entered share names for this server. */
        val manualShares: List<String> = emptyList()
    ) : AddResourceEvent()
    /** SMB share scan completed successfully but returned no selectable shares. */
    data object ShowNoSharesFound : AddResourceEvent()

    /**
     * S1423: the one event neither cancel nor failure emits. [createdResourceIds] names the rows this
     * path actually inserted, so a caller that opted into pinning knows what to pin.
     */
    data class ResourcesAdded(val createdResourceIds: List<Long>) : AddResourceEvent()
}

/**
 * Orchestrator for the Add Resource screen.
 *
 * State/event plumbing lives here; protocol- and feature-specific logic is delegated
 * to coordinator classes via the [AddResourceBridge] interface:
 * - [AddResourceVirtualCoordinator]   - local/virtual folders, SAF picker handoff
 * - [AddResourceNetworkScanCoordinator] - host discovery + SMB share listing
 * - [AddResourceSmbCoordinator]       - SMB test/scan/add (scanned + manual)
 * - [AddResourceSftpFtpCoordinator]   - SFTP (password) + FTP test/add
 * - [AddResourceSftpKeyCoordinator]   - SFTP with SSH private key
 */
@HiltViewModel
// Screen-level ViewModel: every parameter is an independent Hilt collaborator, and folding them
// into a holder object would relocate the list rather than shorten it. The constructor was already
// past the threshold and baselined at twelve parameters; S1378's volume registry made it thirteen,
// which changes the signature the baseline entry recorded and resurfaces the finding.
@Suppress("LongParameterList")
class AddResourceViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    scanLocalFoldersUseCase: ScanLocalFoldersUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    mediaScannerFactory: MediaScannerFactory,
    smbOperationsUseCase: SmbOperationsUseCase,
    discoverNetworkResourcesUseCase: DiscoverNetworkResourcesUseCase,
    private val networkCredentialsRepository: NetworkCredentialsRepository,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val networkSpeedTestUseCase: NetworkSpeedTestUseCase,
    // S1378: the folder dialog offers connected removable volumes; the registry is reached from
    // here so the UI layer never holds a use case of its own (CLAUDE.md Rule 3 and the layering).
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<AddResourceState, AddResourceEvent>() {

    override fun getInitialState() = AddResourceState()

    private val bridge: AddResourceBridge = VmBridge()

    private val finalizer = AddResourceFinalizer(context, bridge, resourceRepository, mediaScannerFactory)

    private val virtualCoordinator = AddResourceVirtualCoordinator(
        context, scanLocalFoldersUseCase, addResourceUseCase, mediaScannerFactory,
        resourceRepository, settingsRepository, bridge
    )

    private val networkScanCoordinator = AddResourceNetworkScanCoordinator(
        context, discoverNetworkResourcesUseCase, smbOperationsUseCase,
        networkCredentialsRepository, bridge
    )

    private val smbCoordinator = AddResourceSmbCoordinator(
        context, smbOperationsUseCase, addResourceUseCase, resourceRepository,
        settingsRepository, finalizer, bridge
    )

    private val sftpFtpCoordinator = AddResourceSftpFtpCoordinator(
        context, addResourceUseCase, smbOperationsUseCase, resourceRepository,
        settingsRepository, finalizer, bridge
    )

    private val sftpKeyCoordinator = AddResourceSftpKeyCoordinator(
        context, addResourceUseCase, smbOperationsUseCase, settingsRepository, finalizer, bridge
    )

    // S0421: use case built manually (like the coordinators) - extending the baselined
    // 12-param constructor would resurface its LongParameterList finding.
    private val companionCoordinator = AddResourceCompanionCoordinator(
        context,
        com.sza.fastmediasorter.domain.usecase.companion.ImportCompanionConfigUseCase(
            context,
            com.sza.fastmediasorter.data.companion.CompanionConfigParser(),
            smbOperationsUseCase,
            addResourceUseCase,
            ioDispatcher
        ),
        bridge
    )

    /** S0421: one-action import of a Windows-companion `.fmscfg` config. */
    fun importCompanionConfig(uri: Uri) = companionCoordinator.importFromUri(uri)

    /** S0988: import a companion config from a scanned QR payload string. */
    fun importCompanionConfigFromQr(payload: String) = companionCoordinator.importFromPayload(payload)

    // ==================== Media type / settings helpers ====================

    /**
     * Supported media types from current settings. `allFiles` shortcuts to the full set -
     * individual toggles are ignored when the global switch is on.
     */
    suspend fun getSupportedMediaTypes(): Set<MediaType> {
        val settings = settingsRepository.getSettings().first()

        // allFiles is the override: honor the shortcut to avoid subtle drift
        // between "everything on" vs. individual toggles that happen to all be true.
        if (settings.allFiles) return MediaType.entries.toSet()

        val types = mutableSetOf<MediaType>()
        if (settings.supportImages) types.add(MediaType.IMAGE)
        if (settings.supportVideos) types.add(MediaType.VIDEO)
        if (settings.supportAudio) types.add(MediaType.AUDIO)
        if (settings.supportGifs) types.add(MediaType.GIF)
        if (settings.supportText) types.add(MediaType.TEXT)
        if (settings.supportPdf) types.add(MediaType.PDF)
        if (settings.supportEpub) types.add(MediaType.EPUB)
        if (settings.supportOfficeDocuments) types.add(MediaType.OFFICE_DOCUMENT)
        return types
    }

    suspend fun getSettings() = settingsRepository.getSettings().first()

    // ==================== Copy mode ====================

    fun loadResourceForCopy(resourceId: Long) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            try {
                val resource = resourceRepository.getResourceById(resourceId)
                if (resource == null) {
                    Timber.e("Resource not found for copy: $resourceId")
                    sendEvent(AddResourceEvent.ShowError(context.getString(R.string.addresource_resource_not_found)))
                    return@launch
                }

                Timber.d("Loaded resource for copy: ${resource.name}")
                updateState { it.copy(copyFromResource = resource) }

                // Credential hydration runs only when an id is attached - local/virtual
                // resources lack credentials entirely so we skip the repo round-trip.
                var username: String? = null
                var password: String? = null
                var domain: String? = null
                var sshKey: String? = null
                var sshPassphrase: String? = null

                val credentialsId = resource.credentialsId
                if (credentialsId != null) {
                    when (resource.type) {
                        ResourceType.SMB -> {
                            smbOperationsUseCaseLazy.getConnectionInfo(credentialsId).onSuccess { info ->
                                username = info.username
                                password = info.password
                                domain = info.domain
                            }
                        }
                        ResourceType.SFTP -> {
                            smbOperationsUseCaseLazy.getSftpCredentials(credentialsId).onSuccess { creds ->
                                username = creds.username
                                sshKey = creds.sshPrivateKey
                                // password column doubles as key passphrase when a private key is stored
                                if (creds.sshPrivateKey != null) {
                                    sshPassphrase = creds.password
                                } else {
                                    password = creds.password
                                }
                            }
                        }
                        ResourceType.FTP -> {
                            smbOperationsUseCaseLazy.getFtpCredentials(credentialsId).onSuccess { creds ->
                                username = creds.username
                                password = creds.password
                            }
                        }
                        else -> {}
                    }
                }

                sendEvent(AddResourceEvent.LoadResourceForCopy(
                    resource, username, password, domain, sshKey, sshPassphrase
                ))
            } catch (e: Exception) {
                Timber.e(e, "Failed to load resource for copy: $resourceId")
                sendEvent(AddResourceEvent.ShowError(context.getString(R.string.addresource_resource_load_failed)))
            }
        }
    }

    // ==================== Cloud account picker ====================

    fun loadCloudAccounts(providerName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            try {
                val allCreds = networkCredentialsRepository.getAllCredentials().first()
                val emails = allCreds
                    .filter { it.type == providerName }
                    .mapNotNull { it.accountId }
                    .filter { it.isNotEmpty() }
                    .distinct()

                sendEvent(AddResourceEvent.ShowAccountPicker(providerName, emails))
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cloud accounts for $providerName")
                sendEvent(AddResourceEvent.ShowError(context.getString(R.string.addresource_accounts_load_failed)))
            }
        }
    }

    // ==================== Virtual / local ====================

    fun scanLocalFolders() = virtualCoordinator.scanLocalFolders()
    fun addVirtualResource(virtualPath: String) = virtualCoordinator.addVirtualResource(virtualPath)
    suspend fun getExistingVirtualPaths(): Set<String> = virtualCoordinator.getExistingVirtualPaths()

    /** S1378: mounted removable volumes, in the one order every surface renders them in. */
    suspend fun getRemovableVolumes(): List<StorageVolumeInfo> =
        getStorageVolumesUseCase.removableOnly().filter { it.isMounted }
    fun addManualFolder(uri: Uri) = virtualCoordinator.addManualFolder(uri, null)
    fun addManualFolder(uri: Uri, accessPin: String?) = virtualCoordinator.addManualFolder(uri, accessPin)

    // ==================== Network discovery ====================

    fun scanNetwork() = networkScanCoordinator.scanNetwork()

    /** Responds within <1 second (spec NF-05). */
    fun stopScan() = networkScanCoordinator.stopScan()

    fun scanShares(
        server: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) = networkScanCoordinator.scanShares(server, username, password, domain, port)

    /**
     * S0064: Persists a manually entered share name to the per-server history so it
     * reappears in the share picker on the next session.
     */
    fun rememberManualShareName(server: String, port: Int, shareName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            networkCredentialsRepository.addManualShareName(server, port, shareName)
        }
    }

    // ==================== Selection / per-resource toggles ====================

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
                if (r.path == resource.path) r.copy(name = newName) else r
            }
            state.copy(resourcesToAdd = updated)
        }
    }

    fun toggleDestination(resource: MediaResource, isDestination: Boolean) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) r.copy(isDestination = isDestination) else r
            }
            // Auto-select so the user doesn't have to toggle twice
            state.copy(resourcesToAdd = updated, selectedPaths = state.selectedPaths + resource.path)
        }
    }

    fun toggleScanSubdirectories(resource: MediaResource, scanSubdirectories: Boolean) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) r.copy(scanSubdirectories = scanSubdirectories) else r
            }
            state.copy(resourcesToAdd = updated, selectedPaths = state.selectedPaths + resource.path)
        }
    }

    fun toggleReadOnlyMode(resource: MediaResource, isReadOnly: Boolean) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) {
                    // read-only and destination are mutually exclusive - clear destination when flipping on
                    r.copy(
                        isReadOnly = isReadOnly,
                        isDestination = if (isReadOnly) false else r.isDestination
                    )
                } else r
            }
            state.copy(resourcesToAdd = updated, selectedPaths = state.selectedPaths + resource.path)
        }
    }

    fun toggleMediaType(resource: MediaResource, type: MediaType) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) {
                    val currentTypes = r.supportedMediaTypes.toMutableSet()
                    if (type in currentTypes) currentTypes.remove(type) else currentTypes.add(type)
                    r.copy(supportedMediaTypes = currentTypes)
                } else r
            }
            state.copy(resourcesToAdd = updated, selectedPaths = state.selectedPaths + resource.path)
        }
    }

    fun toggleAllFiles(resource: MediaResource, allFiles: Boolean) {
        updateState { state ->
            val updated = state.resourcesToAdd.map { r ->
                if (r.path == resource.path) r.copy(allFiles = allFiles) else r
            }
            state.copy(resourcesToAdd = updated, selectedPaths = state.selectedPaths + resource.path)
        }
    }

    // ==================== Bulk / manual add ====================

    fun addSelectedResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)

            val current = state.value
            val selectedResources = current.resourcesToAdd
                .filter { it.path in current.selectedPaths }
                // id=0 ensures Room autoincrement even if the in-memory list has synthetic ids
                .map { it.copy(id = 0) }

            if (selectedResources.isEmpty()) {
                sendEvent(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_none_selected)))
                setLoading(false)
                return@launch
            }

            addResourceUseCase.addMultiple(selectedResources).onSuccess { addResult ->
                Timber.d("Added ${addResult.addedCount} resources")

                // speed tests for newly added network resources run outside viewModelScope
                // so they survive navigation away from the Add Resource screen
                applicationScope.launch(ioDispatcher) {
                    val allResources = resourceRepository.getAllResources().first()
                    selectedResources.forEach { resource ->
                        if (resource.type in AddResourceTypes.networkResourceTypes) {
                            val inserted = allResources.firstOrNull { it.path == resource.path }
                            if (inserted != null) triggerSpeedTest(inserted)
                        }
                    }
                }

                val count = addResult.addedCount
                val addedMsg = context.resources.getQuantityString(
                    R.plurals.added_n_resources, count, count
                )

                val message = if (addResult.destinationsFull) {
                    context.getString(
                        R.string.addresource_quick_sort_full_message,
                        addedMsg,
                        addResult.skippedDestinations
                    )
                } else {
                    addedMsg
                }
                sendEvent(AddResourceEvent.ShowMessage(message))
                sendEvent(AddResourceEvent.ResourcesAdded(addResult.createdResourceIds))
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
                sendEvent(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_resource_added)))
                sendEvent(AddResourceEvent.ResourcesAdded(listOf(id)))
            }.onFailure { e ->
                Timber.e(e, "Error adding resource")
                handleError(e)
            }
            setLoading(false)
        }
    }

    // ==================== SMB / SFTP / FTP delegation ====================

    fun testSmbConnection(
        server: String, shareName: String, username: String, password: String, domain: String, port: Int
    ) = smbCoordinator.testSmbConnection(server, shareName, username, password, domain, port)

    fun scanSmbShares(
        server: String, username: String, password: String, domain: String, port: Int
    ) = smbCoordinator.scanSmbShares(server, username, password, domain, port)

    fun addSmbResources(
        server: String, shareName: String, username: String, password: String, domain: String, port: Int
    ) = smbCoordinator.addSmbResources(server, shareName, username, password, domain, port)

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
    ) = smbCoordinator.addSmbResourceManually(
        server, shareName, username, password, domain, port, resourceName, comment,
        addToDestinations, supportedTypes, isReadOnly, allFiles, scanSubdirectories,
        rememberFileList, disableThumbnails, showSubfoldersAsItems, accessPin, profile
    )

    fun testSftpFtpConnection(
        protocolType: ResourceType, host: String, port: Int, username: String, password: String,
        expectedFingerprint: String? = null
    ) = sftpFtpCoordinator.testSftpFtpConnection(protocolType, host, port, username, password, expectedFingerprint)

    fun testSftpConnection(
        host: String, port: Int, username: String, password: String, expectedFingerprint: String? = null
    ) = sftpFtpCoordinator.testSftpConnection(host, port, username, password, expectedFingerprint)

    fun addSftpFtpResource(
        protocolType: ResourceType,
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        resourceName: String? = null,
        comment: String? = null,
        supportedTypes: Set<MediaType> = emptySet(),
        isReadOnly: Boolean = false,
        allFiles: Boolean = false,
        scanSubdirectories: Boolean = false,
        addToDestinations: Boolean = false,
        rememberFileList: Boolean = false,
        disableThumbnails: Boolean = false,
        showSubfoldersAsItems: Boolean = false,
        accessPin: String? = null,
        profile: ResourceProfile = ResourceProfile.NONE,
        hostKeyFingerprint: String? = null
    ) = sftpFtpCoordinator.addSftpFtpResource(
        protocolType, host, port, username, password, remotePath, resourceName, comment,
        supportedTypes, isReadOnly, allFiles, scanSubdirectories, addToDestinations,
        rememberFileList, disableThumbnails, showSubfoldersAsItems, accessPin, profile, hostKeyFingerprint
    )

    fun addSftpResource(
        host: String, port: Int, username: String, password: String, remotePath: String
    ) = sftpFtpCoordinator.addSftpResource(host, port, username, password, remotePath)

    fun testSftpConnectionWithKey(
        host: String, port: Int, username: String, privateKey: String, keyPassphrase: String?,
        expectedFingerprint: String? = null
    ) = sftpKeyCoordinator.testSftpConnectionWithKey(host, port, username, privateKey, keyPassphrase, expectedFingerprint)

    fun addSftpResourceWithKey(
        host: String,
        port: Int,
        username: String,
        privateKey: String,
        keyPassphrase: String?,
        remotePath: String,
        resourceName: String? = null,
        comment: String? = null,
        supportedTypes: Set<MediaType> = emptySet(),
        allFiles: Boolean = false,
        isReadOnly: Boolean = false,
        scanSubdirectories: Boolean = false,
        addToDestinations: Boolean = false,
        rememberFileList: Boolean = false,
        disableThumbnails: Boolean = false,
        showSubfoldersAsItems: Boolean = false,
        accessPin: String? = null,
        profile: ResourceProfile = ResourceProfile.NONE,
        hostKeyFingerprint: String? = null
    ) = sftpKeyCoordinator.addSftpResourceWithKey(
        host, port, username, privateKey, keyPassphrase, remotePath, resourceName, comment,
        supportedTypes, allFiles, isReadOnly, scanSubdirectories, addToDestinations,
        rememberFileList, disableThumbnails, showSubfoldersAsItems, accessPin, profile, hostKeyFingerprint
    )

    // ==================== Speed test ====================

    private suspend fun triggerSpeedTest(resource: MediaResource) {
        try {
            Timber.d("Triggering automatic speed test for ${resource.name}")
            networkSpeedTestUseCase.runSpeedTest(resource).collect { status ->
                when (status) {
                    is NetworkSpeedTestUseCase.SpeedTestStatus.Complete -> {
                        Timber.d("Speed test complete for ${resource.name}: Read=${status.result.readSpeedMbps} Mbps")
                    }
                    is NetworkSpeedTestUseCase.SpeedTestStatus.MeasurementUnavailable -> {
                        // Normal: no usable data returned (e.g. empty server response) - logged at DEBUG only
                        Timber.d("Speed test: measurement unavailable for ${resource.name}: ${status.reason}")
                    }
                    is NetworkSpeedTestUseCase.SpeedTestStatus.Error -> {
                        // Unexpected error - WARNING only, not ERROR, to keep ERROR channel clean
                        Timber.w("Speed test error for ${resource.name}: ${status.message}")
                    }
                    is NetworkSpeedTestUseCase.SpeedTestStatus.Progress -> Unit
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Automatic speed test failed for ${resource.name}")
        }
    }

    // ==================== Bridge to coordinators ====================

    // The copy-mode credential hydration needs SmbOperationsUseCase too - exposing it via
    // constructor field would break Hilt constructor wiring cleanliness, so re-reference
    // through the coordinator's own dependency chain instead.
    private val smbOperationsUseCaseLazy = smbOperationsUseCase

    /**
     * Narrow projection of this ViewModel's protected state/event API for coordinators.
     * Keeps BaseViewModel's `updateState`/`sendEvent`/etc. scoped to the VM class while
     * letting the coordinators do their work without reaching for reflection.
     */
    private inner class VmBridge : AddResourceBridge {
        override val vmScope: CoroutineScope get() = viewModelScope
        override val appScope: CoroutineScope = applicationScope
        override val ioDispatcher: CoroutineDispatcher = this@AddResourceViewModel.ioDispatcher
        override val exHandler = exceptionHandler
        override val stateValue: AddResourceState get() = state.value
        override fun mutate(transform: (AddResourceState) -> AddResourceState) = updateState(transform)
        override fun emit(event: AddResourceEvent) = sendEvent(event)
        override fun markLoading(loading: Boolean) = setLoading(loading)
        override fun reportError(e: Throwable) = handleError(e)
        override suspend fun supportedMediaTypes(): Set<MediaType> = getSupportedMediaTypes()
        override suspend fun runSpeedTest(resource: MediaResource) = triggerSpeedTest(resource)
    }
}
