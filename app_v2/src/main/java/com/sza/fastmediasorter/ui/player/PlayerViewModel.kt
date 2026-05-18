package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
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
import com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val appContext: Context,
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
    private val streamingCacheRepository: StreamingCacheRepository,
    // S0189: lookup for newly-created staged text notes (Downloads/FastMediaSorter/notes/)
    private val textNoteStagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry,
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
        val imageEditMode: PlayerImageEditMode = PlayerImageEditMode.NONE,
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
        val castDeviceName: String? = null,
        val showBlackScreenButton: Boolean = false,
        // S0162: screen rotation control
        val followSystemRotation: Boolean = true,
        val playerRotationSensorEnabled: Boolean = true,
        val showRotationToggle: Boolean = false,  // true when followSystemRotation=false && hasAccelerometer
        val playbackOrderMode: PlaybackOrderMode = PlaybackOrderMode.LOOP_LIST,
        val shuffleIndices: List<Int> = emptyList()
    ) {
        val currentFile: MediaFile? get() = files.getOrNull(currentIndex)
        // Circular navigation: always allow prev/next if files.size > 1
        val hasPrevious: Boolean get() = files.size > 1
        val hasNext: Boolean get() = files.size > 1
        // True only for IMAGE/GIF slideshows - drives UI hiding, 9-zone gestures, system bars.
        // VIDEO/AUDIO use isSlideShowActive for auto-advance only, not visual slideshow mode.
        val isPhotoSlideshowActive: Boolean get() =
            isSlideShowActive && (currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF)
        // S0023: convenience aliases for callers that prefer the flat names.
        // resourceId is derived from the active resource; isSlideshowEnabled mirrors
        // isSlideShowActive for symmetry with intent-extra naming used to rebuild panels.
        val resourceId: Long get() = resource?.id ?: 0L
        val isSlideshowEnabled: Boolean get() = isSlideShowActive
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
        object StopPlayback : PlayerEvent()
        // S0162: fired when the player-level rotation sensor toggle is changed
        data class RotationSensorToggled(val sensorEnabled: Boolean) : PlayerEvent()
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
    // S0028: per-window resume state isolation
    val windowId: String = savedStateHandle.get<String>(PlayerActivity.EXTRA_WINDOW_ID)
        ?: ResumeStateRepository.WINDOW_ID_MAIN

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
        context = appContext,
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
    fun setAutoDetectedStereoMode(mode: StereoMode, forFilePath: String = "") =
        stereoCoordinator.setAutoDetectedStereoMode(mode, forFilePath)
    fun resetStereoModeForNewFile(filePath: String? = state.value.currentFile?.path) =
        stereoCoordinator.resetStereoModeForNewFile(filePath)
    fun rememberStereoModeIfEnabled(mode: StereoMode) = stereoCoordinator.rememberStereoModeIfEnabled(mode)

    // ── S0107: Draw overlay current-frame bitmap ──────────────────────────────
    /** Bitmap currently displayed in the image viewer; set by ImageLoadingManager on load. Null for video/audio. */
    var currentDisplayedBitmap: Bitmap? = null
        internal set
    // ── End S0107 ─────────────────────────────────────────────────────────────

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

    // ── Adaptive pre-cache & stream-offload - delegated to PlayerPrefetchOffloadCoordinator.
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

    /**
     * Reload media files list.
     * Call when returning from background to reflect external changes.
     */
    fun reloadFiles() {
        loadMediaFiles()
    }

    /**
     * S0107: Inserts a newly created file immediately after the current file and navigates to it.
     * Used after a draw overlay save to the same directory.
     */
    fun onFileCreatedInCurrentDirectory(newFile: MediaFile) {
        val currentFiles = state.value.files.toMutableList()
        val insertAt = (state.value.currentIndex + 1).coerceAtMost(currentFiles.size)
        currentFiles.add(insertAt, newFile)
        updateState { it.copy(files = currentFiles, currentIndex = insertAt) }
    }

    fun updateCastState(isCasting: Boolean, deviceName: String?) {
        updateState { it.copy(isCasting = isCasting, castDeviceName = deviceName) }
        sendEvent(PlayerEvent.CastStateChanged(isCasting, deviceName))
    }

    // Settings collection + media-file loading pipeline extracted in Wave 4.1
    // (spec_decompose-giant-files.md). VM keeps only thin wrappers below.
    private val mediaFilesLoader = com.sza.fastmediasorter.ui.player.helpers.PlayerMediaFilesLoader(
        context = appContext,
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
        textNoteStagingRegistry = textNoteStagingRegistry,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        sendEvent = { event -> sendEvent(event) },
        setLoading = { loading -> setLoading(loading) },
        stereoCoordinator = stereoCoordinator
    )

    private fun loadSettings() = mediaFilesLoader.loadSettings()

    private fun loadMediaFiles() = mediaFilesLoader.loadMediaFiles()

    // Navigation (next/prev/jump/lookahead/adjacent) extracted in Wave 4.2.
    private val navigationCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationCoordinator(
        scope = viewModelScope,
        resourceRepository = resourceRepository,
        stateFlow = state,
        updateState = { update -> updateState(update) },
        saveResumeState = { this.saveResumeState() },
        clearPlaybackWatchdogs = { smbClient.playbackConnectionTracker.clearAllWatchdogs() },
        sendEvent = { event -> sendEvent(event) }
    )

    // IMPORTANT: init must run AFTER mediaFilesLoader/navigationCoordinator are initialized -
    // loadSettings()/loadMediaFiles() delegate into mediaFilesLoader. Moving this block above
    // the helper property initializers causes NPE at ViewModel construction (Wave 4.1 regression).
    init {
        loadSettings()
        loadMediaFiles()
    }

    /** Lookahead file info for prefetch system. */
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
    fun getLookaheadTargets(maxLookahead: Int = 2): List<LookaheadItem> =
        navigationCoordinator.getLookaheadTargets(maxLookahead)
    
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
     * Unlike [nextFile], this does NOT save resume state or trigger any side effects -
     * the audio service is already playing the correct track; we only update the index
     * so the UI (title, cover art, next/prev buttons) reflects the current track.
     */
    fun syncAudioServiceIndex(serviceIndex: Int) = navigationCoordinator.syncAudioServiceIndex(serviceIndex)

    fun jumpToIndex(index: Int, manual: Boolean = false) = navigationCoordinator.jumpToIndex(index, manual)

    fun nextFile(skipDocuments: Boolean = false, manual: Boolean = false) = navigationCoordinator.nextFile(skipDocuments, manual)

    fun previousFile(skipDocuments: Boolean = false, manual: Boolean = false) = navigationCoordinator.previousFile(skipDocuments, manual)

    
    fun cancelLoading() = mediaFilesLoader.cancelLoading()

    /** Clear resume state when user explicitly exits the player. */
    fun clearResumeState() {
        viewModelScope.launch {
            Timber.d("PlayerViewModel: clearResumeState - user explicitly exited player")
            clearResumeStateUseCase(windowId)
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
                    saveResumeStateUseCase(windowId, resumeState)
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

    private fun rebuildShuffleIndices() {
        val files = state.value.files
        if (files.isEmpty()) return
        val indices = files.indices.toMutableList().also { it.shuffle(kotlin.random.Random) }
        val current = state.value.currentIndex
        val pos = indices.indexOf(current)
        if (pos == 0 && indices.size > 1) {
            val swap = kotlin.random.Random.nextInt(1, indices.size)
            indices[0] = indices[swap].also { indices[swap] = indices[0] }
        } else if (pos > 0) {
            indices.removeAt(pos)
            indices.add(0, current)
        }
        updateState { it.copy(shuffleIndices = indices) }
    }

    fun setPlaybackOrderMode(mode: PlaybackOrderMode) {
        updateState { it.copy(playbackOrderMode = mode) }
        if (mode == PlaybackOrderMode.SHUFFLE && state.value.files.isNotEmpty()) {
            rebuildShuffleIndices()
        }
    }

    fun cyclePlaybackOrderMode(): PlaybackOrderMode {
        val next = state.value.playbackOrderMode.next()
        setPlaybackOrderMode(next)
        return next
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

    fun setImageEditMode(mode: PlayerImageEditMode) {
        if (state.value.imageEditMode == mode) return
        updateState { it.copy(imageEditMode = mode) }
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
     * S0023: hot StateFlow of app settings for UI consumers that need synchronous reads.
     * Eager start so `.value` is non-default after the first emission of the underlying
     * DataStore flow.
     */
    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // S0162: set once from PlayerActivity.onCreate via initRotationCapability()
    private var hasAccelerometer: Boolean = false

    /**
     * S0162: Called once from PlayerActivity after ScreenRotationManager.isAccelerometerPresent().
     * Launches a coroutine that maps rotation-related AppSettings → PlayerState on every settings
     * emission; the coroutine lives for the ViewModel's lifetime.
     */
    fun initRotationCapability(hasAccelerometer: Boolean) {
        this.hasAccelerometer = hasAccelerometer
        viewModelScope.launch {
            settingsRepository.getSettings().collect { s ->
                updateState {
                    it.copy(
                        followSystemRotation = s.followSystemRotation,
                        playerRotationSensorEnabled = s.playerRotationSensorEnabled,
                        showRotationToggle = !s.followSystemRotation && hasAccelerometer
                    )
                }
            }
        }
    }

    /**
     * S0162: Toggle the player-level rotation sensor.
     * Guard: only fires when followSystemRotation=false (showRotationToggle must be visible).
     * Persists the new state and emits RotationSensorToggled so the Activity applies it immediately.
     */
    fun toggleRotationSensor() {
        val current = state.value
        if (current.followSystemRotation) return   // guard: button must not be visible in this case
        val newEnabled = !current.playerRotationSensorEnabled
        updateState { it.copy(playerRotationSensorEnabled = newEnabled) }
        viewModelScope.launch {
            val appSettings = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(appSettings.copy(playerRotationSensorEnabled = newEnabled))
            sendEvent(PlayerEvent.RotationSensorToggled(newEnabled))
        }
    }

    /**
     * Get adjacent files for preloading (previous + next).
     * Only returns IMAGE and GIF files for preloading.
     * Supports circular navigation.
     * 
     * @return List of MediaFile to preload (previous, next)
     */
    fun getAdjacentFiles(): List<MediaFile> = navigationCoordinator.getAdjacentFiles()

    fun getNextAudioFile(): MediaFile? = navigationCoordinator.getNextAudioFile()

    fun saveLastViewedFile(filePath: String) = navigationCoordinator.saveLastViewedFile(filePath)
    
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
                sendEvent(PlayerEvent.ShowMessage(appContext.getString(R.string.player_file_moved)))
                sendEvent(PlayerEvent.FinishActivity)
            } else {
                // Check if we moved the last file
                if (index >= updatedFiles.size) {
                    // We moved the last file. Loop back to first file.
                    val newIndex = 0
                    updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                    saveResumeState()
                    sendEvent(PlayerEvent.ShowMessage(appContext.getString(R.string.player_file_moved)))
                    Timber.d("File moved (was last), looping to first file. New list size: ${updatedFiles.size}")
                } else {
                    // Navigate to next file (which is now at the same index)
                    val newIndex = index
                    updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                    saveResumeState()
                    sendEvent(PlayerEvent.ShowMessage(appContext.getString(R.string.player_file_moved)))
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

    fun updateRenamedFilePath(oldPath: String, newPath: String): Boolean {
        val currentState = state.value
        val updatedFiles = currentState.files.toMutableList()
        val fileIndex = updatedFiles.indexOfFirst { it.path == oldPath }
        if (fileIndex == -1) {
            Timber.w("updateRenamedFilePath: file not found by path '$oldPath'")
            return false
        }
        updatedFiles[fileIndex] = updatedFiles[fileIndex].copy(
            path = newPath,
            name = java.io.File(newPath).name
        )
        updateState { it.copy(files = updatedFiles) }
        saveResumeState()
        Timber.d("File renamed in list at index=$fileIndex: '$oldPath' → '$newPath'")
        return true
    }

    /**
     * Common logic to remove a file from the list and update state.
     * Identifies the target by path, not by currentIndex, so it is safe to call
     * after optimistic navigation has already advanced currentIndex.
     * @param filePath Path of the file to remove
     * @param operation Description of operation for logging
     * @return true if there are files remaining, false if list is empty
     */
    private fun removeFileFromList(filePath: String, operation: String): Boolean {
        val currentState = state.value
        val updatedFiles = currentState.files.toMutableList()
        val removeIndex = updatedFiles.indexOfFirst { it.path == filePath }

        if (removeIndex == -1) {
            Timber.w("removeFileFromList: '$filePath' not found in list (already removed?)")
            return updatedFiles.isNotEmpty()
        }

        updatedFiles.removeAt(removeIndex)

        if (updatedFiles.isEmpty()) return false

        val currentIndex = currentState.currentIndex
        val newIndex = when {
            currentIndex > removeIndex -> (currentIndex - 1).coerceAtLeast(0)
            currentIndex >= updatedFiles.size -> 0
            else -> currentIndex
        }

        updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
        saveResumeState()
        Timber.d("File $operation removed by path at index=$removeIndex, new size=${updatedFiles.size}, currentIndex=$newIndex")
        return true
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
                sendEvent(PlayerEvent.ShowError(appContext.getString(R.string.error_favorite_status_update_failed)))
            }
        }
    }

}


