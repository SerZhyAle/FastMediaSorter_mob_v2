package com.sza.fastmediasorter.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Now Playing bottom sheet and mini bar.
 *
 * Owns a MediaController connection to AudioPlaybackService independent of
 * AudioServiceController (which is owned by PlayerActivity). This allows the
 * BottomSheetDialogFragment to have its own scoped connection that is released
 * when the sheet is dismissed.
 *
 * Position is polled at 500 ms intervals only while isPlaying == true.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class NowPlayingState(
        val isPlaying: Boolean = false,
        val title: String = "",
        val artist: String? = null,
        val albumArtUri: Uri? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val queueItems: List<QueueItem> = emptyList(),
        val currentQueueIndex: Int = 0,
        val serviceRunning: Boolean = false
    )

    data class QueueItem(
        val index: Int,
        val title: String,
        val artist: String?
    )

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionPollJob: Job? = null

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.d("NowPlayingViewModel: onIsPlayingChanged isPlaying=$isPlaying")
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startPositionPoll() else stopPositionPoll()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val running = playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
            Timber.d("NowPlayingViewModel: onPlaybackStateChanged state=$playbackState serviceRunning=$running")
            _state.value = _state.value.copy(serviceRunning = running)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val meta = mediaItem?.mediaMetadata
            val index = controller.currentMediaItemIndex
            Timber.d("NowPlayingViewModel: onMediaItemTransition index=$index title=${meta?.title}")
            _state.value = _state.value.copy(
                title = meta?.title?.toString() ?: "",
                artist = meta?.artist?.toString(),
                albumArtUri = meta?.artworkUri,
                currentQueueIndex = index,
                durationMs = controller.duration.coerceAtLeast(0L)
            )
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueueItems()
        }
    }

    /** Connect to AudioPlaybackService and start observing state. */
    fun connect() {
        if (mediaController?.isConnected == true) return

        Timber.d("NowPlayingViewModel: connecting to AudioPlaybackService")
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)

                val meta = controller.mediaMetadata
                val isPlaying = controller.isPlaying
                val state = NowPlayingState(
                    isPlaying = isPlaying,
                    title = meta.title?.toString() ?: "",
                    artist = meta.artist?.toString(),
                    albumArtUri = meta.artworkUri,
                    positionMs = controller.currentPosition.coerceAtLeast(0L),
                    durationMs = controller.duration.coerceAtLeast(0L),
                    currentQueueIndex = controller.currentMediaItemIndex,
                    serviceRunning = AudioPlaybackService.isRunning
                )
                _state.value = state
                refreshQueueItems()
                if (isPlaying) startPositionPoll()
                Timber.d("NowPlayingViewModel: connected, isPlaying=$isPlaying")
            } catch (e: Exception) {
                Timber.e(e, "NowPlayingViewModel: failed to connect")
                _state.value = _state.value.copy(serviceRunning = false)
            }
        }, MoreExecutors.directExecutor())
    }

    /** Disconnect and release MediaController. */
    fun disconnect() {
        Timber.d("NowPlayingViewModel: disconnect")
        stopPositionPoll()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun togglePlayPause() {
        val c = mediaController ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekToNext() {
        mediaController?.seekToNext()
    }

    fun seekToPrevious() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun jumpToQueueItem(index: Int) {
        mediaController?.seekToDefaultPosition(index)
    }

    private fun startPositionPoll() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (isActive) {
                val c = mediaController
                if (c != null && c.isConnected) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = c.duration.coerceAtLeast(0L)
                    )
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionPoll() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    private fun refreshQueueItems() {
        val c = mediaController ?: return
        val count = c.mediaItemCount
        val items = (0 until count).map { i ->
            val meta = c.getMediaItemAt(i).mediaMetadata
            QueueItem(
                index = i,
                title = meta.title?.toString() ?: "",
                artist = meta.artist?.toString()
            )
        }
        _state.value = _state.value.copy(queueItems = items)
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }
}
