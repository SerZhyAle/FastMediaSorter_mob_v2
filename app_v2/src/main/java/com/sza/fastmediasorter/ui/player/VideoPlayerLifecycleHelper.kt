package com.sza.fastmediasorter.ui.player

import android.os.Build
import androidx.lifecycle.Lifecycle
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.ui.player.helpers.cancelPlaybackHealthCheck
import com.sza.fastmediasorter.ui.player.helpers.cancelStreamStallWatchdog
import com.sza.fastmediasorter.ui.player.helpers.releaseMediaPlayer
import com.sza.fastmediasorter.ui.player.helpers.releaseStreamDiagnostics
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

    // S0893: remembers whether onStop() tore down an actively-loaded player, so onStart() knows to
    // recreate it - release-on-onStop/recreate-on-onStart is the official Media3 guidance for API24+
    // multi-window (developer.android.com/media/media3/exoplayer/lifecycle).
    private var wasLoadedBeforeStop = false
    private var wasPlayingBeforeStop = false

    fun releasePlayer() {
        if (manager.exoPlayer == null && manager.activeResourceKey == null) return
        manager.flushWatchClock()
        MemoryEnduranceTracker.endScenario()
        // S0854: this is also the only teardown path when switching to a non-video file
        // (PlayerLifecycleManager.stopVideoPlayback) - without this, the save loop has no future
        // startPositionSaving() call to be replaced by and ticks forever against a released player.
        manager.stopPositionSaving()

        manager.pendingEffectsRunnable?.let { manager.effectsHandler.removeCallbacks(it) }
        manager.pendingEffectsRunnable = null

        Timber.d(
            "VideoPlayerManager: releasePlayer() - exoPlayer=${if (manager.exoPlayer != null) "NOT_NULL" else "NULL"}, " +
                "resourceKey=${manager.activeResourceKey}"
        )

        manager.exoPlayer?.let { player ->
            // S0893: consolidated so every listener the active session may have added (playerListener
            // is always present; activeExtraPlayerListener is PauseAwareLoadControl or the per-stream
            // listener, whichever this session's protocol helper attached) is removed from one place.
            listOfNotNull(manager.playerListener, manager.activeExtraPlayerListener).forEach(player::removeListener)
            manager.activeExtraPlayerListener = null
            // S1127: detach the stream diagnostics listener + log the session summary (the helper co-locates
            // removeAnalyticsListener with its add for the per-file listener-symmetry gate).
            manager.releaseStreamDiagnostics(player)
            // S0893: Media3 1.2.1 - release() can hang the main thread when a setVideoEffects() GL
            // pipeline is still active (androidx/media #1139, #2098; same class of bug worked around
            // in StandaloneViewManager.releaseVideoPlayer(), S0859). Drain effects and detach the
            // surface while EGL is still valid, then release.
            player.setVideoEffects(emptyList())
            manager.currentPlayerView?.player = null
            player.release()
            manager.exoPlayer = null
            Timber.d("VideoPlayerManager: ExoPlayer released")
        }

        manager.releaseMediaPlayer()
        manager.cancelPlaybackHealthCheck()
        // S0936: the watchdog is a de-facto listener side-channel on activeExtraPlayerListener's
        // player - cancel it on the same teardown edge that listener is removed above.
        manager.cancelStreamStallWatchdog()

        manager.activeResourceKey?.let { key ->
            ConnectionThrottleManager.deactivateVideoPlayerMode(key)
            manager.activeResourceKey = null
        }

        manager.retryRunnable?.let { manager.retryHandler.removeCallbacks(it) }
        manager.retryRunnable = null
    }

    fun onPause() {
        // S0942: entering PiP dispatches ON_PAUSE to this observer while the media must keep
        // playing inside the PiP window. The host activity's onPause() already gates its own
        // togglePause() on isInPictureInPictureMode, but this observer pauses the player on a
        // separate path - so streams (and local video) hosted by PlayerActivity froze on PiP
        // entry and never resumed (onResume is not dispatched while in PiP). Skip the lifecycle
        // pause while the host is in PiP; a real background transition later fires onStop(),
        // which releases the player. The standalone hosts already apply the same guard.
        if (isHostInPictureInPictureMode()) {
            return
        }
        // Player no longer on screen: bank player time so background time is not counted.
        manager.flushWatchClock()
        wasPlayingBeforePause = manager.exoPlayer?.isPlaying == true ||
            (manager.isUsingMediaPlayer && manager.mediaPlayer?.isPlaying == true)
        manager.pause()
        manager.stopPositionSaving()
    }

    // S0942: the host activity backing this player (PlayerActivity / standalone hosts) is passed in
    // as [VideoPlayerManager.context]. isInPictureInPictureMode exists since API 24, matching the
    // onStop() guard below.
    private fun isHostInPictureInPictureMode(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            (manager.context as? android.app.Activity)?.isInPictureInPictureMode == true

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

    // S0893: API24+ release edge - a prepared player (codec + buffered media) must not be retained
    // for the unbounded duration the host sits stopped in background (CODE_AUDIT_PROTOCOL contract
    // item 2). Below API 24 this is a no-op - onPause()/onResume() above already cover the release
    // per official pre-multi-window guidance, and the legacy flavor's narrow API23 sliver keeps
    // today's behavior unchanged.
    fun onStop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val hasActivePlayer = manager.exoPlayer != null ||
            (manager.isUsingMediaPlayer && manager.mediaPlayer != null)
        wasLoadedBeforeStop = hasActivePlayer
        wasPlayingBeforeStop = manager.exoPlayer?.isPlaying == true ||
            (manager.isUsingMediaPlayer && manager.mediaPlayer?.isPlaying == true)
        if (!hasActivePlayer) return
        // Explicit save before release - releasePlayer() does not save, and the periodic auto-save
        // loop may be up to POSITION_SAVE_INTERVAL_MS stale.
        manager.saveCurrentPosition()
        releasePlayer()
    }

    // S0893: recreate only what was actually torn down by onStop() above.
    fun onStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !wasLoadedBeforeStop) return
        wasLoadedBeforeStop = false
        val path = manager.currentFilePath
        val resourceType = manager.lastResourceType
        if (path == null || resourceType == null) return
        manager.playVideo(path, resourceType, manager.lastCredentialsId, playWhenReady = wasPlayingBeforeStop)
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
            // S0893: same consolidation as releasePlayer() above.
            val player = playerToRelease
            if (player != null) {
                listOfNotNull(manager.playerListener, manager.activeExtraPlayerListener).forEach(player::removeListener)
                // S1127: symmetric stream-diagnostics teardown on the onDestroy path too.
                manager.releaseStreamDiagnostics(player)
            }
            manager.activeExtraPlayerListener = null
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to remove listener")
        }
        try {
            manager.currentPlayerView?.player = null
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to detach PlayerView")
        }

        manager.cancelPlaybackHealthCheck()
        // S0936: same rationale as releasePlayer() above - onDestroy() is a distinct teardown
        // path (below API 24 the player survives until here, not released on onStop()).
        manager.cancelStreamStallWatchdog()

        manager.activeResourceKey?.let { key ->
            ConnectionThrottleManager.deactivateVideoPlayerMode(key)
            manager.activeResourceKey = null
        }

        manager.retryRunnable?.let { manager.retryHandler.removeCallbacks(it) }
        manager.retryRunnable = null

        try {
            // S0893: same Media3 1.2.1 GL-pipeline-release-hang guard as releasePlayer() above -
            // drain the effects pipeline before release() (surface already detached above).
            playerToRelease?.setVideoEffects(emptyList())
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