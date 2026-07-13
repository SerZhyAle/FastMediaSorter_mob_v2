package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Silent-freeze detector + bounded recovery for the stream-playback path (S0936).
 *
 * `StreamPlaybackHelper.streamPlaybackListener` only recovers on a thrown `PlaybackException`
 * (behind-live-window desync / transient network errors) - a stream that stalls WITHOUT
 * throwing (position frozen while `STATE_READY`, or stuck `STATE_BUFFERING` that never reaches
 * `STATE_READY`) has no error to recover from and, before S0937, no diagnostic trace either.
 *
 * Both stall shapes were confirmed on-device (silent stall: BUFFERING with no PlaybackException)
 * and the owner ratified the thresholds below, so a confirmed stall now triggers a bounded
 * re-prepare within [STREAM_MAX_WATCHDOG_RECOVERIES]; exhaustion surfaces the regular error path.
 *
 * Extension functions on [VideoPlayerManager], mirroring [PlaybackHealthHelper]'s pattern: the
 * shared `retryHandler`, manager-held state fields, no new class instance, no Hilt.
 */

/** Begin polling position progress once the stream reaches `STATE_READY`. */
internal fun VideoPlayerManager.startStreamStallWatchdog() {
    cancelStreamStallWatchdog()
    streamStallLastPosition = exoPlayer?.currentPosition ?: 0L
    streamStallPolls = 0
    streamStallRunnable = Runnable { checkStreamStall() }
    retryHandler.postDelayed(streamStallRunnable!!, STALL_POLL_INTERVAL_MS)
}

/** Poll: detect a position frozen while the stream reports `STATE_READY` + `isPlaying`. */
internal fun VideoPlayerManager.checkStreamStall() {
    val player = exoPlayer ?: return
    if (!player.isPlaying || player.playbackState != Player.STATE_READY) return

    val currentPosition = player.currentPosition
    val positionDelta = currentPosition - streamStallLastPosition
    streamStallLastPosition = currentPosition

    if (positionDelta < STALL_MIN_PROGRESS_MS) {
        streamStallPolls++
        if (streamStallPolls >= STALL_MAX_POLLS) {
            streamStallPolls = 0
            recoverFromStreamStall("position frozen")
            return
        }
    } else {
        streamStallPolls = 0
    }
    retryHandler.postDelayed(streamStallRunnable!!, STALL_POLL_INTERVAL_MS)
}

/** Cancel whichever watchdog runnable is pending (poll or buffering-timeout) and reset state. */
internal fun VideoPlayerManager.cancelStreamStallWatchdog() {
    streamStallRunnable?.let { retryHandler.removeCallbacks(it) }
    streamStallRunnable = null
    streamStallPolls = 0
    streamBufferingSince = 0L
}

/**
 * Arm the buffering-without-ready timeout when the stream enters `STATE_BUFFERING`. Snapshots
 * `totalBufferedDuration` in the scheduled runnable's closure so [checkStreamBufferingTimeout]
 * can tell legitimate slow buffering (still downloading) from a genuine stall.
 */
internal fun VideoPlayerManager.armStreamBufferingTimeout() {
    streamStallRunnable?.let { retryHandler.removeCallbacks(it) }
    streamBufferingSince = System.currentTimeMillis()
    val bufferedAtArm = exoPlayer?.totalBufferedDuration ?: 0L
    val runnable = Runnable { checkStreamBufferingTimeout(bufferedAtArm) }
    streamStallRunnable = runnable
    retryHandler.postDelayed(runnable, BUFFERING_STALL_TIMEOUT_MS)
}

/**
 * Fires after [BUFFERING_STALL_TIMEOUT_MS] still buffering. A stream still downloading on a weak
 * link (`isLoading` true and the buffer growing) is legitimate - re-arm instead of recovering.
 */
internal fun VideoPlayerManager.checkStreamBufferingTimeout(bufferedAtArm: Long) {
    val player = exoPlayer ?: return
    if (player.playbackState != Player.STATE_BUFFERING || streamBufferingSince == 0L) return

    val stillProgressing = player.isLoading && player.totalBufferedDuration > bufferedAtArm
    if (stillProgressing) {
        armStreamBufferingTimeout()
        return
    }
    recoverFromStreamStall("buffering timeout")
}

/**
 * Bounded stall recovery. Runs synchronously on the main thread inside the watchdog runnable, so
 * the captured player cannot be swapped mid-flight (unlike `onPlayerError`'s delayed branches).
 *
 * Unlike the `onPlayerError` recovery path - where the player has already transitioned to `IDLE`
 * and a bare `prepare()` restarts it - a silent stall leaves the player in `BUFFERING`/`READY`,
 * where `prepare()` is a no-op. `stop()` forces `IDLE` first so the re-prepare rebuilds the whole
 * loading pipeline; a live stream then starts back at its default (live-edge) position, which is
 * exactly the re-anchor the error path achieves with `seekToDefaultPosition()`.
 */
internal fun VideoPlayerManager.recoverFromStreamStall(reason: String) {
    val stalledPlayer = exoPlayer ?: return
    if (streamWatchdogRecoveries >= STREAM_MAX_WATCHDOG_RECOVERIES) {
        Timber.w(
            "Stream stall - watchdog budget exhausted (%d attempts, %s) path=%s",
            streamWatchdogRecoveries,
            reason,
            currentFilePath
        )
        cancelStreamStallWatchdog()
        streamWatchdogReconnecting = false
        playerCallback.onBuffering(false)
        playerCallback.onStreamWaitPhase(null)
        playerCallback.onPlaybackError(
            PlaybackException(
                "Stream stalled ($reason) and the watchdog recovery budget is exhausted",
                null,
                PlaybackException.ERROR_CODE_TIMEOUT
            )
        )
        return
    }
    streamWatchdogRecoveries++
    streamWatchdogReconnecting = true
    Timber.w(
        "Stream stall - watchdog re-anchor (attempt %d, %s) path=%s",
        streamWatchdogRecoveries,
        reason,
        currentFilePath
    )
    playerCallback.onStreamWaitPhase(VideoPlayerManager.StreamWaitPhase.RECONNECTING)
    val wasLive = stalledPlayer.isCurrentMediaItemLive
    val resumePosition = if (wasLive) 0L else stalledPlayer.currentPosition
    stalledPlayer.stop()
    stalledPlayer.prepare()
    // Non-live (http VOD) streams have no live edge to re-anchor to - restore where the user was.
    if (!wasLive && resumePosition > 0L) stalledPlayer.seekTo(resumePosition)
}

private const val STALL_POLL_INTERVAL_MS = 3_000L
private const val STALL_MIN_PROGRESS_MS = 500L
private const val STALL_MAX_POLLS = 3
private const val BUFFERING_STALL_TIMEOUT_MS = 15_000L
internal const val STREAM_MAX_WATCHDOG_RECOVERIES = 3
