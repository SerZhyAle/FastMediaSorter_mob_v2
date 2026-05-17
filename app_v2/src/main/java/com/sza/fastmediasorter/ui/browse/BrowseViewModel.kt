package com.sza.fastmediasorter.ui.browse

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class PlaybackStatus { IDLE, PLAYING, PAUSED }

data class InlinePlayerState(
    val playingPath: String? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val downloadingPath: String? = null,
    val downloadProgressPercent: Int = 0
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val deleteByFileSizeUseCase: com.sza.fastmediasorter.domain.usecase.DeleteByFileSizeUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val settingsRepository: SettingsRepository,
    private val cachedFileListRepository: CachedFileListRepository,
    private val updateResourceUseCase: UpdateResourceUseCase,
    val fileOperationUseCase: FileOperationUseCase, // Public for RenameDialog
    private val smbClient: com.sza.fastmediasorter.data.network.SmbClient,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val cleanupTrashFoldersUseCase: com.sza.fastmediasorter.domain.usecase.CleanupTrashFoldersUseCase,
    private val cleanupOrphanedTempFilesUseCase: com.sza.fastmediasorter.domain.usecase.CleanupOrphanedTempFilesUseCase,
    private val googleDriveClient: com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient,
    private val dropboxClient: com.sza.fastmediasorter.data.cloud.DropboxClient,
    private val oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository,
    private val favoritesUseCase: com.sza.fastmediasorter.domain.usecase.FavoritesUseCase,
    private val cachedMediaMetadataExtractor: CachedMediaMetadataExtractor,
    private val audioMetadataLoader: com.sza.fastmediasorter.core.util.AudioMetadataLoader,
    private val browseStateDataStore: com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore,
    private val manualOrderPrefs: com.sza.fastmediasorter.data.local.preferences.BrowseManualOrderPrefs,
    private val unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache,
    private val syncMediaStoreUseCase: com.sza.fastmediasorter.domain.usecase.SyncMediaStoreUseCase,
    private val clearResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase,
    private val getResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase,
    private val saveResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.SaveResumeStateUseCase,
    private val createDirectoryUseCase: com.sza.fastmediasorter.domain.usecase.CreateDirectoryUseCase,
    private val createTextNoteUseCase: com.sza.fastmediasorter.domain.usecase.CreateTextNoteUseCase,
    private val archiveFilesUseCase: com.sza.fastmediasorter.domain.usecase.ArchiveFilesUseCase,
    private val extractArchiveUseCase: com.sza.fastmediasorter.domain.usecase.ExtractArchiveUseCase,
    private val addResourceAsDestinationUseCase: com.sza.fastmediasorter.domain.usecase.AddResourceAsDestinationUseCase,
    private val deleteDirectoriesUseCase: com.sza.fastmediasorter.domain.usecase.DeleteDirectoriesUseCase,
    private val unifiedFileOperationHandler: com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<BrowseState, BrowseEvent>() {

    companion object {
        private const val PAGINATION_THRESHOLD = 500 // Use pagination for folders with 500+ files (reduced to improve initial load performance)
        private const val PAGE_SIZE = 50 // Load 50 files per page
    }

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId")
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull()
        ?: 0L
    // S0028: per-window resume state isolation
    private val windowId: String = savedStateHandle.get<String>("extra_window_id")
        ?: com.sza.fastmediasorter.domain.repository.ResumeStateRepository.WINDOW_ID_MAIN
    private val windowIdProvider: () -> String = { windowId }

    private val skipAvailabilityCheck: Boolean = savedStateHandle.get<Boolean>("skipAvailabilityCheck") ?: false
    
    // Resume extras (from MainActivity resume logic)
    private val resumeInitialFolderPath: String? = savedStateHandle.get<String>("initialFolderPath")
    private val resumeInitialFilePath: String? = savedStateHandle.get<String>("initialFilePath")
    private val resumeIsPlaying: Boolean? = savedStateHandle.get<Boolean>("resumeIsPlaying")
    
    // Selection management
    private val selectionManager = com.sza.fastmediasorter.ui.browse.selection.BrowseSelectionManager()
    
    // Undo management
    private val undoManager = com.sza.fastmediasorter.ui.browse.undo.BrowseUndoManager(
        context = context,
        callbacks = object : com.sza.fastmediasorter.ui.browse.undo.BrowseUndoManager.UndoCallbacks {
            override suspend fun addFilesToList(files: List<MediaFile>) {
                addFiles(files)
            }
            override suspend fun reloadFileList() {
                loadResource()
            }
            override fun createMediaFileFromFile(file: java.io.File): MediaFile {
                return this@BrowseViewModel.createMediaFileFromFile(file)
            }
            override fun showMessage(message: String) {
                sendEvent(BrowseEvent.ShowMessage(message))
            }
            override fun showUndoToast(operationType: String) {
                sendEvent(BrowseEvent.ShowUndoToast(operationType))
            }
            override fun showError(message: String, details: String?, exception: Throwable?) {
                sendEvent(BrowseEvent.ShowError(message, details, exception))
            }
        }
    )
    
    // File list management
    private val fileListManager = com.sza.fastmediasorter.ui.browse.filelist.BrowseFileListManager(resourceId)
    
    private val metadataManager = com.sza.fastmediasorter.ui.browse.metadata.BrowseMetadataManager(
        updateResourceUseCase = updateResourceUseCase,
        ioDispatcher = ioDispatcher
    )
    
    private val loadingManager = com.sza.fastmediasorter.ui.browse.loading.BrowseLoadingManager(
        mediaScannerFactory = mediaScannerFactory,
        getMediaFilesUseCase = getMediaFilesUseCase,
        favoritesUseCase = favoritesUseCase,
        resourceId = resourceId,
        viewModelScope = viewModelScope,
        ioDispatcher = ioDispatcher,
        pageSize = PAGE_SIZE,
        paginationThreshold = PAGINATION_THRESHOLD
    )
    
    private val cacheManager = com.sza.fastmediasorter.ui.browse.cache.BrowseCacheManager(
        resourceId = resourceId,
        paginationThreshold = PAGINATION_THRESHOLD
    )
    
    // Subfolder navigation management (delegated to BrowseNavigationManager)
    private val navigationManager = com.sza.fastmediasorter.ui.browse.managers.BrowseNavigationManager(
        context = context,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { isLoading -> setLoading(isLoading) },
        mediaScannerFactory = mediaScannerFactory,
        cancelLoad = { resourceLoadManager.cancelLoad(loadFilesJob) },
        onLoadMediaFiles = { resourceLoadManager.loadMediaFiles() },
        onLoadResource = { resourceLoadManager.loadResource() }
    )

    // File observer management (delegated to BrowseFileObserverManager)
    private val fileObserverManager = com.sza.fastmediasorter.ui.browse.managers.BrowseFileObserverManager(
        scope = viewModelScope,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        onRemoveFiles = { paths -> removeFiles(paths) },
        onReloadFiles = { reloadFiles() },
        onSortFiles = { files, sortMode -> sortFiles(files, sortMode, forceSort = true) }
    )

    // Job for current file loading operation (to cancel on reload)
    private var loadFilesJob: Job? = null
    
    // Job for loadResource() - ensures only one loadResource runs at a time
    private var loadResourceJob: Job? = null
    
    // Job for reloadFiles() - ensures only one reload runs at a time
    private var reloadFilesJob: Job? = null
    
    // Graceful stop flag: when true, scanner should stop and return partial results
    private val shouldStopScan = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Job for delayed STOP button visibility (10 seconds after scan start)
    private var stopButtonTimerJob: Job? = null

    // --- Loading aux: error formatting, audio enrichment, ExoPlayer warmup ---
    private val auxManager = com.sza.fastmediasorter.ui.browse.managers.BrowseLoadingAuxManager(
        context = context,
        updateResourceUseCase = updateResourceUseCase,
        cachedMediaMetadataExtractor = cachedMediaMetadataExtractor,
        cachedFileListRepository = cachedFileListRepository,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        exceptionHandler = exceptionHandler,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { isLoading -> setLoading(isLoading) },
        getSettings = { getSettings() },
        resourceId = resourceId,
        onHandleError = { e -> handleError(e) }
    )

    // --- Inline Audio Player (delegated to BrowseInlineAudioManager) ---
    private val audioManager = com.sza.fastmediasorter.ui.browse.managers.BrowseInlineAudioManager(
        context = context,
        smbClient = smbClient,
        smbOperationsUseCase = smbOperationsUseCase,
        saveResumeStateUseCase = saveResumeStateUseCase,
        windowIdProvider = windowIdProvider,
        scope = viewModelScope,
        stateFlow = state,
        onNavigateToFolder = { path -> navigateToFolder(path) }
    )
    val inlinePlayerState: StateFlow<InlinePlayerState> = audioManager.inlinePlayerState

    // --- Archive / Extraction (delegated to BrowseArchiveManager) ---
    private val archiveManager = com.sza.fastmediasorter.ui.browse.managers.BrowseArchiveManager(
        context = context,
        archiveFilesUseCase = archiveFilesUseCase,
        extractArchiveUseCase = extractArchiveUseCase,
        createDirectoryUseCase = createDirectoryUseCase,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        clearSelection = { clearSelection() },
        reloadFiles = { clearList -> reloadFiles(clearList) }
    )

    // --- Delete (delegated to BrowseDeleteManager) ---
    private val deleteManager = com.sza.fastmediasorter.ui.browse.managers.BrowseDeleteManager(
        context = context,
        settingsRepository = settingsRepository,
        fileOperationUseCase = fileOperationUseCase,
        deleteDirectoriesUseCase = deleteDirectoriesUseCase,
        deleteByFileSizeUseCase = deleteByFileSizeUseCase,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        sendEvent = { event -> sendEvent(event) },
        setLoading = { isLoading -> setLoading(isLoading) },
        setIgnoringFileChanges = { ignoring -> fileObserverManager.setIgnoringFileChanges(ignoring) },
        clearSelection = { clearSelection() },
        removeFiles = { paths -> removeFiles(paths) },
        saveUndoOperation = { op -> saveUndoOperation(op) },
        reloadFiles = { reloadFiles() },
        loadResource = { loadResource() }
    )

    // --- Directory operations (delegated to BrowseDirectoryOpsManager) ---
    private val directoryOpsManager = com.sza.fastmediasorter.ui.browse.managers.BrowseDirectoryOpsManager(
        context = context,
        createDirectoryUseCase = createDirectoryUseCase,
        unifiedFileOperationHandler = unifiedFileOperationHandler,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        sendEvent = { event -> sendEvent(event) },
        reloadResource = { loadResource() }
    )

    // --- Text note creation (delegated to BrowseTextNoteCreateManager) ---
    // notifyCreatedForOpen lambda is replaced by BrowseManagerInitializer via setOpenNoteCallback().
    private var openNoteCallback: ((String) -> Unit)? = null
    internal fun setOpenNoteCallback(cb: (String) -> Unit) { openNoteCallback = cb }

    /** S0189: open a freshly created text note in the editor with edit mode pre-activated. */
    internal fun openTextNoteInEditor(path: String) {
        fileOpenManager.openTextNoteInEditor(path, resourceId)
    }

    private val textNoteCreateManager = com.sza.fastmediasorter.ui.browse.managers.BrowseTextNoteCreateManager(
        context = context,
        createTextNoteUseCase = createTextNoteUseCase,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        sendEvent = { event -> sendEvent(event) },
        reloadResource = { loadResource() },
        notifyCreatedForOpen = { path -> openNoteCallback?.invoke(path) }
    )

    // --- Favorites / cache sync (delegated to BrowseStateSyncManager) ---
    private val stateSyncManager = com.sza.fastmediasorter.ui.browse.managers.BrowseStateSyncManager(
        favoritesUseCase = favoritesUseCase,
        getResourcesUseCase = getResourcesUseCase,
        cachedFileListRepository = cachedFileListRepository,
        resourceId = resourceId,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        setLoading = { isLoading -> setLoading(isLoading) },
        scheduleWarmupIfEligible = { files -> auxManager.schedulePlayerWarmupIfEligible(files) },
        applyFilterToList = { files, filter -> applyFilterToList(files, filter) },
        reloadCurrentSubfolder = { path: String -> launchSubfolderReload(path) },
        reloadFiles = { clearList -> reloadFiles(clearList) }
    )

    // --- Refresh/reload flow (delegated to BrowseRefreshManager) ---
    private val refreshManager = com.sza.fastmediasorter.ui.browse.managers.BrowseRefreshManager(
        syncMediaStoreUseCase = syncMediaStoreUseCase,
        smbOperationsUseCase = smbOperationsUseCase,
        cachedFileListRepository = cachedFileListRepository,
        resourceId = resourceId,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        setLastEmittedMediaFilesNull = { lastEmittedMediaFiles = null },
        setIgnoringFileChanges = { ignoring -> fileObserverManager.setIgnoringFileChanges(ignoring) },
        loadResource = { forceRescan -> loadResource(forceRescan) }
    )

    // --- Sort / Filter (delegated to BrowseSortFilterManager) ---
    private val sortFilterManager = com.sza.fastmediasorter.ui.browse.managers.BrowseSortFilterManager(
        context = context,
        updateResourceUseCase = updateResourceUseCase,
        getResourcesUseCase = getResourcesUseCase,
        getMediaFilesUseCase = getMediaFilesUseCase,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { isLoading -> setLoading(isLoading) },
        loadMediaFiles = { loadMediaFiles() },
        getFriendlyErrorMessage = { throwable -> getFriendlyBrowseErrorMessage(throwable) },
        getSettings = { getSettings() },
        fileListManager = fileListManager,
        resourceId = resourceId,
        paginationThreshold = PAGINATION_THRESHOLD
    )

    // --- File Open (delegated to BrowseFileOpenManager) ---
    private val fileOpenManager = com.sza.fastmediasorter.ui.browse.managers.BrowseFileOpenManager(
        context = context,
        updateResourceUseCase = updateResourceUseCase,
        mediaScannerFactory = mediaScannerFactory,
        cachedFileListRepository = cachedFileListRepository,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { isLoading -> setLoading(isLoading) },
        inlineStop = { inlineStop() },
        sortFiles = { files, mode, forceSort -> sortFiles(files, mode, forceSort) },
        resourceId = resourceId
    )

    // --- Resource / Media-file loading pipeline (delegated to BrowseResourceLoadManager) ---
    private val resourceLoadManager = com.sza.fastmediasorter.ui.browse.managers.BrowseResourceLoadManager(
        context = context,
        updateResourceUseCase = updateResourceUseCase,
        cachedFileListRepository = cachedFileListRepository,
        googleDriveClient = googleDriveClient,
        dropboxClient = dropboxClient,
        oneDriveClient = oneDriveClient,
        favoritesUseCase = favoritesUseCase,
        audioMetadataLoader = audioMetadataLoader,
        cleanupOrphanedTempFilesUseCase = cleanupOrphanedTempFilesUseCase,
        getResourcesUseCase = getResourcesUseCase,
        cacheManager = cacheManager,
        loadingManager = loadingManager,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        exceptionHandler = exceptionHandler,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { isLoading -> setLoading(isLoading) },
        isLoading = { loading.value },
        getSettings = { getSettings() },
        resourceId = resourceId,
        skipAvailabilityCheck = skipAvailabilityCheck,
        paginationThreshold = PAGINATION_THRESHOLD,
        setLoadFilesJobRef = { job -> loadFilesJob = job },
        setLoadResourceJobRef = { job -> loadResourceJob = job },
        setStopButtonTimerJobRef = { job -> stopButtonTimerJob = job },
        shouldStopScanRef = shouldStopScan,
        loadFavorites = { stateSyncManager.loadFavorites() },
        onFilesLoadedSaveAndEnrich = { resource, files ->
            if (resource.rememberFileList) {
                try {
                    cachedFileListRepository.saveCachedFiles(resource.id, files)
                } catch (e: Exception) {
                    Timber.e(e, "BrowseViewModel: failed to save DB cache for ${resource.id}")
                }
            }
            auxManager.enrichAudioMetadataInBackground(resource)
        },
        onHandleLoadingError = { resource, e -> auxManager.handleLoadingError(resource, e) },
        schedulePlayerWarmup = { files -> auxManager.schedulePlayerWarmupIfEligible(files) },
        updateResourceMetadata = { resource, fileCount, subfolderCount ->
            metadataManager.updateMetadata(resource, fileCount, subfolderCount)
        },
        startFileObserver = { fileObserverManager.start() },
        sortFiles = { files, mode, force -> sortFilterManager.sortFiles(files, mode, force) },
        setPagingDataFlow = { flow -> _pagingDataFlow.value = flow }
    )

    // --- File-list mutations (delegated to BrowseFileListMutationManager) ---
    private val fileListMutationManager = com.sza.fastmediasorter.ui.browse.managers.BrowseFileListMutationManager(
        fileListManager = fileListManager,
        cachedFileListRepository = cachedFileListRepository,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        resourceId = resourceId,
        invalidateDirectoryCache = { navigationManager.invalidateDirectoryCache() },
        onFilesRemoved = { paths -> selectionManager.onFilesRemoved(paths) },
        onFilePathChanged = { oldPath, newPath -> selectionManager.onFilePathChanged(oldPath, newPath) }
    )

    // --- Resource state persistence (delegated to BrowseResourceStateManager) ---
    private val resourceStateManager = com.sza.fastmediasorter.ui.browse.managers.BrowseResourceStateManager(
        context = context,
        favoritesUseCase = favoritesUseCase,
        updateResourceUseCase = updateResourceUseCase,
        getResourcesUseCase = getResourcesUseCase,
        addResourceAsDestinationUseCase = addResourceAsDestinationUseCase,
        clearResumeStateUseCase = clearResumeStateUseCase,
        windowIdProvider = windowIdProvider,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        exceptionHandler = exceptionHandler,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        resourceId = resourceId
    )

    // --- Shutdown / network-key helper (delegated to BrowseShutdownCoordinator) ---
    private val shutdownCoordinator = com.sza.fastmediasorter.ui.browse.managers.BrowseShutdownCoordinator(
        stateFlow = state,
        ioDispatcher = ioDispatcher,
        browseStateDataStore = browseStateDataStore,
        unifiedCache = unifiedCache,
        cleanupTrash = { resource -> refreshManager.cleanupTrashOnBackground(resource) }
    )

    // --- Manual-order ordering (delegated to BrowseManualOrderCoordinator) ---
    private val manualOrderCoordinator = com.sza.fastmediasorter.ui.browse.managers.BrowseManualOrderCoordinator(
        manualOrderPrefs = manualOrderPrefs,
        resourceId = resourceId,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        fallbackSort = { files, mode, force -> sortFilterManager.sortFiles(files, mode, force) }
    )

    // --- Lifecycle setup (delegated to BrowseLifecycleSetupManager) ---
    private val lifecycleSetupManager = com.sza.fastmediasorter.ui.browse.managers.BrowseLifecycleSetupManager(
        context = context,
        browseStateDataStore = browseStateDataStore,
        settingsRepository = settingsRepository,
        unifiedCache = unifiedCache,
        selectionManager = selectionManager,
        undoManager = undoManager,
        getResumeStateUseCase = getResumeStateUseCase,
        clearResumeStateUseCase = clearResumeStateUseCase,
        windowIdProvider = windowIdProvider,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        exceptionHandler = exceptionHandler,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        applyFilter = { applyFilter() },
        resourceId = resourceId
    )

    // Track last emitted list to avoid redundant UI updates (survives Activity recreation)
    var lastEmittedMediaFiles: List<MediaFile>? = null
        private set
    
    fun markListAsSubmitted(list: List<MediaFile>) {
        lastEmittedMediaFiles = list
    }
    
    // PagingData flow for large datasets (used when usePagination = true)
    private val _pagingDataFlow = MutableStateFlow<Flow<PagingData<MediaFile>>?>(null)
    val pagingDataFlow: StateFlow<Flow<PagingData<MediaFile>>?> = _pagingDataFlow.asStateFlow()

    override fun getInitialState() = BrowseState()

    private fun getFriendlyBrowseErrorMessage(throwable: Throwable): String =
        context.getString(resolveFriendlyBrowseErrorRes(throwable))

    private fun resolveFriendlyBrowseErrorRes(throwable: Throwable): Int {
        // WifiRequiredException fires before any socket attempt — clearly a Wi-Fi gate rejection,
        // not a generic outage. Must be checked by type before the message-based heuristics below.
        if (throwable is WifiRequiredException) return R.string.error_wifi_required_smb

        val message = throwable.message.orEmpty()
        return when {
            message.contains("Authentication", ignoreCase = true) ||
                message.contains("LOGON_FAILURE", ignoreCase = true) ||
                message.contains("Not authenticated", ignoreCase = true) ->
                R.string.friendly_copy_error_auth_failed

            (message.contains("permission", ignoreCase = true) &&
                message.contains("denied", ignoreCase = true)) ||
                message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
                message.contains("access denied", ignoreCase = true) ->
                R.string.friendly_copy_error_access_denied

            message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
                message.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true) ||
                message.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ->
                R.string.friendly_copy_error_not_found

            message.contains("unreachable", ignoreCase = true) ||
                message.contains("Cannot resolve host", ignoreCase = true) ||
                message.contains("Unknown host", ignoreCase = true) ->
                R.string.friendly_copy_error_no_connection

            message.contains("Connection reset", ignoreCase = true) ||
                message.contains("connection closed", ignoreCase = true) ||
                message.contains("broken pipe", ignoreCase = true) ||
                message.contains("connection lost", ignoreCase = true) ->
                R.string.error_network_connection_lost

            message.contains("timed out", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("SocketTimeoutException", ignoreCase = true) ->
                R.string.error_network_timeout

            message.contains("Connection", ignoreCase = true) ||
                message.contains("Network", ignoreCase = true) ->
                R.string.error_network_connection

            else -> R.string.friendly_copy_error_generic
        }
    }

    /**
     * Map raw exception messages to concise, resource-backed user-facing strings.
     * BaseViewModel default leaks technical details (e.g. "Server unreachable (192.168.1.112:445)").
     */
    override fun handleError(throwable: Throwable) {
        setError(getFriendlyBrowseErrorMessage(throwable))
        setLoading(false)
    }

    init {
        lifecycleSetupManager.initialize()
        loadResource()

        // Resume inline playback if launched from resume logic
        if (resumeInitialFilePath != null) {
            audioManager.attemptResumeInlinePlayback(
                initialFolderPath = resumeInitialFolderPath,
                initialFilePath = resumeInitialFilePath,
                isPlaying = resumeIsPlaying
            )
        }
    }
    
    /** Get current settings — delegates to BrowseLifecycleSetupManager. */
    suspend fun getSettings(): com.sza.fastmediasorter.domain.model.AppSettings = lifecycleSetupManager.getSettings()

    /** True if scheduled operations are enabled in user settings (runtime flag). */
    val scheduledOperationsEnabled: Boolean get() = lifecycleSetupManager.scheduledOperationsEnabled

    fun cancelBackgroundThumbnailLoading() = shutdownCoordinator.cancelBackgroundThumbnailLoading()

    /** Toggle inline playback — delegates to BrowseInlineAudioManager. */
    fun inlinePlayToggle(file: MediaFile) = audioManager.inlinePlayToggle(file)

    /** Stop inline playback — delegates to BrowseInlineAudioManager. */
    fun inlineStop() = audioManager.inlineStop()

    override fun onCleared() {
        Timber.d("BrowseViewModel.onCleared: START - resourceId=$resourceId, fileCount=${state.value.mediaFiles.size}")

        loadFilesJob?.cancel()
        loadResourceJob?.cancel()
        reloadFilesJob?.cancel()
        stopButtonTimerJob?.cancel()
        auxManager.cancelAll()
        shouldStopScan.set(true) // Graceful stop flag for any in-flight scanner

        fileObserverManager.stop()
        shutdownCoordinator.onShutdown(viewModelScope)
        inlineStop() // Release MediaPlayer
        navigationManager.clearDirectoryCache()
        shutdownCoordinator.launchPostShutdownCleanup()

        super.onCleared()
        Timber.d("BrowseViewModel.onCleared: COMPLETE")
    }

    private fun launchSubfolderReload(path: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            navigationManager.reloadCurrentSubfolder(path)
        }
    }

    fun removeFiles(filePaths: List<String>): Unit = fileListMutationManager.removeFiles(filePaths)
    fun addFiles(newFiles: List<MediaFile>): Unit = fileListMutationManager.addFiles(newFiles)
    
    fun updateFile(oldPath: String, newFile: MediaFile): Unit = fileListMutationManager.updateFile(oldPath, newFile)
    
    fun createFolder(name: String) = directoryOpsManager.createFolder(name)

    fun createTextNote(name: String) {
        Timber.d("S0189: BrowseViewModel.createTextNote name=$name")
        textNoteCreateManager.createTextNote(name)
    }

    fun renameDirectory(path: String, newName: String) = directoryOpsManager.renameDirectory(path, newName)

    fun reloadFiles(clearList: Boolean = false, syncMediaStore: Boolean = true) {
        reloadFilesJob?.cancel()
        reloadFilesJob = refreshManager.launchReload(clearList, syncMediaStore)
    }

    fun scrollToFileAfterRefresh(fileName: String) {
        viewModelScope.launch {
            state.first { it.mediaFiles.any { f -> f.name == fileName } }
            sendEvent(BrowseEvent.ScrollToFile(fileName))
        }
    }

    fun refreshResourceMetadata() = resourceStateManager.refreshResourceMetadata()

    /**
     * Cancel active scan. For network resources the job is killed immediately
     * to stop SMB/SFTP/FTP I/O. For local resources the scanner is allowed
     * to finish gracefully so partial results can be displayed.
     */
    fun cancelScan(forceCancel: Boolean = false) {
        shouldStopScan.set(true)
        reloadFilesJob?.cancel()
        auxManager.cancelPlayerWarmup()
        
        // For network resources (or forced cancel e.g. onStop), kill the job
        // to immediately release network connections and stop I/O.
        val isNetwork = state.value.resource?.type.let {
            it == ResourceType.SMB || it == ResourceType.SFTP || it == ResourceType.FTP
        }
        if (forceCancel || isNetwork) {
            loadFilesJob?.cancel()
            Timber.d("cancelScan: loadFilesJob cancelled (forceCancel=$forceCancel, network=$isNetwork)")
        }
    }

    fun navigateToFolder(folderPath: String) {
        // S0120: track folder-enter events for BRW-sort endurance analysis
        MemoryEnduranceTracker.checkpoint("FOLDER_ENTER", "BRW-sort")
        navigationManager.navigateToFolder(folderPath)
    }
    fun navigateBack(): Boolean = navigationManager.navigateBack()
    fun getCurrentBreadcrumb(): String = navigationManager.getCurrentBreadcrumb()
    fun enableSubfolderMode() = navigationManager.enableSubfolderMode()
    fun disableSubfolderMode() = navigationManager.disableSubfolderMode()

    private fun loadResource(forceRescan: Boolean = false) {
        loadResourceJob?.cancel()
        resourceLoadManager.loadResource(forceRescan)
    }

    private fun loadMediaFiles() {
        loadFilesJob?.cancel()
        resourceLoadManager.loadMediaFiles()
    }

    // Sort / Display Mode - delegated to BrowseSortFilterManager
    fun setSortMode(sortMode: SortMode) = sortFilterManager.setSortMode(sortMode)
    fun reshuffleRandom() = sortFilterManager.reshuffleRandom()
    fun toggleDisplayMode() = sortFilterManager.toggleDisplayMode()

    /** Called from BrowseActivity's submitList callback after DiffUtil finishes for a sort. */
    fun clearSorting() {
        updateState { it.copy(isSorting = false) }
        setLoading(false)
    }

    fun selectFile(filePath: String) {
        selectionManager.toggleSelection(filePath)
    }

    /**
     * Returns the current selection set directly from [selectionManager] (synchronous read).
     * Use this immediately after [selectFile] in inline-item button callbacks to avoid the
     * async propagation lag of [state].selectedFiles (which is updated via a collect coroutine).
     */
    fun currentSelectedPaths(): Set<String> = selectionManager.selectionState.value.selectedFiles
    
    fun selectFileRange(filePath: String) {
        val mediaFiles = state.value.mediaFiles
        selectionManager.selectRange(filePath, mediaFiles)
    }

    fun clearSelection() {
        selectionManager.clearSelection()
    }
    
    fun selectAll() {
        val mediaFiles = state.value.mediaFiles
        selectionManager.selectAll(mediaFiles)
    }

    fun openFile(file: com.sza.fastmediasorter.domain.model.MediaFile, approximatePosition: Int = 0) = fileOpenManager.openFile(file, approximatePosition)

    fun deleteSelectedFiles() = deleteManager.deleteSelectedFiles()

    fun onDeletePermissionGranted() = deleteManager.onDeletePermissionGranted()

    fun toggleFavorite(file: MediaFile) = resourceStateManager.toggleFavorite(file)

    fun saveUndoOperation(operation: UndoOperation) {
        undoManager.saveOperation(operation)
    }
    
    fun undoLastOperation() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            undoManager.undoLastOperation()
        }
    }
    
    /**
     * Clear undo operation if it has expired (older than 10 seconds).
     * Call this when activity resumes or when checking before showing undo button.
     */
    fun clearExpiredUndoOperation() {
        undoManager.clearIfExpired()
    }
    
    // Filter - delegated to BrowseSortFilterManager
    fun setFilter(filter: FileFilter?) = sortFilterManager.setFilter(filter)
    fun applyFilter() {
        // S0120: track filter-change events for BRW-sort endurance analysis
        MemoryEnduranceTracker.checkpoint("FILTER_CHANGE", "BRW-sort")
        sortFilterManager.applyFilter()
    }
    internal fun applyFilterToList(files: List<com.sza.fastmediasorter.domain.model.MediaFile>, filter: com.sza.fastmediasorter.domain.model.FileFilter): List<MediaFile> = sortFilterManager.applyFilterToList(files, filter)
    private fun sortFiles(
        files: List<MediaFile>,
        mode: SortMode,
        forceSort: Boolean = false
    ): List<MediaFile> = manualOrderCoordinator.sortFiles(files, mode, forceSort)

    /**
     * Persists drag-reordered file paths and updates the visible list immediately.
     * Called from BrowseManagerInitializer after drag ends (clearView).
     */
    fun saveManualOrder(orderedPaths: List<String>) = manualOrderCoordinator.saveManualOrder(orderedPaths)

    /**
     * Move [file] one position up in MANUAL sort order.
     * No-op if sort mode is not MANUAL, file is already first, or file is not in the list.
     */
    fun moveFileUp(file: MediaFile) {
        if (state.value.sortMode != SortMode.MANUAL) return
        val files = state.value.mediaFiles.toMutableList()
        val idx = files.indexOfFirst { it.path == file.path }
        if (idx <= 0) return
        files.add(idx - 1, files.removeAt(idx))
        saveManualOrder(files.map { it.path })
    }

    /**
     * Move [file] one position down in MANUAL sort order.
     * No-op if sort mode is not MANUAL, file is already last, or file is not in the list.
     */
    fun moveFileDown(file: MediaFile) {
        if (state.value.sortMode != SortMode.MANUAL) return
        val files = state.value.mediaFiles.toMutableList()
        val idx = files.indexOfFirst { it.path == file.path }
        if (idx < 0 || idx >= files.size - 1) return
        files.add(idx + 1, files.removeAt(idx))
        saveManualOrder(files.map { it.path })
    }

    fun saveLastViewedFile(filePath: String) = resourceStateManager.saveLastViewedFile(filePath)
    
    fun saveScrollPosition(position: Int) = resourceStateManager.saveScrollPosition(position)

    fun clearResumeState() = resourceStateManager.clearResumeState()

    fun createMediaFileFromFile(file: java.io.File): MediaFile = fileListMutationManager.createMediaFileFromFile(file)
    
    /**
     * Syncs current state with cache.
     * Called when returning from PlayerActivity to reflect changes (move/delete)
     * without full network reload.
     */
    fun syncWithCache() = stateSyncManager.syncWithCache()
    
    /**
     * Check if resource settings (supportedMediaTypes, scanSubdirectories) changed in database.
     * If changed, reload files to reflect new filter.
     * If not changed, sync with PlayerActivity cache for deleted/moved/renamed files.
    * Called from BrowseActivity.onResume() when returning from PlayerActivity or ResourceEditorActivity.
     */
    fun checkAndReloadIfResourceChanged() = stateSyncManager.checkAndReloadIfResourceChanged()
    
    fun removeFilesFromList(paths: List<String>) = fileListMutationManager.removeFilesFromList(paths)

    suspend fun onFileMissingFromDisk(mediaFile: MediaFile) = fileListMutationManager.onFileMissingFromDisk(mediaFile)
    
    fun setIgnoringFileChanges(ignoring: Boolean) = fileObserverManager.setIgnoringFileChanges(ignoring)
    fun isIgnoringFileChanges(): Boolean = fileObserverManager.ignoringFileChanges
    
    suspend fun isSubfolderModeEnabled(): Boolean = navigationManager.isSubfolderModeEnabled()
    fun navigateToFolder(folder: MediaFile) {
        // S0120: track folder-enter events for BRW-sort endurance analysis
        MemoryEnduranceTracker.checkpoint("FOLDER_ENTER", "BRW-sort")
        navigationManager.navigateToFolder(folder)
    }
    fun canNavigateUp(): Boolean = navigationManager.canNavigateUp()
    fun navigateUp(): Boolean = navigationManager.navigateUp()
    fun resetToRoot() = navigationManager.resetToRoot()
    fun getCurrentFolderName(): String? = navigationManager.getCurrentFolderName()
    fun getBreadcrumbPath(): String = navigationManager.getBreadcrumbPath()
    fun getBreadcrumbParts(): Pair<String, List<String>> = navigationManager.getBreadcrumbParts()
    fun navigateToDepth(depth: Int) = navigationManager.navigateToDepth(depth)

    // Delete - delegated to BrowseDeleteManager
    fun deleteBySize(minSizeMb: Float?, maxSizeMb: Float?) = deleteManager.deleteBySize(minSizeMb, maxSizeMb)
    fun scanBySize(minSizeMb: Float?, maxSizeMb: Float?) = deleteManager.scanBySize(minSizeMb, maxSizeMb)
    fun executeBySizeDeleteConfirmed(files: List<com.sza.fastmediasorter.domain.model.MediaFile>) = deleteManager.executeBySizeDeleteConfirmed(files)

    // Archive / Extraction - delegated to BrowseArchiveManager
    fun archiveSelectedFiles(archiveName: String, destinationPath: String) =
        archiveManager.archiveSelectedFiles(archiveName, destinationPath)

    fun cancelArchive() = archiveManager.cancelArchive()
    fun prepareExtraction(file: MediaFile) = archiveManager.prepareExtraction(file)
    fun extractArchive(file: MediaFile) = archiveManager.extractArchive(file)
    fun cancelExtraction() = archiveManager.cancelExtraction()
    fun addCurrentResourceAsDestination() = resourceStateManager.addCurrentResourceAsDestination()
}
