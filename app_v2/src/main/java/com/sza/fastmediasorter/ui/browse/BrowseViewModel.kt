package com.sza.fastmediasorter.ui.browse

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import android.app.PendingIntent
import com.sza.fastmediasorter.R
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.paging.MediaFilesPagingSource
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.data.observer.MediaFileObserver
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.data.network.glide.NetworkVideoFrameDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class BrowseState(
    val resource: MediaResource? = null,
    val mediaFiles: List<MediaFile> = emptyList(),
    val usePagination: Boolean = false, // True if file count >= PAGINATION_THRESHOLD
    val totalFileCount: Int? = null, // Total count (null if not yet calculated)
    val selectedFiles: Set<String> = emptySet(),
    val lastSelectedPath: String? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    val filter: FileFilter? = null,
    val lastOperation: UndoOperation? = null,
    val undoOperationTimestamp: Long? = null, // Timestamp when undo operation was saved (for expiry check)
    val loadingProgress: Int = 0, // Number of files found during scan (0 = not scanning)
    val isCloudResource: Boolean = false, // True for cloud resources (to show animated dots)
    val isScanCancellable: Boolean = false, // True when scan runs >5 seconds, shows STOP button
    val showSmallControls: Boolean = false, // True if "Small controls" setting is enabled
    // Subfolder navigation state
    val currentPath: String? = null, // Current browsed path (null = root of resource)
    val pathStack: List<String> = emptyList(), // Stack of visited paths for back navigation
    val isSubfolderMode: Boolean = false // True when subfolder navigation is enabled
)

sealed class BrowseEvent {
    data class ShowError(val message: String, val details: String? = null, val exception: Throwable? = null) : BrowseEvent()
    data class ShowMessage(val message: String) : BrowseEvent()
    data class ShowUndoToast(val operationType: String) : BrowseEvent()
    data class NavigateToPlayer(val filePath: String, val fileIndex: Int) : BrowseEvent()
    data class ShowCloudAuthenticationRequired(val provider: CloudProvider) : BrowseEvent()
    data class CloudAuthRequired(val provider: String, val message: String) : BrowseEvent()
    data class PermissionRequired(val pendingIntent: PendingIntent) : BrowseEvent()
    data class NoFilesFound(val message: String? = null, val messageResId: Int? = null) : BrowseEvent()  // Return to main screen with toast
}

/**
 * Cache entry for directory contents with hash-based validation.
 * Hash is computed from file names, sizes, and modification dates.
 */
