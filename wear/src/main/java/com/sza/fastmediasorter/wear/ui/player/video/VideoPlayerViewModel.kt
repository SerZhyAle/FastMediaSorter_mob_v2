package com.sza.fastmediasorter.wear.ui.player.video

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.wear.WatchPlaybackCommandEvents
import com.sza.fastmediasorter.wear.domain.model.FAVORITE_ITEM_KIND_STREAM
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearCastMediaType
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode
import com.sza.fastmediasorter.wear.domain.model.displayName
import com.sza.fastmediasorter.wear.domain.playback.WEAR_PLAYBACK_STALL_TIMEOUT_MS
import com.sza.fastmediasorter.wear.domain.playback.WearPlaybackStallPolicy
import com.sza.fastmediasorter.wear.domain.playback.WearPlaybackStallWatchdog
import com.sza.fastmediasorter.wear.domain.playback.WearStationInfo
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.EndPhoneCameraSessionOnStreamErrorUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.ui.player.common.PlaybackProgressTicker
import com.sza.fastmediasorter.wear.ui.player.common.PlayerCastManager
import com.sza.fastmediasorter.wear.ui.player.common.PlayerVolumeController
import com.sza.fastmediasorter.wear.ui.player.common.awaitPanelHide
import com.sza.fastmediasorter.wear.ui.player.common.backwardSeekTarget
import com.sza.fastmediasorter.wear.ui.player.common.forwardSeekTarget
import com.sza.fastmediasorter.wear.ui.player.common.jumpToLive
import com.sza.fastmediasorter.wear.ui.player.common.pauseForHostStop
import com.sza.fastmediasorter.wear.ui.player.common.resolveFavoriteIdentity
import com.sza.fastmediasorter.wear.ui.player.common.togglePlayPause
import com.sza.fastmediasorter.wear.ui.player.common.wearPlaybackStatePayload
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

