package com.sza.fastmediasorter.wear.ui.player.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.data.wear.WatchPlaybackCommandEvents
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val PREFS_NAME = "wear_video_prefs"
private const val KEY_BATTERY_WARNING_SHOWN = "battery_warning_shown"

/** S1683: same step the audio player uses, so one bezel detent means the same thing in both. */
private const val SEEK_STEP_MS = 10_000L

/**
 * ViewModel for the video player screen.
 * Manages ExoPlayer instance and playback state for video files.
 */
// The collaborators remain visible to Hilt because each owns a distinct player concern.
@Suppress("LongParameterList")
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val preferencesRepository: WearPreferencesRepository,
    private val downloadNetworkFile: DownloadNetworkFileUseCase,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    private val streamPlaybackSessionFactory: StreamPlaybackSessionFactory,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L
    private var progressUpdateJob: Job? = null

    private var controlsHideJob: Job? = null

    /**
     * S1683: the selection this screen was opened with, kept only when it is a network one, so paging
     * re-enters the download path with the same source id instead of a bare uri.
     */
    private var networkSelection: SelectedMedia? = null

    /**
     * S1838: the slideshow flag decides whether a finished video opens the next file. Held as a field
     * rather than read at STATE_ENDED, because that branch is a player callback and cannot suspend.
     */
    private var isSlideshowEnabled = false

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.d("onIsPlayingChanged: $isPlaying")
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
                scheduleHideControls()
                streamPlaybackSession.withWideChannel()
            } else {
                stopProgressUpdates()
                showControls()
            }
            publishPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.d("Playback state changed: $playbackState")
            when (playbackState) {
                Player.STATE_READY -> {
                    Timber.d("Player STATE_READY, duration: ${exoPlayer.duration}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            durationMs = exoPlayer.duration.coerceAtLeast(0)
                        )
                    }
                    publishPlaybackState()
                }
                Player.STATE_ENDED -> {
                    // S1838: a finished video advances only while the slideshow is on - the rule the
                    // phone already applies, where PlayerPlaybackCallbackImpl.onPlaybackEnded pages the
                    // set under isSlideShowActive and otherwise leaves the player standing. Audio
                    // differs on purpose (S1837): it plays its set through unconditionally.
                    // A set of one is excluded, because restarting the only file is the endless loop
                    // S0902 removed below.
                    val setSize = playbackSetManager.currentSet.value?.files?.size ?: 0
                    if (isSlideshowEnabled && setSize > 1) {
                        Timber.d("S1838: video ended, advancing within the set of $setSize")
                        skipToNext()
                    } else {
                        streamPlaybackSession.stop()
                        Timber.d("Player STATE_ENDED - video finished")
                        _uiState.update { it.copy(isPlaying = false, showControls = true) }
                        // S0902: pause before seeking - playWhenReady stays true otherwise and the
                        // file auto-restarts from 0, looping indefinitely.
                        exoPlayer.pause()
                        exoPlayer.seekTo(0)
                        _uiState.update { it.copy(currentPositionMs = 0) }
                        publishPlaybackState()
                    }
                }
                Player.STATE_BUFFERING -> {
                    Timber.d("Player STATE_BUFFERING")
                    _uiState.update { it.copy(isLoading = true) }
                }
                Player.STATE_IDLE -> {
                    streamPlaybackSession.stop()
                    Timber.d("Player STATE_IDLE")
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            streamPlaybackSession.stop()
            Timber.e(error, "ExoPlayer error: ${error.errorCodeName}")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Playback error: ${error.message ?: error.errorCodeName}"
                )
            }
        }
    }

    private val streamPlaybackSession = streamPlaybackSessionFactory.create(
        scope = viewModelScope,
        onChannelReason = { reason -> _uiState.update { it.copy(channelReason = reason) } }
    )

    init {
        Timber.d("VideoPlayerViewModel initialized with fileId: $fileId")
        exoPlayer.addListener(playerListener)

        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            // S1683: navigation carries the id alone, so the shared set has to be pointed at it here
            // before any paging call can answer - same as the image viewer does.
            playbackSetManager.moveTo(fileId)
            syncSetPosition()
            loadVideoFile()
        }

        // S1838: the stored flag is the single source of truth for auto-advance, so the watch and the
        // phone that pushed the setting can never disagree about whether a set plays through.
        viewModelScope.launch {
            preferencesRepository.isSlideshowEnabled.collect { enabled ->
                isSlideshowEnabled = enabled
            }
        }

        // Subscribe to remote playback commands from phone
        viewModelScope.launch {
            WatchPlaybackCommandEvents.commandFlow.collect { command ->
                when (command) {
                    WearPlaybackCommand.PLAY_PAUSE -> togglePlayPause()
                    WearPlaybackCommand.NEXT       -> exoPlayer.seekToNextMediaItem()
                    WearPlaybackCommand.PREVIOUS   -> exoPlayer.seekToPreviousMediaItem()
                    WearPlaybackCommand.STOP       -> {
                        exoPlayer.stop()
                        streamPlaybackSession.stop()
                    }
                }
            }
        }
    }

    private fun loadVideoFile() {
        Timber.d("Loading video file with fileId: $fileId")
        checkBatteryWarning()
        loadMediaFile()
    }

    private fun checkBatteryWarning() {
        val warningShown = prefs.getBoolean(KEY_BATTERY_WARNING_SHOWN, false)
        if (!warningShown) {
            _uiState.update { it.copy(showBatteryWarning = true) }
        }
    }

    fun dismissBatteryWarning() {
        prefs.edit().putBoolean(KEY_BATTERY_WARNING_SHOWN, true).apply()
        _uiState.update { it.copy(showBatteryWarning = false) }
        // S0902: loadMediaFile/loadNetworkVideo defer playWhenReady while the warning is showing -
        // without this, first-run video never auto-starts once the user dismisses it.
        if (streamPlaybackSession.canStartCurrentStream()) {
            exoPlayer.play()
        }
    }

    /**
     * S1683: move to the neighbouring file of the same browsed set without returning to the list.
     * A set that cannot answer leaves the current file playing rather than stopping on nothing.
     */
    fun skipToNext() {
        val next = playbackSetManager.next() ?: return
        playFile(next)
    }

    fun skipToPrevious() {
        val previous = playbackSetManager.previous() ?: return
        playFile(previous)
    }

    /**
     * S1683: plays a file the set moved to through the same two branches first open uses - a network
     * file re-enters the download path carrying the source id its selection held, a local one goes
     * straight to its MediaStore uri. Reusing the branches is what keeps paging and first open from
     * drifting apart.
     */
    private fun playFile(file: WearMediaFile) {
        streamPlaybackSession.clear()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.update {
            it.copy(mediaFile = file, currentPositionMs = 0, durationMs = 0, error = null)
        }
        val selection = networkSelection
        if (selection != null) {
            viewModelScope.launch {
                loadNetworkVideo(selection.copy(file = file, streamUri = file.uri.toString()))
            }
        } else {
            playLocalFile(file)
        }
        syncSetPosition()
    }

    /** S1683: keeps the position marker in step with the set on first open and on every page. */
    private fun syncSetPosition() {
        val set = playbackSetManager.currentSet.value ?: return
        _uiState.update { it.copy(setIndex = set.index, setSize = set.files.size) }
    }

    private fun playLocalFile(file: WearMediaFile) {
        exoPlayer.setMediaItem(MediaItem.fromUri(file.uri))
        exoPlayer.prepare()
        // Don't auto-play until battery warning is dismissed
        if (!_uiState.value.showBatteryWarning) {
            exoPlayer.playWhenReady = true
        }
    }

    private fun loadMediaFile() {
        viewModelScope.launch {
            // First, check if we have a selected file from SelectedMediaManager (network source)
            val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)

            if (selectedMedia != null && selectedMedia.isNetworkSource) {
                // S1684: same omission as the audio player. Beyond the on-screen title, the empty
                // name also reached the phone - publishPlaybackState below sends
                // `mediaFile?.name ?: ""`, so the phone's remote-control card was blank too.
                _uiState.update { it.copy(mediaFile = selectedMedia.file) }
                // S1683: remembered so paging can re-enter the download path with the same source id.
                networkSelection = selectedMedia
                Timber.d("Loading network video: ${selectedMedia.file.name}")
                loadNetworkVideo(selectedMedia)
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.VIDEO)
                if (file != null) {
                    _uiState.update { it.copy(mediaFile = file) }
                    playLocalFile(file)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "File not found") }
                }
            }
        }
    }

    /**
     * Play a network video from its cached copy - ExoPlayer cannot read a remote InputStream
     * directly. S1687: which protocol that download speaks is the use case's decision, not this
     * screen's; this view model used to call SMB unconditionally and broke every other source.
     */
    private suspend fun loadNetworkVideo(selected: SelectedMedia) {
        if (selected.isDirectStream) {
            Timber.d("S1708: direct video stream playback uri=${selected.streamUri}")
            val mediaKind = ClassifyWearStreamMediaKindUseCase.VIDEO
            if (!streamPlaybackSession.prepare(mediaKind)) {
                _uiState.update { it.copy(isLoading = false) }
                return
            }
            _uiState.update { it.copy(isLoading = true) }
            val mediaItem = MediaItem.fromUri(Uri.parse(selected.streamUri))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            _uiState.update { it.copy(isLoading = false) }
            if (!_uiState.value.showBatteryWarning) {
                exoPlayer.playWhenReady = true
            }
            return
        }
        Timber.d("S1687: network video entry sourceId=${selected.sourceId} uri=${selected.streamUri}")
        _uiState.update { it.copy(isLoading = true) }

        downloadNetworkFile(selected, DownloadNetworkFileUseCase.Kind.VIDEO).fold(
            onSuccess = { cachedFile ->
                val mediaItem = MediaItem.fromUri(Uri.fromFile(cachedFile))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                _uiState.update { it.copy(isLoading = false) }

                // Don't auto-play until battery warning is dismissed
                if (!_uiState.value.showBatteryWarning) {
                    exoPlayer.playWhenReady = true
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load: ${e.message}")
                }
            }
        )
    }

    fun getPlayer(): ExoPlayer = exoPlayer

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            streamPlaybackSession.stop()
        } else {
            if (streamPlaybackSession.canStartCurrentStream()) {
                exoPlayer.play()
            }
        }
    }

    /**
     * S0902: called from the screen's onStop lifecycle effect - without this, playback
     * keeps running while the host activity is stopped (screen off / app backgrounded);
     * onCleared was the only prior teardown edge.
     */
    fun onHostStopped() {
        exoPlayer.pause()
        streamPlaybackSession.stop()
    }

    fun onScreenTap() {
        if (_uiState.value.showControls) {
            _uiState.update { it.copy(showControls = false) }
        } else {
            showControls()
            if (exoPlayer.isPlaying) {
                scheduleHideControls()
            }
        }
    }

    private fun showControls() {
        controlsHideJob?.cancel()
        _uiState.update { it.copy(showControls = true) }
    }

    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(showControls = false) }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() {
        val target = exoPlayer.currentPosition + SEEK_STEP_MS
        // ExoPlayer reports C.TIME_UNSET, a large negative, while the duration is still unknown -
        // clamping to it would send playback backwards past the start on the first turn of the bezel.
        val duration = exoPlayer.duration
        seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun seekBackward() {
        val newPosition = (exoPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)
        seekTo(newPosition)
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive && exoPlayer.isPlaying) {
                _uiState.update {
                    it.copy(currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0))
                }
                delay(500)
            }
        }
    }

    fun toggleScaleMode() {
        _uiState.update { current ->
            val nextMode = if (current.scaleMode == VideoScaleMode.FIT) VideoScaleMode.CROP_PAN else VideoScaleMode.FIT
            current.copy(scaleMode = nextMode, panOffsetX = 0f, panOffsetY = 0f)
        }
    }

    fun onPanDelta(dx: Float, dy: Float) {
        _uiState.update { current ->
            if (current.scaleMode == VideoScaleMode.CROP_PAN) {
                current.copy(
                    panOffsetX = current.panOffsetX + dx,
                    panOffsetY = current.panOffsetY + dy
                )
            } else {
                current
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun publishPlaybackState() {
        val state = _uiState.value
        val selected = selectedMediaManager.getSelectedFileById(fileId)
        val sourceName = if (selected?.isNetworkSource == true) {
            selected.file.uri.host ?: ""
        } else {
            "Local"
        }
        val payload = WearPlaybackStatePayload(
            isPlaying = state.isPlaying,
            fileName = state.mediaFile?.name ?: "",
            sourceName = sourceName,
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            mediaType = "VIDEO"
        )
        viewModelScope.launch { publishPlaybackStateUseCase(payload) }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("VideoPlayerViewModel cleared")
        stopProgressUpdates()
        controlsHideJob?.cancel()
        streamPlaybackSession.clear()
        exoPlayer.removeListener(playerListener)
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // instead of just stop()+clearMediaItems(); pairs with PlayerView.player = null in the screen's
        // onDispose so neither the player nor the disposed PlayerView/Context survives screen exit.
        exoPlayer.release()
    }
}
