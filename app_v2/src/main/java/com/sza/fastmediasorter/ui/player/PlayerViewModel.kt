package com.sza.fastmediasorter.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.CleanupPromptRequest
import com.sza.fastmediasorter.domain.model.OffloadOffer
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode
import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScreenType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideEntity
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.domain.usecase.StreamOffloadUseCase
import com.sza.fastmediasorter.ui.player.helpers.PrefetchPolicyManager
import com.sza.fastmediasorter.ui.player.helpers.PrefetchProgress
import com.sza.fastmediasorter.ui.player.helpers.PrefetchProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val stereoFormatOverrideDao: StereoFormatOverrideDao,
    private val resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository,
    private val googleDriveClient: com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository,
    private val favoritesUseCase: com.sza.fastmediasorter.domain.usecase.FavoritesUseCase,
    private val smbClient: com.sza.fastmediasorter.data.network.SmbClient,
    private val cachedFileListRepository: CachedFileListRepository,
    private val clearResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase,
    private val saveResumeStateUseCase: com.sza.fastmediasorter.domain.usecase.SaveResumeStateUseCase,
    private val prefetchPolicyManager: PrefetchPolicyManager,
    private val streamOffloadUseCase: StreamOffloadUseCase,
    private val streamingCacheRepository: StreamingCacheRepository
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
        val backgroundAudioExitBehavior: BackgroundAudioExitBehavior = BackgroundAudioExitBehavior.ASK,
        val showNowPlayingPanel: Boolean = false,
        val showControls: Boolean = false,
        val isPaused: Boolean = false,
        val showCommandPanel: Boolean = true,
        val showSmallControls: Boolean = false,
        val useCompactElements: Boolean = false, // True if global compact mode is enabled
        val allowRename: Boolean = true,
        val allowDelete: Boolean = true,
        val enableCopying: Boolean = true,
        val enableMoving: Boolean = true,
        val enableTranslation: Boolean = false,
        val enableOcr: Boolean = false,
        val enableGoogleLens: Boolean = false,
        val resource: MediaResource? = null,
        val lastOperation: UndoOperation? = null,
        val undoOperationTimestamp: Long? = null,
        val isCasting: Boolean = false,
        val castDeviceName: String? = null
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
        data class ShowMissingFileInfo(val fileName: String) : PlayerEvent()
        // Removed: LoadingProgress event (dialog not needed for single file loads)
        object FinishActivity : PlayerEvent()
        data class CastStateChanged(val isCasting: Boolean, val deviceName: String?) : PlayerEvent()
        /** Standard flavor: 3D content detected, suggest VR edition. */
        data class ShowVrInstallCta(val stereoMode: StereoMode) : PlayerEvent()
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
    
    private var saveLastViewedFileJob: Job? = null // Debounce job for database updates

    // ── Stereo / 3D video state ──────────────────────────────────────────────
    // Separate flow from PlayerState because the effective stereo mode needs to react
    // immediately to auto-detection, dialog overrides, and remembered VR format settings.
    private val stereoCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerStereoModeCoordinator(
        stereoFormatOverrideDao = stereoFormatOverrideDao,
        scope = viewModelScope,
        getCurrentFilePath = { state.value.currentFile?.path }
    )

    val stereoMode: StateFlow<StereoMode> = stereoCoordinator.stereoMode
    val detectedStereoMode: StateFlow<StereoMode> = stereoCoordinator.detectedStereoMode

    private val deleteUndoCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerDeleteUndoCoordinator(
        fileOperationUseCase = fileOperationUseCase,
        settingsRepository = settingsRepository,
        getMediaFilesUseCase = getMediaFilesUseCase,
        scope = viewModelScope,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        parentCallbacks = object : com.sza.fastmediasorter.ui.player.helpers.PlayerDeleteUndoCoordinator.ParentCallbacks {
            override fun saveResumeState() = this@PlayerViewModel.saveResumeState()
            override fun reloadFiles() = this@PlayerViewModel.reloadFiles()
        }
    )

    fun setStereoMode(mode: StereoMode) = stereoCoordinator.setStereoMode(mode)
    fun setAutoDetectedStereoMode(mode: StereoMode) = stereoCoordinator.setAutoDetectedStereoMode(mode)
    fun resetStereoModeForNewFile(filePath: String? = state.value.currentFile?.path) =
        stereoCoordinator.resetStereoModeForNewFile(filePath)
    fun rememberStereoModeIfEnabled(mode: StereoMode) = stereoCoordinator.rememberStereoModeIfEnabled(mode)

    fun showMessage(message: String) {
        sendEvent(PlayerEvent.ShowMessage(message))
    }

    /**
     * Emit CTA event suggesting the user install the VR edition.
     * Called from PlayerPlaybackCallbackImpl when 3D content is detected on standard flavor.
     */
    fun showVrInstallCta(mode: StereoMode) {
        sendEvent(PlayerEvent.ShowVrInstallCta(mode))
    }

    // ── Adaptive pre-cache & stream-offload — delegated to PlayerPrefetchOffloadCoordinator.
    // See spec: PLAN/spec_adaptive-playback-strategy.md §5.5-§5.6.
    private val prefetchOffloadCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerPrefetchOffloadCoordinator(
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        streamOffloadUseCase = streamOffloadUseCase,
        streamingCacheRepository = streamingCacheRepository,
        stateFlow = state,
        updateState = { update -> updateState(update) }
    )

    val prefetchCacheMultiplier: StateFlow<PrefetchCacheMultiplier> = prefetchOffloadCoordinator.prefetchCacheMultiplier
    val prefetchPlan: StateFlow<PrefetchPlan?> = prefetchOffloadCoordinator.prefetchPlan
    val prefetchProgress: StateFlow<PrefetchProgress> = prefetchOffloadCoordinator.prefetchProgress
    val offloadOffer: SharedFlow<OffloadOffer> = prefetchOffloadCoordinator.offloadOffer
    val offloadProgress: StateFlow<StreamOffloadUseCase.OffloadProgress?> = prefetchOffloadCoordinator.offloadProgress
    val cleanupPrompt: SharedFlow<CleanupPromptRequest> = prefetchOffloadCoordinator.cleanupPrompt

    fun updatePrefetchPlan(plan: PrefetchPlan) = prefetchOffloadCoordinator.updatePrefetchPlan(plan)
    fun bindPrefetchTracker(tracker: PrefetchProgressTracker) = prefetchOffloadCoordinator.bindPrefetchTracker(tracker)
    fun unbindPrefetchTracker() = prefetchOffloadCoordinator.unbindPrefetchTracker()
    fun emitOffloadOffer(offer: OffloadOffer) = prefetchOffloadCoordinator.emitOffloadOffer(offer)
    fun acceptOffload(offer: OffloadOffer) = prefetchOffloadCoordinator.acceptOffload(offer)
    fun declineOffload(offer: OffloadOffer) = prefetchOffloadCoordinator.declineOffload(offer)
    fun cancelOffload() = prefetchOffloadCoordinator.cancelOffload()
    fun switchToLocalFile(entry: com.sza.fastmediasorter.data.local.db.StreamingCacheEntry) =
        prefetchOffloadCoordinator.switchToLocalFile(entry)
    fun requestCleanupIfNeeded() = prefetchOffloadCoordinator.requestCleanupIfNeeded()
    fun deleteLocalCopy(entry: com.sza.fastmediasorter.data.local.db.StreamingCacheEntry) =
        prefetchOffloadCoordinator.deleteLocalCopy(entry)

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

    fun updateCastState(isCasting: Boolean, deviceName: String?) {
        updateState { it.copy(isCasting = isCasting, castDeviceName = deviceName) }
        sendEvent(PlayerEvent.CastStateChanged(isCasting, deviceName))
    }

    // Settings collection + media-file loading pipeline extracted in Wave 4.1
    // (spec_decompose-giant-files.md). VM keeps only thin wrappers below.
    private val mediaFilesLoader = com.sza.fastmediasorter.ui.player.helpers.PlayerMediaFilesLoader(
        scope = viewModelScope,
        resourceId = resourceId,
        initialFilePath = initialFilePath,
        initialIndex = initialIndex,
        skipAvailabilityCheck = skipAvailabilityCheck,
        resumeIsPlaying = resumeIsPlaying,
        shuffleOnStart = shuffleOnStart,
        getResourcesUseCase = getResourcesUseCase,
        getMediaFilesUseCase = getMediaFilesUseCase,
        settingsRepository = settingsRepository,
        favoritesUseCase = favoritesUseCase,
        googleDriveClient = googleDriveClient,
        cachedFileListRepository = cachedFileListRepository,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { loading -> setLoading(loading) },
        stereoCoordinator = stereoCoordinator
    )

    private fun loadSettings() = mediaFilesLoader.loadSettings()

    private fun loadMediaFiles() = mediaFilesLoader.loadMediaFiles()




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

    /**
     * Sync ViewModel's currentIndex to match the audio service's auto-advanced position.
     * Called when ExoPlayer playlist advances via MEDIA_ITEM_TRANSITION_REASON_AUTO.
     * Unlike [nextFile], this does NOT save resume state or trigger any side effects —
     * the audio service is already playing the correct track; we only update the index
     * so the UI (title, cover art, next/prev buttons) reflects the current track.
     */
    fun syncAudioServiceIndex(serviceIndex: Int) {
        val files = state.value.files
        if (serviceIndex < 0 || serviceIndex >= files.size) return
        Timber.d("syncAudioServiceIndex: ${state.value.currentIndex} → $serviceIndex / ${files.size}")
        updateState { it.copy(currentIndex = serviceIndex) }
    }

    fun jumpToIndex(index: Int) {
        val currentState = state.value
        if (index !in currentState.files.indices) {
            Timber.d("jumpToIndex: ABORT invalid index=$index for size=${currentState.files.size}")
            return
        }
        if (index == currentState.currentIndex) {
            Timber.d("jumpToIndex: ABORT target index matches current index=$index")
            return
        }

        Timber.d("jumpToIndex: index ${currentState.currentIndex} → $index / ${currentState.files.size}")

        // Reuse the same side effects as prev/next so resume and browse restore stay in sync.
        updateState { it.copy(currentIndex = index) }
        saveResumeState()

        val resource = currentState.resource
        if (resource != null) {
            val fileToSave = currentState.files[index]
            saveLastViewedFileDebounced(fileToSave.path)
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
    
    fun cancelLoading() = mediaFilesLoader.cancelLoading()

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

    fun updateExitBehavior(behavior: BackgroundAudioExitBehavior) {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                settingsRepository.updateSettings(settings.copy(backgroundAudioExitBehavior = behavior))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save background audio exit behavior")
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
    fun deleteCurrentFile(): Boolean? = deleteUndoCoordinator.deleteCurrentFile()

    fun reloadAfterRename() = deleteUndoCoordinator.reloadAfterRename()

    fun saveUndoOperation(operation: UndoOperation) = deleteUndoCoordinator.saveUndoOperation(operation)

    fun undoLastOperation() = deleteUndoCoordinator.undoLastOperation()

    fun clearExpiredUndoOperation() = deleteUndoCoordinator.clearExpiredUndoOperation()

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


