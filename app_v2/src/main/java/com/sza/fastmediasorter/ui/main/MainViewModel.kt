package com.sza.fastmediasorter.ui.main

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.core.ui.UiState
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceShareFormat
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.DedupAuthAccountsUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteResourceUseCase
import com.sza.fastmediasorter.domain.usecase.ExportResourcesToFileUseCase
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.MigrateCameraResourceUseCase
import com.sza.fastmediasorter.domain.usecase.MigrateS0059UseCase
import com.sza.fastmediasorter.domain.usecase.ProvisionDefaultResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ProvisionDownloadsDestinationUseCase
import com.sza.fastmediasorter.domain.usecase.ReadMainListSessionUseCase
import com.sza.fastmediasorter.domain.usecase.RefreshResourceFileCountsUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveResourceIconUseCase
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.domain.usecase.SaveMainListSessionUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.domain.usecase.companion.ExportCompanionConfigUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObservePinnedStreamSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UnpinStreamSourceUseCase
import com.sza.fastmediasorter.ui.main.helpers.MainListSessionManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceFilterManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceNavigationCoordinator
import com.sza.fastmediasorter.ui.main.helpers.ResourceOrderManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class ResourceTab {
    ALL,
    LOCAL,
    SMB,
    FTP_SFTP,
    CLOUD,
    FAVORITES
}

data class MainState(
    val resources: List<MediaResource> = emptyList(),
    val isResourceGridMode: Boolean = false,
    val selectedResource: MediaResource? = null,
    val sortMode: SortMode = SortMode.MANUAL,
    val filterByType: Set<ResourceType>? = null,
    val filterByMediaType: Set<MediaType>? = null,
    val filterByName: String? = null,
    val activeResourceTab: ResourceTab = ResourceTab.ALL,
    val previousTab: ResourceTab? = null, // Tab to restore when returning from Favorites
    val isNavigating: Boolean = false, // Prevents multiple simultaneous navigation clicks
    val navigationMessage: String? = null // Status message during navigation
)

sealed class MainEvent {
    data class ShowError(val message: String, val details: String? = null) : MainEvent()
    data class ShowInfo(val message: String, val details: String? = null) : MainEvent()
    data class ShowMessage(val message: String) : MainEvent()
    data class ShowResourceMessage(val resId: Int, val args: Array<Any> = emptyArray()) : MainEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ShowResourceMessage
            if (resId != other.resId) return false
            if (!args.contentEquals(other.args)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = resId
            result = 31 * result + args.contentHashCode()
            return result
        }
    }
    data class RequestPassword(
        val resource: com.sza.fastmediasorter.domain.model.MediaResource,
        val forSlideshow: Boolean = false
    ) : MainEvent()
    data class NavigateToBrowse(val resourceId: Long, val skipAvailabilityCheck: Boolean = false) : MainEvent()
    data class NavigateToPlayerSlideshow(val resourceId: Long) : MainEvent()
    data class NavigateToPlayerRandomMusic(val resourceId: Long) : MainEvent()
    data class NavigateToEditResource(val resourceId: Long) : MainEvent()
    data class NavigateToAddResource(val preselectedTab: ResourceTab) : MainEvent()
    data class NavigateToAddResourceCopy(val copyResourceId: Long) : MainEvent()
    object NavigateToSettings : MainEvent()
    object NavigateToFavorites : MainEvent()
    data class ScanProgress(val currentFile: String?, val scannedCount: Int) : MainEvent()
    object ScanComplete : MainEvent()
    object ConfirmRescanWithVirtualResources : MainEvent()

    // S0422: a single resource has been written to [filePath]; the host shares it via ACTION_SEND.
    data class ShareResourceFile(val filePath: String) : MainEvent()

    // S0984: an SFTP access .fmscfg has been written to [filePath]; the host shares it via ACTION_SEND.
    data class ShareCompanionConfigFile(val filePath: String) : MainEvent()

    // S1039: show the compact .fmscfg [payload] as a QR (via CompanionQrShareActivity).
    data class ShowCompanionQr(
        val payload: String,
        val resourceName: String,
        val passwordIncluded: Boolean
    ) : MainEvent()
}