private const val PREFS_NAME = "wear_video_prefs"
private const val KEY_BATTERY_WARNING_SHOWN = "battery_warning_shown"
private const val MAX_AUTO_HIDE_SECONDS = 600
private const val MILLIS_PER_SECOND = 1000L
private const val BITS_PER_KILOBIT = 1000

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
    private val endPhoneCameraSessionOnStreamError: EndPhoneCameraSessionOnStreamErrorUseCase,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    private val streamPlaybackSessionFactory: StreamPlaybackSessionFactory,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val toggleStreamPinUseCase: com.sza.fastmediasorter.wear.domain.usecase.ToggleStreamPinUseCase,
    private val nowPlayingRepository: WearNowPlayingRepository,
    val fileOperations: com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager,
    val castManager: PlayerCastManager,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private var controlsHideJob: Job? = null

    private var streamSessionStartRealtime: Long = 0L
    private var streamAccumulatedMs: Long = 0L

    private val progressTicker = PlaybackProgressTicker(viewModelScope, exoPlayer) { position ->
        if (_uiState.value.isStream) {
            val now = SystemClock.elapsedRealtime()
            val currentElapsed = if (_uiState.value.isPlaying && streamSessionStartRealtime > 0L) {
                streamAccumulatedMs + (now - streamSessionStartRealtime).coerceAtLeast(0L)
            } else {
                streamAccumulatedMs
            }
            _uiState.update { it.copy(currentPositionMs = currentElapsed) }
        } else {
            _uiState.update { it.copy(currentPositionMs = position) }
        }
    }

    private val volumeController = PlayerVolumeController(
        scope = viewModelScope,
        context = context,
        onReadout = { level, max ->
            _uiState.update { it.copy(volumeLevel = level, volumeMax = max, isVolumeVisible = true) }
        },
        onHidden = { _uiState.update { it.copy(isVolumeVisible = false) } }
    )

    /**
     * S1683: the selection this screen was opened with, kept only when it is a network one, so paging
     * re-enters the download path with the same source id instead of a bare uri.
     */
    private var networkSelection: SelectedMedia? = null

    /**
     * S3212: the address the open direct stream is being pulled from, or null when a file is playing.
     * A player error carries no address of its own, and it is the address that says whether the
     * session that just died was the phone's camera.
     */
    private var directStreamUri: String? = null

    /**
     * S1838: the slideshow flag decides whether a finished video opens the next file. Held as a field
     * rather than read at STATE_ENDED, because that branch is a player callback and cannot suspend.
     */
    private var isSlideshowEnabled = false

    /**
     * S1948: true once the scale button has decided the mode for this screen, which stops the stored
     * value seeded in [init] from landing on top of a choice the user already made.
     */
    private var scaleModeChosen = false

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.d("onIsPlayingChanged: $isPlaying")
            if (_uiState.value.isStream) {
                val now = SystemClock.elapsedRealtime()
                if (isPlaying) {
                    streamSessionStartRealtime = now
                } else {
                    if (streamSessionStartRealtime > 0L) {
                        streamAccumulatedMs += (now - streamSessionStartRealtime).coerceAtLeast(0L)
                        streamSessionStartRealtime = 0L
                    }
                }
            }
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                progressTicker.start()
                scheduleHideControls()
                streamPlaybackSession.withWideChannel()
            } else {
                progressTicker.stop()
                showControls()
            }
            publishPlaybackState()
            updateStallWatch()
        }

        /**
         * S2849: the edge a stalled session is left on. A player that is paused while already silent
         * changes neither `isPlaying` nor the playback state, so without this callback the watchdog
         * armed by the stall would still be counting down over a session that is already settled.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _uiState.update { it.copy(isPlaybackRequested = playWhenReady) }
            updateStallWatch()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.d("Playback state changed: $playbackState")
            when (playbackState) {
                Player.STATE_READY -> {
                    Timber.d("Player STATE_READY, duration: ${exoPlayer.duration}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            durationMs = if (it.isStream) 0L else exoPlayer.duration.coerceAtLeast(0)
                        )
                    }
                    refreshStationFormat()
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
                    if (_uiState.value.playbackMode == WearPlaybackMode.LOOP) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (isSlideshowEnabled && setSize > 1) {
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
            updateStallWatch()
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshStationFormat()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val res = "${videoSize.width}x${videoSize.height}"
                _uiState.update { state ->
                    if (state.isStream) {
                        val current = state.station ?: WearStationInfo()
                        state.copy(station = current.copy(resolution = res))
                    } else {
                        state
                    }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            streamPlaybackSession.stop()
            Timber.e(error, "ExoPlayer error: ${error.errorCodeName}")
            // S3212: the phone's camera session ends without a word from the phone, so a dead stream
            // on its address is the end of it. The screen it was started from states the reason and
            // offers a fresh start, which a raw error line here could do neither of.
            if (endPhoneCameraSessionOnStreamError(directStreamUri)) {
                _uiState.update {
                    it.copy(isLoading = false, isPlaying = false, closeScreen = true)
                }
                return
            }
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

    /**
     * S2849: the screen's own copy of the guard S2848 gave the background service. The screen was
     * spared the overnight drain by the pause on ON_STOP (S0902), which the screen-off mode suppresses
     * by holding the display awake - so behind that sheet a stream that stops answering has exactly
     * the service's problem, with a lit screen on top of it.
     */
    private val stallWatchdog = WearPlaybackStallWatchdog(
        scope = viewModelScope,
        stallTimeoutMs = WEAR_PLAYBACK_STALL_TIMEOUT_MS,
        onStalled = { onPlaybackStalled() }
    )

    init {
        Timber.d("VideoPlayerViewModel initialized with fileId: $fileId")
        exoPlayer.addListener(playerListener)

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
                        playFile(result.nextFile)
                    }
                    is com.sza.fastmediasorter.wear.ui.player.common.PlayerOperationResult.SetEmpty -> {
                        _uiState.update { it.copy(closeScreen = true) }
                    }
                    com.sza.fastmediasorter.wear.ui.player.common.PlayerOperationResult.Stay, null -> {}
                }
            }
        }

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

        // S1948: read once rather than collect. A subscription would route this screen's own write
        // back into the state and undo the pan reset that toggleScaleMode makes in the same press.
        viewModelScope.launch {
            val storedMode = preferencesRepository.videoScaleMode.first()
            // The controls are on screen before this disk read returns, so a press can beat it. The
            // seed is the older value by then and would silently undo the choice just made.
            if (!scaleModeChosen) {
                _uiState.update { it.copy(scaleMode = storedMode) }
            }
        }

        // S2006: the same collector the audio player has had since S1701. The stored flag is the one
        // source of truth, so this screen shows it and asks for a change rather than keeping its own.
        viewModelScope.launch {
            preferencesRepository.isShuffleEnabled.collect { enabled ->
                playbackSetManager.shuffleEnabled = enabled
                _uiState.update { it.copy(isShuffleEnabled = enabled) }
            }
        }

        // S2250: the stored flag is the watch's own path to the policy - the phone keeps a separate
        // holder in its own module, and the two modules share no sources.
        viewModelScope.launch {
            preferencesRepository.isAnimationsDisabled.collect { disabled ->
                _uiState.update { it.copy(animationsDisabled = disabled) }
            }
        }

        // Subscribe to remote playback commands from phone
        viewModelScope.launch {
            WatchPlaybackCommandEvents.commandFlow.collect { command ->
                when (command) {
                    WearPlaybackCommand.PLAY_PAUSE -> togglePlayPause()
                    WearPlaybackCommand.NEXT -> {
                        skipToNext()
                    }
                    WearPlaybackCommand.PREVIOUS -> {
                        skipToPrevious()
                    }
                    WearPlaybackCommand.STOP -> {
                        exoPlayer.stop()
                        streamPlaybackSession.stop()
                        streamSessionStartRealtime = 0L
                        streamAccumulatedMs = 0L
                    }
                }
            }
        }
    }

    fun toggleShuffle() {
        togglePlaybackMode()
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
        if (_uiState.value.isStream) return
        val next = playbackSetManager.next() ?: return
        playFile(next)
    }

    fun skipToPrevious() {
        if (_uiState.value.isStream) return
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
        directStreamUri = null
        streamSessionStartRealtime = 0L
        streamAccumulatedMs = 0L
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.update {
            it.copy(
                mediaFile = file,
                currentPositionMs = 0,
                durationMs = 0,
                station = null,
                error = null
            )
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
        refreshFavoriteState()
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
            // S1884: check if SelectedMediaManager holds the file (network source or phone-delivered file)
            val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)

            if (selectedMedia != null) {
                _uiState.update {
                    it.copy(
                        mediaFile = selectedMedia.file,
                        isStream = selectedMedia.sourceId == SOURCE_ID_STREAM
                    )
                }
                refreshFavoriteState()
                if (selectedMedia.isNetworkSource) {
                    // S1683: remembered so paging can re-enter the download path with the same source id.
                    networkSelection = selectedMedia
                    Timber.d("Loading network video: ${selectedMedia.file.name}")
                    loadNetworkVideo(selectedMedia)
                } else {
                    Timber.d("Loading local video from SelectedMediaManager: ${selectedMedia.file.name}")
                    playLocalFile(selectedMedia.file)
                }
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.VIDEO)
                if (file != null) {
                    _uiState.update { it.copy(mediaFile = file) }
                    refreshFavoriteState()
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
            val mediaKind = ClassifyWearStreamMediaKindUseCase.VIDEO
            if (!streamPlaybackSession.prepare(mediaKind)) {
                _uiState.update { it.copy(isLoading = false) }
                return
            }
            streamSessionStartRealtime = 0L
            streamAccumulatedMs = 0L
            directStreamUri = selected.streamUri
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isStream = true,
                    durationMs = 0L,
                    currentPositionMs = 0L,
                    station = null
                )
            }
            val mediaItem = MediaItem.fromUri(Uri.parse(selected.streamUri))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            _uiState.update { it.copy(isLoading = false) }
            if (!_uiState.value.showBatteryWarning) {
                exoPlayer.playWhenReady = true
            }
            return
        }
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

    /**
     * S3202: format and codec metadata for the active stream.
     * Video format provides video codec/resolution/bitrate; audio format provides audio fallback codec/bitrate.
     */
    private fun refreshStationFormat() {
        if (!_uiState.value.isStream) return
        val vFormat = exoPlayer.videoFormat
        val aFormat = exoPlayer.audioFormat
        val videoCodec = WearStationInfo.codecLabel(vFormat?.sampleMimeType)
        val audioCodec = WearStationInfo.codecLabel(aFormat?.sampleMimeType)
        val codec = videoCodec ?: audioCodec
        val res = if (vFormat != null && vFormat.width > 0 && vFormat.height > 0) {
            "${vFormat.width}x${vFormat.height}"
        } else {
            null
        }
        val bitrate = (vFormat?.bitrate?.takeIf { it > 0 } ?: aFormat?.bitrate?.takeIf { it > 0 })
            ?.div(BITS_PER_KILOBIT)
        _uiState.update { state ->
            val current = state.station ?: WearStationInfo()
            state.copy(
                station = current.copy(
                    codec = codec ?: current.codec,
                    resolution = res ?: current.resolution,
                    bitrateKbps = bitrate ?: current.bitrateKbps
                )
            )
        }
    }

    fun getPlayer(): ExoPlayer = exoPlayer

    fun togglePlayPause() = streamPlaybackSession.togglePlayPause(exoPlayer)

    /** S3217: a file has no live edge, so only a direct stream is re-prepared. */
    fun jumpToLive() {
        if (!_uiState.value.isStream) return
        streamPlaybackSession.jumpToLive(exoPlayer)
    }

    /**
     * S2166 (ADR-1): this pause stays unconditional while the audio twin of it became conditional.
     * A minimized watch app has no surface to show video on, so "video in the background" would be
     * the sound of a video and not this content at all - a different capability, not this one.
     */
    fun onHostStopped() = streamPlaybackSession.pauseForHostStop(exoPlayer)

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
            val autoHideSec = preferencesRepository.panelAutoHideSeconds.first()
                .coerceIn(1, MAX_AUTO_HIDE_SECONDS)
            val hideDelayMs = autoHideSec * MILLIS_PER_SECOND
            if (awaitPanelHide(isActive = exoPlayer.isPlaying, delayMillis = hideDelayMs)) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    /**
     * S2140: one bezel step, on the system media stream - the same mapping S1701 gave the audio player.
     *
     * Unlike [onScreenTap] this never hides an already-visible panel: it only ever reveals one that was
     * hidden, so the readout below is visible whenever a step actually changes something. Reusing
     * [showControls]/[scheduleHideControls] instead of routing through the tap toggle is what keeps that
     * one-directional guarantee - going through the toggle would hide the panel on every other step.
     */
    fun onVolumeStep(up: Boolean) {
        volumeController.onStep(up)
        showControls()
        scheduleHideControls()
    }

    fun seekTo(positionMs: Long) {
        if (_uiState.value.isStream) return
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() {
        if (_uiState.value.isStream) return
        seekTo(forwardSeekTarget(exoPlayer))
    }

    fun seekBackward() {
        if (_uiState.value.isStream) return
        seekTo(backwardSeekTarget(exoPlayer))
    }

    fun toggleScaleMode() {
        scaleModeChosen = true
        val nextMode = _uiState.updateAndGet { current ->
            val mode = if (current.scaleMode == VideoScaleMode.FIT) VideoScaleMode.CROP_PAN else VideoScaleMode.FIT
            current.copy(scaleMode = mode, panOffsetX = 0f, panOffsetY = 0f)
        }.scaleMode
        // S1948: the state moves first so the frame answers the press at once; the store catches up
        // after, and is what carries the choice to the next file or stream.
        viewModelScope.launch {
            preferencesRepository.setVideoScaleMode(nextMode)
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

    private fun publishPlaybackState() {
        val state = _uiState.value
        val payload = wearPlaybackStatePayload(
            selected = selectedMediaManager.getSelectedFileById(fileId),
            isPlaying = state.isPlaying,
            fileName = state.mediaFile?.name ?: "",
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            mediaType = "VIDEO"
        )
        val title = state.mediaFile?.name ?: ""
        viewModelScope.launch {
            publishPlaybackStateUseCase(payload)
            if (title.isNotBlank()) {
                nowPlayingRepository.setNowPlaying(title, null)
                nowPlayingRepository.setPlaying(state.isPlaying)
            }
        }
    }

    fun togglePlaybackMode() {
        if (_uiState.value.isStream) return
        val nextMode = _uiState.value.playbackMode.next()
        val isShuffle = nextMode == WearPlaybackMode.SHUFFLE
        _uiState.update { it.copy(playbackMode = nextMode, isShuffleEnabled = isShuffle) }
        viewModelScope.launch { preferencesRepository.setShuffleEnabled(isShuffle) }
    }

    fun togglePin() {
        val identity = currentFavoriteIdentity() ?: return
        if (_uiState.value.isStream) {
            viewModelScope.launch {
                val marked = toggleStreamPinUseCase.toggle(identity.filePath, _uiState.value.isPinned)
                _uiState.update { it.copy(isPinned = marked) }
            }
        }
    }

    /**
     * S2531: hands what this screen is playing to the phone, which owns the Cast session, or ends the
     * one already running - one entry, and the phone's reported state decides which of the two it is.
     */
    fun toggleCast() {
        if (castManager.castState.value.isCasting) {
            castManager.stopCasting()
            return
        }
        val file = _uiState.value.mediaFile ?: return
        castManager.castCurrentFile(file, networkSelection, WearCastMediaType.VIDEO)
    }

    fun toggleFavorite() {
        val identity = currentFavoriteIdentity() ?: return
        val file = _uiState.value.mediaFile
        val isStream = _uiState.value.isStream
        viewModelScope.launch {
            val fallbackName = identity.filePath.substringAfterLast('/').ifBlank { identity.filePath }
            val record = WearFavoriteRecord(
                sourceId = identity.sourceId,
                filePath = identity.filePath,
                displayName = file?.displayName ?: fallbackName,
                mimeType = file?.mimeType,
                itemKind = if (isStream) FAVORITE_ITEM_KIND_STREAM else null
            )
            val marked = toggleFavoriteUseCase.toggle(record, _uiState.value.isFavorite)
            _uiState.update { it.copy(isFavorite = marked) }
        }
    }

    /** Re-reads the mark for whatever is open now, so paging to another file cannot show a stale star. */
    private fun refreshFavoriteState() {
        val identity = currentFavoriteIdentity()
        if (identity == null) {
            _uiState.update { it.copy(isFavorite = false, isPinned = false) }
            return
        }
        viewModelScope.launch {
            val marked = toggleFavoriteUseCase.isFavorite(identity.sourceId, identity.filePath)
            val pinned = if (_uiState.value.isStream) {
                toggleStreamPinUseCase.isPinned(identity.filePath)
            } else {
                false
            }
            _uiState.update { it.copy(isFavorite = marked, isPinned = pinned) }
        }
    }

    /**
     * The manager answers first here; the remembered network selection only stands in when it cannot.
     * The identity rule itself is shared with the audio player.
     */
    private fun currentFavoriteIdentity() = resolveFavoriteIdentity(
        selected = selectedMediaManager.getSelectedFileById(fileId) ?: networkSelection,
        fallbackUri = _uiState.value.mediaFile?.uri?.toString()
    )

    /** S2849: asked after anything that could have started or ended a stall. */
    private fun updateStallWatch() {
        val playbackState = exoPlayer.playbackState
        stallWatchdog.onActivityChanged(
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                isEndedOrIdle = playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE
            )
        )
    }

    /**
     * S2849: pausing is what ends the retry loop, and it is also what releases the display - the
     * screen-off hold follows `playWhenReady`, so the same call that stops the refetching lets the
     * watch sleep. The mode is left as well, because a black sheet over a stopped stream hides the
     * one thing the wearer now needs to see.
     */
    private fun onPlaybackStalled() {
        Timber.w(
            "VideoPlayerViewModel: no picture for %d ms, stopping the stalled stream",
            WEAR_PLAYBACK_STALL_TIMEOUT_MS
        )
        exoPlayer.pause()
        streamPlaybackSession.stop()
        streamSessionStartRealtime = 0L
        _uiState.update {
            it.copy(
                isLoading = false,
                isPlaying = false,
                error = context.getString(R.string.wear_stream_stalled)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("VideoPlayerViewModel cleared")
        stallWatchdog.cancel()
        progressTicker.stop()
        controlsHideJob?.cancel()
        volumeController.cancel()
        streamPlaybackSession.clear()
        exoPlayer.removeListener(playerListener)
        viewModelScope.launch {
            nowPlayingRepository.clearPlayingFlag()
        }
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // instead of just stop()+clearMediaItems(); pairs with PlayerView.player = null in the screen's
        // onDispose so neither the player nor the disposed PlayerView/Context survives screen exit.
        exoPlayer.release()
    }
}
