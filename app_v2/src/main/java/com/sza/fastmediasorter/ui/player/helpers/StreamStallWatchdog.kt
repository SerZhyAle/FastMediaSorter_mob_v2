package com.sza.fastmediasorter.ui.player.helpers

import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sza.fastmediasorter.core.playback.resilience.StreamStallObservation
import com.sza.fastmediasorter.core.playback.resilience.StreamStallOutcome
import com.sza.fastmediasorter.core.playback.resilience.StreamStallRule
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
 * shared `retryHandler`, manager-held state, no Hilt. Since S1513 the decision is no longer here -
 * this file samples the player and applies the answer, while `StreamStallRule` (a manager field,
 * beside [StreamStallRecoveryWindow]) counts the polls and owns the buffering deadline. Every entry
 * into it passes `SystemClock.elapsedRealtime()`, so the whole path is on one monotonic clock.
 */

/** Begin polling position progress once the stream reaches `STATE_READY`. */
internal fun VideoPlayerManager.startStreamStallWatchdog() {
    cancelStreamStallWatchdog()
    streamStallRule.start(streamStallObservation())
    streamStallRunnable = Runnable { checkStreamStall() }
    retryHandler.postDelayed(streamStallRunnable!!, StreamStallRule.POLL_INTERVAL_MS)
}

/** Poll live video by rendered frames, retaining position only for audio-only streams. */
internal fun VideoPlayerManager.checkStreamStall() {
    val player = exoPlayer ?: return
    if (!player.isPlaying || player.playbackState != Player.STATE_READY) return

    val outcome = streamStallRule.onPoll(streamStallObservation(), SystemClock.elapsedRealtime())
    when (outcome) {
        is StreamStallOutcome.Stalled -> recoverFromStreamStall(outcome.reason.diagnostic)
        is StreamStallOutcome.NoEvidence -> {
            logStreamStallNoEvidence(outcome)
            rescheduleStreamStallPoll()
        }
        StreamStallOutcome.Progressing -> rescheduleStreamStallPoll()
    }
}

/** Cancel whichever watchdog runnable is pending (poll or buffering-timeout) and reset state. */
internal fun VideoPlayerManager.cancelStreamStallWatchdog() {
    streamStallRunnable?.let { retryHandler.removeCallbacks(it) }
    streamStallRunnable = null
    streamStallRule.reset()
}

/**
 * Arm the buffering-without-ready timeout when the stream enters `STATE_BUFFERING`. Hands the rule
 * the `totalBufferedDuration` of this moment so [checkStreamBufferingTimeout] can tell legitimate
 * slow buffering (still downloading) from a genuine stall.
 */
internal fun VideoPlayerManager.armStreamBufferingTimeout() {
    streamStallRunnable?.let { retryHandler.removeCallbacks(it) }
    streamStallRule.onBufferingArmed(streamStallObservation(), SystemClock.elapsedRealtime())
    val runnable = Runnable { checkStreamBufferingTimeout() }
    streamStallRunnable = runnable
    retryHandler.postDelayed(runnable, StreamStallRule.BUFFERING_TIMEOUT_MS)
}

/**
 * Fires after `StreamStallRule.BUFFERING_TIMEOUT_MS` still buffering. A stream still downloading on
 * a weak link (`isLoading` true and the buffer growing) is legitimate - re-arm instead of
 * recovering.
 */
internal fun VideoPlayerManager.checkStreamBufferingTimeout() {
    val player = exoPlayer ?: return
    if (player.playbackState != Player.STATE_BUFFERING) return

    val outcome = streamStallRule.onBufferingDeadline(
        streamStallObservation(),
        SystemClock.elapsedRealtime()
    )
    when (outcome) {
        is StreamStallOutcome.Stalled -> recoverFromStreamStall(outcome.reason.diagnostic)
        // The deadline arrived without the rule ever being armed, so there is no leg to judge and
        // nothing to re-arm against - staying silent here would look identical to a healthy stream.
        is StreamStallOutcome.NoEvidence -> Timber.i(
            "Stream stall - buffering deadline fired unarmed path=%s",
            currentFilePath
        )
        StreamStallOutcome.Progressing -> armStreamBufferingTimeout()
    }
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
    val attempt = streamWatchdogRecoveryWindow.tryAcquire(SystemClock.elapsedRealtime())
    if (attempt == null) {
        Timber.w(
            "Stream stall - watchdog budget exhausted (%d attempts, %s) path=%s",
            STREAM_MAX_WATCHDOG_RECOVERIES,
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
    streamWatchdogReconnecting = true
    Timber.w(
        "Stream stall - watchdog re-anchor (attempt %d, %s) path=%s",
        attempt,
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

/**
 * The player values the rule is allowed to see. Read through the nullable player with the same
 * fallbacks the pre-S1513 code used, because a session can be torn down between a scheduled poll
 * and its arrival and the watchdog defaulted rather than skipping that poll.
 */
private fun VideoPlayerManager.streamStallObservation(): StreamStallObservation {
    val player = exoPlayer
    return StreamStallObservation(
        positionMs = player?.currentPosition ?: 0L,
        renderedFrames = activeStreamAnalyticsListener?.renderedFrames(),
        hasVideo = player?.videoFormat != null,
        bufferedDurationMs = player?.totalBufferedDuration ?: 0L,
        isLoading = player?.isLoading == true,
        isLive = player?.isCurrentMediaItemLive == true
    )
}

/**
 * One line per blind episode at INFO, so an archive shows a rule that could not run rather than one
 * that ran and declined to fire; the repeats drop to DEBUG because a poll every three seconds would
 * otherwise bury the file log of the very session this evidence is wanted from.
 */
private fun VideoPlayerManager.logStreamStallNoEvidence(outcome: StreamStallOutcome.NoEvidence) {
    if (outcome.blindForMs == 0L) {
        Timber.i(
            "Stream stall - no progress evidence (no frame counter) live=%b path=%s",
            outcome.isLive,
            currentFilePath
        )
    } else {
        Timber.d(
            "Stream stall - still no progress evidence blindFor=%dms path=%s",
            outcome.blindForMs,
            currentFilePath
        )
    }
}

private fun VideoPlayerManager.rescheduleStreamStallPoll() {
    streamStallRunnable?.let { retryHandler.postDelayed(it, StreamStallRule.POLL_INTERVAL_MS) }
}

internal const val STREAM_MAX_WATCHDOG_RECOVERIES = 3