@HiltViewModel
// S0783 adds favoritesUseCase to an already-large central VM constructor (channel favorite toggle for
// the main-window streams panel). The dependency list was already past the threshold; kept whole.
@Suppress("LongParameterList")
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val exportResourcesToFileUseCase: ExportResourcesToFileUseCase,
    private val exportCompanionConfigUseCase: ExportCompanionConfigUseCase,
    // S0869: Lazy so MainViewModel construction on the main thread does not force provideAppDatabase()
    // via this path. Dereferenced with .get() only inside coroutine bodies (mirrors S0194). NOTE: the
    // sibling resource use-cases above are still non-Lazy and transitively open the DB at construction;
    // the off-main Room warm-up in FastMediaSorterApp is the load-bearing fix (S0869 §3 step 4).
    private val resourceRepository: dagger.Lazy<ResourceRepository>,
    private val mediaScannerFactory: MediaScannerFactory,
    private val settingsRepository: SettingsRepository,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val provisionDefaultResourcesUseCase: ProvisionDefaultResourcesUseCase,
    private val provisionDownloadsDestinationUseCase: ProvisionDownloadsDestinationUseCase,
    private val migrateCameraResourceUseCase: MigrateCameraResourceUseCase,
    private val migrateS0059UseCase: MigrateS0059UseCase,
    private val dedupAuthAccountsUseCase: DedupAuthAccountsUseCase,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase,
    // S0783: shared Favorites - toggle a channel favorite and observe favorited channel URLs for the panel.
    private val favoritesUseCase: FavoritesUseCase,
    private val appShortcutsManager: com.sza.fastmediasorter.core.AppShortcutsManager,
    private val networkContextAnalyzer: com.sza.fastmediasorter.core.network.NetworkContextAnalyzer,
    private val remoteSourceGate: RemoteSourceAvailabilityGate,
    private val refreshResourceFileCountsUseCase: RefreshResourceFileCountsUseCase,
    // S1195: capture + streams-panel operations the Activity used to inject and hand to its managers.
    private val saveCapturedMediaUseCase: SaveCapturedMediaUseCase,
    private val observePinnedStreamSourcesUseCase: ObservePinnedStreamSourcesUseCase,
    private val unpinStreamSourceUseCase: UnpinStreamSourceUseCase,
    // S2199: last-session sort and filters for this list.
    private val readMainListSessionUseCase: ReadMainListSessionUseCase,
    private val saveMainListSessionUseCase: SaveMainListSessionUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<MainState, MainEvent>() {

    override fun getInitialState() = MainState()

    val resourceListUiState: StateFlow<UiState<MainState>> =
        createUiState { currentState -> currentState.resources.isEmpty() }

    // S0783: URLs of favorited channels, so the streams-panel per-channel menu can label its action add
    // vs remove. Eager so `.value` is current when the panel menu opens.
    val favoriteStreamIdentities: StateFlow<Set<String>> = favoritesUseCase.observeFavoriteStreamIdentities()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * S1842: whether this channel is favourited. Matching is by channel identity, so a catalog that
     * re-published the channel under a cosmetically different address no longer darkens the star.
     * Call sites ask this instead of comparing the raw URL themselves.
     */
    fun isFavoriteChannel(source: StreamSourceEntity): Boolean =
        favoriteStreamIdentities.value.contains(favoritesUseCase.channelIdentity(source))

    /** S0756: pinned channels in pin order, for the main-window streams panel. */
    fun pinnedStreamSources(): Flow<List<StreamSourceEntity>> = observePinnedStreamSourcesUseCase()

    /** S0770: drop a channel's pin; the pinned-sources flow then stops emitting it. */
    fun unpinStreamSource(id: String) {
        viewModelScope.launch { unpinStreamSourceUseCase(id) }
    }

    /** S0523/S0563: persist a freshly captured photo/video into the device's public folders. */
    suspend fun saveCapturedMedia(captured: File, isVideo: Boolean): SaveResult =
        saveCapturedMediaUseCase(captured, isVideo)

    /**
     * The main screen routes BaseViewModel error state into a dedicated full-screen surface,
     * so this path must stay resource-backed instead of exposing raw exception text.
     */
    override fun handleError(throwable: Throwable) {
        setError(context.getString(R.string.toast_error_loading_resources))
        setLoading(false)
    }

    companion object {
        const val FAVORITES_RESOURCE_ID = -100L
    }

    private val filterManager = ResourceFilterManager()
    private val navigationCoordinator = ResourceNavigationCoordinator(
        context = context,
        resourceRepository = resourceRepository,
        updateResourceUseCase = updateResourceUseCase,
        networkContextAnalyzer = networkContextAnalyzer
    )
    private val orderManager = ResourceOrderManager(
        resourceRepository = resourceRepository
    )
    private val listSessionManager = MainListSessionManager(
        readSession = readMainListSessionUseCase,
        saveSession = saveMainListSessionUseCase
    )
    private val scanCoordinator = ResourceScanCoordinator(
        getResourcesUseCase = getResourcesUseCase,
        resourceRepository = resourceRepository,
        updateResourceUseCase = updateResourceUseCase,
        mediaScannerFactory = mediaScannerFactory,
        smbOperationsUseCase = smbOperationsUseCase,
        remoteSourceGate = remoteSourceGate,
        refreshResourceFileCountsUseCase = refreshResourceFileCountsUseCase
    )

    init {
        viewModelScope.launch(ioDispatcher) {
            // S2199: before observeResourcesFromDatabase() below, because that collector reads
            // state.value when it filters and sorts; a restore landing after its first emission
            // would leave the list unnarrowed until some unrelated source happened to re-emit.
            val restored = listSessionManager.restore(state.value)
            updateState { restored }
            // NonCancellable: provisioning must finish atomically even if this ViewModel
            // is destroyed during WelcomeActivity's ephemeral first MainActivity creation.
            // Without this, viewModelScope.cancel() can interrupt after "Recent" is inserted
            // but before the other virtual resources are written, leaving the DB in a partial
            // state. The next launch then sees a non-empty resource list and skips provisioning.
            withContext(NonCancellable) {
                provisionDefaultResourcesUseCase()
                provisionDownloadsDestinationUseCase()
            }
            migrateCameraResourceUseCase()
            migrateS0059UseCase()
            runCatching { dedupAuthAccountsUseCase() }
                .onFailure { Timber.w(it, "DedupAuthAccountsUseCase failed") }
            // Backfill icon ids for resources that existed before S0034 (DB v25 → v26 migration)
            resourceRepository.get().backfillMissingIcons { path, profileName, typeName ->
                val profile = runCatching {
                    com.sza.fastmediasorter.domain.model.ResourceProfile.valueOf(profileName)
                }.getOrElse { com.sza.fastmediasorter.domain.model.ResourceProfile.NONE }
                val type = runCatching {
                    com.sza.fastmediasorter.domain.model.ResourceType.valueOf(typeName)
                }.getOrElse { com.sza.fastmediasorter.domain.model.ResourceType.LOCAL }
                resolveResourceIconUseCase(path = path, profile = profile, type = type)
            }
            observeResourcesFromDatabase()
        }
    }

    private fun observeResourcesFromDatabase() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            kotlinx.coroutines.flow.combine(
                getResourcesUseCase(),
                settingsRepository.getSettings(),
                // S0391: re-emit when remote-source availability changes so the list updates live on
                // a toggle; this also guarantees applyFiltersAndSorting reads a current gate snapshot.
                remoteSourceGate.enabledRemoteSources()
            ) { allResources, settings, _ ->
                // OPTIMIZATION: Removed global ConnectionThrottleManager setup for ALL resources.
                // Now configured only when opening specific resource in PlayerViewModel/BrowseViewModel.
                // This prevents unnecessary FTP/SFTP configuration when only using SMB.

                val filteredResources = applyFiltersAndSorting(allResources, settings.enableFavorites)
                Pair(filteredResources, settings.isResourceGridMode)
            }
                .catch { e ->
                    Timber.e(e, "Error observing resources from database")
                    handleError(e)
                }
                .collect { (resources, isGridMode) ->
                    updateState { it.copy(resources = resources, isResourceGridMode = isGridMode) }
                }
        }
    }

    private fun applyFiltersAndSorting(
        resources: List<MediaResource>,
        enableFavorites: Boolean
    ): List<MediaResource> {
        // S0391: a disabled remote source's resources are invisible everywhere - filter once here,
        // upstream of every tab/type filter, so they never surface (including under the ALL tab).
        val availableResources = resources.filter { remoteSourceGate.isEnabled(it) }
        return filterManager.applyFiltersAndSorting(
            resources = availableResources,
            activeTab = state.value.activeResourceTab,
            filterByType = state.value.filterByType,
            filterByMediaType = state.value.filterByMediaType,
            filterByName = state.value.filterByName,
            sortMode = state.value.sortMode,
            enableFavorites = enableFavorites
        )
    }

    private fun loadResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            try {
                val effectiveFilterByType = filterManager.getEffectiveTypeFilter(
                    activeTab = state.value.activeResourceTab,
                    explicitFilter = state.value.filterByType
                )
                val resources = filterManager.pinAllFilesFirst(
                    getResourcesUseCase.getFiltered(
                        filterByType = effectiveFilterByType,
                        filterByMediaType = state.value.filterByMediaType,
                        filterByName = state.value.filterByName,
                        sortMode = state.value.sortMode
                    )
                )

                updateState { it.copy(resources = resources) }

                appShortcutsManager.updateRecentResourceShortcuts()
            } catch (e: Exception) {
                Timber.e(e, "Error loading resources")
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun selectResource(resource: MediaResource) {
        updateState { it.copy(selectedResource = resource) }
    }

    fun openBrowse(resourceOverride: MediaResource? = null) {
        if (state.value.isNavigating) {
            Timber.d("Navigation already in progress, ignoring click")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val resource = resourceOverride ?: state.value.selectedResource
            if (resource == null || resource.id == 0L) {
                sendEvent(
                    MainEvent.ShowMessage(
                        context.getString(com.sza.fastmediasorter.R.string.main_select_resource_first)
                    )
                )
                return@launch
            }

            try {
                updateState {
                    it.copy(
                        isNavigating = true,
                        navigationMessage = context.getString(
                            com.sza.fastmediasorter.R.string.connecting_to_resource,
                            resource.name
                        )
                    )
                }

                saveLastUsedResourceId(resource.id)
                validateAndOpenResource(resource, slideshowMode = false)
            } finally {
                updateState {
                    it.copy(
                        isNavigating = false,
                        navigationMessage = null
                    )
                }
            }
        }
    }

    /**
     * S0422: exports a single resource (with credentials) to a cache file and signals the host to
     * share it via the system share sheet with the vendor MIME type.
     */
    fun exportResourceForShare(resource: MediaResource) {
        viewModelScope.launch {
            val safeName = resource.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resource" }
            val file = java.io.File(context.cacheDir, "$safeName.${ResourceShareFormat.EXTENSION}")
            when (val result = exportResourcesToFileUseCase(listOf(resource.id), android.net.Uri.fromFile(file))) {
                is ExportResourcesToFileUseCase.ExportResult.Success ->
                    if (result.exported > 0) {
                        sendEvent(MainEvent.ShareResourceFile(file.absolutePath))
                    } else {
                        sendEvent(MainEvent.ShowResourceMessage(R.string.resource_share_export_failed))
                    }
                is ExportResourcesToFileUseCase.ExportResult.Failure -> {
                    Timber.e(result.error, "Per-resource export failed")
                    sendEvent(MainEvent.ShowResourceMessage(R.string.resource_share_export_failed))
                }
            }
        }
    }

    /**
     * S0984: exports a single SFTP resource's access to a `.fmscfg` cache file and signals the host
     * to share it via the system share sheet. [includePassword] is driven by the export dialog.
     */
    fun shareSftpResourceConfig(resource: MediaResource, includePassword: Boolean) {
        viewModelScope.launch {
            exportCompanionConfigUseCase(resource, includePassword).fold(
                onSuccess = { file -> sendEvent(MainEvent.ShareCompanionConfigFile(file.absolutePath)) },
                onFailure = { e ->
                    Timber.e(e, "SFTP config export failed")
                    sendEvent(MainEvent.ShowResourceMessage(R.string.sftp_share_export_failed))
                }
            )
        }
    }

    /**
     * S1039: exports the same SFTP access as a compact QR payload and signals the host to display it
     * (CompanionQrShareActivity). [includePassword] mirrors [shareSftpResourceConfig].
     */
    fun shareSftpResourceConfigAsQr(resource: MediaResource, includePassword: Boolean) {
        viewModelScope.launch {
            exportCompanionConfigUseCase.exportQrPayload(resource, includePassword).fold(
                onSuccess = { export ->
                    sendEvent(MainEvent.ShowCompanionQr(export.payload, resource.name, export.passwordIncluded))
                },
                onFailure = { e ->
                    Timber.e(e, "SFTP QR export failed")
                    sendEvent(MainEvent.ShowResourceMessage(R.string.sftp_share_export_failed))
                }
            )
        }
    }

    fun startPlayer() {
        if (state.value.isNavigating) {
            Timber.d("Navigation already in progress, ignoring click")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val resource = state.value.selectedResource
                val resourceToOpen = if (resource != null && resource.id != 0L) {
                    resource
                } else {
                    val lastUsedId = settingsRepository.getLastUsedResourceId()
                    val targetResource = if (lastUsedId != -1L) {
                        state.value.resources.firstOrNull { it.id == lastUsedId }
                    } else {
                        null
                    }

                    targetResource ?: state.value.resources.firstOrNull()
                }

                if (resourceToOpen == null || resourceToOpen.id == 0L) {
                    sendEvent(
                        MainEvent.ShowMessage(
                            context.getString(com.sza.fastmediasorter.R.string.no_resources_available)
                        )
                    )
                    return@launch
                }

                updateState {
                    it.copy(
                        isNavigating = true,
                        navigationMessage = context.getString(
                            com.sza.fastmediasorter.R.string.starting_slideshow_for,
                            resourceToOpen.name
                        )
                    )
                }

                saveLastUsedResourceId(resourceToOpen.id)
                validateAndOpenResource(resourceToOpen, slideshowMode = true)
            } finally {
                updateState {
                    it.copy(
                        isNavigating = false,
                        navigationMessage = null
                    )
                }
            }
        }
    }

    fun startSlideshowFor(resource: MediaResource) {
        if (state.value.isNavigating) {
            Timber.d("Navigation already in progress, ignoring icon click")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                updateState {
                    it.copy(
                        isNavigating = true,
                        navigationMessage = context.getString(
                            com.sza.fastmediasorter.R.string.starting_slideshow_for,
                            resource.name
                        )
                    )
                }
                selectResource(resource)
                saveLastUsedResourceId(resource.id)
                validateAndOpenResource(resource, slideshowMode = true)
            } finally {
                updateState { it.copy(isNavigating = false, navigationMessage = null) }
            }
        }
    }

    fun startRandomMusicPlayback() {
        viewModelScope.launch(ioDispatcher) {
            val resource = state.value.resources.firstOrNull {
                it.path == LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO
            }
            if (resource == null) {
                sendEvent(
                    MainEvent.ShowMessage(
                        context.getString(com.sza.fastmediasorter.R.string.widget_random_music_resource_not_found)
                    )
                )
            } else {
                sendEvent(MainEvent.NavigateToPlayerRandomMusic(resource.id))
            }
        }
    }

    fun openCameraPhotos() {
        viewModelScope.launch(ioDispatcher) {
            val resource = state.value.resources.firstOrNull {
                it.path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
            }
            if (resource == null) {
                sendEvent(
                    MainEvent.ShowMessage(
                        context.getString(com.sza.fastmediasorter.R.string.widget_camera_photos_resource_not_found)
                    )
                )
            } else {
                sendEvent(MainEvent.NavigateToBrowse(resource.id, skipAvailabilityCheck = true))
            }
        }
    }

    private suspend fun saveLastUsedResourceId(resourceId: Long) {
        try {
            settingsRepository.saveLastUsedResourceId(resourceId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save last used resource ID")
        }
    }

    private suspend fun validateAndOpenResource(resource: MediaResource, slideshowMode: Boolean = false) {
        // Delegate validation to navigation coordinator
        when (val result = navigationCoordinator.validateAndNavigate(resource, slideshowMode)) {
            is ResourceNavigationCoordinator.NavigationResult.Navigate -> {
                when (val destination = result.destination) {
                    is ResourceNavigationCoordinator.NavigationDestination.Browse -> {
                        sendEvent(MainEvent.NavigateToBrowse(destination.resourceId, destination.skipAvailabilityCheck))
                    }
                    is ResourceNavigationCoordinator.NavigationDestination.PlayerSlideshow -> {
                        sendEvent(MainEvent.NavigateToPlayerSlideshow(destination.resourceId))
                    }
                    is ResourceNavigationCoordinator.NavigationDestination.Favorites -> {
                        sendEvent(MainEvent.NavigateToFavorites)
                    }
                }
            }
            is ResourceNavigationCoordinator.NavigationResult.RequestPin -> {
                sendEvent(MainEvent.RequestPassword(result.resource, result.forSlideshow))
            }
            is ResourceNavigationCoordinator.NavigationResult.Error -> {
                sendEvent(MainEvent.ShowError(result.message, result.details))
            }
            is ResourceNavigationCoordinator.NavigationResult.Info -> {
                sendEvent(MainEvent.ShowMessage(result.message))
            }
        }
    }

    /** Called after password verification to proceed with navigation. */
    fun proceedAfterPasswordCheck(resourceId: Long, slideshowMode: Boolean) {
        if (slideshowMode) {
            sendEvent(MainEvent.NavigateToPlayerSlideshow(resourceId))
        } else {
            sendEvent(MainEvent.NavigateToBrowse(resourceId, skipAvailabilityCheck = true))
        }
    }

    fun addResource() {
        sendEvent(MainEvent.NavigateToAddResource(state.value.activeResourceTab))
    }

    fun deleteResource(resource: MediaResource) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            deleteResourceUseCase(resource.id).onSuccess {
                Timber.d("Resource deleted: ${resource.name}")
                sendEvent(MainEvent.ShowResourceMessage(com.sza.fastmediasorter.R.string.resource_deleted))
                if (state.value.selectedResource?.id == resource.id) {
                    updateState { it.copy(selectedResource = null) }
                }
                // Reload resources list to update UI
                loadResources()
            }.onFailure { e ->
                Timber.e(e, "Error deleting resource")
                handleError(e)
            }
            setLoading(false)
        }
    }

    /**
     * The one body the four move actions shared. They differed only in which [ResourceOrderManager]
     * call they made, so every one of them had to grow the same persist call in S2199 - four copies
     * of a block that has to stay identical is what kept this class over its size ceiling.
     *
     * CannotMove means the resource is already at the edge, and Error is delivered through the
     * exception handler; neither leaves anything for this function to do.
     */
    private fun reorderResource(
        resource: MediaResource,
        move: suspend (MediaResource, List<MediaResource>) -> ResourceOrderManager.OrderResult
    ) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            if (move(resource, state.value.resources) is ResourceOrderManager.OrderResult.Success) {
                // Switch to manual sort mode to preserve user's ordering
                updateState { it.copy(sortMode = orderManager.getRecommendedSortMode()) }
                loadResources()
                persistListSession()
            }
        }
    }

    fun moveResourceUp(resource: MediaResource) =
        reorderResource(resource, orderManager::moveResourceUp)

    fun moveResourceDown(resource: MediaResource) =
        reorderResource(resource, orderManager::moveResourceDown)

    fun moveResourceToTop(resource: MediaResource) =
        reorderResource(resource, orderManager::moveResourceToTop)

    fun moveResourceToBottom(resource: MediaResource) =
        reorderResource(resource, orderManager::moveResourceToBottom)

    /**
     * Persist the new display order after a drag-to-reorder gesture.
     * Switches to MANUAL sort mode so the new order is respected on next load.
     */
    fun saveResourceOrder(resources: List<MediaResource>) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            when (orderManager.saveResourceOrder(resources)) {
                is ResourceOrderManager.OrderResult.Success -> {
                    updateState { it.copy(sortMode = orderManager.getRecommendedSortMode()) }
                    persistListSession()
                }
                is ResourceOrderManager.OrderResult.Error -> {
                    // Error handled by exception handler
                }
                is ResourceOrderManager.OrderResult.CannotMove -> { /* not emitted by saveResourceOrder */ }
            }
        }
    }

    /** S2199: remembers the sort and filters the list ended up in. */
    private fun persistListSession() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            listSessionManager.persist(state.value)
        }
    }

    fun setSortMode(sortMode: SortMode) {
        updateState { it.copy(sortMode = sortMode) }
        loadResources()
        persistListSession()
    }

    fun setFilterByType(types: Set<ResourceType>?) {
        updateState { it.copy(filterByType = types) }
        loadResources()
        persistListSession()
    }

    fun setFilterByMediaType(mediaTypes: Set<MediaType>?) {
        updateState { it.copy(filterByMediaType = mediaTypes) }
        loadResources()
        persistListSession()
    }

    fun setFilterByName(name: String?) {
        updateState { it.copy(filterByName = name) }
        loadResources()
        persistListSession()
    }

    fun clearFilters() {
        updateState {
            it.copy(
                filterByType = null,
                filterByMediaType = null,
                filterByName = null
            )
        }
        loadResources()
        persistListSession()
    }

    fun setActiveTab(tab: ResourceTab) {
        updateState { it.copy(activeResourceTab = tab) }
        // Re-apply filters with new tab selection
        viewModelScope.launch(ioDispatcher) {
            val settings = settingsRepository.getSettings().first()
            val allResources = getResourcesUseCase().first()
            val filteredResources = applyFiltersAndSorting(allResources, settings.enableFavorites)
            updateState { it.copy(resources = filteredResources) }
        }
    }

    /** S0783: add or remove the channel from the shared Favorites (streams-panel per-channel menu). */
    fun toggleStreamFavorite(source: StreamSourceEntity) {
        viewModelScope.launch(ioDispatcher) {
            favoritesUseCase.toggleStreamFavorite(source)
        }
    }

    fun openFavorites() {
        // Save current tab to restore later (only if not already on FAVORITES)
        val currentTab = state.value.activeResourceTab
        if (currentTab != ResourceTab.FAVORITES) {
            updateState { it.copy(previousTab = currentTab) }
        }
        // Open Browse with Favorites resource directly
        sendEvent(MainEvent.NavigateToFavorites)
    }

    fun openResourceDirect(resourceId: Long) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val resource = state.value.resources.firstOrNull { it.id == resourceId }
                if (resource != null) {
                    sendEvent(MainEvent.NavigateToBrowse(resourceId, skipAvailabilityCheck = true))
                } else {
                    sendEvent(
                        MainEvent.ShowMessage(context.getString(com.sza.fastmediasorter.R.string.resource_not_found))
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error opening resource via shortcut")
                sendEvent(
                    MainEvent.ShowMessage(context.getString(com.sza.fastmediasorter.R.string.main_open_resource_failed))
                )
            }
        }
    }

    fun restorePreviousTab() {
        // Restore tab that was active before opening Favorites
        val tabToRestore = state.value.previousTab ?: ResourceTab.ALL
        updateState { it.copy(activeResourceTab = tabToRestore, previousTab = null) }
    }

    fun copySelectedResource(resourceOverride: MediaResource? = null) {
        val selected = resourceOverride ?: state.value.selectedResource
        if (selected == null) {
            sendEvent(
                MainEvent.ShowMessage(
                    context.getString(com.sza.fastmediasorter.R.string.main_select_resource_to_copy)
                )
            )
            return
        }

        // Open copy flow in ResourceEditorActivity with source resource id
        sendEvent(MainEvent.NavigateToAddResourceCopy(selected.id))
    }

    /**
     * Generate a unique copy name by appending " (copy)" or " (copy N)"
     */
    private fun generateCopyName(originalName: String): String {
        val resources = state.value.resources
        val existingNames = resources.map { it.name }.toSet()

        // Try "Name (copy)" first
        var copyName = "$originalName (copy)"
        if (!existingNames.contains(copyName)) {
            return copyName
        }

        // If it exists, try "Name (copy 2)", "Name (copy 3)", etc.
        var counter = 2
        while (existingNames.contains("$originalName (copy $counter)")) {
            counter++
        }

        return "$originalName (copy $counter)"
    }

    fun toggleResourceViewMode() {
        viewModelScope.launch(ioDispatcher) {
            // Get current value from settings (source of truth)
            val settings = settingsRepository.getSettings().first()
            val newMode = !settings.isResourceGridMode
            settingsRepository.setResourceGridMode(newMode)
            // State will be updated automatically via observeResourcesFromDatabase
        }
    }

    /**
     * Refresh resources list from database (fast)
     */
    fun refreshResources() {
        // Refreshing resources from database
        loadResources()
    }

    /**
     * Quick check all resources: test availability and check write access.
     * Does NOT count files - only checks connectivity and permissions for UI status indicators.
     * File count is updated only when opening resource in BrowseActivity.
     */
    fun scanAllResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            // Check if aggregate virtual resources exist - show warning if so
            val resources = getResourcesUseCase().first()
            if (scanCoordinator.hasAggregateVirtualResources(resources)) {
                sendEvent(MainEvent.ConfirmRescanWithVirtualResources)
                return@launch
            }
            performScanAllResources()
        }
    }

    fun forceRescanAllResources() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            performScanAllResources()
        }
    }

    // S0160: test availability + refresh file count for a single resource row.
    fun scanSingleResource(resource: MediaResource) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            when (scanCoordinator.scanAndRefreshSingleResource(resource)) {
                is ResourceScanCoordinator.SingleScanResult.Unavailable ->
                    sendEvent(
                        MainEvent.ShowMessage(
                            context.getString(R.string.resource_unavailable_name, resource.name)
                        )
                    )
                is ResourceScanCoordinator.SingleScanResult.Available -> {
                    // DB updated via updateResourceUseCase inside scanCoordinator;
                    // the resource-list observer refreshes the UI automatically.
                }
            }
        }
    }

    private suspend fun performScanAllResources() {
        setLoading(true)
        try {
            val result = scanCoordinator.scanAllResources()
            Timber.d("Scan complete: ${result.availableCount} available, ${result.unavailableCount} unavailable")
        } catch (e: Exception) {
            Timber.e(e, "Error scanning resources")
            handleError(e)
        } finally {
            setLoading(false)
        }
    }
}
