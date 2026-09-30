package com.sza.fastmediasorter.wear.ui.player.image

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearCastMediaType
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode
import com.sza.fastmediasorter.wear.domain.model.displayName
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCastManager
import com.sza.fastmediasorter.wear.ui.player.common.awaitPanelHide
import com.sza.fastmediasorter.wear.ui.player.common.resolveFavoriteIdentity
import com.sza.fastmediasorter.wear.ui.slideshow.ImageSlideshowController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the image viewer screen.
 * Manages image loading, navigation, and slideshow functionality.
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val downloadNetworkFile: DownloadNetworkFileUseCase,
    private val favoritesRepository: WearFavoritesRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    val fileOperations: com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager,
    val castManager: PlayerCastManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private var slideshowController: ImageSlideshowController? = null

    private var controlsHideJob: Job? = null

    /** Builds the current slideshow controller and then collects the slideshow setting for it. */
    private var slideshowSetupJob: Job? = null

    /**
     * The one network download in flight. Two quick page turns used to start two, and the older one
     * finishing last showed its picture beside the newer page's position.
     */
    private var loadJob: Job? = null

    /**
     * The selection this screen was opened with, kept only when it is a network one. Paging has to
     * re-enter the download path with the same source id, or S1687's routing loses the protocol on
     * the second image.
     */
    private var networkSelection: SelectedMedia? = null

    init {
        Timber.d("ImageViewerViewModel initialized with fileId: $fileId")

        castManager.bind(viewModelScope)
        val currentFileFlow = MutableStateFlow<WearMediaFile?>(null)
        fileOperations.bind(
            scope = viewModelScope,
            currentFile = currentFileFlow,
            isNetworkSource = { networkSelection != null },
            networkSourceId = { networkSelection?.sourceId }
        )
        viewModelScope.launch {
            _uiState.collect { state ->
                currentFileFlow.value = state.mediaFile
            }
        }
        viewModelScope.launch {
            fileOperations.operationResult.collect { result ->
                when (result) {
                    is com.sza.fastmediasorter.wear.ui.player.common.PlayerOperationResult.Advance -> {
                        advanceTo(result.nextFile)
                    }
                    is com.sza.fastmediasorter.wear.ui.player.common.PlayerOperationResult.SetEmpty -> {
                        _uiState.update { it.copy(closeScreen = true) }
                    }
                    com.sza.fastmediasorter.wear.ui.player.common.PlayerOperationResult.Stay, null -> {}
                }
            }
        }

        seedScaleMode()

        // S2006: the shuffle flag already governs this screen's paging through the shared set, so the
        // screen reads it from the same stored value the audio player writes rather than keeping its own.
        viewModelScope.launch {
            preferencesRepository.isShuffleEnabled.collect { enabled ->
                playbackSetManager.shuffleEnabled = enabled
                _uiState.update { it.copy(isShuffleEnabled = enabled) }
            }
        }

        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            // Navigation carries the id alone, so the shared set has to be pointed at it here.
            playbackSetManager.moveTo(fileId)
            loadImageFile()
        }
    }

    /**
     * S1948's reason, applied here: read once rather than collected. A subscription would route this
     * screen's own write back into the state, and the store is what carries the choice to the next file.
     */
    private fun seedScaleMode() {
        viewModelScope.launch {
            val stored = preferencesRepository.imageScaleMode.first()
            _uiState.update { it.copy(scaleMode = stored) }
        }
    }

    fun toggleScaleMode() {
        val next = _uiState.updateAndGet { current ->
            val mode = if (current.scaleMode == VideoScaleMode.FIT) {
                VideoScaleMode.CROP_PAN
            } else {
                VideoScaleMode.FIT
            }
            current.copy(scaleMode = mode)
        }.scaleMode
        viewModelScope.launch { preferencesRepository.setImageScaleMode(next) }
    }

    fun togglePlaybackMode() {
        val nextMode = _uiState.value.playbackMode.next()
        val isShuffle = nextMode == WearPlaybackMode.SHUFFLE
        _uiState.update { it.copy(playbackMode = nextMode, isShuffleEnabled = isShuffle) }
        viewModelScope.launch { preferencesRepository.setShuffleEnabled(isShuffle) }
    }

    fun toggleShuffle() {
        togglePlaybackMode()
    }

    private fun loadImageFile() {
        Timber.d("Loading image file with fileId: $fileId")

        // First, check if we have a selected file from SelectedMediaManager (network source)
        val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)

        if (selectedMedia != null && selectedMedia.isNetworkSource) {
            Timber.d("Loading network image: ${selectedMedia.file.name}")
            networkSelection = selectedMedia
            loadNetworkImage(selectedMedia)
        } else {
            // Local file - the browse screen already handed over the list it came from
            showCurrentFromSet()
        }
    }

    /**
     * Show a network image from its cached copy. S1687: which protocol that download speaks is the
     * use case's decision, not this screen's; this view model used to call SMB unconditionally and
     * broke every other source.
     */
    private fun loadNetworkImage(selected: SelectedMedia) {
        loadJob?.cancel()
        // The interval counts from the picture being on screen: a slow share would otherwise spend
        // the whole interval downloading and the next tick would cancel the picture before it showed.
        slideshowController?.pause()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val downloaded = downloadNetworkFile(selected, DownloadNetworkFileUseCase.Kind.IMAGE)
            // A page turn cancels this load; a download that ignored the cancel must not reach the screen.
            ensureActive()
            downloaded.fold(
                onSuccess = { cachedFile ->
                    // The cached copy is what gets displayed, but the position stays that of the
                    // remote file inside the browsed set.
                    val localFile = selected.file.copy(uri = Uri.fromFile(cachedFile))
                    val set = playbackSetManager.currentSet.value
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mediaFile = localFile,
                            currentIndex = set?.index ?: it.currentIndex,
                            totalCount = set?.files?.size ?: it.totalCount
                        )
                    }
                    checkFavoriteState()
                    // Only the local branch used to build the controller, so a network slideshow
                    // advanced once and stopped. Built once: paging re-enters this path per picture.
                    if (slideshowSetupJob == null && set != null) {
                        Timber.d("S3901: network slideshow controller built for ${set.files.size} files")
                        initializeSlideshowController(set.files.size)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load: ${e.message}")
                    }
                }
            )
            slideshowController?.resume()
        }
    }

    private fun showCurrentFromSet() {
        val set = playbackSetManager.currentSet.value
        val current = set?.current
        if (set == null || current == null) {
            showWithoutSet()
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                mediaFile = current,
                currentIndex = set.index,
                totalCount = set.files.size
            )
        }
        checkFavoriteState()
        initializeSlideshowController(set.files.size)
    }

    /**
     * A player reached by any route that did not go through browse gets no set. The file it was
     * opened with is still shown; position and count keep their empty defaults, which the screen
     * reads as "paging unavailable" rather than as an error.
     */
    private fun showWithoutSet() {
        val fallback = selectedMediaManager.getSelectedFileById(fileId)?.file
        if (fallback == null) {
            _uiState.update { it.copy(isLoading = false, error = "Image not found") }
            return
        }
        Timber.i("No published set for fileId=$fileId, paging unavailable")
        _uiState.update { it.copy(isLoading = false, mediaFile = fallback) }
        checkFavoriteState()
    }

    /**
     * Re-entered after every delete, move or rename, so the previous controller and its settings
     * collector are torn down first: left alive, its timer kept paging with the old item count and
     * [stopSlideshow] reached only the new one. A running slideshow carries over to the new set.
     */
    private fun initializeSlideshowController(totalItems: Int) {
        slideshowSetupJob?.cancel()
        slideshowController?.stop()
        slideshowController = null
        slideshowSetupJob = viewModelScope.launch {
            val intervalSeconds = preferencesRepository.slideshowIntervalSeconds.first()

            val controller = ImageSlideshowController(
                scope = viewModelScope,
                intervalSeconds = intervalSeconds,
                totalItems = totalItems,
                onIndexChanged = { newIndex ->
                    navigateToIndex(newIndex)
                }
            )
            slideshowController = controller
            playbackSetManager.currentSet.value?.index?.let(controller::onManualNavigation)
            if (_uiState.value.isSlideshowActive) {
                controller.start()
            }

            // S2006: collected, not read once. A one-shot read is why turning the setting off used to
            // leave an open viewer showing, and why the video player and this screen disagreed about
            // whether a settings change reaches a screen that is already up.
            preferencesRepository.isSlideshowEnabled.collect { enabled ->
                when {
                    enabled && !_uiState.value.isSlideshowActive -> startSlideshow()
                    !enabled && _uiState.value.isSlideshowActive -> stopSlideshow()
                }
            }
        }
    }

    fun startSlideshow() {
        Timber.d("Starting slideshow")
        slideshowController?.start()
        // S2480: the press has to produce a visible result. Starting the controller alone left the
        // same picture on screen for a whole interval under an unchanged panel, which read as the
        // button doing nothing - so the next picture comes up at once and the panel goes with it.
        controlsHideJob?.cancel()
        _uiState.update { it.copy(isSlideshowActive = true, showControls = false) }
        navigateToNext()
    }

    fun stopSlideshow() {
        Timber.d("Stopping slideshow")
        slideshowController?.stop()
        _uiState.update { it.copy(isSlideshowActive = false) }
        showControls()
        // Stopping a slideshow leaves the panel on a countdown, not on screen for good.
        scheduleHideControls()
    }

    /** A tap on the picture is the only way back to a hidden panel, so it toggles rather than reveals. */
    fun onScreenTap() {
        if (_uiState.value.showControls) {
            controlsHideJob?.cancel()
            _uiState.update { it.copy(showControls = false) }
        } else {
            showControls()
            scheduleHideControls()
        }
    }

    private fun showControls() {
        controlsHideJob?.cancel()
        _uiState.update { it.copy(showControls = true) }
    }

    /**
     * S2480: the countdown runs whether or not a slideshow does. An image has no playing state, so
     * having one on screen is itself the active condition - the same reasoning the screen already
     * applies to keeping the display awake. Tying it to the slideshow left a hand-paged viewer
     * showing its panel forever, which is what the owner reported.
     */
    @Suppress("MagicNumber")
    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            val hideDelayMs = preferencesRepository.panelAutoHideSeconds.first().coerceIn(1, 600) * 1000L
            if (awaitPanelHide(isActive = true, delayMillis = hideDelayMs)) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    fun toggleSlideshow() {
        if (_uiState.value.isSlideshowActive) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    fun navigateToNext() {
        val next = playbackSetManager.next() ?: return
        showFile(next)
        syncSlideshowToSet()
        restartHideCountdownIfShown()
    }

    fun navigateToPrevious() {
        val previous = playbackSetManager.previous() ?: return
        showFile(previous)
        syncSlideshowToSet()
        restartHideCountdownIfShown()
    }

    /**
     * S2480: paging is a touch, so it postpones the hide - but only when the panel is already up.
     * A zone tap on a hidden panel must page without bringing the buttons back over the picture.
     */
    private fun restartHideCountdownIfShown() {
        if (_uiState.value.showControls) {
            scheduleHideControls()
        }
    }

    /**
     * The set is the single source of position, so the slideshow controller follows it rather than
     * keeping a second index of its own.
     */
    private fun syncSlideshowToSet() {
        val index = playbackSetManager.currentSet.value?.index ?: return
        slideshowController?.onManualNavigation(index)
    }

    /**
     * After a delete, move or rename. Going through [loadImageFile] re-read the file the screen was
     * opened with, so a network set downloaded that picture again - possibly the one just deleted.
     * The controller is rebuilt only where one was built: its item count is fixed and the set shrank.
     */
    private fun advanceTo(next: WearMediaFile) {
        Timber.d("S3899: image advance after operation shows ${next.name}, network=${networkSelection != null}")
        playbackSetManager.moveTo(next.id)
        showFile(next)
        val size = playbackSetManager.currentSet.value?.files?.size ?: return
        if (slideshowSetupJob != null) {
            initializeSlideshowController(size)
        }
    }

    private fun navigateToIndex(index: Int) {
        val target = playbackSetManager.currentSet.value?.files?.getOrNull(index) ?: return
        if (playbackSetManager.moveTo(target.id)) {
            showFile(target)
        }
    }

    /**
     * A network file is not readable at its remote path, so paging into one re-enters the download
     * path instead of handing that path to the image loader.
     */
    private fun showFile(file: WearMediaFile) {
        val selection = networkSelection
        if (selection != null) {
            // S3894: remembered, not just passed on - the favourite mark is resolved from it, and
            // leaving it on the opened picture marked that one after every page turn.
            val paged = selection.copy(file = file, streamUri = file.uri.toString())
            networkSelection = paged
            Timber.d("S3894: image paged, favourite identity follows ${paged.streamUri}")
            loadNetworkImage(paged)
            return
        }
        val set = playbackSetManager.currentSet.value
        _uiState.update {
            it.copy(
                mediaFile = file,
                currentIndex = set?.index ?: it.currentIndex,
                totalCount = set?.files?.size ?: it.totalCount
            )
        }
        // Every per-file indicator has to follow the file, or paging leaves the previous one's
        // favourite state on screen. Read after the update: the identity comes from the file on screen.
        checkFavoriteState()
    }

    /**
     * S2531: hands the picture on screen to the phone, which owns the Cast session, or ends the one
     * already running - the screen shows one entry and the phone's reported state decides which of
     * the two it is, so the choice is made here rather than in the composable.
     */
    fun toggleCast() {
        if (castManager.castState.value.isCasting) {
            castManager.stopCasting()
            return
        }
        val file = _uiState.value.mediaFile ?: return
        castManager.castCurrentFile(file, networkSelection, WearCastMediaType.IMAGE)
    }

    fun toggleFavorite() {
        val identity = currentFavoriteIdentity() ?: return
        Timber.d("S3894: image toggle favourite ${identity.sourceId}:${identity.filePath}")
        val mediaFile = _uiState.value.mediaFile
        val displayName = mediaFile?.displayName ?: identity.filePath.substringAfterLast('/')
        viewModelScope.launch {
            // S1846: marking goes through the use case that also pushes the delta, which is what the audio
            // player already did; this screen used to bypass it and repeat both halves by hand.
            val record = WearFavoriteRecord(
                sourceId = identity.sourceId,
                filePath = identity.filePath,
                displayName = displayName,
                mimeType = mediaFile?.mimeType
            )
            _isFavorite.value = toggleFavoriteUseCase.toggle(record, _isFavorite.value)
        }
    }

    private fun checkFavoriteState() {
        val identity = currentFavoriteIdentity()
        if (identity == null) {
            _isFavorite.value = false
            return
        }
        viewModelScope.launch {
            val marked = favoritesRepository.isFavorite(identity.sourceId, identity.filePath)
            // Reads race across page turns; only the answer for the picture still on screen is kept.
            if (currentFavoriteIdentity() == identity) {
                _isFavorite.value = marked
            }
        }
    }

    /**
     * S3894: one identity for the read and the write of the mark. Only the remembered network selection
     * is consulted: it follows paging, while the manager keeps answering with the opened picture, and a
     * local picture has always been marked by the uri on screen.
     */
    private fun currentFavoriteIdentity() = resolveFavoriteIdentity(
        selected = networkSelection,
        fallbackUri = _uiState.value.mediaFile?.uri?.toString()
    )

    override fun onCleared() {
        super.onCleared()
        controlsHideJob?.cancel()
        slideshowController?.stop()
    }
}
