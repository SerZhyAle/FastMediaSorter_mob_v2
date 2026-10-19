package com.sza.fastmediasorter.ui.player.helpers

import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.WeakHashMap

/** Tracks rendered timestamps instead of assuming constant frame duration, including VFR media. */
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
        Timber.d("S4373: forward frame requested")
        return clocks[player]?.step() == true
    }

    private data class FrameStamp(val presentationUs: Long, val releaseNs: Long)

    private class FrameClock(player: ExoPlayer) : VideoFrameMetadataListener, Player.Listener {
        private val owner = WeakReference(player)
        private val frames = ArrayDeque<FrameStamp>()
        private var pendingAfterUs: Long? = null
        private var previousSeek: SeekParameters? = null
        private var queuedSteps = 0

        override fun onVideoFrameAboutToBeRendered(
            presentationTimeUs: Long,
            releaseTimeNs: Long,
            format: Format,
            mediaFormat: MediaFormat?,
        ) {
            synchronized(frames) {
                frames.addLast(FrameStamp(presentationTimeUs, releaseTimeNs))
                while (frames.size > FRAME_HISTORY_SIZE) frames.removeFirst()
            }
        }

        private fun visibleTimestamp(): Long? = synchronized(frames) {
            val now = System.nanoTime()
            frames.lastOrNull { it.releaseNs <= now }?.presentationUs
        }

        fun step(): Boolean {
            val player = owner.get() ?: return false
            return when {
                pendingAfterUs != null -> {
                    queuedSteps = (queuedSteps + 1).coerceAtMost(MAX_QUEUED_STEPS)
                    true
                }
                !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) -> false
                else -> visibleTimestamp()?.let { beginStep(player, it) } ?: false
            }
        }

        private fun beginStep(player: ExoPlayer, timestampUs: Long): Boolean {
            val nextMs = timestampUs / MICROSECONDS_PER_MILLISECOND + 1
            if (player.duration != C.TIME_UNSET && nextMs >= player.duration) return false
            player.pause()
            pendingAfterUs = timestampUs
            previousSeek = player.seekParameters
            player.setSeekParameters(SeekParameters.EXACT)
            // Decode-only samples before this boundary are skipped; the next presentation timestamp is rendered.
            player.seekTo(nextMs)
            return true
        }

        override fun onRenderedFirstFrame() {
            val after = pendingAfterUs ?: return
            if ((visibleTimestamp() ?: after) <= after) return
            restoreSeek()
            pendingAfterUs = null
            if (queuedSteps > 0) {
                queuedSteps--
                step()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = reset()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) reset()
        }

        fun reset() {
            restoreSeek()
            pendingAfterUs = null
            queuedSteps = 0
            synchronized(frames) { frames.clear() }
        }

        private fun restoreSeek() {
            previousSeek?.let { owner.get()?.setSeekParameters(it) }
            previousSeek = null
        }
    }

    private const val MICROSECONDS_PER_MILLISECOND = 1_000L
    private const val FRAME_HISTORY_SIZE = 64
    private const val MAX_QUEUED_STEPS = 64
}
