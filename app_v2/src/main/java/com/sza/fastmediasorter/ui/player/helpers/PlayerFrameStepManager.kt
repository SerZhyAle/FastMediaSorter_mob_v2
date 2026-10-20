package com.sza.fastmediasorter.ui.player.helpers

import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.WeakHashMap

/**
 * Advances exactly one decoded frame, including VFR media.
 *
 * An exact seek to "visible timestamp + 1 ms" re-renders the frame that covers that instant, which
 * is the visible frame itself, so the next frame cannot be reached by seeking without knowing its
 * timestamp in advance. Instead the player runs muted at a crawl and pauses on the first decoded
 * frame whose presentation timestamp is later than the visible one: the stop is driven by the
 * renderer's own frame report, never by a timer or a nominal frame rate.
 */
object PlayerFrameStepManager {
    // Registry access is confined to the player's application thread; frame queues have their own monitor.
    private val clocks = WeakHashMap<ExoPlayer, FrameClock>()

    fun attach(player: ExoPlayer) {
        if (clocks.containsKey(player)) return
        val clock = FrameClock(player)
        clocks[player] = clock
        player.setVideoFrameMetadataListener(clock)
        player.addListener(clock)
    }

    fun detach(player: ExoPlayer) {
        clocks.remove(player)?.let { clock ->
            player.clearVideoFrameMetadataListener(clock)
            player.removeListener(clock)
            clock.reset()
        }
    }

    fun stepForward(player: ExoPlayer): Boolean {
        return clocks[player]?.step() == true
    }

    private class FrameClock(player: ExoPlayer) : VideoFrameMetadataListener, Player.Listener {
        private val owner = WeakReference(player)
        private val handler = Handler(player.applicationLooper)
        private val frames = ArrayDeque<Long>()

        // Written on the application thread, read by the renderer callback on the playback thread.
        @Volatile private var pendingAfterUs: Long? = null
        private var savedParameters: PlaybackParameters? = null
        private var savedVolume: Float? = null
        private var queuedSteps = 0

        // The frame a finished step paused on; trusted until playback or a seek moves the picture.
        private var steppedToUs: Long? = null
        private val timeout = Runnable { finishStep(reachedUs = null) }
        private var clockStop: PlayerMessage? = null

        override fun onVideoFrameAboutToBeRendered(
            presentationTimeUs: Long,
            releaseTimeNs: Long,
            format: Format,
            mediaFormat: MediaFormat?,
        ) {
            synchronized(frames) {
                frames.addLast(presentationTimeUs)
                while (frames.size > FRAME_HISTORY_SIZE) frames.removeFirst()
            }
            val after = pendingAfterUs ?: return
            if (presentationTimeUs > after) handler.post { finishStep(presentationTimeUs) }
        }

        // During playback the renderer reports frames ahead of their display, and a pause leaves those
        // reports behind for frames never shown; only a frame at or before the clock is on screen.
        private fun visibleTimestamp(player: ExoPlayer): Long? {
            val positionUs = player.currentPosition * MICROSECONDS_PER_MILLISECOND
            return steppedToUs ?: synchronized(frames) { frames.filter { it <= positionUs }.maxOrNull() }
        }

        fun step(): Boolean {
            val player = owner.get() ?: return false
            if (pendingAfterUs != null) {
                queuedSteps = (queuedSteps + 1).coerceAtMost(MAX_QUEUED_STEPS)
            }
            val visibleUs = visibleTimestamp(player)
            return pendingAfterUs != null ||
                visibleUs != null && canStep(player, visibleUs) && beginStep(player, visibleUs)
        }

