package com.sza.fastmediasorter.wear.ui.player.image

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode
import com.sza.fastmediasorter.wear.domain.model.favoriteSourceId
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.ui.player.common.awaitPanelHide
import com.sza.fastmediasorter.wear.ui.slideshow.ImageSlideshowController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private var slideshowController: ImageSlideshowController? = null

    private var controlsHideJob: Job? = null

    /**
     * The selection this screen was opened with, kept only when it is a network one. Paging has to
     * re-enter the download path with the same source id, or S1687's routing loses the protocol on
     * the second image.
     */
    private var networkSelection: SelectedMedia? = null

    init {
        Timber.d("ImageViewerViewModel initialized with fileId: $fileId")

        val currentFileFlow = MutableStateFlow<WearMediaFile?>(null)
        fileOperations.bind(
            scope = viewModelScope,
            currentFile = currentFileFlow,
            isNetworkSource = { networkSelection != null }
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
                        playbackSetManager.moveTo(result.nextFile.id)
                        loadImageFile()
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            downloadNetworkFile(selected, DownloadNetworkFileUseCase.Kind.IMAGE).fold(
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
                    checkFavoriteState(sourceId = "network", filePath = selected.streamUri)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load: ${e.message}")
                    }
                }
            )
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
        checkFavoriteState(sourceId = "local", filePath = current.uri.toString())
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
        checkFavoriteState(sourceId = "local", filePath = fallback.uri.toString())
    }

    private fun initializeSlideshowController(totalItems: Int) {
        viewModelScope.launch {
            val intervalSeconds = preferencesRepository.slideshowIntervalSeconds.first()

            slideshowController = ImageSlideshowController(
                scope = viewModelScope,
                intervalSeconds = intervalSeconds,
                totalItems = totalItems,
                onIndexChanged = { newIndex ->
                    navigateToIndex(newIndex)
                }
            )

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
        Timber.d("S2480: slideshow start advances and clears the panel")
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
    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        Timber.d("S2480: panel hide countdown started")
        controlsHideJob = viewModelScope.launch {
            val hideDelayMs = preferencesRepository.panelAutoHideSeconds.first().coerceIn(1, 600) * 1000L
            Timber.d("S2505: ImageViewerViewModel scheduleHideControls delayMillis=$hideDelayMs")
            if (awaitPanelHide(isActive = true, delayMillis = hideDelayMs)) {
                Timber.d("S2480: panel hidden by countdown")
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
            loadNetworkImage(selection.copy(file = file, streamUri = file.uri.toString()))
            return
        }
        // Every per-file indicator has to follow the file, or paging leaves the previous one's
        // favourite state on screen.
        checkFavoriteState(sourceId = "local", filePath = file.uri.toString())
        val set = playbackSetManager.currentSet.value
        _uiState.update {
            it.copy(
                mediaFile = file,
                currentIndex = set?.index ?: it.currentIndex,
                totalCount = set?.files?.size ?: it.totalCount
            )
        }
    }

    fun toggleFavorite() {
        val selected = selectedMediaManager.getSelectedFileById(fileId)
        val isNetwork = selected?.isNetworkSource == true
        // S1846: one rule for the source id, shared with the audio player - the two used to disagree.
        val sourceId = favoriteSourceId(isNetwork, selected?.sourceId)
        val filePath = if (isNetwork) {
            selected.streamUri
        } else {
            _uiState.value.mediaFile?.uri?.toString() ?: return
        }
        val displayName = _uiState.value.mediaFile?.name ?: filePath.substringAfterLast('/')
        viewModelScope.launch {
            // S1846: marking goes through the use case that also pushes the delta, which is what the audio
            // player already did; this screen used to bypass it and repeat both halves by hand.
            _isFavorite.value = if (_isFavorite.value) {
                toggleFavoriteUseCase.toggle(sourceId, filePath, wasFavorite = true)
            } else {
                toggleFavoriteUseCase.add(
                    WearFavoriteRecord(
                        sourceId = sourceId,
                        filePath = filePath,
                        displayName = displayName,
                        mimeType = _uiState.value.mediaFile?.mimeType
                    )
                )
            }
        }
    }

    private fun checkFavoriteState(sourceId: String, filePath: String) {
        viewModelScope.launch {
            _isFavorite.value = favoritesRepository.isFavorite(sourceId, filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        controlsHideJob?.cancel()
        slideshowController?.stop()
    }
}
