package com.sza.fastmediasorter.wear.ui.player.audio

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.data.wear.WatchPlaybackCommandEvents
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.wear.domain.model.favoriteSourceId
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.StreamNetworkHold
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.EvaluateStreamStartUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveAlbumArtUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionManager
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

/** S1683: named because the bezel now reaches the same step the buttons do. */
private const val SEEK_STEP_MS = 10_000L

/** S1701: how long the volume readout stays after the last bezel step. */
private const val VOLUME_VISIBLE_MS = 1_500L

/**
 * ViewModel for the audio player screen.
 * Manages ExoPlayer instance and playback state.
 */
// S1701: the tenth injected dependency crosses detekt's threshold. Bundling collaborators behind a
// holder would hide the graph Hilt actually builds, which is the same trade PlayerViewModel took.
@Suppress("LongParameterList")
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val downloadNetworkFile: DownloadNetworkFileUseCase,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    @ApplicationContext private val context: Context,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val resolveAlbumArt: ResolveAlbumArtUseCase,
    private val preferencesRepository: WearPreferencesRepository,
    private val evaluateStreamStart: EvaluateStreamStartUseCase,
    private val streamNetworkHold: StreamNetworkHold,
    private val networkChannelMonitor: WearNetworkChannelMonitor,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private var progressUpdateJob: Job? = null

    /** Cancelled and restarted on every bezel step; dies with the ViewModel. */
    private var volumeHideJob: Job? = null

    /**
     * S1683: the selection this screen was opened with, kept only when it is a network one, so paging
     * re-enters the download path with the same source id instead of a bare uri.
     */
    private var networkSelection: SelectedMedia? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
                streamPlaybackSession.withWideChannel()
            } else {
                stopProgressUpdates()
            }
            publishPlaybackState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            if (title != null || artist != null) {
                Timber.d("S1866: metadata updated title=%s artist=%s", title, artist)
                _uiState.update { state ->
                    state.copy(
                        trackTitle = title ?: state.trackTitle,
                        artistName = artist ?: state.artistName
                    )
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            durationMs = exoPlayer.duration.coerceAtLeast(0)
                        )
                    }
                    publishPlaybackState()
                }
                Player.STATE_ENDED -> {
                    // S1837: a finished track advances through the set's own rule, so auto-advance
                    // and the NEXT button can never disagree about what follows. That rule wraps
                    // past the last file (S1683 section 6.5, owner ruling 2026-08-15), which is also
                    // what makes the shuffle order of S1701 audible without pressing anything.
                    // A set of one is excluded deliberately: it has nowhere to advance to, and
                    // restarting the only track is exactly the endless loop S0902 removed below.
                    val setSize = playbackSetManager.currentSet.value?.files?.size ?: 0
                    if (setSize > 1) {
                        Timber.d("S1837: track ended, advancing within the set of $setSize")
                        skipToNext()
                    } else {
                        streamPlaybackSession.stop()
                        _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                        // S0902: pause before seeking - playWhenReady stays true otherwise and the
                        // track auto-restarts from 0, looping indefinitely (mirrors VideoPlayerViewModel).
                        exoPlayer.pause()
                        exoPlayer.seekTo(0)
                        publishPlaybackState()
                    }
                }
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
                Player.STATE_IDLE -> streamPlaybackSession.stop()
                else -> {}
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            streamPlaybackSession.stop()
        }
    }

    private val streamPlaybackSession = StreamPlaybackSessionManager(
        scope = viewModelScope,
        networkHold = streamNetworkHold,
        channelMonitor = networkChannelMonitor,
        evaluateStreamStart = evaluateStreamStart,
        onChannelReason = { reason -> _uiState.update { it.copy(channelReason = reason) } }
    )

    init {
        Timber.d("AudioPlayerViewModel initialized with fileId: $fileId")
        exoPlayer.addListener(playerListener)

        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            // S1683: navigation carries the id alone, so the shared set has to be pointed at it here
            // before any paging call can answer - same as the image viewer does.
            playbackSetManager.moveTo(fileId)
            syncSetPosition()
            loadAudioFile()
        }

        // S1701: the stored flag is the single source of truth - it feeds both the control that
        // renders it and the set that resolves the successor, so the two can never disagree about
        // which order is active.
        viewModelScope.launch {
            preferencesRepository.isShuffleEnabled.collect { enabled ->
                playbackSetManager.shuffleEnabled = enabled
                _uiState.update { it.copy(isShuffleEnabled = enabled) }
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

    private fun loadAudioFile() {
        Timber.d("Loading audio file with fileId: $fileId")
        loadMediaFile()
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
        Timber.d("S1683: paging to ${file.name} art=${file.albumArt != null}")
        streamPlaybackSession.clear()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.update {
            it.withMediaFile(file).copy(currentPositionMs = 0, durationMs = 0, error = null)
        }
        fetchRemoteAlbumArt(file)
        val selection = networkSelection
        if (selection != null) {
            viewModelScope.launch {
                loadNetworkAudio(selection.copy(file = file, streamUri = file.uri.toString()))
            }
        } else {
            playLocalFile(file)
        }
        syncSetPosition()
    }

    /**
     * S1683: the cover travels with the file, so both fields are written in one place. `albumArtUrl`
     * was declared from the start and never assigned anywhere, which is what happens when the file
     * and its cover are published from separate call sites.
     */
    private fun AudioPlayerUiState.withMediaFile(file: WearMediaFile): AudioPlayerUiState =
        copy(
            mediaFile = file,
            albumArtUrl = file.albumArt?.toString(),
            trackTitle = file.title?.takeIf { it.isNotBlank() },
            artistName = file.artist?.takeIf { it.isNotBlank() }
        )

    /**
     * S1689: the cover in the file wins; the network is asked only when there is none and the user
     * asked for it. The setting has existed - and been offered on two screens - since before
     * anything read it, so this is the call that makes the switch mean something.
     */
    private fun fetchRemoteAlbumArt(file: WearMediaFile) {
        viewModelScope.launch {
            val url = resolveAlbumArt(file)
            if (url != null) {
                Timber.d("S1689: remote cover found for ${file.artist} - ${file.album}")
                _uiState.update { state ->
                    // The track may have paged on while the lookup was in flight; a late answer
                    // must not paint the previous track's cover over the current one. Until it
                    // arrives the waves-and-particles background stands in, which is the correct
                    // no-cover state.
                    if (state.mediaFile?.id == file.id) state.copy(albumArtUrl = url) else state
                }
            }
        }
    }

    /** S1683: keeps the position marker in step with the set on first open and on every page. */
    private fun syncSetPosition() {
        val set = playbackSetManager.currentSet.value ?: return
        _uiState.update { it.copy(setIndex = set.index, setSize = set.files.size) }
    }

    private fun playLocalFile(file: WearMediaFile) {
        exoPlayer.setMediaItem(MediaItem.fromUri(file.uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun loadMediaFile() {
        viewModelScope.launch {
            // First, check if we have a selected file from SelectedMediaManager (network source)
            val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)

            if (selectedMedia != null && selectedMedia.isNetworkSource) {
                // S1684: publish the file into ui state before downloading. The screen renders
                // `mediaFile?.name ?: "Unknown"`, and this branch used to pass the name onwards
                // without ever storing it, so every network track was titled "Unknown" while a
                // local one showed its name. SelectedMediaManager carries this object precisely
                // because MediaStore cannot answer for network sources.
                _uiState.update { it.withMediaFile(selectedMedia.file) }
                // S1683: remembered so paging can re-enter the download path with the same source id.
                networkSelection = selectedMedia
                Timber.d("Loading network audio: ${selectedMedia.file.name}")
                loadNetworkAudio(selectedMedia)
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.MUSIC)
                if (file != null) {
                    _uiState.update { it.withMediaFile(file) }
                    fetchRemoteAlbumArt(file)
                    checkFavoriteState(sourceId = "local", filePath = file.uri.toString())
                    playLocalFile(file)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "File not found") }
                }
            }
        }
    }

    /**
     * Play a network audio file from its cached copy - ExoPlayer cannot read a remote InputStream
     * directly. S1687: which protocol that download speaks is the use case's decision, not this
     * screen's; this view model used to call SMB unconditionally and broke every other source.
     */
    private suspend fun loadNetworkAudio(selected: SelectedMedia) {
        if (selected.isDirectStream) {
            Timber.d("S1708: direct audio stream playback uri=${selected.streamUri}")
            val mediaKind = ClassifyWearStreamMediaKindUseCase.AUDIO
            if (!streamPlaybackSession.prepare(mediaKind)) {
                _uiState.update { it.copy(isLoading = false) }
                return
            }
            _uiState.update { it.copy(isLoading = true) }
            val mediaItem = MediaItem.fromUri(Uri.parse(selected.streamUri))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            _uiState.update { it.copy(isLoading = false) }
            exoPlayer.playWhenReady = true
            return
        }
        Timber.d("S1687: network audio entry sourceId=${selected.sourceId} uri=${selected.streamUri}")
        _uiState.update { it.copy(isLoading = true) }

        downloadNetworkFile(selected, DownloadNetworkFileUseCase.Kind.AUDIO).fold(
            onSuccess = { cachedFile ->
                val mediaItem = MediaItem.fromUri(Uri.fromFile(cachedFile))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                _uiState.update { it.copy(isLoading = false) }
                exoPlayer.playWhenReady = true
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load: ${e.message}")
                }
            }
        )
    }

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
     * S1701: writes the new order to settings and lets the collector above publish it back, so the
     * screen never holds a copy that a failed write could leave stale.
     */
    fun toggleShuffle() {
        Timber.d("S1701: shuffle toggled")
        val enabled = !_uiState.value.isShuffleEnabled
        viewModelScope.launch { preferencesRepository.setShuffleEnabled(enabled) }
    }

    /**
     * S1683: blanks the screen without touching playback, and any touch on the black screen calls this
     * again. The flag lives here rather than in the composition so it survives a recomposition, and it
     * dies with this view model when the player is left - a screen reopened is never already dark.
     */
    fun toggleDimmed() {
        // The twice-a-second position update deliberately keeps running while the screen is dark.
        // Stopping it was tried and measured on the watch: 679 ticks per ten seconds against 672 with
        // it running, so the recomposition it drives is not what the dark screen costs, and the extra
        // stop/restart/refresh path bought nothing. What the screen does cost is tracked in S1709.
        Timber.d("S1683: screen-off toggled to ${!_uiState.value.isDimmed}")
        _uiState.update { it.copy(isDimmed = !it.isDimmed) }
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

    /**
     * S1701: one bezel step, on the system media stream.
     *
     * ADR-1 moved the bezel from seeking to volume, which is the Wear OS media convention; the progress
     * bar added in phase 02 is how this screen seeks now. The level is read back from the system after
     * the adjustment instead of being tracked here, so a change made by the watch's own volume UI is
     * reflected the next time the bezel moves rather than fighting a private counter.
     */
    fun onVolumeStep(up: Boolean) {
        Timber.d("S1701: bezel volume step up=%b", up)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
        _uiState.update {
            it.copy(
                volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                isVolumeVisible = true,
            )
        }
        hideVolumeAfterDelay()
    }

    /**
     * Hides the readout a moment after the last step, and nothing animates while it is hidden.
     *
     * Restarted on every step so a continuous turn keeps it up; strategic 3.2 forbids adding to what the
     * wave drawing already costs, so it must not stay on screen once the user has stopped.
     */
    private fun hideVolumeAfterDelay() {
        volumeHideJob?.cancel()
        volumeHideJob = viewModelScope.launch {
            delay(VOLUME_VISIBLE_MS)
            _uiState.update { it.copy(isVolumeVisible = false) }
        }
    }

    fun seekTo(positionMs: Long) {
        Timber.d("S1701: position bar seek to %d ms", positionMs)
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() {
        val target = exoPlayer.currentPosition + SEEK_STEP_MS
        // ExoPlayer reports C.TIME_UNSET, a large negative, while the duration is still unknown -
        // clamping to it would send playback backwards past the start. Reachable here since S1683,
        // because the bezel can now reach this action within the first moments of a stream opening.
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
            mediaType = "AUDIO"
        )
        viewModelScope.launch { publishPlaybackStateUseCase(payload) }
    }

    fun toggleFavorite() {
        val selected = selectedMediaManager.getSelectedFileById(fileId)
        // S1846: one rule for the source id, shared with the image viewer - the host name this used to
        // write was not resolvable back to a source, so a favourite could not be reopened from it.
        val sourceId = favoriteSourceId(selected?.isNetworkSource == true, selected?.sourceId)
        val filePath = selected?.streamUri ?: _uiState.value.mediaFile?.uri?.toString() ?: return
        viewModelScope.launch {
            _isFavorite.value = toggleFavoriteUseCase.toggle(sourceId, filePath, _isFavorite.value)
        }
    }

    private fun checkFavoriteState(sourceId: String, filePath: String) {
        viewModelScope.launch {
            _isFavorite.value = toggleFavoriteUseCase.isFavorite(sourceId, filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("AudioPlayerViewModel cleared")
        stopProgressUpdates()
        streamPlaybackSession.clear()
        exoPlayer.removeListener(playerListener)
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // (HandlerThread, AudioTrack/audio-focus, codecs) instead of just stop()+clearMediaItems().
        exoPlayer.release()
    }
}