private data class DirectoryCacheEntry(
    val files: List<MediaFile>,
    val contentHash: String,
    val timestamp: Long
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val settingsRepository: SettingsRepository,
    private val updateResourceUseCase: UpdateResourceUseCase,
    val fileOperationUseCase: FileOperationUseCase, // Public for RenameDialog
    private val smbClient: com.sza.fastmediasorter.data.network.SmbClient,
    private val cleanupTrashFoldersUseCase: com.sza.fastmediasorter.domain.usecase.CleanupTrashFoldersUseCase,
    private val cleanupOrphanedTempFilesUseCase: com.sza.fastmediasorter.domain.usecase.CleanupOrphanedTempFilesUseCase,
    private val googleDriveClient: com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository,
    private val favoritesUseCase: com.sza.fastmediasorter.domain.usecase.FavoritesUseCase,
    private val browseStateDataStore: com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore,
    private val unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache,
    private val syncMediaStoreUseCase: com.sza.fastmediasorter.domain.usecase.SyncMediaStoreUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<BrowseState, BrowseEvent>() {

    companion object {
        private const val PAGINATION_THRESHOLD = 500 // Use pagination for folders with 500+ files (reduced to improve initial load performance)
        private const val PAGE_SIZE = 50 // Load 50 files per page
    }

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId") 
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() 
        ?: 0L
    
    private val skipAvailabilityCheck: Boolean = savedStateHandle.get<Boolean>("skipAvailabilityCheck") ?: false
    
    // Selection management
    private val selectionManager = com.sza.fastmediasorter.ui.browse.selection.BrowseSelectionManager()
    
    // Undo management
    private val undoManager = com.sza.fastmediasorter.ui.browse.undo.BrowseUndoManager(
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
    
    // Metadata management
    private val metadataManager = com.sza.fastmediasorter.ui.browse.metadata.BrowseMetadataManager(
        updateResourceUseCase = updateResourceUseCase,
        ioDispatcher = ioDispatcher
    )
    
    // Loading management
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
    
    // Cache management
    private val cacheManager = com.sza.fastmediasorter.ui.browse.cache.BrowseCacheManager(
        resourceId = resourceId,
        paginationThreshold = PAGINATION_THRESHOLD
    )
    
    // Cache for subdirectories with hash-based validation
    private val directoryCache = mutableMapOf<String, DirectoryCacheEntry>()
    
    private var fileObserver: MediaFileObserver? = null
    
    // Job for debouncing reloadFiles() calls from FileObserver
    private var reloadDebounceJob: Job? = null
    
    // Track pending rename operation (from=null + to=X or from=X + to=null events)
    private var pendingRenameFrom: String? = null
    private var pendingRenameTo: String? = null
    private var pendingRenameJob: Job? = null
    
    // Flag to ignore FileObserver events during programmatic file operations
    private var ignoringFileChanges = false
    
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
    
    // Track last emitted list to avoid redundant UI updates (survives Activity recreation)
    var lastEmittedMediaFiles: List<MediaFile>? = null
        private set
    
    fun markListAsSubmitted(list: List<MediaFile>) {
        lastEmittedMediaFiles = list
    }
    
    // PagingData flow for large datasets (used when usePagination = true)
    private val _pagingDataFlow = MutableStateFlow<Flow<PagingData<MediaFile>>?>(null)
    val pagingDataFlow: StateFlow<Flow<PagingData<MediaFile>>?> = _pagingDataFlow.asStateFlow()

    // Cached settings to avoid multiple reads during single operation
    private var cachedSettings: com.sza.fastmediasorter.domain.model.AppSettings? = null

    override fun getInitialState() = BrowseState()


    init {
        // Clear PDF thumbnail cache from previous Browse sessions
        // (in case app was killed without proper onCleared() call)
        clearPdfThumbnailCache()
        
        loadResource()
        loadSettings()
        restoreFilterState()
        observeSelectionChanges()
        observeUndoChanges()
    }
    
    private fun restoreFilterState() {
        viewModelScope.launch(ioDispatcher) {
            browseStateDataStore.filter.first()?.let { savedFilter ->
                if (!savedFilter.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        updateState { it.copy(filter = savedFilter) }
                        applyFilter()
                        
                        // Show hint about restored filter
                        val filterDesc = buildString {
                            if (!savedFilter.nameContains.isNullOrBlank()) append("Name")
                            if (savedFilter.minSizeMb != null) {
                                if (isNotEmpty()) append(", ")
                                append("Size")
                            }
                            if (savedFilter.minDate != null) {
                                if (isNotEmpty()) append(", ")
                                append("Date")
                            }
                        }
                        if (filterDesc.isNotEmpty()) {
                            sendEvent(BrowseEvent.ShowMessage("Restored last filter: $filterDesc"))
                        }
                    }
                }
            }
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val settings = settingsRepository.getSettings().first()
            cachedSettings = settings // Cache for subsequent operations
            updateState { it.copy(showSmallControls = settings.showSmallControls) }
        }
    }
    
    /**
     * Get current settings from repository.
     * Used by Activity to access Safe Mode settings.
     * Uses cached value if available, otherwise fetches from repository.
     */
    suspend fun getSettings(): com.sza.fastmediasorter.domain.model.AppSettings {
        return cachedSettings ?: settingsRepository.getSettings().first().also {
            cachedSettings = it
        }
    }
    
    /**
     * Observe selection changes from SelectionManager and sync to state.
     */
    private fun observeSelectionChanges() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            selectionManager.selectionState.collect { selection ->
                updateState { state ->
                    state.copy(
                        selectedFiles = selection.selectedFiles,
                        lastSelectedPath = selection.lastSelectedPath
                    )
                }
            }
        }
    }
    
    /**
     * Observe undo state changes from UndoManager and sync to state.
     */
    private fun observeUndoChanges() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            undoManager.undoState.collect { undoState ->
                updateState { state ->
                    state.copy(
                        lastOperation = undoState.lastOperation,
                        undoOperationTimestamp = undoState.undoOperationTimestamp
                    )
                }
            }
        }
    }
    
    /**
     * Cancel background thumbnail loading when navigating to PlayerActivity.
     * This frees network bandwidth for the player without affecting the adapter cache.
     */
    fun cancelBackgroundThumbnailLoading() {
        val resource = state.value.resource
        if (resource != null && resource.type != ResourceType.LOCAL) {
            val resourceKey = when (resource.type) {
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> {
                    val uri = java.net.URI(resource.path)
                    "${uri.scheme}://${uri.host}:${uri.port.takeIf { it != -1 } ?: 445}"
                }
                ResourceType.CLOUD -> "cloud://${resource.cloudProvider}/${resource.cloudFolderId}"
                else -> null
            }
            resourceKey?.let { 
                ConnectionThrottleManager.cancelAllForResource(it)
                Timber.d("BrowseViewModel.cancelBackgroundThumbnailLoading: Cancelled all operations for $it")
            }
        }
    }
    
    override fun onCleared() {
        Timber.d("BrowseViewModel.onCleared: START - resourceId=$resourceId, fileCount=${state.value.mediaFiles.size}")
        // Save current filter state before clearing
        val currentFilter = state.value.filter
        viewModelScope.launch(ioDispatcher) {
            browseStateDataStore.saveFilter(currentFilter)
        }
        
        super.onCleared()
        
        // Cancel all active network operations for this resource to prevent conflicts
        val resource = state.value.resource
        if (resource != null && resource.type != ResourceType.LOCAL) {
            val resourceKey = when (resource.type) {
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> {
                    // Extract host:port from path (e.g., "smb://192.168.1.112:445/share")
                    val uri = java.net.URI(resource.path)
                    "${uri.scheme}://${uri.host}:${uri.port.takeIf { it != -1 } ?: 445}"
                }
                ResourceType.CLOUD -> "cloud://${resource.cloudProvider}/${resource.cloudFolderId}"
                else -> null
            }
            resourceKey?.let { 
                ConnectionThrottleManager.cancelAllForResource(it)
                Timber.d("BrowseViewModel.onCleared: Cancelled all operations for $it")
            }
        }
        
        // Explicitly cancel active jobs to stop network requests immediately
        // This ensures browsing stops but doesn't affect background copy/move operations
        // which run in their own scope (e.g. CopyToDialog scope)
        loadFilesJob?.cancel()
        loadResourceJob?.cancel()
        reloadFilesJob?.cancel()
        reloadDebounceJob?.cancel()
        stopButtonTimerJob?.cancel()
        cancelScan() // Set flag for graceful stop
        
        stopFileObserver()
        
        // Cleanup trash folders when leaving resource (background, maxAge=0 = delete all)
        if (resource != null) {
            viewModelScope.launch(ioDispatcher) {
                cleanupTrashOnBackground(resource, maxAgeMs = 0L)
            }
        }
        
        // Clear PDF thumbnail cache - full PDF files no longer needed after leaving Browse
        // Bitmap thumbnails remain in Glide disk cache for fast reload
        clearPdfThumbnailCache()
    }
    
    /**
     * Clear PDF thumbnail cache when leaving Browse screen.
     * Now uses UnifiedFileCache instead of legacy pdf_thumbnails directory.
     */
    private fun clearPdfThumbnailCache() {
        viewModelScope.launch(ioDispatcher) {
            try {
                // Clear UnifiedFileCache (contains all network files)
                unifiedCache.clearAll()
                Timber.d("Cleared UnifiedFileCache")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear UnifiedFileCache")
            }
        }
    }

    /**
     * Remove specific files from the current list without full reload.
     * Used after Move and Delete operations to avoid unnecessary scanning.
     * 
     * @param filePaths Paths of files to remove from the list
     */
    fun removeFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        
        // Notify selection manager about removed files
        selectionManager.onFilesRemoved(filePaths)
        
        updateState { state ->
            val updatedFiles = fileListManager.removeFiles(state.mediaFiles, filePaths)
            state.copy(mediaFiles = updatedFiles)
        }
        
        // Invalidate directory cache after removing files
        invalidateDirectoryCache()
    }
    
    /**
     * Add files to the list (e.g., after undo move/delete).
     * Files are added and list is re-sorted according to current sort mode.
     * 
     * @param newFiles List of MediaFile objects to add
     */
    fun addFiles(newFiles: List<MediaFile>) {
        if (newFiles.isEmpty()) return
        
        updateState { state ->
            val updatedFiles = fileListManager.addFiles(state.mediaFiles, newFiles, state.sortMode)
            state.copy(mediaFiles = updatedFiles)
        }
        
        // Invalidate directory cache after adding files
        invalidateDirectoryCache()
    }
    
    /**
     * Update specific file in the list (e.g., after rename).
     * 
     * @param oldPath Original file path
     * @param newFile Updated MediaFile object
     */
    fun updateFile(oldPath: String, newFile: MediaFile) {
        // Notify selection manager about path change
        selectionManager.onFilePathChanged(oldPath, newFile.path)
        
        updateState { state ->
            val updatedFiles = fileListManager.updateFile(state.mediaFiles, oldPath, newFile, state.sortMode)
            state.copy(mediaFiles = updatedFiles)
        }
        
        // Invalidate directory cache after updating file
        invalidateDirectoryCache()
    }

    fun reloadFiles(clearList: Boolean = true) {
        // Cancel previous reload if still running to prevent parallel reloads
        reloadFilesJob?.cancel()
        reloadFilesJob = viewModelScope.launch(ioDispatcher) {
            val currentResource = state.value.resource
            
            // Step 0: CONDITIONAL - Clear current file list only if file visibility settings changed
            // clearList=true: user changed supportedMediaTypes, scanSubdirectories, etc - old files must disappear
            // clearList=false: user changed only metadata (name, comment, pin) - keep showing current files
            if (clearList) {
                withContext(Dispatchers.Main) {
                    Timber.i("BrowseViewModel.reloadFiles: Clearing current file list to force UI refresh (file settings changed)")
                    updateState { it.copy(mediaFiles = emptyList(), loadingProgress = 0) }
                    // Reset lastEmittedMediaFiles to force adapter submit on next update
                    lastEmittedMediaFiles = null
                }
            } else {
                Timber.i("BrowseViewModel.reloadFiles: Keeping current file list (only metadata changed)")
            }
            
            // Step 1: Clean up ALL .trash folders in the resource directory
            if (currentResource?.type == ResourceType.LOCAL) {
                try {
                    val rootDir = File(currentResource.path)
                    Timber.i("BrowseViewModel.reloadFiles: Cleaning up ALL .trash folders in '${currentResource.name}'")
                    val deletedCount = cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs = 0L)
                    Timber.i("BrowseViewModel.reloadFiles: Deleted $deletedCount .trash folders")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "BrowseViewModel.reloadFiles: Failed to cleanup .trash folders")
                }
            }
            
            // Step 2: Sync MediaStore for local resources to register any "ghost" files
            // Block FileObserver during sync to prevent cascading reload triggers
            if (currentResource?.type == ResourceType.LOCAL) {
                ignoringFileChanges = true
                try {
                    Timber.i("BrowseViewModel.reloadFiles: Syncing MediaStore for local resource '${currentResource.name}'")
                    val syncResult = syncMediaStoreUseCase.invoke(currentResource)
                    syncResult.onSuccess { count ->
                        Timber.i("BrowseViewModel.reloadFiles: MediaStore sync completed - $count files registered")
                    }.onFailure { error ->
                        Timber.e(error, "BrowseViewModel.reloadFiles: MediaStore sync failed")
                    }
                } finally {
                    // Small delay to let any pending FileObserver events from MediaStore sync drain
                    kotlinx.coroutines.delay(500)
                    ignoringFileChanges = false
                }
            }
            
            // Step 3: Clear caches and reload files
            withContext(Dispatchers.Main) {
                // Clearing cache for resource
                MediaFilesCacheManager.clearCache(resourceId)
                // Clear video frame extraction cache to retry failed videos
                NetworkFileDataFetcher.clearFailedVideoCache()
                loadResource()
            }
        }
    }
    
    /**
     * Refreshes resource metadata from database without reloading file list.
     * Used when only metadata changed (name, comment, pin, displayMode, etc).
     */
    fun refreshResourceMetadata() {
        viewModelScope.launch(ioDispatcher) {
            Timber.i("BrowseViewModel.refreshResourceMetadata: START - loading updated resource from database")
            val updatedResource = getResourcesUseCase.getById(resourceId)
            if (updatedResource != null) {
                withContext(Dispatchers.Main) {
                    Timber.i("BrowseViewModel.refreshResourceMetadata: Resource loaded - name='${updatedResource.name}', sortMode=${updatedResource.sortMode}, displayMode=${updatedResource.displayMode}")
                    updateState { 
                        it.copy(
                            resource = updatedResource,
                            sortMode = updatedResource.sortMode,
                            displayMode = updatedResource.displayMode
                        ) 
                    }
                }
            } else {
                Timber.e("BrowseViewModel.refreshResourceMetadata: Resource not found for id=$resourceId")
            }
        }
    }

    fun cancelScan() {
        // Setting graceful stop flag
        // Set flag to signal scanner to stop gracefully and return partial results
        shouldStopScan.set(true)
        
        // Don't cancel the job - let it complete gracefully
        // loadFilesJob will finish and emit partial results
    }
    
    // ===== SUBFOLDER NAVIGATION =====
    
    /**
     * Navigate into a subfolder.
     * Called when user taps on a folder item in the file list.
     * 
     * @param folderPath Full path of the folder to navigate into
     */
    fun navigateToFolder(folderPath: String) {
        val currentPath = state.value.currentPath ?: state.value.resource?.path
        Timber.d("BrowseViewModel.navigateToFolder: from='$currentPath' to='$folderPath'")
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            // Push current path to stack
            val newStack = state.value.pathStack.toMutableList()
            if (currentPath != null) {
                newStack.add(currentPath)
            }
            
            // Update state with new path
            updateState { it.copy(
                currentPath = folderPath,
                pathStack = newStack,
                isSubfolderMode = true,
                mediaFiles = emptyList(), // Clear files while loading
                selectedFiles = emptySet() // Clear selection
            ) }
            
            // Load contents of the new folder
            loadDirectoryContents(folderPath)
        }
    }
    
    /**
     * Navigate back to parent folder.
     * Called when user presses back button while in a subfolder.
     * 
     * @return true if navigated back to parent, false if already at root (should finish activity)
     */
    fun navigateBack(): Boolean {
        val pathStack = state.value.pathStack
        
        if (pathStack.isEmpty()) {
            Timber.d("BrowseViewModel.navigateBack: At root, returning false")
            return false // At root, let activity handle back
        }
        
        val parentPath = pathStack.last()
        val newStack = pathStack.dropLast(1)
        
        Timber.d("BrowseViewModel.navigateBack: Going to '$parentPath', stack size=${newStack.size}")
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateState { it.copy(
                currentPath = if (newStack.isEmpty()) null else parentPath,
                pathStack = newStack,
                isSubfolderMode = newStack.isNotEmpty(),
                mediaFiles = emptyList(),
                selectedFiles = emptySet()
            ) }
            
            // Load contents of parent folder
            loadDirectoryContents(parentPath)
        }
        
        return true
    }
    
    /**
     * Computes a hash of directory contents based on file names, sizes, and modification dates.
     * Used to detect if directory contents changed without full comparison.
     * 
     * @param path Directory path to compute hash for
     * @return Hash string or null if computation failed
     */
    private suspend fun computeDirectoryHash(path: String): String? {
        return try {
            val resource = state.value.resource ?: return null
            val scanner = mediaScannerFactory.getScanner(resource.type)
            val supportedTypes = resource.supportedMediaTypes.ifEmpty { MediaType.entries.toSet() }
            
            // Get lightweight directory listing (names, sizes, dates only)
            val contents = scanner.listDirectoryContents(
                path = path,
                supportedTypes = supportedTypes,
                sizeFilter = null,
                credentialsId = resource.credentialsId,
                showHiddenFiles = false
            )
            
            // Compute hash from sorted file metadata (name + size + date)
            val metadataString = contents
                .sortedBy { it.name }
                .joinToString("|") { "${it.name}:${it.size}:${it.createdDate}:${it.isDirectory}" }
            
            // Use simple hash (hashCode is deterministic for same string)
            metadataString.hashCode().toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to compute directory hash for '$path'")
            null
        }
    }
    
    /**
     * Invalidates directory cache for current path and all parent paths.
     * Called after file operations (delete, move, copy, rename) to ensure
     * next navigation shows fresh data.
     */
    private fun invalidateDirectoryCache() {
        val currentPath = state.value.currentPath
        if (currentPath != null) {
            directoryCache.remove(currentPath)
            Timber.d("BrowseViewModel: Invalidated directory cache for '$currentPath'")
        }
        
        // Also invalidate parent paths in stack (user might navigate back)
        state.value.pathStack.forEach { path ->
            directoryCache.remove(path)
        }
        
        if (directoryCache.isNotEmpty()) {
            Timber.d("BrowseViewModel: Invalidated ${directoryCache.size} parent directory caches")
            directoryCache.clear() // Clear all to be safe
        }
    }
    
    /**
     * Get current breadcrumb path for display.
     * Returns relative path from resource root.
     */
    fun getCurrentBreadcrumb(): String {
        val resourcePath = state.value.resource?.path ?: return ""
        val currentPath = state.value.currentPath ?: return ""
        
        return if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            java.io.File(currentPath).name
        }
    }
    
    /**
     * Load directory contents (files + folders) for subfolder navigation mode.
     */
    private suspend fun loadDirectoryContents(path: String) {
        val resource = state.value.resource ?: return
        
        // Check cache first
        val cachedEntry = directoryCache[path]
        if (cachedEntry != null) {
            Timber.d("BrowseViewModel.loadDirectoryContents: Found cache for '$path', computing hash...")
            
            // Compute current hash to check if directory changed
            val currentHash = computeDirectoryHash(path)
            
            if (currentHash != null && currentHash == cachedEntry.contentHash) {
                Timber.d("BrowseViewModel.loadDirectoryContents: Cache valid (hash match), using cached ${cachedEntry.files.size} files")
                withContext(Dispatchers.Main) {
                    updateState { it.copy(
                        mediaFiles = cachedEntry.files,
                        loadingProgress = 0
                    ) }
                }
                return
            } else {
                Timber.d("BrowseViewModel.loadDirectoryContents: Cache invalid (hash mismatch or compute failed), rescanning")
                directoryCache.remove(path) // Invalidate stale cache
            }
        } else {
            Timber.d("BrowseViewModel.loadDirectoryContents: No cache for '$path', scanning...")
        }
        
        setLoading(true)
        updateState { it.copy(loadingProgress = 0) }
        
        try {
            Timber.d("BrowseViewModel.loadDirectoryContents: Loading '$path'")
            
            val scanner = mediaScannerFactory.getScanner(resource.type)
            val supportedTypes = resource.supportedMediaTypes.ifEmpty { MediaType.entries.toSet() }
            
            val contents = scanner.listDirectoryContents(
                path = path,
                supportedTypes = supportedTypes,
                sizeFilter = null,
                credentialsId = resource.credentialsId,
                showHiddenFiles = false
            )
            
            // Filter out directories if scanSubdirectories is disabled
            // When scanSubdirectories = false, user should only see files at the resource path
            val filteredContents = if (resource.scanSubdirectories) {
                contents
            } else {
                // Only show files, not directories
                contents.filter { !it.isDirectory }
            }
            
            Timber.d("BrowseViewModel.loadDirectoryContents: Found ${contents.size} items (${contents.count { it.isDirectory }} folders), after filter: ${filteredContents.size} items")
            
            // Compute and cache hash for future use
            val metadataString = filteredContents
                .sortedBy { it.name }
                .joinToString("|") { "${it.name}:${it.size}:${it.createdDate}:${it.isDirectory}" }
            val contentHash = metadataString.hashCode().toString()
            
            directoryCache[path] = DirectoryCacheEntry(
                files = filteredContents,
                contentHash = contentHash,
                timestamp = System.currentTimeMillis()
            )
            Timber.d("BrowseViewModel.loadDirectoryContents: Cached results with hash=$contentHash")
            
            withContext(Dispatchers.Main) {
                updateState { it.copy(
                    mediaFiles = filteredContents,
                    loadingProgress = 0
                ) }
            }
            
            if (filteredContents.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage("Folder is empty"))
            }
        } catch (e: Exception) {
            Timber.e(e, "BrowseViewModel.loadDirectoryContents: Error loading '$path'")
            sendEvent(BrowseEvent.ShowError("Failed to load folder: ${e.message}"))
        } finally {
            setLoading(false)
        }
    }
    
    /**
     * Enable subfolder navigation mode.
     * Reloads current resource with folder listing instead of recursive scan.
     */
    fun enableSubfolderMode() {
        Timber.d("BrowseViewModel.enableSubfolderMode")
        val resourcePath = state.value.resource?.path ?: return
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateState { it.copy(
                isSubfolderMode = true,
                currentPath = resourcePath,
                pathStack = emptyList(),
                mediaFiles = emptyList()
            ) }
            
            loadDirectoryContents(resourcePath)
        }
    }
    
    /**
     * Disable subfolder navigation mode.
     * Reloads files with recursive scan (flat view).
     */
    fun disableSubfolderMode() {
        Timber.d("BrowseViewModel.disableSubfolderMode")
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateState { it.copy(
                isSubfolderMode = false,
                currentPath = null,
                pathStack = emptyList()
            ) }
            
            // Reload with recursive scan
            loadResource()
        }
    }
    
    // ===== END SUBFOLDER NAVIGATION =====

    private fun loadResource() {
        Timber.d("BrowseViewModel.loadResource: START - resourceId=$resourceId")
        // Cancel previous loadResource if still running to prevent parallel loads
        loadResourceJob?.cancel()
        loadResourceJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Handle virtual Favorites resource
            if (resourceId == -100L) { // FAVORITES_RESOURCE_ID
                Timber.d("BrowseViewModel.loadResource: Loading Favorites resource")
                val favoritesResource = MediaResource(
                    id = -100L,
                    name = "Favorites", // Should be localized ideally
                    path = "Favorites",
                    type = ResourceType.LOCAL,
                    isAvailable = true,
                    fileCount = 0, // Will be updated when files load
                    isWritable = false // Favorites list itself is not writable (can't add files to it directly via file system)
                )
                
                updateState { 
                    it.copy(
                        resource = favoritesResource,
                        sortMode = SortMode.DATE_DESC, // Default to newest added
                        displayMode = DisplayMode.LIST,
                        isCloudResource = false,
                        filter = null
                    ) 
                }
                
                loadFavorites()
                return@launch
            }
            
            Timber.d("BrowseViewModel.loadResource: Fetching resource from database...")
            val resource = getResourcesUseCase.getById(resourceId)
            if (resource == null) {
                Timber.e("BrowseViewModel.loadResource: ERROR - Resource not found for id=$resourceId")
                sendEvent(BrowseEvent.ShowError("Resource not found"))
                setLoading(false)
                return@launch
            }
            
            // Configure ConnectionThrottleManager for active resource (instead of global config in MainViewModel)
            if (resource.recommendedThreads != null) {
                val resourceKey = when {
                    resource.path.startsWith("smb://") -> resource.path.substringBefore("/", resource.path)
                    resource.path.startsWith("ftp://") -> resource.path.substringBefore("/", resource.path.substringAfter("://"))
                        .let { "ftp://$it" }
                    resource.path.startsWith("sftp://") -> resource.path.substringBefore("/", resource.path.substringAfter("://"))
                        .let { "sftp://$it" }
                    else -> resource.path
                }
                com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setRecommendedThreads(
                    resourceKey,
                    resource.recommendedThreads
                )
                Timber.d("BrowseViewModel: Configured ConnectionThrottleManager for $resourceKey with ${resource.recommendedThreads} threads")
            }
            
            Timber.d("BrowseViewModel.loadResource: Resource found - name='${resource.name}', type=${resource.type}, path='${resource.path}'")
            Timber.d("BrowseViewModel.loadResource: fileCount=${resource.fileCount}, isWritable=${resource.isWritable}, lastBrowse=${resource.lastBrowseDate}")
            Timber.i("╔═══ RESOURCE MEDIA TYPES ═══")
            Timber.i("║ supportedMediaTypes: ${resource.supportedMediaTypes.map { it.name }}")
            Timber.i("║ Size: ${resource.supportedMediaTypes.size}/7")
            Timber.i("║ Contains VIDEO: ${resource.supportedMediaTypes.contains(com.sza.fastmediasorter.domain.model.MediaType.VIDEO)}")
            Timber.i("╚═══════════════════════════")
            
            // Note: Previously we called smbClient.resetClients() here, but that kills 
            // active background operations (copy/move) which share the singleton client.
            // Removed to allow background operations to continue when re-entering browse.
            
            // Check if resource is available (skip if already validated in MainActivity)
            // For network resources (SFTP/FTP/SMB), skip this check and try to load files directly
            // because fileCount might be 0 if initial scan failed, but connection might work
            val isNetworkResource = resource.type in setOf(
                com.sza.fastmediasorter.domain.model.ResourceType.SMB,
                com.sza.fastmediasorter.domain.model.ResourceType.SFTP,
                com.sza.fastmediasorter.domain.model.ResourceType.FTP
            )
            
            Timber.d("BrowseViewModel.loadResource: isNetworkResource=$isNetworkResource, skipAvailabilityCheck=$skipAvailabilityCheck")
            
            if (!skipAvailabilityCheck && !isNetworkResource && resource.fileCount == 0 && !resource.isWritable) {
                Timber.w("BrowseViewModel.loadResource: Resource unavailable - fileCount=0, isWritable=false")
                sendEvent(BrowseEvent.ShowError(
                    message = "Resource '${resource.name}' is unavailable. Check network connection or resource settings.",
                    details = "Resource ID: ${resource.id}\nType: ${resource.type}\nPath: ${resource.path}"
                ))
                setLoading(false)
                return@launch
            }
            
            // Determine if cloud resource for progress UI
            val isCloudResource = resource.type == ResourceType.CLOUD
            
            // Restore filter from resource.supportedMediaTypes if it's a subset
            // If supportedMediaTypes contains all 7 types (IVAGTPE), filter should be null (no filter)
            val restoredFilter = if (resource.supportedMediaTypes.size < 7) {
                FileFilter(
                    nameContains = null,
                    minDate = null,
                    maxDate = null,
                    minSizeMb = null,
                    maxSizeMb = null,
                    mediaTypes = resource.supportedMediaTypes
                )
            } else {
                null // All types = no filter
            }
            
            Timber.d("BrowseViewModel.loadResource: restoredFilter=$restoredFilter (supportedTypes=${resource.supportedMediaTypes.size})")
            Timber.i("╔═══ FILTER RESTORED ═══")
            Timber.i("║ Will filter: ${restoredFilter != null}")
            Timber.i("║ Filter types: ${restoredFilter?.mediaTypes?.map { it.name } ?: "ALL"}")
            Timber.i("╚════════════════════════")
            Timber.w("🔶 SORT_DEBUG loadResource: RESTORING sortMode=${resource.sortMode} from DATABASE for resource '${resource.name}' (id=${resource.id})")
            
            // Determine initial subfolder mode based on resource settings
            // Subfolder navigation only makes sense when scanSubdirectories is enabled
            val initialSubfolderMode = resource.scanSubdirectories && resource.showSubfoldersAsItems
            Timber.d("BrowseViewModel.loadResource: initialSubfolderMode=$initialSubfolderMode (scanSubdirectories=${resource.scanSubdirectories}, showSubfoldersAsItems=${resource.showSubfoldersAsItems})")
            
            updateState { 
                it.copy(
                    resource = resource,
                    sortMode = resource.sortMode,
                    displayMode = resource.displayMode,
                    isCloudResource = isCloudResource,
                    filter = restoredFilter,
                    isSubfolderMode = initialSubfolderMode,
                    currentPath = null, // Start at root
                    pathStack = emptyList()
                ) 
            }
            
            Timber.d("BrowseViewModel.loadResource: State updated - displayMode=${resource.displayMode}, sortMode=${resource.sortMode}")
            Timber.d("BrowseViewModel.loadResource: Cloud resource details - cloudProvider=${resource.cloudProvider}, cloudFolderId=${resource.cloudFolderId}, path=${resource.path}")
            
            // For Google Drive resources, verify authentication by testing API access
            if (resource.type == ResourceType.CLOUD && resource.cloudProvider == CloudProvider.GOOGLE_DRIVE) {
                // Try to restore authentication from encrypted storage if not authenticated
                if (!googleDriveClient.isAuthenticated()) {
                    Timber.d("BrowseViewModel.loadResource: Client not authenticated, attempting automatic restoration")
                    val restored = googleDriveClient.tryRestoreFromStorage()
                    if (restored) {
                        Timber.d("BrowseViewModel.loadResource: Successfully restored Google Drive authentication")
                    } else {
                        Timber.d("BrowseViewModel.loadResource: Failed to restore authentication, will require interactive sign-in")
                    }
                }
                
                Timber.d("BrowseViewModel.loadResource: Verifying Google Drive access for folder: ${resource.cloudFolderId}")
                val testResult = googleDriveClient.listFiles(resource.cloudFolderId ?: "root")
                if (testResult is com.sza.fastmediasorter.data.cloud.CloudResult.Error) {
                    Timber.d("BrowseViewModel.loadResource: Google Drive access failed: ${testResult.message}")
                    // Check if error indicates authentication issue
                    val is401 = testResult.message.contains("401", ignoreCase = true)
                    val isUnauth = testResult.message.contains("unauthorized", ignoreCase = true)
                    val hasAuth = testResult.message.contains("authentication", ignoreCase = true)
                    val notAuth = testResult.message.contains("not authenticated", ignoreCase = true)
                    Timber.d("BrowseViewModel.loadResource: Auth error check: 401=$is401, unauthorized=$isUnauth, authentication=$hasAuth, not_authenticated=$notAuth")
                    
                    if (is401 || isUnauth || hasAuth || notAuth) {
                        Timber.d("BrowseViewModel.loadResource: Sending ShowCloudAuthenticationRequired event")
                        sendEvent(BrowseEvent.ShowCloudAuthenticationRequired(resource.cloudProvider))
                        setLoading(false)
                        return@launch
                    }
                    // Other errors will be handled by normal error flow
                    Timber.d("BrowseViewModel.loadResource: Error is not authentication-related, continuing normal flow")
                } else {
                    Timber.d("BrowseViewModel.loadResource: Google Drive access verified successfully")
                }
            }
            
            // Check cache using BrowseCacheManager with TTL-based freshness check
            Timber.d("BrowseViewModel.loadResource: Checking for cached files...")
            when (val cacheResult = cacheManager.checkCache(
                filter = restoredFilter ?: FileFilter(),
                lastBrowseDate = resource.lastBrowseDate
            )) {
                is com.sza.fastmediasorter.ui.browse.cache.BrowseCacheManager.CacheCheckResult.UseCache -> {
                    // Filter cached files based on scanSubdirectories setting
                    var filteredFiles = if (resource.scanSubdirectories) {
                        cacheResult.files
                    } else {
                        // When scanSubdirectories=false: remove directories AND files from subdirectories
                        // Cache may contain recursive results from a previous scanSubdirectories=true session
                        val resourcePath = resource.path.trimEnd('/')
                        cacheResult.files.filter { file ->
                            if (file.isDirectory) return@filter false
                            // Keep only files directly in the resource root folder
                            // A file is in root if its parent path matches the resource path
                            val filePath = file.path.trimEnd('/')
                            val parentPath = filePath.substringBeforeLast('/', "")
                            parentPath.trimEnd('/') == resourcePath
                        }
                    }
                    
                    // CRITICAL FIX: Filter cached files by resource.supportedMediaTypes
                    // Cache may contain files from previous "allFiles" mode session
                    // Always respect resource-level supportedMediaTypes configuration
                    if (!resource.allFiles && resource.supportedMediaTypes.isNotEmpty()) {
                        val beforeCount = filteredFiles.size
                        filteredFiles = filteredFiles.filter { file ->
                            file.isDirectory || resource.supportedMediaTypes.contains(file.type)
                        }
                        Timber.d("BrowseViewModel.loadResource: Cache filtered by supportedMediaTypes=${resource.supportedMediaTypes.map { it.name }} - before=$beforeCount, after=${filteredFiles.size}")
                    }
                    
                    Timber.d("BrowseViewModel.loadResource: Cache files filtered - original=${cacheResult.files.size}, after scanSubdirectories filter=${filteredFiles.size}, scanSubdirectories=${resource.scanSubdirectories}")
                    
                    // Update totalFileCount from actual filtered files count
                    updateState { it.copy(mediaFiles = filteredFiles, totalFileCount = filteredFiles.size) }
                    setLoading(false)
                    
                    // Update resource metadata (lastBrowseDate) even when loading from cache
                    Timber.d("BrowseViewModel.loadResource: Cache hit, updating metadata for ${resource.name}")
                    updateResourceMetadataAfterBrowse(resource, filteredFiles.size)
                    
                    return@launch
                }
                is com.sza.fastmediasorter.ui.browse.cache.BrowseCacheManager.CacheCheckResult.Rescan -> {
                    Timber.d("BrowseViewModel.loadResource: Cache rejected - ${cacheResult.reason}")
                    // Continue with fresh scan below
                }
            }
            
            // NOTE: Trash cleanup removed from loadResource() to avoid race condition
            // with ongoing delete operations. Cleanup now only runs on onCleared().
            // See commit: Fix delete race condition with trash cleanup
            
            Timber.d("BrowseViewModel.loadResource: Calling loadMediaFiles()...")
            loadMediaFiles()
        }
    }

    private fun loadFavorites() {
        Timber.d("BrowseViewModel.loadFavorites: START")
        
        loadFilesJob?.cancel()
        
        loadFilesJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            favoritesUseCase.getAllFavorites().collect { favorites ->
                Timber.d("BrowseViewModel.loadFavorites: Received ${favorites.size} favorites from database")
                favorites.forEachIndexed { index, entity ->
                    Timber.d("BrowseViewModel.loadFavorites: [$index] uri='${entity.uri}', name='${entity.displayName}', resourceId=${entity.resourceId}")
                }
                
                val mediaFiles = favorites.map { entity ->
                    MediaFile(
                        path = entity.uri,
                        name = entity.displayName,
                        type = MediaType.entries.getOrElse(entity.mediaType) { MediaType.IMAGE },
                        size = entity.size,
                        createdDate = entity.dateModified,
                        isFavorite = true,
                        resourceId = entity.resourceId,
                        width = 0,
                        height = 0,
                        duration = 0
                    )
                }
                
                Timber.d("BrowseViewModel.loadFavorites: Mapped to ${mediaFiles.size} MediaFile objects")
                
                // Cache the list for PlayerActivity
                MediaFilesCacheManager.setCachedList(-100L, mediaFiles)
                
                updateState { it.copy(
                    mediaFiles = mediaFiles,
                    totalFileCount = mediaFiles.size,
                    loadingProgress = mediaFiles.size,
                    usePagination = false // No pagination for favorites for now
                ) }
                setLoading(false)
                Timber.d("BrowseViewModel.loadFavorites: COMPLETE - updated UI state")
            }
        }
    }

    private fun loadMediaFiles() {
        val resource = state.value.resource ?: return
        
        if (resource.id == -100L) {
            loadFavorites()
            return
        }
        
        Timber.d("BrowseViewModel.loadMediaFiles: START - resource='${resource.name}' (id=${resource.id})")
        
        // Cancel previous load if still running (e.g., during manual refresh)
        if (loadFilesJob?.isActive == true) {
            Timber.d("BrowseViewModel.loadMediaFiles: Cancelling previous load job")
            loadFilesJob?.cancel()
        }
        
        // Starting new load
        
        // Don't clear current list to prevent UI flashing/empty state
        // updateState { it.copy(mediaFiles = emptyList(), loadingProgress = 0, isScanCancellable = false) }
        updateState { it.copy(loadingProgress = 0, isScanCancellable = false) }
        
        Timber.d("BrowseViewModel.loadMediaFiles: Starting progress timer")
        // Start progress timer for UI updates every 2 seconds
        var lastProgressUpdate = 0
        val progressJob = viewModelScope.launch(ioDispatcher) {
            while (true) {
                delay(2000) // Update every 2 seconds
                val currentProgress = state.value.loadingProgress
                if (currentProgress > 0 && currentProgress != lastProgressUpdate) {
                    Timber.d("BrowseViewModel.loadMediaFiles: Progress timer update - $currentProgress files found")
                    lastProgressUpdate = currentProgress
                }
            }
        }
        
        loadFilesJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Reset graceful stop flag at start of new scan
            shouldStopScan.set(false)
            
            // Cleanup orphaned .temp_copy files from previous failed transfers
            // Non-blocking - errors are logged but do not prevent file loading
            try {
                Timber.d("BrowseViewModel.loadMediaFiles: Starting cleanup of orphaned temp files in '${resource.path}'")
                val cleanupResult = cleanupOrphanedTempFilesUseCase(resource.path)
                cleanupResult.fold(
                    onSuccess = { deletedCount ->
                        if (deletedCount > 0) {
                            Timber.i("BrowseViewModel.loadMediaFiles: Cleanup completed - deleted $deletedCount orphaned temp file(s)")
                        } else {
                            Timber.d("BrowseViewModel.loadMediaFiles: Cleanup completed - no orphaned temp files found")
                        }
                    },
                    onFailure = { error ->
                        Timber.w(error, "BrowseViewModel.loadMediaFiles: Cleanup failed (non-critical, continuing)")
                    }
                )
            } catch (e: Exception) {
                Timber.w(e, "BrowseViewModel.loadMediaFiles: Cleanup exception (non-critical, continuing)")
            }
            
            // Start 5-second timer to show STOP button in the same coroutine context
            launch {
                delay(5_000L)
                if (loadFilesJob?.isActive == true) {
                    updateState { it.copy(isScanCancellable = true) }
                    Timber.d("BrowseViewModel.loadMediaFiles: Scan running >5s, showing STOP button")
                }
            }
            
            Timber.d("BrowseViewModel.loadMediaFiles: Getting size filter settings")
            // Get current settings for size filters and global media type restrictions (use cache)
            val settings = getSettings()
            val sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin,
                imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin,
                videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin,
                audioSizeMax = settings.audioSizeMax
            )
            Timber.d("BrowseViewModel.loadMediaFiles: Size filter configured - imageMin=${settings.imageSizeMin}, videoMin=${settings.videoSizeMin}")
            
            // CRITICAL: Check if "All Files" setting is enabled
            // FIXED: Resource-level allFiles overrides supportedMediaTypes, but global setting does NOT override resource configuration
            val effectiveMediaTypes = if (resource.allFiles) {
                // Resource-specific "show all files" flag - overrides supportedMediaTypes for THIS resource
                Timber.d("BrowseViewModel.loadMediaFiles: 'All Files' mode ON for RESOURCE - showing ALL file types (resource.allFiles=true)")
                MediaType.entries.toSet() // All 7 types: IMAGE, VIDEO, AUDIO, GIF, TEXT, PDF, EPUB
            } else {
                // Respect resource's supportedMediaTypes, intersect with global restrictions
                val globallyEnabled = settings.getGloballyEnabledMediaTypes()
                val intersected = resource.supportedMediaTypes.intersect(globallyEnabled)
                Timber.d("BrowseViewModel.loadMediaFiles: Normal mode - resource types: ${resource.supportedMediaTypes}, global enabled: $globallyEnabled, effective: $intersected (global allFiles ignored: ${settings.allFiles})")
                intersected
            }
            
            val resourceWithGlobalFilter = if (effectiveMediaTypes != resource.supportedMediaTypes) {
                resource.copy(supportedMediaTypes = effectiveMediaTypes)
            } else {
                resource
            }
            
            try {
                // Use cached fileCount from resource metadata if available
                if (resource.fileCount > 0 && resource.lastBrowseDate != null) {
                    Timber.d("BrowseViewModel.loadMediaFiles: Using cached file count from resource: ${resource.fileCount}")
                    updateState { it.copy(totalFileCount = resource.fileCount) }
                } else {
                    Timber.d("BrowseViewModel.loadMediaFiles: No cached file count, will count during scan")
                }
                // If no cache, totalFileCount stays null (shows "counting..." in UI)
                
                // Network resources use internal timeout handling (no wrapping timeout)
                // SMB handles adaptive timeouts via SmbConnectionManager
                // No timeout wrapper - let protocol clients handle their own timeouts
                // This allows long file listing operations to complete without premature timeout
                loadMediaFilesStandard(resourceWithGlobalFilter, sizeFilter, progressJob)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "BrowseViewModel.loadMediaFiles: ERROR determining file count")
                progressJob.cancel()
                // Fallback to standard loading if count fails
                loadMediaFilesStandard(resourceWithGlobalFilter, sizeFilter, progressJob)
            }
        }
    }
    
    private suspend fun loadMediaFilesStandard(resource: MediaResource, sizeFilter: SizeFilter, progressJob: Job? = null) {
        // Get settings to determine showHiddenFiles flag (use cache)
        val settings = getSettings()
        val showHiddenFiles = settings.showHiddenFiles || resource.showHiddenFiles
        Timber.d("BrowseViewModel.loadMediaFilesStandard: showHiddenFiles=$showHiddenFiles (global=${settings.showHiddenFiles}, resource=${resource.showHiddenFiles})")
        
        // Delegate to BrowseLoadingManager with callbacks
        val callbacks = object : com.sza.fastmediasorter.ui.browse.loading.BrowseLoadingManager.LoadingCallbacks {
            override suspend fun updateLoadingProgress(progress: Int) {
                updateState { it.copy(loadingProgress = progress) }
            }
            
            override suspend fun updateState(mediaFiles: List<MediaFile>, usePagination: Boolean, loadingProgress: Int, totalFileCount: Int, isScanCancellable: Boolean) {
                // Note: Don't check for empty mediaFiles here and send NoFilesFound event
                // If there was a scan error, handleLoadingError() already showed the correct error message
                // Empty mediaFiles here could mean either:
                // 1. Scan error (already handled by handleLoadingError)
                // 2. Truly empty folder (user will see empty list, which is correct)
                
                this@BrowseViewModel.updateState { it.copy(
                    mediaFiles = mediaFiles,
                    usePagination = usePagination,
                    loadingProgress = loadingProgress,
                    totalFileCount = totalFileCount,
                    isScanCancellable = isScanCancellable
                ) }
            }
            
            override fun setLoading(loading: Boolean) {
                this@BrowseViewModel.setLoading(loading)
            }
            
            override suspend fun handleLoadingError(resource: MediaResource, error: Throwable) {
                this@BrowseViewModel.handleLoadingError(resource, error)
            }
            
            override suspend fun updateResourceMetadata(resource: MediaResource, fileCount: Int) {
                updateResourceMetadataAfterBrowse(resource, fileCount)
            }
            
            override fun startFileObserver() {
                this@BrowseViewModel.startFileObserver()
            }
            
            override fun sortFiles(files: List<MediaFile>, sortMode: SortMode, forceSort: Boolean): List<MediaFile> {
                return this@BrowseViewModel.sortFiles(files, sortMode, forceSort)
            }
        }
        
        // Get subfolder navigation state from current state
        val currentState = state.value
        val currentPath = currentState.currentPath
        val isSubfolderMode = currentState.isSubfolderMode
        
        loadingManager.loadFilesStandard(
            resource = resource,
            sortMode = state.value.sortMode,
            sizeFilter = sizeFilter,
            shouldStopScan = shouldStopScan,
            progressJob = progressJob,
            showHiddenFiles = showHiddenFiles,
            currentPath = currentPath,
            isSubfolderMode = isSubfolderMode,
            callbacks = callbacks
        )
    }
    
    private fun loadMediaFilesWithPagination(resource: MediaResource, sizeFilter: SizeFilter) {
        try {
            // Delegate pagination setup to BrowseLoadingManager
            val newFlow = loadingManager.setupPagination(
                resource = resource,
                sortMode = state.value.sortMode,
                sizeFilter = sizeFilter
            )
            
            _pagingDataFlow.value = newFlow
            updateState { it.copy(usePagination = true, mediaFiles = emptyList()) }
            setLoading(false)
            
            // Update resource metadata (fileCount from totalFileCount and lastBrowseDate)
            // Launch in viewModelScope since updateResourceMetadataAfterBrowse is now suspend
            viewModelScope.launch(ioDispatcher) {
                val actualFileCount = state.value.totalFileCount ?: 0
                updateResourceMetadataAfterBrowse(resource, actualFileCount)
            }
            
            Timber.d("Pagination enabled for ${resource.name}")

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Error setting up pagination")
            handleLoadingError(resource, e)
            setLoading(false)
        }
    }
    
    /**
     * Updates resource metadata (fileCount and lastBrowseDate) after successful file loading.
     * Delegated to BrowseMetadataManager.
     */
    private suspend fun updateResourceMetadataAfterBrowse(resource: MediaResource, actualFileCount: Int) {
        metadataManager.updateMetadata(resource, actualFileCount)
    }
    
    private fun handleLoadingError(resource: MediaResource, e: Throwable) {
        // Check if this is ConnectionThrottle cancellation (video player active)
        if (e is java.util.concurrent.CancellationException && 
            e.message?.contains("Video player priority", ignoreCase = true) == true) {
            Timber.d("Folder loading blocked by video player throttle")
            sendEvent(BrowseEvent.ShowError(
                message = "Cannot load folder while video is playing\n\n" +
                         "The video player has priority access to network resources.\n\n" +
                         "Please close the video player and try again.",
                details = "Resource: ${resource.name}\nPath: ${resource.path}\n\n" +
                         "This is a safety feature to prevent network congestion and ensure smooth video playback.",
                exception = e
            ))
            setLoading(false)
            return
        }
        
        // Check if this is Cloud authentication error - send special event
        if (resource.type == ResourceType.CLOUD && 
            (e.message?.contains("Google Drive authentication required", ignoreCase = true) == true ||
             e.message?.contains("Not authenticated", ignoreCase = true) == true)) {
            Timber.d("Cloud authentication error detected, sending ShowCloudAuthenticationRequired event")
            sendEvent(BrowseEvent.ShowCloudAuthenticationRequired(resource.cloudProvider ?: CloudProvider.GOOGLE_DRIVE))
            setLoading(false)
            return
        }
        
        // Update resource availability to false on connection errors
        viewModelScope.launch(ioDispatcher) {
            try {
                val isConnectionError = e.message?.contains("Connection", ignoreCase = true) == true ||
                    e.message?.contains("Network", ignoreCase = true) == true ||
                    e.message?.contains("Authentication", ignoreCase = true) == true ||
                    e.message?.contains("timed out", ignoreCase = true) == true
                
                if (isConnectionError && resource.isAvailable) {
                    Timber.d("Updating resource availability to false due to connection error")
                    val updatedResource = resource.copy(isAvailable = false)
                    updateResourceUseCase(updatedResource)
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Failed to update resource availability")
            }
        }
        
        // Build detailed error information
        val errorTitle = when {
            e.message?.contains("Authentication failed", ignoreCase = true) == true ||
            e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true -> {
                "Authentication Failed"
            }
            e.message?.contains("Connection error", ignoreCase = true) == true ||
            e.message?.contains("Network", ignoreCase = true) == true -> {
                "Network Connection Error"
            }
            e.message?.contains("Permission denied", ignoreCase = true) == true -> {
                "Permission Denied"
            }
            else -> {
                "Error Loading Files"
            }
        }
        
        val errorMessage = when {
            e.message?.contains("Authentication failed", ignoreCase = true) == true ||
            e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true -> {
                "Invalid username or password.\n\n" +
                "Recommendations:\n" +
                "• Edit this resource and update credentials\n" +
                "• Verify username and password are correct\n" +
                "• Check if account is locked or expired"
            }
            e.message?.contains("Connection error", ignoreCase = true) == true ||
            e.message?.contains("Network", ignoreCase = true) == true -> {
                "Cannot connect to server.\n\n" +
                "Recommendations:\n" +
                "• Check your network connection\n" +
                "• Verify server address and port\n" +
                "• Check if server is online and accessible\n" +
                "• Try pinging the server from another device"
            }
            e.message?.contains("timed out", ignoreCase = true) == true ||
            e.message?.contains("SocketTimeoutException", ignoreCase = true) == true -> {
                "Connection timeout.\n\n" +
                "Possible causes:\n" +
                "• Server is slow or overloaded\n" +
                "• Network latency is too high\n" +
                "• Firewall blocking data connection (FTP passive mode)\n" +
                "• NAT/Router issues with FTP passive mode\n\n" +
                "Recommendations:\n" +
                "• Check server status and load\n" +
                "• Test connection from computer/laptop\n" +
                "• Configure firewall to allow FTP passive mode\n" +
                "• Check router port forwarding settings\n" +
                "• Contact server administrator if problem persists"
            }
            e.message?.contains("Permission denied", ignoreCase = true) == true -> {
                "No access to this folder.\n\n" +
                "Recommendations:\n" +
                "• Check folder permissions on server\n" +
                "• Verify your account has read access\n" +
                "• Try accessing from root directory first"
            }
            else -> {
                "Failed to load media files.\n\n" +
                "See error details below for more information."
            }
        }
        
        val errorDetails = buildString {
            append("Resource: ${resource.name}\n")
            append("Path: ${resource.path}\n")
            append("Type: ${resource.type}\n\n")
            append("Error: ${e.message ?: "Unknown error"}\n\n")
            append("Stack trace:\n${e.stackTraceToString()}")
        }
        
        sendEvent(BrowseEvent.ShowError(
            message = "$errorTitle\n\n$errorMessage",
            details = errorDetails,
            exception = e
        ))
        handleError(e)
    }

    fun setSortMode(sortMode: SortMode) {
        val resource = state.value.resource ?: return
        
        Timber.w("🔶 SORT_DEBUG setSortMode: START - changing from ${state.value.sortMode} to $sortMode for resource '${resource.name}' (id=${resource.id})")
        
        // Create updated resource with new sortMode
        val updatedResource = resource.copy(sortMode = sortMode)
        
        // Update state immediately for UI responsiveness (update BOTH sortMode and resource)
        updateState { it.copy(sortMode = sortMode, resource = updatedResource) }
        Timber.w("🔶 SORT_DEBUG setSortMode: State updated immediately for UI (sortMode + resource)")
        
        // Save to database SYNCHRONOUSLY to ensure it persists
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            // Use NonCancellable to ensure save completes even if user navigates away
            withContext(NonCancellable) {
                Timber.w("🔶 SORT_DEBUG setSortMode: Calling updateResourceUseCase with sortMode=$sortMode, resource.id=${updatedResource.id}")
                updateResourceUseCase(updatedResource)
                Timber.w("🔶 SORT_DEBUG setSortMode: SAVED sortMode=$sortMode for resource: ${resource.name} (id=${resource.id})")
                
                // VERIFICATION: Read back from DB to confirm save
                val verification = getResourcesUseCase.getById(resource.id)
                Timber.w("🔶 SORT_DEBUG setSortMode: VERIFICATION - read back sortMode=${verification?.sortMode} from database")
            }
        }
        
        // If we have cached list, re-sort it instead of rescanning
        val cachedFiles = MediaFilesCacheManager.getCachedList(resourceId)
        if (cachedFiles != null && cachedFiles.isNotEmpty()) {
            Timber.d("setSortMode: Applying sort to cached list (${cachedFiles.size} files)")
            // Apply current filter first, then sort
            val currentFilter = state.value.filter ?: FileFilter()
            val filteredFiles = applyFilterToList(cachedFiles, currentFilter)
            val sortedFiles = sortFiles(filteredFiles, sortMode, forceSort = true)  // User-requested sort - always apply
            // Update cache with full list (unfiltered) but sorted
            val sortedCache = sortFiles(cachedFiles, sortMode, forceSort = true)
            MediaFilesCacheManager.setCachedList(resourceId, sortedCache)
            updateState { it.copy(mediaFiles = sortedFiles) }
        } else {
            // No cache or empty cache - need to reload
            if (cachedFiles != null && cachedFiles.isEmpty()) {
                Timber.w("setSortMode: Found empty cache for resource $resourceId, clearing before reload")
                MediaFilesCacheManager.clearCache(resourceId)
            }
            loadMediaFiles()
        }
    }

    fun toggleDisplayMode() {
        val resource = state.value.resource ?: return
        
        val newMode = if (state.value.displayMode == DisplayMode.LIST) {
            DisplayMode.GRID
        } else {
            DisplayMode.LIST
        }
        
        // Update resource in state with new display mode
        val updatedResource = resource.copy(displayMode = newMode)
        
        // Update state immediately for UI responsiveness
        updateState { it.copy(displayMode = newMode, resource = updatedResource) }
        
        // Save to database
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            // Use NonCancellable to ensure save completes even if user navigates away
            withContext(NonCancellable) {
                updateResourceUseCase(updatedResource)
                Timber.d("Saved displayMode=$newMode for resource: ${resource.name}")
            }
        }
    }

    fun selectFile(filePath: String) {
        selectionManager.toggleSelection(filePath)
    }
    
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

    fun openFile(file: MediaFile, approximatePosition: Int = 0) {
        Timber.d("BrowseViewModel.openFile: file=${file.name}, type=${file.type}, pos=$approximatePosition")
        val resource = state.value.resource ?: return
        
        // In pagination mode, pass approximatePosition to Player for smart chunk loading
        val index = if (state.value.usePagination) {
            Timber.d("openFile (pagination mode): ${file.name}, approximatePosition=$approximatePosition")
            approximatePosition
        } else {
            // Standard mode - find actual index in mediaFiles list
            val foundIndex = state.value.mediaFiles.indexOfFirst { it.path == file.path }
            if (foundIndex == -1) {
                Timber.w("openFile: Cache miss detected for ${file.path}, attempting fallback")
                if (!tryResolveMissingFile(file, approximatePosition)) {
                    sendEvent(BrowseEvent.ShowError("File not found: ${file.name}", null))
                }
                return
            }
            // Opening file in standard mode
            foundIndex
        }
        
        // Update state immediately for instant UI response
        val updatedResource = resource.copy(lastViewedFile = file.path)
        updateState { it.copy(resource = updatedResource) }
        
        // Save to database asynchronously
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateResourceUseCase(updatedResource)
            Timber.d("Saved lastViewedFile=${file.path} for resource: ${resource.name}")
        }
        
        sendEvent(BrowseEvent.NavigateToPlayer(file.path, index))
    }

    private fun tryResolveMissingFile(file: MediaFile, approximatePosition: Int): Boolean {
        val resource = state.value.resource ?: return false
        return when (resource.type) {
            ResourceType.SMB -> {
                resolveMissingSmbFile(resource, file, approximatePosition)
                true
            }
            else -> false
        }
    }

    private fun resolveMissingSmbFile(resource: MediaResource, file: MediaFile, approximatePosition: Int) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            Timber.d("resolveMissingSmbFile: Refreshing metadata for ${file.path}")
            setLoading(true)
            try {
                val scanner = mediaScannerFactory.getScanner(resource.type)
                val smbScanner = scanner as? com.sza.fastmediasorter.data.network.SmbMediaScanner
                if (smbScanner == null) {
                    Timber.e("resolveMissingSmbFile: Unable to obtain SMB scanner for resource ${resource.name}")
                    sendEvent(BrowseEvent.ShowError("File not found: ${file.name}", file.path))
                    return@launch
                }

                val refreshed = smbScanner.getFileByPath(
                    path = file.path,
                    supportedTypes = resource.supportedMediaTypes,
                    credentialsId = resource.credentialsId
                )

                if (refreshed != null) {
                    mergeResolvedFileAndOpen(refreshed, approximatePosition)
                } else {
                    Timber.e("resolveMissingSmbFile: Remote SMB file still missing ${file.path}")
                    sendEvent(BrowseEvent.ShowError("File not found: ${file.name}", file.path))
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun mergeResolvedFileAndOpen(resolvedFile: MediaFile, approximatePosition: Int) {
        val updatedList = state.value.mediaFiles.toMutableList().apply {
            removeAll { it.path == resolvedFile.path }
            add(resolvedFile)
        }

        val sortedList = sortFiles(updatedList, state.value.sortMode, forceSort = true)

        MediaFilesCacheManager.setCachedList(resourceId, sortedList)
        updateState { current ->
            current.copy(
                mediaFiles = sortedList,
                totalFileCount = sortedList.size
            )
        }

        val targetIndex = sortedList.indexOfFirst { it.path == resolvedFile.path }
        val indexForPlayer = if (targetIndex >= 0) targetIndex else approximatePosition

        sendEvent(BrowseEvent.NavigateToPlayer(resolvedFile.path, indexForPlayer))
    }

    fun deleteSelectedFiles() {
        Timber.d("deleteSelectedFiles: ===== START =====")
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val selectedPaths = state.value.selectedFiles.toList()
            if (selectedPaths.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage("No files selected"))
                return@launch
            }
            
            setLoading(true)
            
            // Convert all paths to File objects (works for both local, network, and cloud)
            val filesToDelete = selectedPaths.map { path ->
                if (path.startsWith("smb://") || path.startsWith("sftp://") || path.startsWith("ftp://") || path.startsWith("cloud://")) {
                    // Network/cloud file - wrap in File object with original path
                    object : java.io.File(path) {
                        override fun getAbsolutePath(): String = path
                        override fun getPath(): String = path
                    }
                } else {
                    // Local file or SAF URI
                    java.io.File(path)
                }
            }
            
            // Determine if soft-delete is possible (only for local files, not SAF/network/cloud)
            val canUseSoftDelete = selectedPaths.all { path ->
                !path.startsWith("content:/") && !path.startsWith("smb://") && 
                !path.startsWith("sftp://") && !path.startsWith("ftp://") && 
                !path.startsWith("cloud://")
            }
            
            // Use FileOperationUseCase for delete (soft-delete for local, hard-delete for SAF/network/cloud)
            Timber.d("Deleting ${filesToDelete.size} files via FileOperationUseCase (softDelete=$canUseSoftDelete)")
            
            // Show progress message for large operations
            if (filesToDelete.size > 10) {
                sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.deleting_n_files, filesToDelete.size)))
            }
            
            // Block FileObserver to avoid duplicate removal from list
            setIgnoringFileChanges(true)
            
            val deleteOperation = FileOperation.Delete(
                files = filesToDelete,
                softDelete = canUseSoftDelete // Use trash only for local files
            )
            
            Timber.d("deleteSelectedFiles: Calling fileOperationUseCase.execute() with ${filesToDelete.size} files")
            when (val result = fileOperationUseCase.execute(deleteOperation)) {
                is com.sza.fastmediasorter.domain.usecase.FileOperationResult.Success -> {
                    val deletedCount = result.processedCount
                    Timber.i("Successfully deleted $deletedCount files (softDelete=$canUseSoftDelete)")
                    
                    // Save undo operation only for soft-delete (trash)
                    if (canUseSoftDelete) {
                        // result.copiedFilePaths format: [trashDirPath, originalPath1, originalPath2, ...]
                        val undoOp = UndoOperation(
                            type = com.sza.fastmediasorter.domain.model.FileOperationType.DELETE,
                            sourceFiles = selectedPaths,
                            destinationFolder = null,
                            copiedFiles = result.copiedFilePaths, // Contains trash dir + original paths
                            oldNames = null
                        )
                        saveUndoOperation(undoOp)
                    } else {
                        Timber.d("Hard delete used - no undo available for SAF/network/cloud files")
                    }
                    
                    // Clear selection and remove deleted files from list
                    clearSelection()
                    removeFiles(selectedPaths)
                    
                    // Re-enable FileObserver after operation complete
                    viewModelScope.launch {
                        delay(200) // Wait for OS events to settle
                        setIgnoringFileChanges(false)
                    }
                    
                    sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.deleted_n_files, deletedCount)))
                }
                is com.sza.fastmediasorter.domain.usecase.FileOperationResult.PartialSuccess -> {
                    val message = context.getString(R.string.deleted_n_of_m_files, result.processedCount, filesToDelete.size) + ". Errors: ${result.errors.take(3).joinToString(", ")}"
                    Timber.w(message)
                    
                    // PartialSuccess now provides deletedPaths - remove only actually deleted files from list
                    clearSelection()
                    removeFiles(result.deletedPaths)
                    
                    // Re-enable FileObserver after operation complete
                    viewModelScope.launch {
                        delay(200)
                        setIgnoringFileChanges(false)
                    }
                    
                    sendEvent(BrowseEvent.ShowMessage(message))
                }
                is com.sza.fastmediasorter.domain.usecase.FileOperationResult.Failure -> {
                    Timber.e("Failed to delete files: ${result.error}")
                    
                    // Re-enable FileObserver even on failure
                    setIgnoringFileChanges(false)
                    
                    sendEvent(BrowseEvent.ShowError(
                        message = "Failed to delete files",
                        details = result.error
                    ))
                }
                is com.sza.fastmediasorter.domain.usecase.FileOperationResult.AuthenticationRequired -> {
                    Timber.w("Cloud authentication required: ${result.provider}")
                    
                    // Re-enable FileObserver (operation not executed)
                    setIgnoringFileChanges(false)
                    
                    sendEvent(BrowseEvent.CloudAuthRequired(result.provider, result.message))
                }
                is com.sza.fastmediasorter.domain.usecase.FileOperationResult.PermissionRequired -> {
                    Timber.i("deleteSelectedFiles: ========================================")
                    Timber.i("deleteSelectedFiles: PERMISSION REQUIRED DETECTED")
                    Timber.i("deleteSelectedFiles: PendingIntent: ${result.pendingIntent}")
                    Timber.i("deleteSelectedFiles: URIs count: ${result.fileUris.size}")
                    result.fileUris.forEachIndexed { idx, uri ->
                        Timber.i("deleteSelectedFiles:   [$idx] $uri")
                    }
                    Timber.i("deleteSelectedFiles: Sending BrowseEvent.PermissionRequired...")
                    sendEvent(BrowseEvent.PermissionRequired(result.pendingIntent))
                    Timber.i("deleteSelectedFiles: Event sent successfully")
                    Timber.i("deleteSelectedFiles: ========================================")
                }
            }
            
            setLoading(false)
        }
    }

    fun onDeletePermissionGranted() {
        Timber.i("onDeletePermissionGranted: ========================================")
        Timber.i("onDeletePermissionGranted: CALLED - User granted batch delete permission")
        // Android 11+ Scoped Storage: User granted permission via system dialog,
        // so the system has already deleted the files.
        // However, some files might have been already deleted before permission dialog appeared
        // (e.g., via FileObserver), so we need to check which files still exist in the current state.
        val selectedPaths = state.value.selectedFiles.toList()
        Timber.i("onDeletePermissionGranted: selectedPaths.size = ${selectedPaths.size}")
        val currentFiles = state.value.mediaFiles.map { it.path }.toSet()
        val filesToRemove = selectedPaths.filter { it in currentFiles }
        val alreadyRemoved = selectedPaths.size - filesToRemove.size
        
        Timber.i("Permission granted for delete - ${selectedPaths.size} were selected, $alreadyRemoved already removed, removing ${filesToRemove.size} from UI")
        
        // Warn if state inconsistency detected (possible partial operation)
        if (alreadyRemoved > 0) {
            Timber.w("State inconsistency detected: $alreadyRemoved files were removed from UI but may still exist physically")
            sendEvent(BrowseEvent.ShowMessage(
                "Warning: Some files may have been uploaded but not deleted from source. " +
                "Please check source and destination folders. Affected: $alreadyRemoved file(s)"
            ))
        }
        
        // Remove files that are still in the UI state (system deleted them after permission granted)
        if (filesToRemove.isNotEmpty()) {
            removeFiles(filesToRemove)
        }
        
        clearSelection()
        reloadFiles()
        
        // Only show success message if no inconsistency
        if (alreadyRemoved == 0) {
            sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.deleted_n_files, selectedPaths.size)))
        }
    }
    
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
    
    fun setFilter(filter: FileFilter?) {
        Timber.d("BrowseViewModel.setFilter: Applying session-only filter (NOT saving to resource)")
        Timber.d("BrowseViewModel.setFilter: filter=$filter")
        
        // IMPORTANT: Filters are session-only and do NOT modify the resource permanently
        // Resource properties (supportedMediaTypes, etc.) can only be changed in ResourceEditActivity
        
        // Update state with filter (resource stays unchanged)
        updateState { 
            it.copy(filter = filter)
        }
        
        // Apply the filter to the current file list
        applyFilter()
    }
    
    private fun applyFilter() {
        val resource = state.value.resource ?: return
        val filter = state.value.filter
        
        Timber.d("applyFilter: filter=$filter")
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Get current settings for size filters (use cache)
            val settings = getSettings()
            val sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin,
                imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin,
                videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin,
                audioSizeMax = settings.audioSizeMax
            )
            
            getMediaFilesUseCase(resource, state.value.sortMode, sizeFilter)
                .catch { e ->
                    Timber.e(e, "Error loading media files")
                    sendEvent(BrowseEvent.ShowError(
                        message = "Failed to load media files: ${e.message ?: "Unknown error"}",
                        details = e.stackTraceToString(),
                        exception = e
                    ))
                }
                .collect { files ->
                    Timber.d("applyFilter: loaded ${files.size} files")
                    
                    // Apply filter if exists
                    val filteredFiles = if (filter != null) {
                        Timber.d("applyFilter: applying filter to ${files.size} files")
                        applyFilterToList(files, filter).also {
                            Timber.d("applyFilter: filtered to ${it.size} files")
                        }
                    } else {
                        files
                    }
                    
                    updateState { 
                        it.copy(mediaFiles = filteredFiles) 
                    }
                    setLoading(false)
                }
        }
    }
    
    fun toggleFavorite(file: MediaFile) {
        val resource = state.value.resource ?: return
        
        Timber.d("BrowseViewModel.toggleFavorite: START - file='${file.name}', path='${file.path}', currentIsFavorite=${file.isFavorite}, resourceId=${resource.id}")
        
        // Optimistic UI update
        val newFavoriteState = !file.isFavorite
        updateState { state ->
            val updatedFiles = state.mediaFiles.map { 
                if (it.path == file.path) it.copy(isFavorite = newFavoriteState) else it 
            }
            state.copy(mediaFiles = updatedFiles)
        }
        Timber.d("BrowseViewModel.toggleFavorite: Optimistic UI update complete - newIsFavorite=$newFavoriteState")
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            try {
                Timber.d("BrowseViewModel.toggleFavorite: Calling favoritesUseCase.toggleFavorite")
                favoritesUseCase.toggleFavorite(file, resource.id)
                Timber.d("BrowseViewModel.toggleFavorite: favoritesUseCase.toggleFavorite completed successfully")
                // If successful, UI is already correct.
            } catch (e: Exception) {
                Timber.e(e, "BrowseViewModel.toggleFavorite: Error toggling favorite")
                // Revert UI on error
                updateState { state ->
                    val updatedFiles = state.mediaFiles.map { 
                        if (it.path == file.path) it.copy(isFavorite = !it.isFavorite) else it 
                    }
                    state.copy(mediaFiles = updatedFiles)
                }
                sendEvent(BrowseEvent.ShowError("Failed to update favorite status"))
            }
        }
    }

    private fun applyFilterToList(files: List<MediaFile>, filter: FileFilter): List<MediaFile> {
        return files.filter { file ->
            val matchesName = filter.nameContains == null || 
                file.name.contains(filter.nameContains, ignoreCase = true)
            
            val matchesMinDate = filter.minDate == null || 
                file.createdDate >= filter.minDate
            
            val matchesMaxDate = filter.maxDate == null || 
                file.createdDate <= filter.maxDate
            
            val fileSizeMb = file.size / (1024f * 1024f)
            val matchesMinSize = filter.minSizeMb == null || 
                fileSizeMb >= filter.minSizeMb
            
            val matchesMaxSize = filter.maxSizeMb == null || 
                fileSizeMb <= filter.maxSizeMb
            
            val matchesType = filter.mediaTypes == null || 
                filter.mediaTypes.contains(file.type)
            
            matchesName && matchesMinDate && matchesMaxDate && 
                matchesMinSize && matchesMaxSize && matchesType
        }
    }
    
    /**
     * Start FileObserver for local resources to detect external file changes
     */
    private fun startFileObserver() {
        val resource = state.value.resource ?: return
        
        // Only observe local folders
        if (resource.type != com.sza.fastmediasorter.domain.model.ResourceType.LOCAL) {
            return
        }
        
        // Stop previous observer if exists
        stopFileObserver()
        
        try {
            fileObserver = MediaFileObserver(
                path = resource.path,
                listener = object : MediaFileObserver.FileChangeListener {
                    override fun onFileDeleted(fileName: String) {
                        // Skip FileObserver events during programmatic operations
                        if (ignoringFileChanges) {
                            Timber.d("Ignoring file deleted event during programmatic operation: $fileName")
                            return
                        }
                        
                        // Ignore trash folders (deleted during cleanup)
                        if (fileName.startsWith(".trash_")) {
                            Timber.d("Ignoring trash folder deletion: $fileName")
                            return
                        }
                        
                        Timber.i("External file deleted: $fileName")
                        // Use targeted removal instead of full reload
                        val currentResource = state.value.resource ?: return
                        val fullPath = java.io.File(currentResource.path, fileName).absolutePath
                        removeFiles(listOf(fullPath))
                    }

                    override fun onFileCreated(fileName: String) {
                        // Skip FileObserver events during programmatic operations
                        if (ignoringFileChanges) {
                            Timber.d("Ignoring file created event during programmatic operation: $fileName")
                            return
                        }
                        
                        // Ignore trash folders (created during delete operation)
                        if (fileName.startsWith(".trash_")) {
                            Timber.d("Ignoring trash folder creation: $fileName")
                            return
                        }
                        
                        Timber.i("External file created: $fileName")
                        // Reload files to reflect new file (debounced)
                        scheduleReload()
                    }

                    override fun onFileMoved(fromName: String?, toName: String?) {
                        // Skip FileObserver events during programmatic operations
                        if (ignoringFileChanges) {
                            Timber.d("Ignoring file moved event during programmatic operation: from=$fromName, to=$toName")
                            return
                        }
                        
                        Timber.i("External file moved: from=$fromName, to=$toName")
                        
                        // Rename generates 2 events: (from=X, to=null) and (from=null, to=Y)
                        // Collect both parts before processing
                        pendingRenameJob?.cancel()
                        
                        if (fromName != null) {
                            pendingRenameFrom = fromName
                        }
                        if (toName != null) {
                            pendingRenameTo = toName
                        }
                        
                        // Wait for both events, then process
                        pendingRenameJob = viewModelScope.launch {
                            delay(50) // Wait for second event
                            
                            val from = pendingRenameFrom
                            val to = pendingRenameTo
                            
                            // Reset state
                            pendingRenameFrom = null
                            pendingRenameTo = null
                            
                            if (from != null && to != null) {
                                // Complete rename event - update in-place
                                Timber.i("Complete rename detected: '$from' -> '$to'")
                                handleFileRename(from, to)
                            } else if (from != null && to == null) {
                                // File moved out or deleted
                                // Check if file still exists in original location
                                val currentResource = state.value.resource ?: return@launch
                                val filePath = java.io.File(currentResource.path, from)
                                if (!filePath.exists()) {
                                    // File was deleted (soft-delete moved to .trash_ or external delete)
                                    Timber.i("File deleted/moved to trash: $from - removing from list")
                                    removeFiles(listOf(filePath.absolutePath))
                                } else {
                                    // File still exists but moved - external operation
                                    Timber.w("File moved externally: $from - doing full reload")
                                    scheduleReload()
                                }
                            } else if (from == null && to != null) {
                                // File moved into folder from outside - full reload needed
                                Timber.i("File moved into folder: $to - doing full reload")
                                scheduleReload()
                            } else {
                                // Both null - shouldn't happen
                                Timber.w("Incomplete move event (from=$from, to=$to), doing full reload")
                                scheduleReload()
                            }
                        }
                    }

                    override fun onFileModified(fileName: String) {
                        Timber.d("External file modified: $fileName")
                        // Optionally reload to update file metadata
                        // For now, skip reload for modifications to avoid too many refreshes
                    }
                }
            )
            fileObserver?.startWatching()
            Timber.d("Started FileObserver for: ${resource.path}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start FileObserver for: ${resource.path}")
        }
    }
    
    /**
     * Stop FileObserver
     */
    private fun stopFileObserver() {
        fileObserver?.stopWatching()
        fileObserver = null
        reloadDebounceJob?.cancel()
        reloadDebounceJob = null
        pendingRenameJob?.cancel()
        pendingRenameJob = null
        pendingRenameFrom = null
        pendingRenameTo = null
        Timber.d("Stopped FileObserver")
    }
    
    /**
     * Schedule a debounced reloadFiles() call to avoid multiple rapid reloads
     * from FileObserver events (e.g., rename triggers both "moved from" and "moved to")
     */
    private fun scheduleReload() {
        reloadDebounceJob?.cancel()
        reloadDebounceJob = viewModelScope.launch {
            delay(300) // Wait 300ms for additional events
            Timber.d("Debounced reload triggered after file system changes")
            reloadFiles()
        }
    }
    
    /**
     * Handle file rename without full rescan - just update the file in current list
     */
    private fun handleFileRename(oldName: String, newName: String) {
        viewModelScope.launch {
            // Get current files from state
            val currentState = state.value
            val currentList = currentState.mediaFiles
            
            // Find file with old name
            val fileIndex = currentList.indexOfFirst { it.name == oldName }
            if (fileIndex == -1) {
                Timber.w("handleFileRename: File not found in list: $oldName")
                // Fall back to full reload
                reloadFiles()
                return@launch
            }
            
            val oldFile = currentList[fileIndex]
            val resource = currentState.resource ?: return@launch
            
            // Create updated file with new name and path
            val newPath = java.io.File(resource.path, newName).absolutePath
            val updatedFile = oldFile.copy(
                name = newName,
                path = newPath
            )
            
            Timber.i("handleFileRename: Updating file '$oldName' -> '$newName' in list (index=$fileIndex)")
            
            // Update list
            val updatedList = currentList.toMutableList().apply {
                set(fileIndex, updatedFile)
            }
            
            // Re-sort if needed
            val sortedList = if (currentState.sortMode != SortMode.NAME_ASC) {
                sortFiles(updatedList, currentState.sortMode, forceSort = true)
            } else {
                updatedList
            }
            
            // Update state with new list
            updateState { 
                it.copy(mediaFiles = sortedList)
            }
            
            Timber.i("handleFileRename: List updated, total=${sortedList.size} files")
        }
    }
    
    /**
     * Sorts files according to sort mode. Used for in-memory sorting without rescanning.
     */
    private fun sortFiles(files: List<MediaFile>, mode: SortMode, forceSort: Boolean = false): List<MediaFile> {
        // Skip sorting for large folders (> 500 files) ONLY during initial load
        // User-requested sorting (forceSort=true) always executes
        if (!forceSort && files.size > PAGINATION_THRESHOLD) {
            Timber.d("sortFiles: Large folder (${files.size} files), skipping auto-sort for performance")
            return files
        }
        
        return fileListManager.sortFiles(files, mode, forceSort)
    }
    
    /**
     * Save last viewed file path to resource for position restoration
     */
    fun saveLastViewedFile(filePath: String) {
        val resource = state.value.resource ?: return
        
        // Update state immediately for instant UI response
        val updatedResource = resource.copy(lastViewedFile = filePath)
        updateState { it.copy(resource = updatedResource) }
        
        // Save to database asynchronously
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateResourceUseCase(updatedResource)
            Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
        }
    }
    
    /**
     * Save scroll position (first visible item) when leaving Browse screen.
     * Position is restored on next resource open.
     */
    fun saveScrollPosition(position: Int) {
        val resource = state.value.resource ?: return
        
        // Update state immediately
        val updatedResource = resource.copy(lastScrollPosition = position)
        updateState { it.copy(resource = updatedResource) }
        
        // Save to database asynchronously
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            // Use NonCancellable to ensure save completes even if user navigates away
            withContext(NonCancellable) {
                updateResourceUseCase(updatedResource)
                Timber.d("Saved lastScrollPosition=$position for resource: ${resource.name}")
            }
        }
    }
    
    /**
     * Create MediaFile object from java.io.File for undo operations
     */
    /**
     * Create MediaFile from java.io.File for instant list updates (e.g., after rename).
     * Used to avoid full resource reload when only single file metadata changes.
     */
    fun createMediaFileFromFile(file: java.io.File): MediaFile {
        val extension = file.extension.lowercase()
        val mediaType = MediaExtensions.getMediaType(extension)
        
        return MediaFile(
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            createdDate = file.lastModified(),
            type = mediaType,
            duration = null,
            width = null,
            height = null,
            exifOrientation = null,
            exifDateTime = null,
            exifLatitude = null,
            exifLongitude = null,
            videoCodec = null,
            videoBitrate = null,
            videoFrameRate = null,
            videoRotation = null
        )
    }
    
    /**
     * Cleanup trash folders in resource directory on background.
     * For LOCAL resources, cleans up .trash_* folders in resource path.
     * For network resources (SMB/FTP/SFTP), skips cleanup (not supported yet).
     * 
     * @param resource Resource to cleanup
     * @param maxAgeMs Maximum age of trash folders to keep (0 = delete all)
     */
    private fun cleanupTrashOnBackground(resource: MediaResource, maxAgeMs: Long) {
        viewModelScope.launch(ioDispatcher) {
            try {
                when (resource.type) {
                    com.sza.fastmediasorter.domain.model.ResourceType.LOCAL -> {
                        val rootDir = java.io.File(resource.path)
                        if (!rootDir.exists() || !rootDir.isDirectory) {
                            return@launch
                        }
                        
                        val deletedCount = cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs)
                        if (deletedCount > 0) {
                            Timber.i("Cleaned up $deletedCount trash folders in ${resource.name}")
                        }
                    }
                    else -> {
                        // Network resource cleanup: SMB/SFTP/FTP/Cloud
                        // TODO: Implement network trash cleanup when needed
                        // Requires:
                        // 1. Scan for .trash folders via network clients
                        // 2. Delete each .trash folder recursively
                        // 3. Handle connection credentials properly
                        Timber.d("Network cleanup not yet implemented for resource type: ${resource.type}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to cleanup trash folders in ${resource.name}")
            }
        }
    }

    /**
     * Syncs current state with cache.
     * Called when returning from PlayerActivity to reflect changes (move/delete)
     * without full network reload.
     */
    fun syncWithCache() {
        val cachedFiles = MediaFilesCacheManager.getCachedList(resourceId)
        val resource = state.value.resource
        if (cachedFiles != null) {
            val currentFiles = state.value.mediaFiles
            
            // Always update state from cache to reflect deletions, moves, and renames
            // The size check was insufficient because renames don't change size, 
            // and sometimes cache updates might not be reflected if size happens to match
            Timber.d("syncWithCache: Syncing state with cache (${cachedFiles.size} files)")
            
            // CRITICAL: Filter by resource.supportedMediaTypes first
            // Cache may contain files from previous "allFiles" mode session
            var filteredFiles = if (resource != null && !resource.allFiles && resource.supportedMediaTypes.isNotEmpty()) {
                cachedFiles.filter { file ->
                    file.isDirectory || resource.supportedMediaTypes.contains(file.type)
                }
            } else {
                cachedFiles
            }
             
            // Apply current filter to cached files before updating state
            val filter = state.value.filter
            filteredFiles = if (filter != null) {
                applyFilterToList(filteredFiles, filter)
            } else {
                filteredFiles
            }
             
            Timber.d("syncWithCache: After filter applied - ${filteredFiles.size} files (was ${cachedFiles.size})")
            
            // Only update if list content actually changed to avoid unnecessary recompositions
            if (filteredFiles != currentFiles) {
                updateState { it.copy(mediaFiles = filteredFiles, totalFileCount = filteredFiles.size) }
                Timber.d("syncWithCache: State updated")
            } else {
                Timber.d("syncWithCache: State already up to date")
            }
        }
    }
    
    /**
     * Check if resource settings (supportedMediaTypes, scanSubdirectories) changed in database.
     * If changed, reload files to reflect new filter.
     * If not changed, sync with PlayerActivity cache for deleted/moved/renamed files.
     * Called from BrowseActivity.onResume() when returning from PlayerActivity or EditResourceActivity.
     */
    fun checkAndReloadIfResourceChanged() {
        Timber.d("BrowseViewModel.checkAndReloadIfResourceChanged: START - resourceId=$resourceId")
        val currentResource = state.value.resource ?: return
        
        // Skip for Favorites (virtual resource not in database)
        if (currentResource.id == -100L) {
            syncWithCache()
            return
        }
        
        viewModelScope.launch(ioDispatcher) {
            val updatedResource = getResourcesUseCase.getById(resourceId)
            if (updatedResource == null) {
                Timber.w("checkAndReloadIfResourceChanged: Resource not found in database")
                return@launch
            }
            
            // Compare supportedMediaTypes and scanSubdirectories
            val typesChanged = currentResource.supportedMediaTypes != updatedResource.supportedMediaTypes
            val subfoldersChanged = currentResource.scanSubdirectories != updatedResource.scanSubdirectories
            
            if (typesChanged || subfoldersChanged) {
                Timber.d("checkAndReloadIfResourceChanged: Resource settings changed, reloading files")
                Timber.d("  supportedMediaTypes: ${currentResource.supportedMediaTypes} -> ${updatedResource.supportedMediaTypes}")
                Timber.d("  scanSubdirectories: ${currentResource.scanSubdirectories} -> ${updatedResource.scanSubdirectories}")
                
                // Clear cache when scanSubdirectories or mediaTypes changed - forces fresh scan
                if (subfoldersChanged || typesChanged) {
                    Timber.d("checkAndReloadIfResourceChanged: Clearing cache due to settings change")
                    MediaFilesCacheManager.clearCache(resourceId)
                }
                
                // Update resource in state before reloading
                updateState { it.copy(resource = updatedResource) }
                
                // Reload files with new filter
                reloadFiles()
            } else {
                Timber.d("checkAndReloadIfResourceChanged: No changes detected, syncing with cache")
                // No settings changed - just sync with PlayerActivity cache (deletions/moves/renames)
                syncWithCache()
            }
        }
    }
    
    /**
     * Remove deleted/moved files from the current list.
     * Called when returning from PlayerActivity with modified files list.
     * Much faster than reloading all files from network.
     */
    fun removeFilesFromList(paths: List<String>) {
        if (paths.isEmpty()) return
        
        val pathsSet = paths.toSet()
        val currentFiles = state.value.mediaFiles
        val updatedFiles = currentFiles.filterNot { it.path in pathsSet }
        
        if (updatedFiles.size != currentFiles.size) {
            Timber.d("removeFilesFromList: Removed ${currentFiles.size - updatedFiles.size} files from list")
            updateState { it.copy(mediaFiles = updatedFiles, totalFileCount = updatedFiles.size) }
            
            // Also update cache
            MediaFilesCacheManager.setCachedList(resourceId, updatedFiles)
        }
    }
    
    /**
     * Set flag to ignore FileObserver events during programmatic operations
     */
    fun setIgnoringFileChanges(ignoring: Boolean) {
        ignoringFileChanges = ignoring
        Timber.d("FileObserver events ${if (ignoring) "BLOCKED" else "ENABLED"}")
    }
    
    /**
     * Check if file changes are currently being ignored (for MediaStoreObserver)
     */
    fun isIgnoringFileChanges(): Boolean = ignoringFileChanges
    
    // ===== SUBFOLDER NAVIGATION METHODS =====
    
    /**
     * Check if subfolder navigation is enabled for current resource.
     * Subfolder mode is enabled when:
     * 1. scanSubdirectories is ON for resource
     * 2. showSubfoldersAsItems is true for resource
     */
    suspend fun isSubfolderModeEnabled(): Boolean {
        val resource = state.value.resource ?: return false
        return resource.scanSubdirectories && resource.showSubfoldersAsItems
    }
    
    /**
     * Navigate into a subfolder. 
     * Adds current path to stack and loads contents of the new folder.
     */
    fun navigateToFolder(folder: MediaFile) {
        if (!folder.isDirectory) {
            Timber.w("navigateToFolder: Attempted to navigate to non-directory: ${folder.path}")
            return
        }
        
        val currentPath = state.value.currentPath
        val newStack = if (currentPath != null) {
            state.value.pathStack + currentPath
        } else {
            emptyList()
        }
        
        Timber.d("navigateToFolder: Navigating to ${folder.path}, stack depth: ${newStack.size}")
        
        updateState {
            it.copy(
                currentPath = folder.path,
                pathStack = newStack,
                isSubfolderMode = true
            )
        }
        
        // Load directory contents using cache
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            loadDirectoryContents(folder.path)
        }
    }
    
    /**
     * Check if we can navigate up (back) from current subfolder
     */
    fun canNavigateUp(): Boolean {
        return state.value.isSubfolderMode && 
               (state.value.pathStack.isNotEmpty() || (state.value.currentPath != null && state.value.currentPath != state.value.resource?.path))
    }
    
    /**
     * Navigate up to parent folder.
     * Returns true if navigation happened, false if already at root.
     */
    fun navigateUp(): Boolean {
        if (!canNavigateUp()) return false
        
        val stack = state.value.pathStack
        val newPath = stack.lastOrNull()
        val newStack = if (stack.isNotEmpty()) stack.dropLast(1) else emptyList()
        
        Timber.d("navigateUp: Going back to ${newPath ?: "root"}, stack depth: ${newStack.size}")
        
        // CRITICAL FIX: When returning to root with subfolder mode enabled,
        // we need to keep isSubfolderMode=true and set currentPath to resource.path
        // Otherwise loadMediaFiles() will do recursive scan instead of listDirectoryContents()
        val resource = state.value.resource
        val finalPath = newPath ?: resource?.path
        val isStillInSubfolderMode = state.value.isSubfolderMode // Keep subfolder mode active
        
        updateState {
            it.copy(
                currentPath = finalPath,
                pathStack = newStack,
                isSubfolderMode = isStillInSubfolderMode
            )
        }
        
        // Load directory contents using cache
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            if (finalPath != null) {
                loadDirectoryContents(finalPath)
            }
        }
        return true
    }
    
    /**
     * Reset subfolder navigation to root of resource.
     */
    fun resetToRoot() {
        if (!state.value.isSubfolderMode) return
        
        Timber.d("resetToRoot: Resetting to resource root")
        updateState {
            it.copy(
                currentPath = null,
                pathStack = emptyList(),
                isSubfolderMode = false
            )
        }
        
        loadMediaFiles()
    }
    
    /**
     * Get current folder name for breadcrumb display.
     * Returns folder name or null if at root.
     */
    fun getCurrentFolderName(): String? {
        val currentPath = state.value.currentPath ?: return null
        return File(currentPath).name
    }
    
    /**
     * Get breadcrumb path for display.
     * Format: "Resource / Folder1 / Folder2"
     */
    fun getBreadcrumbPath(): String {
        val resource = state.value.resource ?: return ""
        val currentPath = state.value.currentPath
        
        if (currentPath == null) {
            return resource.name
        }
        
        // Build path from root to current folder
        val resourcePath = resource.path
        val relativePath = if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            File(currentPath).name
        }
        
        if (relativePath.isEmpty()) {
            return resource.name
        }
        
        val pathParts = relativePath.split("/", "\\").filter { it.isNotEmpty() }
        return (listOf(resource.name) + pathParts).joinToString(" / ")
    }
    
    /**
     * Get breadcrumb parts as separate list for interactive breadcrumb.
     * @return Pair of resource name and list of folder names
     */
    fun getBreadcrumbParts(): Pair<String, List<String>> {
        val resource = state.value.resource ?: return Pair("", emptyList())
        val currentPath = state.value.currentPath
        
        if (currentPath == null || currentPath == resource.path) {
            return Pair(resource.name, emptyList())
        }
        
        // Build path from root to current folder
        val resourcePath = resource.path
        val relativePath = if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            return Pair(resource.name, listOf(File(currentPath).name))
        }
        
        if (relativePath.isEmpty()) {
            return Pair(resource.name, emptyList())
        }
        
        val pathParts = relativePath.split("/", "\\").filter { it.isNotEmpty() }
        return Pair(resource.name, pathParts)
    }
    
    /**
     * Navigate to specific depth in breadcrumb.
     * @param depth 0 = root, 1 = first folder, 2 = second folder, etc.
     */
    fun navigateToDepth(depth: Int) {
        if (!state.value.isSubfolderMode) return
        
        val resource = state.value.resource ?: return
        val currentPath = state.value.currentPath ?: return
        
        // Get path parts
        val resourcePath = resource.path
        val relativePath = if (currentPath.startsWith(resourcePath)) {
            currentPath.removePrefix(resourcePath).trimStart('/', '\\')
        } else {
            Timber.w("navigateToDepth: Current path doesn't start with resource path")
            return
        }
        
        if (relativePath.isEmpty()) {
            // Already at root
            return
        }
        
        val pathParts = relativePath.split("/", "\\").filter { it.isNotEmpty() }
        
        // Calculate target path
        val targetPath = if (depth == 0) {
            // Navigate to root
            resource.path
        } else if (depth > 0 && depth <= pathParts.size) {
            // Navigate to specific folder
            val targetParts = pathParts.take(depth)
            resourcePath.trimEnd('/', '\\') + "/" + targetParts.joinToString("/")
        } else {
            // Invalid depth
            Timber.w("navigateToDepth: Invalid depth $depth (max ${pathParts.size})")
            return
        }
        
        // Build new path stack (only keep paths up to target depth)
        val newStack = if (depth == 0) {
            emptyList()
        } else {
            val stackPaths = mutableListOf<String>()
            var buildPath = resourcePath.trimEnd('/', '\\')
            for (i in 0 until depth - 1) {
                buildPath += "/" + pathParts[i]
                stackPaths.add(buildPath)
            }
            stackPaths
        }
        
        Timber.d("navigateToDepth: depth=$depth, targetPath=$targetPath, stackSize=${newStack.size}")
        
        updateState {
            it.copy(
                currentPath = targetPath,
                pathStack = newStack,
                isSubfolderMode = true
            )
        }
        
        // Load directory contents
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            loadDirectoryContents(targetPath)
        }
    }
}