        private fun canStep(player: ExoPlayer, visibleUs: Long): Boolean {
            val endReached = player.duration != C.TIME_UNSET &&
                visibleUs / MICROSECONDS_PER_MILLISECOND >= player.duration
            return !endReached &&
                player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) &&
                player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)
        }

        private fun beginStep(player: ExoPlayer, afterUs: Long): Boolean {
            if (savedParameters == null) savedParameters = player.playbackParameters
            if (savedVolume == null) savedVolume = player.volume
            pendingAfterUs = afterUs
            steppedToUs = null
            player.volume = 0f
            player.playbackParameters = PlaybackParameters(STEP_SPEED)
            scheduleKnownNextFrame(player, afterUs)
            player.play()
            handler.postDelayed(timeout, STEP_TIMEOUT_MS)
            return true
        }

        // A pause during playback leaves frames already reported but not yet released; on resume they are
        // shown without a second report, so the stop for such a frame comes from the clock reaching it.
        private fun scheduleKnownNextFrame(player: ExoPlayer, afterUs: Long) {
            val later = synchronized(frames) { frames.filter { it > afterUs }.sorted() }
            val nextUs = later.firstOrNull() ?: return
            // Stopping exactly on the frame's timestamp can pause the renderer before it releases that
            // frame; a short margin, kept clear of the frame after it, lets the release happen first.
            val marginUs = later.getOrNull(1)?.let { (it - nextUs) / 2 } ?: CLOCK_MARGIN_US
            val stopMs = (nextUs + marginUs.coerceAtMost(CLOCK_MARGIN_US)) / MICROSECONDS_PER_MILLISECOND
            clockStop = player.createMessage { _, _ -> finishStep(nextUs) }
                .setLooper(player.applicationLooper)
                .setPosition(stopMs)
                .setDeleteAfterDelivery(true)
                .send()
        }

        private fun cancelPending() {
            handler.removeCallbacks(timeout)
            clockStop?.cancel()
            clockStop = null
        }

        private fun finishStep(reachedUs: Long?) {
            if (pendingAfterUs == null) return
            val player = owner.get()
            pendingAfterUs = null
            cancelPending()
            steppedToUs = reachedUs
            player?.pause()
            if (reachedUs != null && player != null && queuedSteps > 0) {
                queuedSteps--
                if (canStep(player, reachedUs)) {
                    beginStep(player, reachedUs)
                    return
                }
            }
            queuedSteps = 0
            restorePlayback()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = reset()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = owner.get() ?: return
            when {
                pendingAfterUs != null -> Unit
                isPlaying -> steppedToUs = null
                steppedToUs == null -> alignPictureToClock(player)
            }
        }

        // A user pause freezes the clock a frame or two past the picture still on screen, and no player
        // API names the frame shown. Re-rendering the frame at the clock makes picture and step base agree,
        // so the first step after a pause advances one frame instead of skipping the ones in between.
        private fun alignPictureToClock(player: ExoPlayer) {
            val eligible = !player.playWhenReady &&
                player.playbackState == Player.STATE_READY &&
                player.videoFormat != null &&
                !player.isCurrentMediaItemLive &&
                player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            if (!eligible) return
            val previous = player.seekParameters
            player.setSeekParameters(SeekParameters.EXACT)
            player.seekTo(player.currentPosition)
            player.setSeekParameters(previous)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (pendingAfterUs == null) steppedToUs = null
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) reset()
        }

        fun reset() {
            pendingAfterUs = null
            queuedSteps = 0
            steppedToUs = null
            cancelPending()
            restorePlayback()
            synchronized(frames) { frames.clear() }
        }

        private fun restorePlayback() {
            val player = owner.get()
            savedParameters?.let { player?.playbackParameters = it }
            savedVolume?.let { player?.volume = it }
            savedParameters = null
            savedVolume = null
        }
    }

    private const val MICROSECONDS_PER_MILLISECOND = 1_000L
    private const val FRAME_HISTORY_SIZE = 64
    private const val MAX_QUEUED_STEPS = 64
    private const val CLOCK_MARGIN_US = 3_000L

    // At a tenth of real time even a 60 fps frame stays on screen for ~166 ms, far longer than the
    // hop from the renderer callback to the application thread that issues the pause.
    private const val STEP_SPEED = 0.1f

    // A source that stalls or ends before a new frame arrives must not stay muted and slowed.
    private const val STEP_TIMEOUT_MS = 5_000L
}
