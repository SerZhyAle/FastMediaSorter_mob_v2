package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Silent-freeze detector for the stream-playback path (S0936).
 *
 * `StreamPlaybackHelper.streamPlaybackListener` only recovers on a thrown `PlaybackException`
 * (behind-live-window desync / transient network errors) - a stream that stalls WITHOUT
 * throwing (position frozen while `STATE_READY`, or stuck `STATE_BUFFERING` that never reaches
 * `STATE_READY`) has no recovery path and, before S0937, no diagnostic trace either.
 *
 * This phase only detects and logs the two stall shapes - it triggers no recovery, so it is
 * behavior-neutral. The poll thresholds below are provisional defaults pending owner
 * ratification (S0936 strategic spec §3.3); Phase 02 turns a confirmed stall into bounded
 * recovery once a device repro confirms the shape and the owner ratifies the constants.
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
            Timber.w(
                "Stream stall detected (position frozen) polls=%d path=%s",
                streamStallPolls,
                currentFilePath
            )
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
 * link (`isLoading` true and the buffer growing) is legitimate - re-arm instead of logging.
 */
internal fun VideoPlayerManager.checkStreamBufferingTimeout(bufferedAtArm: Long) {
    val player = exoPlayer ?: return
    if (player.playbackState != Player.STATE_BUFFERING || streamBufferingSince == 0L) return

    val stillProgressing = player.isLoading && player.totalBufferedDuration > bufferedAtArm
    if (stillProgressing) {
        armStreamBufferingTimeout()
        return
    }
    Timber.w("Stream stall detected (buffering timeout) path=%s", currentFilePath)
}

private const val STALL_POLL_INTERVAL_MS = 3_000L
private const val STALL_MIN_PROGRESS_MS = 500L
private const val STALL_MAX_POLLS = 3
private const val BUFFERING_STALL_TIMEOUT_MS = 15_000L
