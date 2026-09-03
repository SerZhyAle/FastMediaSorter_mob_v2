package com.sza.fastmediasorter.wear.ui.player.audio

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
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
import com.sza.fastmediasorter.wear.domain.playback.HostStopAction
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundPlaybackPolicy
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSession
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSessionState
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveAlbumArtUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ToggleFavoriteUseCase
import com.sza.fastmediasorter.wear.service.WearPlaybackService
import com.sza.fastmediasorter.wear.ui.player.common.PlaybackProgressTicker
import com.sza.fastmediasorter.wear.ui.player.common.PlayerVolumeController
import com.sza.fastmediasorter.wear.ui.player.common.backwardSeekTarget
import com.sza.fastmediasorter.wear.ui.player.common.forwardSeekTarget
import com.sza.fastmediasorter.wear.ui.player.common.resolveFavoriteIdentity
import com.sza.fastmediasorter.wear.ui.player.common.togglePlayPause
import com.sza.fastmediasorter.wear.ui.player.common.wearPlaybackStatePayload
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
    private val streamPlaybackSessionFactory: StreamPlaybackSessionFactory,
    private val nowPlayingRepository: WearNowPlayingRepository,
    private val backgroundSessionState: WearBackgroundSessionState,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private val progressTicker = PlaybackProgressTicker(viewModelScope, exoPlayer) { position ->
        _uiState.update { it.copy(currentPositionMs = position) }
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
     * S2166: read once into a field because [onHostStopped] runs on the lifecycle edge and cannot
     * suspend to ask the store what the setting says at the moment the host is already stopping.
     */
    private var backgroundPlaybackEnabled: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                progressTicker.start()
                streamPlaybackSession.withWideChannel()
            } else {
                progressTicker.stop()
            }
            publishPlaybackState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            if (title != null || artist != null) {
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

    private val streamPlaybackSession = streamPlaybackSessionFactory.create(
        scope = viewModelScope,
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
            attachToBackgroundSessionOrLoad()
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

        viewModelScope.launch {
            preferencesRepository.backgroundPlaybackEnabled.collect { backgroundPlaybackEnabled = it }
        }

        // Subscribe to remote playback commands from phone
        viewModelScope.launch {
            WatchPlaybackCommandEvents.commandFlow.collect { command ->
                when (command) {
                    WearPlaybackCommand.PLAY_PAUSE -> togglePlayPause()
                    WearPlaybackCommand.NEXT -> exoPlayer.seekToNextMediaItem()
                    WearPlaybackCommand.PREVIOUS -> exoPlayer.seekToPreviousMediaItem()
                    WearPlaybackCommand.STOP -> {
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
     * S2166 (strategic goal 5): the screen takes the running session back instead of preparing a
     * second player on the same track, which is what the owner would hear as the music restarting.
     *
     * The service is stopped either way. A session on another file is still somebody's sound coming
     * out of the watch, and opening a track while a different one plays in the background is the one
     * case where two players really would be audible at once.
     */
    private fun attachToBackgroundSessionOrLoad() {
        val background = backgroundSessionState.session.value
        if (background == null) {
            loadAudioFile()
            return
        }
        context.startService(WearPlaybackService.stopIntent(context))
        backgroundSessionState.clear()
        if (background.fileId != fileId) {
            loadAudioFile()
            return
        }
        resumeHandedBackSession(background)
    }

    /**
     * The metadata still comes from the repository, because the session carries what was playing and
     * not what the screen has to draw around it - title, cover and favourite mark are the screen's.
     */
    private fun resumeHandedBackSession(background: WearBackgroundSession) {
        viewModelScope.launch {
            val selected = selectedMediaManager.getSelectedFileById(fileId)
            if (selected != null) {
                _uiState.update { it.withMediaFile(selected.file) }
                if (selected.isNetworkSource) {
                    networkSelection = selected
                }
                fetchRemoteAlbumArt(selected.file)
            } else {
                mediaRepository.getMediaFileById(fileId, MediaType.MUSIC)?.let { file ->
                    _uiState.update { it.withMediaFile(file) }
                    fetchRemoteAlbumArt(file)
                }
            }
            checkFavoriteState()
            background.streamMediaKind?.let(streamPlaybackSession::prepare)
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(background.mediaUri)))
            exoPlayer.prepare()
            exoPlayer.seekTo(background.positionMs)
            exoPlayer.playWhenReady = background.isPlaying
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
            it.withMediaFile(file).copy(currentPositionMs = 0, durationMs = 0, error = null)
        }
        fetchRemoteAlbumArt(file)
        val selection = networkSelection
        if (selection != null) {
            // S2039: the paged-to file becomes the remembered selection, not just the argument of one
            // call. The mark is resolved from that selection, so leaving it on the previous file showed
            // the previous station's star over the current one.
            val paged = selection.copy(file = file, streamUri = file.uri.toString())
            networkSelection = paged
            checkFavoriteState()
            viewModelScope.launch { loadNetworkAudio(paged) }
        } else {
            checkFavoriteState()
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
            // S1884: check if SelectedMediaManager holds the file (network source or phone-delivered file)
            val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)

            if (selectedMedia != null) {
                _uiState.update { it.withMediaFile(selectedMedia.file) }
                checkFavoriteState()
                if (selectedMedia.isNetworkSource) {
                    // S1683: remembered so paging can re-enter the download path with the same source id.
                    networkSelection = selectedMedia
                    Timber.d("Loading network audio: ${selectedMedia.file.name}")
                    loadNetworkAudio(selectedMedia)
                } else {
                    Timber.d("Loading local audio from SelectedMediaManager: ${selectedMedia.file.name}")
                    fetchRemoteAlbumArt(selectedMedia.file)
                    playLocalFile(selectedMedia.file)
                }
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.MUSIC)
                if (file != null) {
                    _uiState.update { it.withMediaFile(file) }
                    fetchRemoteAlbumArt(file)
                    checkFavoriteState()
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

    fun togglePlayPause() = streamPlaybackSession.togglePlayPause(exoPlayer)

    /**
     * S1701: writes the new order to settings and lets the collector above publish it back, so the
     * screen never holds a copy that a failed write could leave stale.
     */
    fun toggleShuffle() {
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
        _uiState.update { it.copy(isDimmed = !it.isDimmed) }
    }

    /**
     * S0902 made this pause unconditional because playback belonged to the screen, and without it a
     * backgrounded or screen-off host kept playing with `onCleared` as the only teardown edge. S2166
     * makes it conditional rather than removing it: the sound may now outlive the screen, but only
     * when a foreground service takes ownership of it, which is the part S0902 had no way to do.
     * With the setting off, or on a player that is not playing, S0902's behaviour stands exactly.
     */
    fun onHostStopped() {
        val action = WearBackgroundPlaybackPolicy.onHostStopped(
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            isAudioContent = true,
            isPlaying = exoPlayer.isPlaying
        )
        when (action) {
            HostStopAction.Pause -> {
                exoPlayer.pause()
                streamPlaybackSession.stop()
            }
            HostStopAction.HandOff -> handOffToPlaybackService()
        }
    }

    /**
     * Strategic §7 names two owners of one playing player as the defect to design against, so the
     * screen's player goes quiet as the service's starts - the handover is an exchange, not a second
     * player on the same track. The item is taken from the player rather than from the ui state
     * because it is already resolved there: a cached copy for a downloaded network file, the stream
     * uri for a direct one, and the MediaStore uri for a local file.
     */
    private fun handOffToPlaybackService() {
        val uri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        val streamMediaKind = ClassifyWearStreamMediaKindUseCase.AUDIO
            .takeIf { networkSelection?.isDirectStream == true }
        val intent = WearPlaybackService.startIntent(
            context = context,
            fileId = fileId,
            mediaUri = uri,
            positionMs = exoPlayer.currentPosition,
            streamMediaKind = streamMediaKind
        )
        // From API 31 a foreground service started by an app that has already left the foreground is
        // refused with an IllegalStateException, and ON_STOP is exactly that edge. The refusal is
        // recoverable and the recovery is the behaviour this ticket replaced: pause, as S0902 did,
        // rather than let the exception reach the lifecycle callback and take the app down.
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Background playback refused at host stop; pausing instead")
            exoPlayer.pause()
            streamPlaybackSession.stop()
            return
        }
        exoPlayer.pause()
        // stop(), not clear(): the kind stays so a resume on this screen still knows what it is
        // holding the channel for. The service takes its own hold, so releasing here keeps the
        // reference count at one across the hand-off rather than letting the two sides overlap.
        streamPlaybackSession.stop()
    }

    /** The progress bar added in S1701 phase 02 is how this screen seeks; the bezel carries volume. */
    fun onVolumeStep(up: Boolean) = volumeController.onStep(up)

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() = seekTo(forwardSeekTarget(exoPlayer))

    fun seekBackward() = seekTo(backwardSeekTarget(exoPlayer))

    private fun publishPlaybackState() {
        val state = _uiState.value
        val payload = wearPlaybackStatePayload(
            selected = selectedMediaManager.getSelectedFileById(fileId),
            isPlaying = state.isPlaying,
            fileName = state.mediaFile?.name ?: "",
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            mediaType = "AUDIO"
        )
        val title = state.trackTitle?.takeIf { it.isNotBlank() } ?: state.mediaFile?.name ?: ""
        val subtitle = state.artistName?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            publishPlaybackStateUseCase(payload)
            if (title.isNotBlank()) {
                nowPlayingRepository.setNowPlaying(title, subtitle)
                nowPlayingRepository.setPlaying(state.isPlaying)
            }
        }
    }

    fun toggleFavorite() {
        val identity = currentFavoriteIdentity() ?: return
        viewModelScope.launch {
            _isFavorite.value =
                toggleFavoriteUseCase.toggle(identity.sourceId, identity.filePath, _isFavorite.value)
        }
    }

    /** Re-reads the mark for whatever is open now, so paging to another track cannot show a stale star. */
    private fun checkFavoriteState() {
        val identity = currentFavoriteIdentity()
        if (identity == null) {
            _isFavorite.value = false
            return
        }
        viewModelScope.launch {
            _isFavorite.value = toggleFavoriteUseCase.isFavorite(identity.sourceId, identity.filePath)
        }
    }

    /**
     * The remembered selection wins here: it tracks paging, while the manager still answers with
     * whatever file this screen was opened on. The identity rule itself is shared with the video player.
     */
    private fun currentFavoriteIdentity() = resolveFavoriteIdentity(
        selected = networkSelection ?: selectedMediaManager.getSelectedFileById(fileId),
        fallbackUri = _uiState.value.mediaFile?.uri?.toString()
    )

    override fun onCleared() {
        super.onCleared()
        Timber.d("AudioPlayerViewModel cleared")
        progressTicker.stop()
        volumeController.cancel()
        streamPlaybackSession.clear()
        exoPlayer.removeListener(playerListener)
        viewModelScope.launch {
            nowPlayingRepository.clearPlayingFlag()
        }
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // (HandlerThread, AudioTrack/audio-focus, codecs) instead of just stop()+clearMediaItems().
        exoPlayer.release()
    }
}
