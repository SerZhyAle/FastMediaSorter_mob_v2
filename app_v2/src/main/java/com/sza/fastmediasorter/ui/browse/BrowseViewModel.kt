package com.sza.fastmediasorter.ui.browse

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.core.ui.UiState
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException
import com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
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
    private val remoteAccess: BrowseRemoteAccessDependencies,
    private val cleanupUseCases: BrowseCleanupUseCases,
    private val contentDiscovery: BrowseContentDiscoveryDependencies,
    private val persistedState: BrowsePersistedStateDependencies,
    // Public - external reads keep reaching fileOperationUseCase via this holder (S1350).
    val contentAuthoringUseCases: BrowseContentAuthoringUseCases,
    private val fileMutation: BrowseFileMutationDependencies,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<BrowseState, BrowseEvent>() {

    companion object {
        // Large-folder threshold: above this count the browse path force-sorts + prefers the cache.
        private const val PAGINATION_THRESHOLD = 500
    }

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId")
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull()
        ?: 0L
    // S0028: per-window resume state isolation
    private val windowId: String = savedStateHandle.get<String>("extra_window_id")
        ?: com.sza.fastmediasorter.domain.repository.ResumeStateRepository.WINDOW_ID_MAIN
    private val windowIdProvider: () -> String = { windowId }

    // S0730: single shared settings StateFlow for every Browse observer. Previously each observer
    // collected settingsRepository.getSettings() independently, so the cold flow rebuilt the
    // ~150-field AppSettings (with a glidePrefs side-effect write) once per observer on every
    // settings write. Sharing collapses that to a single upstream collection; flowOn(Default) keeps
    // the rebuild off Main; Eagerly so .value is current for synchronous reads.
    val settings: StateFlow<AppSettings> = fileMutation.settingsRepository.getSettings()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val fileListUiState: StateFlow<UiState<BrowseState>> =
        createUiState { currentState -> currentState.mediaFiles.isEmpty() }

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
        statsSink = persistedState.statsSink,
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
            override suspend fun renameViaFileOperation(currentPath: String, originalName: String): Boolean {
                // Preserve smb://, sftp://, ftp://, cloud:// schemes: java.io.File collapses "//" to "/".
                val file = object : java.io.File(currentPath) {
                    override fun getPath(): String = currentPath
                    override fun getAbsolutePath(): String = currentPath
                }
                val operation = com.sza.fastmediasorter.domain.usecase.FileOperation.Rename(file, originalName)
                val result = contentAuthoringUseCases.fileOperationUseCase.execute(operation)
                return result is com.sza.fastmediasorter.domain.usecase.FileOperationResult.Success
            }
        }
    )
    
    // File list management
    private val fileListManager = com.sza.fastmediasorter.ui.browse.filelist.BrowseFileListManager(resourceId)
    
    private val metadataManager = com.sza.fastmediasorter.ui.browse.metadata.BrowseMetadataManager(
        updateResourceUseCase = contentAuthoringUseCases.updateResourceUseCase,
        ioDispatcher = ioDispatcher
    )
    
    private val loadingManager = com.sza.fastmediasorter.ui.browse.loading.BrowseLoadingManager(
        getMediaFilesUseCase = contentDiscovery.getMediaFilesUseCase,
        favoritesUseCase = persistedState.favoritesUseCase,
        resourceId = resourceId,
        viewModelScope = viewModelScope,
        ioDispatcher = ioDispatcher,
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
        mediaScannerFactory = contentDiscovery.mediaScannerFactory,
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
        onSortFiles = { files, sortMode -> sortFiles(files, sortMode, forceSort = true) },
        // S0242 Phase 05 - Route delete / move events through MutationJournal so the
        // Reconciler picks them up on next onResume (single source-mutation reader).
        resourceId = resourceId,
        mutationJournal = fileMutation.mutationJournal,
        pathNormalizer = fileMutation.pathNormalizer
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
        updateResourceUseCase = contentAuthoringUseCases.updateResourceUseCase,
        cachedMediaMetadataExtractor = contentDiscovery.cachedMediaMetadataExtractor,
        cachedFileListRepository = contentDiscovery.cachedFileListRepository,
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
        smbClient = remoteAccess.smbClient,
        smbOperationsUseCase = remoteAccess.smbOperationsUseCase,
        saveResumeStateUseCase = persistedState.saveResumeStateUseCase,
        windowIdProvider = windowIdProvider,
        scope = viewModelScope,
        stateFlow = state,
        onNavigateToFolder = { path -> navigateToFolder(path) }
    )
    val inlinePlayerState: StateFlow<InlinePlayerState> = audioManager.inlinePlayerState

    // --- Archive / Extraction (delegated to BrowseArchiveManager) ---
    private val archiveManager = com.sza.fastmediasorter.ui.browse.managers.BrowseArchiveManager(
        context = context,
        archiveFilesUseCase = contentAuthoringUseCases.archiveFilesUseCase,
        extractArchiveUseCase = contentAuthoringUseCases.extractArchiveUseCase,
        createDirectoryUseCase = contentAuthoringUseCases.createDirectoryUseCase,
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
        settingsRepository = fileMutation.settingsRepository,
        fileOperationUseCase = contentAuthoringUseCases.fileOperationUseCase,
        deleteDirectoriesUseCase = cleanupUseCases.deleteDirectoriesUseCase,
        deleteByFileSizeUseCase = cleanupUseCases.deleteByFileSizeUseCase,
        browseTransferCoordinator = cleanupUseCases.browseTransferCoordinator,
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
        createDirectoryUseCase = contentAuthoringUseCases.createDirectoryUseCase,
        unifiedFileOperationHandler = fileMutation.unifiedFileOperationHandler,
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
    private var openDrawingCallback: ((String) -> Unit)? = null
    internal fun setOpenDrawingCallback(cb: (String) -> Unit) { openDrawingCallback = cb }

    /** S0189: open a freshly created text note in the editor with edit mode pre-activated. */
    internal fun openTextNoteInEditor(path: String) {
        fileOpenManager.openTextNoteInEditor(path, resourceId)
    }

    /** S0191: open a freshly created blank drawing in PlayerActivity with draw mode pre-activated. */
    internal fun openDrawingInEditor(path: String) {
        fileOpenManager.openDrawingInEditor(path, resourceId)
    }

    private val textNoteCreateManager = com.sza.fastmediasorter.ui.browse.managers.BrowseTextNoteCreateManager(
        context = context,
        createTextNoteUseCase = contentAuthoringUseCases.createTextNoteUseCase,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        sendEvent = { event -> sendEvent(event) },
        reloadResource = { loadResource() },
        notifyCreatedForOpen = { path -> openNoteCallback?.invoke(path) }
    )

    private val drawingCreateManager = com.sza.fastmediasorter.ui.browse.managers.BrowseDrawingCreateManager(
        context = context,
        createDrawingUseCase = contentAuthoringUseCases.createDrawingUseCase,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        sendEvent = { event -> sendEvent(event) },
        notifyCreatedForOpen = { path -> openDrawingCallback?.invoke(path) }
    )

    // --- Favorites / settings-changed reload (delegated to BrowseStateSyncManager) ---
    // S0242 Phase 03 - structural-equality cache-sync fast-path removed from the
    // manager; cache→visible reconciliation is now owned by BrowseReconcilerManager.
    // This manager keeps only favorites loading + resource-settings-changed reload.
    private val stateSyncManager = com.sza.fastmediasorter.ui.browse.managers.BrowseStateSyncManager(
        useCases = com.sza.fastmediasorter.ui.browse.managers.BrowseStateSyncUseCases(
            favoritesUseCase = persistedState.favoritesUseCase,
            materializeFavoritesUseCase = persistedState.materializeFavoritesUseCase,
            getResourcesUseCase = contentDiscovery.getResourcesUseCase
        ),
        resourceId = resourceId,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        setLoading = { isLoading -> setLoading(isLoading) },
        scheduleWarmupIfEligible = { files -> auxManager.schedulePlayerWarmupIfEligible(files) },
        reloadFiles = { clearList -> reloadFiles(clearList) }
    )

    // --- Refresh/reload flow (delegated to BrowseRefreshManager) ---
    private val refreshManager = com.sza.fastmediasorter.ui.browse.managers.BrowseRefreshManager(
        syncMediaStoreUseCase = contentDiscovery.syncMediaStoreUseCase,
        smbOperationsUseCase = remoteAccess.smbOperationsUseCase,
        cachedFileListRepository = contentDiscovery.cachedFileListRepository,
        mutationJournal = fileMutation.mutationJournal,
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
        updateResourceUseCase = contentAuthoringUseCases.updateResourceUseCase,
        getResourcesUseCase = contentDiscovery.getResourcesUseCase,
        getMediaFilesUseCase = contentDiscovery.getMediaFilesUseCase,
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
        updateResourceUseCase = contentAuthoringUseCases.updateResourceUseCase,
        mediaScannerFactory = contentDiscovery.mediaScannerFactory,
        cachedFileListRepository = contentDiscovery.cachedFileListRepository,
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
        updateResourceUseCase = contentAuthoringUseCases.updateResourceUseCase,
        cachedFileListRepository = contentDiscovery.cachedFileListRepository,
        googleDriveClient = remoteAccess.googleDriveClient,
        dropboxClient = remoteAccess.dropboxClient,
        oneDriveClient = remoteAccess.oneDriveClient,
        favoritesUseCase = persistedState.favoritesUseCase,
        audioMetadataLoader = contentDiscovery.audioMetadataLoader,
        cleanupOrphanedTempFilesUseCase = cleanupUseCases.cleanupOrphanedTempFilesUseCase,
        getResourcesUseCase = contentDiscovery.getResourcesUseCase,
        remoteSourceGate = remoteAccess.remoteSourceGate,
        resolveScanFilter = contentDiscovery.resolveScanFilterUseCase,
        mediaScannerFactory = contentDiscovery.mediaScannerFactory,
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
                    contentDiscovery.cachedFileListRepository.saveCachedFiles(resource.id, files)
                } catch (e: Exception) {
                    Timber.e(e, "BrowseViewModel: failed to save DB cache for ${resource.id}")
                }
            }
            auxManager.enrichAudioMetadataInBackground(resource)
        },
        onHandleLoadingError = { resource, e -> auxManager.handleLoadingError(resource, e) },
        schedulePlayerWarmup = { files -> auxManager.schedulePlayerWarmupIfEligible(files) },
        updateResourceMetadata = { resource, fileCount, subfolderCount ->
            val persisted = metadataManager.updateMetadata(resource, fileCount, subfolderCount)
            // S1001: merge the freshly persisted stats into the in-memory resource. Without this,
            // every later full-entity write built from state.resource (scroll position, sort mode,
            // display mode) carries the stale load-time stats and clobbers this DB update.
            if (persisted != null) {
                updateState { st ->
                    val current = st.resource
                    if (current != null && current.id == persisted.id) {
                        st.copy(
                            resource = current.copy(
                                fileCount = persisted.fileCount,
                                subfolderCount = persisted.subfolderCount,
                                lastBrowseDate = persisted.lastBrowseDate,
                                lastSyncDate = persisted.lastSyncDate
                            )
                        )
                    } else {
                        st
                    }
                }
            }
        },
        startFileObserver = { fileObserverManager.start() },
        sortFiles = { files, mode, force -> sortFilterManager.sortFiles(files, mode, force) }
    )

    // --- File-list mutations (delegated to BrowseFileListMutationManager) ---
    private val fileListMutationManager = com.sza.fastmediasorter.ui.browse.managers.BrowseFileListMutationManager(
        fileListManager = fileListManager,
        cachedFileListRepository = contentDiscovery.cachedFileListRepository,
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
        favoritesUseCase = persistedState.favoritesUseCase,
        updateResourceUseCase = contentAuthoringUseCases.updateResourceUseCase,
        getResourcesUseCase = contentDiscovery.getResourcesUseCase,
        addResourceAsDestinationUseCase = contentAuthoringUseCases.addResourceAsDestinationUseCase,
        clearResumeStateUseCase = persistedState.clearResumeStateUseCase,
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
        browseStateDataStore = persistedState.browseStateDataStore,
        unifiedCache = contentDiscovery.unifiedCache,
        hasActiveTransfer = { cleanupUseCases.browseTransferCoordinator.hasActiveTransfer() },
        cleanupTrash = { resource -> refreshManager.cleanupTrashOnBackground(resource) }
    )

    // --- Manual-order ordering (delegated to BrowseManualOrderCoordinator) ---
    private val manualOrderCoordinator = com.sza.fastmediasorter.ui.browse.managers.BrowseManualOrderCoordinator(
        manualOrderPrefs = persistedState.manualOrderPrefs,
        resourceId = resourceId,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        fallbackSort = { files, mode, force -> sortFilterManager.sortFiles(files, mode, force) }
    )

    // --- Lifecycle setup (delegated to BrowseLifecycleSetupManager) ---
    private val lifecycleSetupManager = com.sza.fastmediasorter.ui.browse.managers.BrowseLifecycleSetupManager(
        dependencies = com.sza.fastmediasorter.ui.browse.managers.BrowseLifecycleSetupDependencies(
            context = context,
            settingsRepository = fileMutation.settingsRepository,
            cacheCleanup = com.sza.fastmediasorter.ui.browse.managers.BrowseCacheCleanup(
                unifiedCache = contentDiscovery.unifiedCache,
                hasActiveTransfer = { cleanupUseCases.browseTransferCoordinator.hasActiveTransfer() }
            ),
            stateDependencies = com.sza.fastmediasorter.ui.browse.managers.BrowseLifecycleStateDependencies(
                browseStateDataStore = persistedState.browseStateDataStore,
                selectionManager = selectionManager,
                undoManager = undoManager,
                getResumeStateUseCase = persistedState.getResumeStateUseCase,
                clearResumeStateUseCase = persistedState.clearResumeStateUseCase,
                stateFlow = state,
                updateState = { update -> updateState(update) },
                sendEvent = { event -> sendEvent(event) },
                applyFilter = { applyFilter() }
            )
        ),
        windowIdProvider = windowIdProvider,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        exceptionHandler = exceptionHandler,
        resourceId = resourceId
    )

    // Track last emitted list to avoid redundant UI updates (survives Activity recreation)
    var lastEmittedMediaFiles: List<MediaFile>? = null
        private set
    
    fun markListAsSubmitted(list: List<MediaFile>) {
        lastEmittedMediaFiles = list
    }

    override fun getInitialState() = BrowseState()

    private fun getFriendlyBrowseErrorMessage(throwable: Throwable): String =
        context.getString(resolveFriendlyBrowseErrorRes(throwable))

    private fun resolveFriendlyBrowseErrorRes(throwable: Throwable): Int {
        // Checked by type before the message-based heuristics: WifiRequiredException is a Wi-Fi
        // gate rejection (not a generic outage), and SFTP protocol status is locale-independent
        // unlike the server's text (Windows OpenSSH sends "cannot find the file specified", which
        // no message rule below matches). Folded into one `when` to keep a single return. S1000.
        val sftpCategory = SftpOperationFailure.fromThrowable(throwable).category
        val message = throwable.message.orEmpty()
        return when {
            throwable is WifiRequiredException -> R.string.error_wifi_required_smb
            sftpCategory == SftpFailureCategory.NOT_FOUND -> R.string.friendly_copy_error_not_found
            sftpCategory == SftpFailureCategory.PERMISSION_DENIED ->
                R.string.friendly_copy_error_access_denied

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
    
    /** Get current settings - delegates to BrowseLifecycleSetupManager. */
    suspend fun getSettings(): com.sza.fastmediasorter.domain.model.AppSettings = lifecycleSetupManager.getSettings()

    /** True if scheduled operations are enabled in user settings (runtime flag). */
    val scheduledOperationsEnabled: Boolean get() = lifecycleSetupManager.scheduledOperationsEnabled

    /**
     * S1329: the answer the row adapter needs - "may this resource offer a move/copy target" - rather than
     * the use case that computes it, so the host asks a question instead of holding a domain dependency.
     */
    suspend fun hasDestinationsExcluding(resourceId: Long): Boolean =
        fileMutation.getDestinationsUseCase.getDestinationsExcluding(resourceId).isNotEmpty()

    fun cancelBackgroundThumbnailLoading() = shutdownCoordinator.cancelBackgroundThumbnailLoading()

    /** Toggle inline playback - delegates to BrowseInlineAudioManager. */
    fun inlinePlayToggle(file: MediaFile) = audioManager.inlinePlayToggle(file)

    /** Stop inline playback - delegates to BrowseInlineAudioManager. */
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
        textNoteCreateManager.createTextNote(name)
    }

    fun createDrawing(name: String) {
        drawingCreateManager.createDrawing(name)
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

    /**
     * S0371: after a freshly recorded video is saved and the list reloaded, open it in the player.
     * Awaits the file's appearance in the reloaded list, resolves its index, and reuses the normal
     * [openFile] entry point (which emits [BrowseEvent.NavigateToPlayer]). No-op if it never arrives
     * because the reload was superseded.
     */
    fun openCapturedVideoAfterRefresh(fileName: String) {
        viewModelScope.launch {
            val files = state.first { it.mediaFiles.any { f -> f.name == fileName } }.mediaFiles
            val index = files.indexOfFirst { it.name == fileName }
            val file = files.getOrNull(index) ?: return@launch
            openFile(file, approximatePosition = index)
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

    /**
     * Delete files. When [overridePaths] is non-null, deletes those exact paths instead
     * of the global multiselect (used by the per-file overflow menu).
     */
    fun deleteSelectedFiles(overridePaths: Set<String>? = null) =
        deleteManager.deleteSelectedFiles(overridePaths)

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
     * S0242 Phase 03: replaces the visible media-files list with [updatedList].
     * Called by `BrowseActivity` after `BrowseReconcilerManager.reconcile(..)` returns
     * a list whose `visibleChanged == true`. Keeps adapter rebind single-shot - the
     * `observeData()` `collectOnLifecycle(viewModel.state)` handler sees one state emit
     * and submits the new list to the adapter via DiffUtil.
     */
    fun replaceMediaFiles(updatedList: List<MediaFile>) {
        updateState { it.copy(mediaFiles = updatedList, totalFileCount = updatedList.size) }
    }

    /**
     * Check if resource settings (supportedMediaTypes, scanSubdirectories) changed in database.
     * If changed, reload files to reflect new filter.
     * S0242 Phase 03: structural-equality fast-path removed - Reconciler runs first in
     * `BrowseActivity.onResumeWithViews` and handles all cache→visible diff application.
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
    fun extractArchiveWithPassword(file: MediaFile, password: CharArray) =
        archiveManager.extractArchiveWithPassword(file, password)
    fun cancelExtraction() = archiveManager.cancelExtraction()
    fun addCurrentResourceAsDestination() = resourceStateManager.addCurrentResourceAsDestination()
}
