package com.sza.fastmediasorter.ui.player

import androidx.lifecycle.Lifecycle
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.ui.player.helpers.cancelPlaybackHealthCheck
import com.sza.fastmediasorter.ui.player.helpers.releaseMediaPlayer
import com.sza.fastmediasorter.ui.player.helpers.saveCurrentPosition
import com.sza.fastmediasorter.ui.player.helpers.startPositionSaving
import com.sza.fastmediasorter.ui.player.helpers.stopPositionSaving
import kotlinx.coroutines.cancel
import timber.log.Timber

internal class VideoPlayerLifecycleHelper(
    private val manager: VideoPlayerManager,
    private val lifecycle: Lifecycle
) {
    private var wasPlayingBeforePause = false

    fun releasePlayer() {
        if (manager.exoPlayer == null && manager.activeResourceKey == null) return
        manager.flushWatchClock()
        MemoryEnduranceTracker.endScenario()
        // S0854: this is also the only teardown path when switching to a non-video file
        // (PlayerLifecycleManager.stopVideoPlayback) - without this, the save loop has no future
        // startPositionSaving() call to be replaced by and ticks forever against a released player.
        Timber.d("S0854: releasePlayer - stopping position-save loop")
        manager.stopPositionSaving()

        manager.pendingEffectsRunnable?.let { manager.effectsHandler.removeCallbacks(it) }
        manager.pendingEffectsRunnable = null

        Timber.d(
            "VideoPlayerManager: releasePlayer() - exoPlayer=${if (manager.exoPlayer != null) "NOT_NULL" else "NULL"}, " +
                "resourceKey=${manager.activeResourceKey}"
        )

        manager.exoPlayer?.let { player ->
            player.removeListener(manager.playerListener)
            player.release()
            manager.exoPlayer = null
            Timber.d("VideoPlayerManager: ExoPlayer released")
        }

        manager.releaseMediaPlayer()
        manager.cancelPlaybackHealthCheck()

        manager.activeResourceKey?.let { key ->
            ConnectionThrottleManager.deactivateVideoPlayerMode(key)
            manager.activeResourceKey = null
        }

        manager.retryRunnable?.let { manager.retryHandler.removeCallbacks(it) }
        manager.retryRunnable = null
    }

    fun onPause() {
        // Player no longer on screen: bank player time so background time is not counted.
        manager.flushWatchClock()
        wasPlayingBeforePause = manager.exoPlayer?.isPlaying == true ||
            (manager.isUsingMediaPlayer && manager.mediaPlayer?.isPlaying == true)
        manager.pause()
        manager.stopPositionSaving()
    }

    fun onResume() {
        // Resume player-time accounting if a media player is still loaded after returning to foreground.
        manager.startWatchClock()
        if (wasPlayingBeforePause && (manager.exoPlayer != null || (manager.isUsingMediaPlayer && manager.mediaPlayer != null))) {
            manager.play()
            manager.startPositionSaving()
            Timber.d("VideoPlayerManager: Resumed playback after lifecycle pause")
        }
        wasPlayingBeforePause = false
    }

    fun onDestroy() {
        manager.flushWatchClock()
        manager.saveCurrentPosition()
        manager.stopPositionSaving()

        val playerToRelease = manager.exoPlayer
        manager.exoPlayer = null
        val mediaPlayerToRelease = manager.mediaPlayer
        manager.mediaPlayer = null
        manager.isUsingMediaPlayer = false

        try {
            playerToRelease?.removeListener(manager.playerListener)
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to remove listener")
        }
        try {
            manager.currentPlayerView?.player = null
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to detach PlayerView")
        }

        manager.cancelPlaybackHealthCheck()

        manager.activeResourceKey?.let { key ->
            ConnectionThrottleManager.deactivateVideoPlayerMode(key)
            manager.activeResourceKey = null
        }

        manager.retryRunnable?.let { manager.retryHandler.removeCallbacks(it) }
        manager.retryRunnable = null

        try {
            playerToRelease?.release()
        } catch (e: Exception) {
            Timber.e(e, "VideoPlayerManager: Error releasing ExoPlayer")
        }

        Thread {
            try {
                mediaPlayerToRelease?.apply {
                    try {
                        if (isPlaying) stop()
                    } catch (_: Exception) {
                    }
                    release()
                }
            } catch (e: Exception) {
                Timber.e(e, "VideoPlayerManager: Error releasing MediaPlayer")
            }
        }.start()

        manager.videoColorProcessor.release()
        manager.managerScope.cancel()
        lifecycle.removeObserver(manager)
    }
}