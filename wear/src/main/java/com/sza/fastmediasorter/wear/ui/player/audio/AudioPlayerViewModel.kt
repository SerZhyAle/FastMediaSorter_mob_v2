package com.sza.fastmediasorter.wear.ui.player.audio

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
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.domain.usecase.SendFavoritesDeltaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * ViewModel for the audio player screen.
 * Manages ExoPlayer instance and playback state.
 */
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val downloadNetworkFile: DownloadNetworkFileUseCase,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    private val favoritesRepository: WearFavoritesRepository,
    private val sendFavoritesDeltaUseCase: SendFavoritesDeltaUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L

    private var progressUpdateJob: Job? = null

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
            } else {
                stopProgressUpdates()
            }
            publishPlaybackState()
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
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                    // S0902: pause before seeking - playWhenReady stays true otherwise and the
                    // track auto-restarts from 0, looping indefinitely (mirrors VideoPlayerViewModel).
                    exoPlayer.pause()
                    exoPlayer.seekTo(0)
                    publishPlaybackState()
                }
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
                else -> {}
            }
        }
    }

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

        // Subscribe to remote playback commands from phone
        viewModelScope.launch {
            WatchPlaybackCommandEvents.commandFlow.collect { command ->
                when (command) {
                    WearPlaybackCommand.PLAY_PAUSE -> togglePlayPause()
                    WearPlaybackCommand.NEXT       -> exoPlayer.seekToNextMediaItem()
                    WearPlaybackCommand.PREVIOUS   -> exoPlayer.seekToPreviousMediaItem()
                    WearPlaybackCommand.STOP       -> exoPlayer.stop()
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
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.update {
            it.copy(mediaFile = file, currentPositionMs = 0, durationMs = 0, error = null)
        }
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
                _uiState.update { it.copy(mediaFile = selectedMedia.file) }
                // S1683: remembered so paging can re-enter the download path with the same source id.
                networkSelection = selectedMedia
                Timber.d("Loading network audio: ${selectedMedia.file.name}")
                loadNetworkAudio(selectedMedia)
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.MUSIC)
                if (file != null) {
                    _uiState.update { it.copy(mediaFile = file) }
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
        } else {
            exoPlayer.play()
        }
    }

    /**
     * S0902: called from the screen's onStop lifecycle effect - without this, playback
     * keeps running while the host activity is stopped (screen off / app backgrounded);
     * onCleared was the only prior teardown edge.
     */
    fun onHostStopped() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() {
        val newPosition = (exoPlayer.currentPosition + 10_000).coerceAtMost(exoPlayer.duration)
        seekTo(newPosition)
    }

    fun seekBackward() {
        val newPosition = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0)
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
        val sourceId = if (selected?.isNetworkSource == true) selected.file.uri.host ?: "network" else "local"
        val filePath = selected?.streamUri ?: _uiState.value.mediaFile?.uri?.toString() ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                favoritesRepository.removeFavorite(sourceId, filePath)
            } else {
                favoritesRepository.addFavorite(sourceId, filePath)
            }
            _isFavorite.value = !_isFavorite.value
            sendFavoritesDeltaUseCase()
        }
    }

    private fun checkFavoriteState(sourceId: String, filePath: String) {
        viewModelScope.launch {
            _isFavorite.value = favoritesRepository.isFavorite(sourceId, filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("AudioPlayerViewModel cleared")
        stopProgressUpdates()
        exoPlayer.removeListener(playerListener)
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // (HandlerThread, AudioTrack/audio-focus, codecs) instead of just stop()+clearMediaItems().
        exoPlayer.release()
    }
}
