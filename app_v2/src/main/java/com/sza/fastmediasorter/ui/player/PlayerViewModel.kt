package com.sza.fastmediasorter.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScreenType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    val fileOperationUseCase: FileOperationUseCase,
    val getDestinationsUseCase: GetDestinationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository,
    private val googleDriveClient: com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository,
    private val favoritesUseCase: com.sza.fastmediasorter.domain.usecase.FavoritesUseCase,
    private val smbClient: com.sza.fastmediasorter.data.network.SmbClient,
    private val cachedFileListRepository: CachedFileListRepository,
    private val clearResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase,
    private val saveResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.SaveResumeStateUseCase
) : BaseViewModel<PlayerViewModel.PlayerState, PlayerViewModel.PlayerEvent>() {

    data class PlayerState(
        val files: List<MediaFile> = emptyList(),
        val currentIndex: Int = 0,
        val isSlideShowActive: Boolean = false,
        val slideShowInterval: Long = 3000,
        val slideshowMusicUri: String? = null,
        val slideshowMusicResourceId: Long? = null,
        val enableSlideshowBackgroundMusic: Boolean = false,
        val playToEndInSlideshow: Boolean = false,
        val enablePhotosDuringAudio: Boolean = false,
        val audioBackgroundPhotosResourceId: String? = null,
        val enablePersistentAudioPlayback: Boolean = false,
        val showControls: Boolean = false,
        val isPaused: Boolean = false,
        val showCommandPanel: Boolean = false,
        val showSmallControls: Boolean = false,
        val allowRename: Boolean = true,
        val allowDelete: Boolean = true,
        val enableCopying: Boolean = true,
        val enableMoving: Boolean = true,
        val enableTranslation: Boolean = false,
        val enableOcr: Boolean = false,
        val enableGoogleLens: Boolean = false,
        val resource: MediaResource? = null,
        val lastOperation: UndoOperation? = null,
        val undoOperationTimestamp: Long? = null
    ) {
        val currentFile: MediaFile? get() = files.getOrNull(currentIndex)
        // Circular navigation: always allow prev/next if files.size > 1
        val hasPrevious: Boolean get() = files.size > 1
        val hasNext: Boolean get() = files.size > 1
        // True only for IMAGE/GIF slideshows — drives UI hiding, 9-zone gestures, system bars.
        // VIDEO/AUDIO use isSlideShowActive for auto-advance only, not visual slideshow mode.
        val isPhotoSlideshowActive: Boolean get() =
            isSlideShowActive && (currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF)
    }

    sealed class PlayerEvent {
        data class ShowError(val message: String) : PlayerEvent()
        data class ShowMessage(val message: String) : PlayerEvent()
        data class FileModified(val filePath: String) : PlayerEvent()
        data class ShowUndoSnackbar(val operation: UndoOperation) : PlayerEvent()
        data class CloudAuthRequired(val provider: String, val message: String) : PlayerEvent()
        data class ShowMissingFileDialog(val fileName: String, val filePath: String) : PlayerEvent()
        // Removed: LoadingProgress event (dialog not needed for single file loads)
        object FinishActivity : PlayerEvent()
    }

    override fun getInitialState(): PlayerState {
        return PlayerState(currentIndex = initialIndex)
    }

    private val resourceId = savedStateHandle.get<Long>("resourceId")
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() ?: 0L
    private val initialIndex = savedStateHandle.get<Int>("initialIndex")
        ?: savedStateHandle.get<String>("initialIndex")?.toIntOrNull() ?: 0
    private val skipAvailabilityCheck: Boolean = savedStateHandle.get<Boolean>("skipAvailabilityCheck") ?: false
    private val initialFilePath: String? = savedStateHandle.get<String>("initialFilePath")
    val resumeIsPlaying: Boolean? = savedStateHandle.get<Boolean>("resumeIsPlaying")
    val resumeSlideshowEnabled: Boolean = savedStateHandle.get<Boolean>("resumeSlideshowEnabled") ?: false
    private val shuffleOnStart: Boolean = savedStateHandle.get<Boolean>("shuffleOnStart") ?: false
    
    private var loadingJob: Job? = null
    private var saveLastViewedFileJob: Job? = null // Debounce job for database updates

    init {
        loadSettings()
        loadMediaFiles()
    }
    
    /**
     * Reload media files list.
     * Call when returning from background to reflect external changes.
     */
    fun reloadFiles() {
        loadMediaFiles()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                settingsRepository.getSettings().collect { settings ->
                    val resource = state.value.resource
                    
                    // Determine showCommandPanel:
                    // Priority: runtime session value → initial load via same logic as loadMediaFiles()
                    // IMPORTANT: Runtime value (files.isNotEmpty) is checked FIRST so that
                    // toggleCommandPanel() changes are never overridden by subsequent settings emissions.
                    // On initial load (files empty) we apply the same special logic as loadMediaFiles():
                    // resource explicit false is NOT honoured when global default is true.
                    val showCommandPanel = when {
                        state.value.files.isNotEmpty() -> state.value.showCommandPanel // keep runtime value (highest priority)
                        resource?.showCommandPanel == null -> settings.defaultShowCommandPanel // no resource override
                        resource.showCommandPanel == false && settings.defaultShowCommandPanel -> settings.defaultShowCommandPanel // global true wins
                        else -> resource.showCommandPanel
                    }

                    Timber.d("TOUCH_ZONE_DEBUG: loadSettings - resource.showCommandPanel=${resource?.showCommandPanel}, settings.defaultShowCommandPanel=${settings.defaultShowCommandPanel}, filesLoaded=${state.value.files.isNotEmpty()}, RESULT showCommandPanel=$showCommandPanel")
                    
                    Timber.d("PlayerViewModel.loadSettings: enableTranslation=${settings.enableTranslation}, enableOcr=${settings.enableOcr}, enableGoogleLens=${settings.enableGoogleLens}")
                    
                    updateState { 
                        it.copy(
                            showCommandPanel = showCommandPanel,
                            showSmallControls = settings.showSmallControls,
                            allowRename = settings.allowRename,
                            allowDelete = settings.allowDelete,
                            enableCopying = settings.enableCopying,
                            enableMoving = settings.enableMoving,
                            enableTranslation = settings.enableTranslation,
                            enableOcr = settings.enableOcr,
                            enableGoogleLens = settings.enableGoogleLens,
                            // Set slideshow interval from global settings (will be overridden by resource-specific if available)
                            slideShowInterval = settings.slideshowInterval * 1000L,
                            slideshowMusicUri = settings.slideshowMusicUri,
                            slideshowMusicResourceId = settings.slideshowMusicResourceId,
                            enableSlideshowBackgroundMusic = settings.enableSlideshowBackgroundMusic,
                            playToEndInSlideshow = settings.playToEndInSlideshow,
                            enablePhotosDuringAudio = settings.enablePhotosDuringAudio,
                            audioBackgroundPhotosResourceId = settings.audioBackgroundPhotosResourceId,
                            enablePersistentAudioPlayback = settings.enablePersistentAudioPlayback
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Timber.d("PlayerViewModel.loadSettings: Cancelled (normal during destroy)")
                    throw e
                }
                // Use default value (fullscreen mode)
                Timber.e(e, "Error loading settings")
            }
        }
    }

    private fun loadMediaFiles() {
        loadingJob = viewModelScope.launch {
            setLoading(true)
            try {
                // Save current file path to restore position after reload
                val currentFilePath = state.value.currentFile?.path
                
                val resource = if (resourceId == -100L) {
                    MediaResource(
                        id = -100L,
                        name = "Favorites",
                        path = "Favorites",
                        type = ResourceType.LOCAL,
                        isAvailable = true,
                        fileCount = 0,
                        isWritable = false,
                        supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF)
                    )
                } else {
                    getResourcesUseCase.getById(resourceId)
                }

                if (resource == null) {
                    sendEvent(PlayerEvent.ShowError("Resource not found"))
                    sendEvent(PlayerEvent.FinishActivity)
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
                    Timber.d("PlayerViewModel: Configured ConnectionThrottleManager for $resourceKey with ${resource.recommendedThreads} threads")
                }

                // Ensure Google Drive client is initialized with credentials if available
                // Determine actual resource type from current file path
                val actualResourceType = state.value.currentFile?.path?.let { path ->
                    when {
                        path.startsWith("cloud://") -> ResourceType.CLOUD
                        path.startsWith("smb://") -> ResourceType.SMB
                        path.startsWith("sftp://") -> ResourceType.SFTP
                        path.startsWith("ftp://") -> ResourceType.FTP
                        else -> resource.type
                    }
                } ?: resource.type
                
                if (actualResourceType == ResourceType.CLOUD && resource.cloudProvider == com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE) {
                    if (!googleDriveClient.isAuthenticated()) {
                        Timber.d("PlayerViewModel: Google Drive client not authenticated, will authenticate on first API call")
                        // Authentication will be triggered automatically on first API call via googleDriveClient.authenticate()
                    }
                }

                // Note: Do NOT reset SMB client here! It kills active connections and causes
                // 2+ minute delays due to connection staleness checks. The connection pool
                // already handles timeouts and dead connections automatically.

                // Check if resource is available (skip if already validated)
                if (!skipAvailabilityCheck && resource.fileCount == 0 && !resource.isWritable) {
                    sendEvent(PlayerEvent.ShowError("Resource '${resource.name}' is unavailable. Check network connection or resource settings."))
                    sendEvent(PlayerEvent.FinishActivity)
                    setLoading(false)
                    return@launch
                }

                // Get current settings for size filters
                val settings = settingsRepository.getSettings().first()
                val sizeFilter = SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )

            // Load from cache (instant) - BrowseActivity already loaded and cached the list
            // Loading files from cache
            val cachedFiles = MediaFilesCacheManager.getCachedList(resource.id)
            
            // Check if cached files are all directories (indicates we're in a subfolder but cache has parent folder data)
            val cacheHasOnlyDirectories = cachedFiles != null && cachedFiles.isNotEmpty() && cachedFiles.all { it.isDirectory }
            
            val allFiles = if (cachedFiles != null && cachedFiles.isNotEmpty() && !cacheHasOnlyDirectories) {
                // Using cached list (valid file data)
                cachedFiles
            } else {
                // Fallback: cache miss OR cache has only directories - load via UseCase
                if (cacheHasOnlyDirectories) {
                    Timber.w("Cache contains only directories (${cachedFiles?.size} items), loading actual files from current path")
                } else {
                    Timber.w("Cache miss! Loading files via UseCase (slow path)")
                }
                
                // Extract current folder path from currentFile if available
                val currentFolderPath = state.value.currentFile?.path?.let { filePath ->
                    // Extract parent directory path
                    val lastSlash = filePath.lastIndexOf('/')
                    if (lastSlash > 0) {
                        filePath.substring(0, lastSlash)
                    } else {
                        null
                    }
                }
                
                Timber.d("PlayerViewModel: Loading files from currentFolderPath=$currentFolderPath")
                
                getMediaFilesUseCase(
                    resource = resource,
                    sizeFilter = sizeFilter,
                    useChunkedLoading = false,
                    maxFiles = Int.MAX_VALUE,
                    onProgress = null,
                    currentPath = currentFolderPath,
                    isSubfolderMode = currentFolderPath != null
                ).first()
            }
                
                Timber.d("PlayerViewModel: resource.supportedMediaTypes = ${resource.supportedMediaTypes}")
                Timber.d("PlayerViewModel: allFiles count = ${allFiles.size}")
                
                // Filter files by resource's supportedMediaTypes and ensure no directories are included
                val files = if (resource.supportedMediaTypes.size < 7) { // Not all types selected (IVAGTPE = 7 types)
                    val filtered = allFiles.filter { file ->
                        !file.isDirectory && resource.supportedMediaTypes.contains(file.type)
                    }
                    Timber.d("Filtered files by supportedMediaTypes ${resource.supportedMediaTypes}: ${allFiles.size} → ${filtered.size}")
                    filtered
                } else {
                    // Even if all types are supported, we must filter out directories
                    val filtered = allFiles.filter { !it.isDirectory }
                    Timber.d("All types supported (${resource.supportedMediaTypes.size}), filtered directories: ${allFiles.size} → ${filtered.size}")
                    filtered
                }

                val filesWithFavorites = try {
                    if (files.isEmpty()) {
                        files
                    } else {
                        val favoriteMap = favoritesUseCase.getFavoritesForPaths(files.map { it.path })
                        files.map { file ->
                            file.copy(isFavorite = favoriteMap[file.path] == true)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "PlayerViewModel.loadMediaFiles: Failed to reconcile favorites, using existing flags")
                    files
                }
                
                if (filesWithFavorites.isEmpty()) {
                    sendEvent(PlayerEvent.ShowError("No media files found"))
                    sendEvent(PlayerEvent.FinishActivity)
                } else {
                    // Priority order for determining index:
                    // 1. Current file path (if reloading during playback - preserve position)
                    // 2. Initial file path (if provided from BrowseActivity - pagination mode)
                    // 3. Initial index (default fallback)
                    val safeIndex = if (currentFilePath != null) {
                        val foundIndex = filesWithFavorites.indexOfFirst { it.path == currentFilePath }
                        if (foundIndex >= 0) {
                            Timber.d("Restored position to current file: $currentFilePath at index $foundIndex")
                            foundIndex
                        } else {
                            Timber.w("Current file not found: $currentFilePath, trying initialFilePath")
                            if (initialFilePath != null) {
                                val initialFoundIndex = filesWithFavorites.indexOfFirst { it.path == initialFilePath }
                                if (initialFoundIndex >= 0) initialFoundIndex else 0
                            } else {
                                initialIndex.coerceIn(0, filesWithFavorites.size - 1)
                            }
                        }
                    } else if (initialFilePath != null) {
                        val foundIndex = filesWithFavorites.indexOfFirst { it.path == initialFilePath }
                        if (foundIndex >= 0) {
                            // Found file by path at index
                            foundIndex
                        } else {
                            Timber.w("File not found by path: $initialFilePath, using index 0")
                            if (resource.rememberFileList) {
                                val missingName = initialFilePath.substringAfterLast('/').ifBlank { initialFilePath }
                                sendEvent(PlayerEvent.ShowMissingFileDialog(missingName, initialFilePath))
                            }
                            0
                        }
                    } else {
                        initialIndex.coerceIn(0, files.size - 1)
                    }
                    // Always use resource-specific slideshow interval (takes precedence over global settings)
                    val intervalToUse = resource.slideshowInterval * 1000L
                    
                    // CRITICAL: Recalculate showCommandPanel when resource is loaded
                    // Because loadSettings() runs in parallel and may complete before resource is available
                    val currentSettings = settingsRepository.getSettings().first()

                    // Special logic: if resource has explicit false but global default is true,
                    // treat it as "use global default" to avoid confusion
                    val showCommandPanel = when {
                        resource.showCommandPanel == null -> currentSettings.defaultShowCommandPanel
                        resource.showCommandPanel == false && currentSettings.defaultShowCommandPanel == true -> currentSettings.defaultShowCommandPanel
                        else -> resource.showCommandPanel
                    }

                    Timber.d("PlayerViewModel.loadMediaFiles: Determined showCommandPanel=$showCommandPanel (resource.showCommandPanel=${resource.showCommandPanel}, default=${currentSettings.defaultShowCommandPanel})")
                    
                    // Auto-activate slideshow for audio/video library resources
                    val autoSlideshow = resource.profile == ResourceProfile.AUDIO_LIBRARY ||
                            resource.profile == ResourceProfile.VIDEO_LIBRARY
                    
                    // Apply shuffle if requested from widget launch (only on first load)
                    val finalFiles = if (shuffleOnStart && state.value.files.isEmpty()) {
                        filesWithFavorites.shuffled()
                    } else {
                        filesWithFavorites
                    }

                    // Update state with resource first
                    // Preserve isPaused on reload (user may have manually paused before going to background);
                    // only use resumeIsPlaying on first load (when files list is still empty)
                    val effectiveIsPaused = if (state.value.files.isNotEmpty()) state.value.isPaused
                                           else (resumeIsPlaying == false)
                    updateState {
                        it.copy(
                            files = finalFiles,
                            currentIndex = safeIndex, 
                            resource = resource,
                            slideShowInterval = intervalToUse,
                            showCommandPanel = showCommandPanel,
                            isSlideShowActive = autoSlideshow,
                            isPaused = effectiveIsPaused
                        ) 
                    }
                }
                setLoading(false)
            } catch (e: Exception) {
                sendEvent(PlayerEvent.ShowError(e.message ?: "Failed to load media files"))
                sendEvent(PlayerEvent.FinishActivity)
                setLoading(false)
            }
        }
    }

    fun handleMissingFileRefresh(filePath: String) {
        viewModelScope.launch {
            val resource = state.value.resource
            resource?.let {
                MediaFilesCacheManager.removeFile(it.id, filePath)
                if (it.rememberFileList) {
                    cachedFileListRepository.deleteFile(it.id, filePath)
                }
            }
            sendEvent(PlayerEvent.FinishActivity)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // LOOKAHEAD MODEL for prefetch
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Lookahead file info for prefetch system.
     */
    data class LookaheadItem(
        val file: MediaFile,
        val index: Int,
        val priority: com.sza.fastmediasorter.ui.player.render.RenderPriority
    )

    /**
     * Returns adjacent files for prefetch with assigned priorities.
     * Order: NEXT, PREV, then forward lookahead (+2, +3).
     * Does not include current file.
     *
     * @param maxLookahead Maximum lookahead depth (default 2 means +2 and +3 indices)
     * @return List of LookaheadItem for prefetch queue
     */
    fun getLookaheadTargets(maxLookahead: Int = 2): List<LookaheadItem> {
        val currentState = state.value
        val files = currentState.files
        val currentIndex = currentState.currentIndex

        if (files.size <= 1) return emptyList()

        val result = mutableListOf<LookaheadItem>()

        // Next file (highest priority)
        val nextIndex = (currentIndex + 1) % files.size
        result.add(LookaheadItem(
            file = files[nextIndex],
            index = nextIndex,
            priority = com.sza.fastmediasorter.ui.player.render.RenderPriority.NEXT
        ))

        // Previous file
        val prevIndex = if (currentIndex == 0) files.size - 1 else currentIndex - 1
        if (prevIndex != nextIndex) { // Avoid duplicate if only 2 files
            result.add(LookaheadItem(
                file = files[prevIndex],
                index = prevIndex,
                priority = com.sza.fastmediasorter.ui.player.render.RenderPriority.PREVIOUS
            ))
        }

        // Forward lookahead (+2, +3, etc.)
        for (offset in 2..maxLookahead + 1) {
            val lookaheadIndex = (currentIndex + offset) % files.size
            // Skip if it wraps around to already included indices
            if (lookaheadIndex == currentIndex || lookaheadIndex == nextIndex || lookaheadIndex == prevIndex) continue
            result.add(LookaheadItem(
                file = files[lookaheadIndex],
                index = lookaheadIndex,
                priority = com.sza.fastmediasorter.ui.player.render.RenderPriority.LOOKAHEAD
            ))
        }

        return result
    }
    
    /**
     * Get credentialsId for a resource by its ID.
     * Used for Favorites where currentFile.resourceId points to the original resource.
     */
    suspend fun getCredentialsIdForResource(resourceId: Long): String? {
        return try {
            if (resourceId == -100L) null // Favorites itself has no credentials
            else getResourcesUseCase.getById(resourceId)?.credentialsId
        } catch (e: Exception) {
            Timber.e(e, "Failed to get credentialsId for resource $resourceId")
            null
        }
    }

    fun nextFile(skipDocuments: Boolean = false) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Timber.d("╔═══════════════════════════════════════════════════════════════╗")
            Timber.d("║ PlayerViewModel.nextFile() CALLED                             ║")
            Timber.d("╚═══════════════════════════════════════════════════════════════╝")
            Timber.d("Caller: ${caller?.className}.${caller?.methodName}() at line ${caller?.lineNumber}")
            Timber.d("Thread: ${Thread.currentThread().name}")
            Timber.d("skipDocuments: $skipDocuments")
        }
        
        val currentState = state.value
        if (currentState.files.isEmpty()) {
            Timber.d("nextFile: ABORT: No files to navigate")
            return
        }
        
        var nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) {
            Timber.d("nextFile: Looping from last (${currentState.currentIndex}) to first (0)")
            0 // Loop to first file after last
        } else {
            Timber.d("nextFile: Moving from index ${currentState.currentIndex} to ${currentState.currentIndex + 1}")
            currentState.currentIndex + 1
        }
        
        // Skip documents if requested (for slideshow auto-navigation)
        if (skipDocuments) {
            var attempts = 0
            val maxAttempts = currentState.files.size
            
            while (attempts < maxAttempts) {
                val file = currentState.files.getOrNull(nextIndex)
                val isDocument = file?.type == MediaType.TEXT || 
                                file?.type == MediaType.PDF || 
                                file?.type == MediaType.EPUB
                
                if (!isDocument) {
                    Timber.d("nextFile: Found media file at index $nextIndex")
                    break
                }
                
                Timber.d("nextFile: Skipping document at index $nextIndex")
                nextIndex = if (nextIndex >= currentState.files.size - 1) 0 else nextIndex + 1
                attempts++
            }
            
            if (attempts >= maxAttempts) {
                Timber.d("nextFile: All files are documents, staying on current")
                return
            }
        }
        
        Timber.d("nextFile: index ${currentState.currentIndex} → $nextIndex / ${currentState.files.size}")
        
        updateState { it.copy(currentIndex = nextIndex) }
        saveResumeState()
        
        // Save last viewed file for scroll restoration in Browse (debounced - 5 seconds)
        val resource = currentState.resource
        if (resource != null && nextIndex < currentState.files.size) {
            val fileToSave = currentState.files[nextIndex]
            saveLastViewedFileDebounced(fileToSave.path)
        }
    }

    fun previousFile(skipDocuments: Boolean = false) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Timber.d("╔═══════════════════════════════════════════════════════════════╗")
            Timber.d("║ PlayerViewModel.previousFile() CALLED                         ║")
            Timber.d("╚═══════════════════════════════════════════════════════════════╝")
            Timber.d("Caller: ${caller?.className}.${caller?.methodName}() at line ${caller?.lineNumber}")
            Timber.d("Thread: ${Thread.currentThread().name}")
            Timber.d("skipDocuments: $skipDocuments")
        }
        
        val currentState = state.value
        if (currentState.files.isEmpty()) {
            Timber.d("previousFile: ABORT: No files to navigate")
            return
        }
        
        var prevIndex = if (currentState.currentIndex <= 0) {
            Timber.d("previousFile: Looping from first (${currentState.currentIndex}) to last (${currentState.files.size - 1})")
            currentState.files.size - 1 // Loop to last file before first
        } else {
            Timber.d("previousFile: Moving from index ${currentState.currentIndex} to ${currentState.currentIndex - 1}")
            currentState.currentIndex - 1
        }
        
        // Skip documents if requested (for slideshow auto-navigation)
        if (skipDocuments) {
            var attempts = 0
            val maxAttempts = currentState.files.size
            
            while (attempts < maxAttempts) {
                val file = currentState.files.getOrNull(prevIndex)
                val isDocument = file?.type == MediaType.TEXT || 
                                file?.type == MediaType.PDF || 
                                file?.type == MediaType.EPUB
                
                if (!isDocument) {
                    Timber.d("previousFile: Found media file at index $prevIndex")
                    break
                }
                
                Timber.d("previousFile: Skipping document at index $prevIndex")
                prevIndex = if (prevIndex <= 0) currentState.files.size - 1 else prevIndex - 1
                attempts++
            }
            
            if (attempts >= maxAttempts) {
                Timber.d("previousFile: All files are documents, staying on current")
                return
            }
        }
        
        Timber.d("previousFile: index ${currentState.currentIndex} → $prevIndex / ${currentState.files.size}")
        
        updateState { it.copy(currentIndex = prevIndex) }
        saveResumeState()
        
        // Save last viewed file for scroll restoration in Browse (debounced - 5 seconds)
        val resource = currentState.resource
        if (resource != null && prevIndex < currentState.files.size) {
            val fileToSave = currentState.files[prevIndex]
            saveLastViewedFileDebounced(fileToSave.path)
        }
    }
    
    fun cancelLoading() {
        loadingJob?.cancel()
        loadingJob = null
    }

    /** Clear resume state when user explicitly exits the player. */
    fun clearResumeState() {
        viewModelScope.launch {
            Timber.d("PlayerViewModel: clearResumeState — user explicitly exited player")
            clearResumeStateUseCase()
        }
    }

    /** Save resume state for the currently playing file (PLAYER screen). */
    fun saveResumeState() {
        val currentState = state.value
        val currentFile = currentState.currentFile ?: return
        val mediaType = currentFile.type
        if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO) return
        val resource = currentState.resource ?: return

        viewModelScope.launch {
            try {
                val resumeState = ResumeState(
                    filePath = currentFile.path,
                    resourceId = resourceId,
                    currentFolderPath = null,
                    screenType = ScreenType.PLAYER,
                    sortMode = resource.sortMode,
                    isPlaying = !currentState.isPaused,
                    isSlideshowEnabled = currentState.isSlideShowActive,
                    mediaType = mediaType,
                    savedAt = System.currentTimeMillis()
                )
                // NonCancellable: DB write must complete even if this coroutine is cancelled
                // (e.g. fast navigation to next file) to avoid silently losing resume position.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    saveResumeStateUseCase(resumeState)
                }
            } catch (e: Exception) {
                Timber.e(e, "PlayerViewModel: Failed to save resume state")
            }
        }
    }

    fun toggleSlideShow() {
        updateState { it.copy(isSlideShowActive = !it.isSlideShowActive) }
    }
    
    /**
     * Force state re-emit to trigger observers (e.g., to check AudioBackgroundPhotos after slideshow starts).
     */
    fun forceStateUpdate() {
        updateState { it.copy() } // Copy with no changes triggers emit
    }

    fun setSlideShowActive(isActive: Boolean) {
        updateState { it.copy(isSlideShowActive = isActive) }
    }

    fun setSlideShowInterval(interval: Long) {
        updateState { it.copy(slideShowInterval = interval) }
        // Persist
        saveSlideshowSettings(interval, state.value.playToEndInSlideshow, state.value.slideshowMusicUri)
    }

    fun setPlayToEndInSlideshow(playToEnd: Boolean) {
        updateState { it.copy(playToEndInSlideshow = playToEnd) }
        saveSlideshowSettings(state.value.slideShowInterval, playToEnd, state.value.slideshowMusicUri)
    }

    fun setSlideshowMusic(uri: String?) {
        updateState { it.copy(slideshowMusicUri = uri) }
        saveSlideshowSettings(state.value.slideShowInterval, state.value.playToEndInSlideshow, uri)
    }

    private fun saveSlideshowSettings(intervalMs: Long, playToEnd: Boolean, musicUri: String?) {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                settingsRepository.updateSettings(
                    settings.copy(
                        slideshowInterval = (intervalMs / 1000).toInt(),
                        playToEndInSlideshow = playToEnd,
                        slideshowMusicUri = musicUri
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to save slideshow settings")
            }
        }
    }

    fun toggleControls() {
        updateState { it.copy(showControls = !it.showControls) }
    }

    fun togglePause() {
        updateState { it.copy(isPaused = !it.isPaused) }
        saveResumeState()
    }

    fun setPaused(isPaused: Boolean) {
        val wasPaused = state.value.isPaused
        updateState { it.copy(isPaused = isPaused) }
        if (wasPaused != isPaused) {
            saveResumeState()
        }
    }

    fun toggleCommandPanel() {
        val newShowCommandPanel = !state.value.showCommandPanel
        updateState { it.copy(showCommandPanel = newShowCommandPanel) }

        // Save user preference for this resource
        val resource = state.value.resource
        if (resource != null) {
            viewModelScope.launch {
                try {
                    // If new value matches global default, reset resource setting to null (use global)
                    val currentSettings = settingsRepository.getSettings().first()
                    val effectiveShowCommandPanel = if (newShowCommandPanel == currentSettings.defaultShowCommandPanel) {
                        null // Reset to use global default
                    } else {
                        newShowCommandPanel // Override with specific value
                    }
                    resourceRepository.updateResource(resource.copy(showCommandPanel = effectiveShowCommandPanel))
                    Timber.d("PlayerViewModel.toggleCommandPanel: Saved showCommandPanel=$effectiveShowCommandPanel for resource ${resource.id} (global default=${currentSettings.defaultShowCommandPanel})")
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to save command panel preference")
                }
            }
        }
    }
    
    /**
     * Enter fullscreen mode (hide command panel)
     */
    fun enterFullscreenMode() {
        if (state.value.showCommandPanel) {
            toggleCommandPanel()
        }
    }
    
    /**
     * Enter command panel mode (show command panel)
     */
    fun enterCommandPanelMode() {
        if (!state.value.showCommandPanel) {
            toggleCommandPanel()
        }
    }
    
    /**
     * Delete the current file and navigate to next/previous file.
     * @return true if file deleted successfully and navigation occurred, false if deletion failed, null if no files remain (should finish activity)
     */
    fun deleteCurrentFile(): Boolean? {
        val currentFile = state.value.currentFile
        val resource = state.value.resource
        
        if (currentFile == null) {
            sendEvent(PlayerEvent.ShowError("No file to delete"))
            return false
        }
        
        if (resource == null) {
            sendEvent(PlayerEvent.ShowError("Resource not loaded"))
            return false
        }
        
        viewModelScope.launch {
            try {
                // Deleting file
                
                // Check if path is a network resource
                val isNetwork = currentFile.path.let { 
                    it.startsWith("smb://") || 
                    it.startsWith("sftp://") || 
                    it.startsWith("ftp://") || 
                    it.startsWith("cloud://") 
                }
                
                // Wrap path in File object for FileOperationUseCase
                val file = if (isNetwork) {
                    // Network file - wrap with original path
                    object : java.io.File(currentFile.path) {
                        override fun getAbsolutePath(): String = currentFile.path
                        override fun getPath(): String = currentFile.path
                    }
                } else {
                    // Local file
                    java.io.File(currentFile.path)
                }
                
                // Use FileOperationUseCase for both local and network files
                // Network files must use hard delete (softDelete = false) as trash is not supported
                val deleteOperation = FileOperation.Delete(files = listOf(file), softDelete = !isNetwork)
                
                when (val result = fileOperationUseCase.execute(deleteOperation)) {
                    is FileOperationResult.Success,
                    is FileOperationResult.PartialSuccess -> {
                        // Notify that file was deleted
                        sendEvent(PlayerEvent.FileModified(currentFile.path))
                        
                        // Save undo operation if enabled in settings AND it was a soft delete (local file)
                        val settings = settingsRepository.getSettings().first()
                        if (settings.enableUndo && !isNetwork) {
                            // Extract trash paths from result for undo restoration
                            val trashPaths = when (result) {
                                is FileOperationResult.Success -> result.copiedFilePaths
                                is FileOperationResult.PartialSuccess -> emptyList() // Partial failures cannot be undone reliably
                                is FileOperationResult.Failure -> emptyList()
                                is FileOperationResult.AuthenticationRequired -> emptyList()
                                is FileOperationResult.PermissionRequired -> emptyList()
                            }
                            
                            val undoOp = UndoOperation(
                                type = com.sza.fastmediasorter.domain.model.FileOperationType.DELETE,
                                sourceFiles = listOf(currentFile.path),
                                destinationFolder = null,
                                copiedFiles = trashPaths.takeIf { it.isNotEmpty() },
                                oldNames = null
                            )
                            saveUndoOperation(undoOp)
                            
                            // Send event to show Snackbar with Undo button
                            sendEvent(PlayerEvent.ShowUndoSnackbar(undoOp))
                        }
                        
                        // Remove deleted file from the list
                        val updatedFiles = state.value.files.toMutableList()
                        val deletedIndex = state.value.currentIndex
                        updatedFiles.removeAt(deletedIndex)
                        
                        // Update cache to reflect deletion
                        MediaFilesCacheManager.removeFile(resource.id, currentFile.path)
                        
                        if (updatedFiles.isEmpty()) {
                            // No files left, close activity
                            sendEvent(PlayerEvent.FinishActivity)
                        } else {
                            // Check if we deleted the last file
                            if (deletedIndex >= updatedFiles.size) {
                                // We deleted the last file. Loop back to first file.
                                val newIndex = 0
                                updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                                saveResumeState()
                                Timber.d("File deleted (was last), looping to first file. New list size: ${updatedFiles.size}")
                            } else {
                                // Navigate to next file (which is now at the same index)
                                val newIndex = deletedIndex
                                updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                                saveResumeState()
                                Timber.d("File deleted successfully, new list size: ${updatedFiles.size}")
                            }
                        }
                    }
                    is FileOperationResult.Failure -> {
                        sendEvent(PlayerEvent.ShowError(result.error))
                        Timber.e("Delete failed: ${result.error}")
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        sendEvent(PlayerEvent.CloudAuthRequired(result.provider, result.message))
                        Timber.w("Delete requires cloud authentication: ${result.provider}")
                    }
                    is FileOperationResult.PermissionRequired -> {
                        // This is handled by FileOperationsHandler/PlayerActivity
                        Timber.d("Delete requires permission (handled by Activity)")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting file: ${currentFile.path}")
                sendEvent(PlayerEvent.ShowError("Error deleting file: ${e.message}"))
            }
        }
        
        return null // Async operation, result via events
    }
    
    /**
     * Reload files after rename operation to update current file path
     */
    fun reloadAfterRename() {
        viewModelScope.launch {
            try {
                val resource = state.value.resource ?: return@launch
                
                // Reload files from resource
                val files = getMediaFilesUseCase(
                    resource = resource,
                    sortMode = resource.sortMode,
                    sizeFilter = null
                ).first()
                
                // Find renamed file by name (may have changed)
                // Try to locate file at same index
                val currentIndex = state.value.currentIndex
                val newIndex = if (currentIndex < files.size) {
                    currentIndex
                } else {
                    0
                }
                
                updateState { 
                    it.copy(
                        files = files,
                        currentIndex = newIndex
                    )
                }
                
                Timber.d("Files reloaded after rename, total: ${files.size}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload files after rename")
                sendEvent(PlayerEvent.ShowError("Failed to reload files: ${e.message}"))
            }
        }
    }
    
    /**
     * Save undo operation with timestamp
     */
    fun saveUndoOperation(operation: UndoOperation) {
        updateState { 
            it.copy(
                lastOperation = operation,
                undoOperationTimestamp = System.currentTimeMillis()
            ) 
        }
        timber.log.Timber.d("Saved undo operation: ${operation.type}, file: ${operation.sourceFiles.firstOrNull()}")
    }
    
    /**
     * Undo last delete operation
     * Supports both local and network resources (SMB/SFTP/FTP/Cloud)
     */
    fun undoLastOperation() {
        val operation = state.value.lastOperation
        if (operation == null) {
            sendEvent(PlayerEvent.ShowMessage("No operation to undo"))
            return
        }
        
        if (operation.type != com.sza.fastmediasorter.domain.model.FileOperationType.DELETE) {
            sendEvent(PlayerEvent.ShowMessage("Can only undo delete operations"))
            return
        }
        
        viewModelScope.launch {
            try {
                // copiedFiles structure: [0] = trashDirPath, [1..n] = originalFilePaths
                operation.copiedFiles?.let { paths ->
                    if (paths.size < 2) {
                        sendEvent(PlayerEvent.ShowError("Invalid undo operation data"))
                        return@launch
                    }
                    
                    val trashDirPath = paths[0]
                    val originalPath = paths[1]
                    
                    // Detect resource type from path
                    val isLocal = !originalPath.startsWith("smb://") && 
                                  !originalPath.startsWith("sftp://") && 
                                  !originalPath.startsWith("ftp://") && 
                                  !originalPath.startsWith("cloud:/")
                    
                    val restoreSuccess = if (isLocal) {
                        restoreLocalFile(trashDirPath, originalPath)
                    } else {
                        restoreNetworkFile(trashDirPath, originalPath)
                    }
                    
                    if (restoreSuccess) {
                        // Clear undo operation
                        updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                        
                        sendEvent(PlayerEvent.ShowMessage("File restored successfully"))
                        
                        // Reload files to include restored file
                        reloadFiles()
                    } else {
                        sendEvent(PlayerEvent.ShowError("Failed to restore file"))
                    }
                } ?: sendEvent(PlayerEvent.ShowError("No files to restore"))
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Undo operation failed")
                sendEvent(PlayerEvent.ShowError("Undo failed: ${e.message}"))
            }
        }
    }
    
    /**
     * Restore local file from trash directory
     */
    private suspend fun restoreLocalFile(trashDirPath: String, originalPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashDir = java.io.File(trashDirPath)
            val originalFile = java.io.File(originalPath)
            
            if (!trashDir.exists() || !trashDir.isDirectory) {
                Timber.e("Undo: Trash folder not found: $trashDirPath")
                return@withContext false
            }
            
            // Find trashed file by name
            val trashedFile = java.io.File(trashDir, originalFile.name)
            
            if (!trashedFile.exists()) {
                Timber.e("Undo: Trashed file not found: ${trashedFile.absolutePath}")
                return@withContext false
            }
            
            // Restore file by renaming back to original location
            val restored = trashedFile.renameTo(originalFile)
            
            if (restored) {
                // Remove trash directory if empty
                if (trashDir.listFiles()?.isEmpty() == true) {
                    trashDir.delete()
                    Timber.d("Undo: Cleaned up empty trash directory")
                }
                Timber.i("Undo: Successfully restored local file: $originalPath")
            } else {
                Timber.e("Undo: Failed to rename trashed file back to original location")
            }
            
            return@withContext restored
        } catch (e: Exception) {
            Timber.e(e, "Undo: Exception restoring local file")
            return@withContext false
        }
    }
    
    /**
     * Restore network file from trash directory (SMB/S/FTP)
     * Uses FileOperationUseCase.execute(Move) to move file from .trash back to original location
     */
    private suspend fun restoreNetworkFile(trashDirPath: String, originalPath: String): Boolean {
        try {
            val originalFile = java.io.File(originalPath)
            val fileName = originalFile.name
            val trashFilePath = "$trashDirPath/$fileName"
            
            Timber.d("Undo: Restoring network file from $trashFilePath to $originalPath")
            
            val parentDir = originalFile.parentFile ?: throw java.io.IOException("Cannot restore to root directory")
            
            // Use FileOperationUseCase to move file from trash back to original location
            val moveOperation = FileOperation.Move(
                sources = listOf(java.io.File(trashFilePath)),
                destination = parentDir,
                overwrite = true // Overwrite in case original path still exists
            )
            
            return when (val result = fileOperationUseCase.execute(moveOperation)) {
                is FileOperationResult.Success -> {
                    Timber.i("Undo: Successfully restored network file: $originalPath")
                    true
                }
                is FileOperationResult.PartialSuccess -> {
                    Timber.w("Undo: Partial success restoring file (might be OK)")
                    true
                }
                is FileOperationResult.Failure -> {
                    Timber.e("Undo: Failed to restore network file: ${result.error}")
                    false
                }
                is FileOperationResult.AuthenticationRequired -> {
                    Timber.w("Undo: Cloud authentication required: ${result.provider}")
                    false
                }
                is FileOperationResult.PermissionRequired -> {
                    Timber.w("Undo: Permission required (unexpected)")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Undo: Exception restoring network file")
            return false
        }
    }
    
    /**
     * Clear expired undo operation (5 minutes)
     */
    fun clearExpiredUndoOperation() {
        val currentState = state.value
        val timestamp = currentState.undoOperationTimestamp
        
        if (timestamp != null && currentState.lastOperation != null) {
            val elapsed = System.currentTimeMillis() - timestamp
            val expiryTime = 5 * 60 * 1000L // 5 minutes
            
            if (elapsed > expiryTime) {
                updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                timber.log.Timber.d("Expired undo operation cleared")
            }
        }
    }

    suspend fun getSettings() = settingsRepository.getSettings().first()
    
    /**
     * Get adjacent files for preloading (previous + next).
     * Only returns IMAGE and GIF files for preloading.
     * Supports circular navigation.
     * 
     * @return List of MediaFile to preload (previous, next)
     */
    fun getAdjacentFiles(): List<MediaFile> {
        val currentState = state.value
        if (currentState.files.size <= 1) return emptyList()
        
        val result = mutableListOf<MediaFile>()
        
        // Calculate previous index with circular wrap
        val prevIndex = if (currentState.currentIndex <= 0) {
            currentState.files.size - 1 // Loop to last
        } else {
            currentState.currentIndex - 1
        }
        val prevFile = currentState.files.getOrNull(prevIndex)
        
        // Calculate next index with circular wrap
        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) {
            0 // Loop to first
        } else {
            currentState.currentIndex + 1
        }
        val nextFile = currentState.files.getOrNull(nextIndex)
        
        // Add previous file if it's an image or GIF
        if (prevFile != null && (prevFile.type == MediaType.IMAGE || prevFile.type == MediaType.GIF)) {
            result.add(prevFile)
        }
        
        // Add next file if it's an image or GIF AND it's different from previous (avoid duplicates in 2-file case)
        if (nextFile != null && 
            nextFile != prevFile &&
            (nextFile.type == MediaType.IMAGE || nextFile.type == MediaType.GIF)) {
            result.add(nextFile)
        }
        
        return result
    }
    
    /**
     * Get the next audio file in the list for prefetching.
     * Returns the next AUDIO file after current index (circular).
     * Only useful for network sources — local audio uses ExoPlayer playlist.
     */
    fun getNextAudioFile(): MediaFile? {
        val currentState = state.value
        if (currentState.files.size <= 1) return null

        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) 0
            else currentState.currentIndex + 1

        val nextFile = currentState.files.getOrNull(nextIndex) ?: return null
        return if (nextFile.type == MediaType.AUDIO) nextFile else null
    }

    /**
     * Save last viewed file path to resource for position restoration (debounced - 5 seconds)
     */
    private fun saveLastViewedFileDebounced(filePath: String) {
        val resource = state.value.resource ?: return
        
        saveLastViewedFileJob?.cancel()
        saveLastViewedFileJob = viewModelScope.launch {
            delay(5000) // Debounce 5 seconds - reduces DB writes during slideshow navigation
            try {
                resourceRepository.updateResource(resource.copy(lastViewedFile = filePath))
                Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save lastViewedFile")
            }
        }
    }
    
    /**
     * Save last viewed file path to resource for position restoration (immediate save)
     * Used when explicitly requested (e.g., activity pause)
     */
    fun saveLastViewedFile(filePath: String) {
        val resource = state.value.resource ?: return
        
        saveLastViewedFileJob?.cancel() // Cancel any pending debounced save
        viewModelScope.launch {
            try {
                resourceRepository.updateResource(resource.copy(lastViewedFile = filePath))
                Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save lastViewedFile")
            }
        }
    }
    
    /**
     * Handle file moved event (from MoveToDialog).
     * Removes file from list and updates cache.
     */
    fun onFileMoved(path: String) {
        val resource = state.value.resource ?: return
        val updatedFiles = state.value.files.toMutableList()
        val index = updatedFiles.indexOfFirst { it.path == path }
        
        if (index != -1) {
            updatedFiles.removeAt(index)
            MediaFilesCacheManager.removeFile(resource.id, path)
            
            if (updatedFiles.isEmpty()) {
                sendEvent(PlayerEvent.ShowMessage("File moved."))
                sendEvent(PlayerEvent.FinishActivity)
            } else {
                // Check if we moved the last file
                if (index >= updatedFiles.size) {
                    // We moved the last file. Loop back to first file.
                    val newIndex = 0
                    updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                    saveResumeState()
                    sendEvent(PlayerEvent.ShowMessage("File moved."))
                    Timber.d("File moved (was last), looping to first file. New list size: ${updatedFiles.size}")
                } else {
                    // Navigate to next file (which is now at the same index)
                    val newIndex = index
                    updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                    saveResumeState()
                    sendEvent(PlayerEvent.ShowMessage("File moved."))
                }
            }
        }
    }
    
    /**
     * Refresh current file info (size, modification date) after edit operations.
     * This triggers cache invalidation in Glide because size changes.
     */
    fun refreshCurrentFileInfo() {
        viewModelScope.launch {
            try {
                val currentFile = state.value.currentFile ?: return@launch
                
                // Get updated file size
                // For network files, increment size by 1 as workaround (actual size changed on server)
                // The main cache invalidation now happens via NetworkFileData.equals() using path+size
                val updatedSize = when {
                    currentFile.path.startsWith("smb://") || 
                    currentFile.path.startsWith("sftp://") || 
                    currentFile.path.startsWith("ftp://") ||
                    currentFile.path.startsWith("cloud://") -> {
                        // Network/cloud files: increment size to force cache invalidation
                        // Real size will be fetched on next BrowseActivity refresh
                        currentFile.size + 1
                    }
                    currentFile.path.startsWith("content://") || currentFile.path.startsWith("file://") -> {
                        currentFile.size // Keep existing for content URIs
                    }
                    else -> {
                        // Local file - read size directly
                        val file = java.io.File(currentFile.path)
                        if (file.exists()) file.length() else currentFile.size
                    }
                }
                
                // Update file in list
                val updatedFiles = state.value.files.toMutableList()
                val currentIndex = state.value.currentIndex
                if (currentIndex in updatedFiles.indices) {
                    updatedFiles[currentIndex] = currentFile.copy(size = updatedSize)
                    updateState { it.copy(files = updatedFiles) }
                    
                    Timber.d("PlayerViewModel: Refreshed file info - old size: ${currentFile.size}, new size: $updatedSize")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh file info")
            }
        }
    }
    
    /**
     * Remove moved file from the list and update state
     * @param movedFilePath Path of the file that was moved
     * @return true if there are files remaining, false if list is empty
     */
    fun removeMovedFile(movedFilePath: String): Boolean {
        return removeFileFromList(movedFilePath, "moved")
    }
    
    /**
     * Remove deleted file from the list and update state
     * @param deletedFilePath Path of the file that was deleted
     * @return true if there are files remaining, false if list is empty
     */
    fun removeDeletedFile(deletedFilePath: String): Boolean {
        return removeFileFromList(deletedFilePath, "deleted")
    }
    
    /**
     * Common logic to remove a file from the list and update state
     * @param filePath Path of the file to remove (used for logging context)
     * @param operation Description of operation for logging
     * @return true if there are files remaining, false if list is empty
     */
    @Suppress("UNUSED_PARAMETER")
    private fun removeFileFromList(filePath: String, operation: String): Boolean {
        val currentState = state.value
        val updatedFiles = currentState.files.toMutableList()
        val fileIndex = currentState.currentIndex
        
        if (fileIndex in updatedFiles.indices) {
            updatedFiles.removeAt(fileIndex)
            
            if (updatedFiles.isEmpty()) {
                return false // No files left
            }
            
            // Check if we removed the last file
            val newIndex = if (fileIndex >= updatedFiles.size) {
                // Removed last file - loop back to first
                Timber.d("File $operation (was last), looping to first file")
                0
            } else {
                // Navigate to next file (which is now at the same index)
                fileIndex
            }
            
            updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
            saveResumeState()
            Timber.d("File $operation successfully, new list size: ${updatedFiles.size}")
            return true
        }
        
        return false
    }

    fun toggleFavorite() {
        val currentFile = state.value.currentFile ?: return
        val resource = state.value.resource ?: return

        // Optimistic UI update
        val updatedFiles = state.value.files.map {
            if (it.path == currentFile.path) it.copy(isFavorite = !it.isFavorite) else it
        }
        updateState { it.copy(files = updatedFiles) }
        
        // Force state update to trigger UI refresh (button icon update)
        forceStateUpdate()

        viewModelScope.launch {
            try {
                // Use resourceId from file (original source) or fallback to current resource
                val targetResourceId = currentFile.resourceId ?: resource.id
                favoritesUseCase.toggleFavorite(currentFile, targetResourceId)
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite")
                // Revert UI on error
                val revertedFiles = state.value.files.map {
                    if (it.path == currentFile.path) it.copy(isFavorite = !it.isFavorite) else it
                }
                updateState { it.copy(files = revertedFiles) }
                forceStateUpdate() // Also force update on error to revert icon
                sendEvent(PlayerEvent.ShowError("Failed to update favorite status"))
            }
        }
    }


}


