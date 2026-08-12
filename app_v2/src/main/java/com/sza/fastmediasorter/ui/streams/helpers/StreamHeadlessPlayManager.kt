package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * S1471: starts or stops one radio stream through the audio foreground service with no view attached,
 * so a pinned shortcut can play a channel without building the Streams screen first.
 *
 * [StreamInlineAudioManager] cannot serve this path: it owns the visible mini-control and its play()
 * binds to that view. Only [AudioServiceController] is view-free, and this class is the caller that
 * drives it.
 *
 * Suspending rather than callback-based because the caller is a trampoline whose only job is to finish:
 * it needs exactly one answer, and it needs one even when the connection never resolves.
 */
class StreamHeadlessPlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Plays [source] through the service, or stops it when that same channel is already playing - the
     * toggle a tap on the channel's row performs on the Streams screen (S0690).
     *
     * Returns true when the stream is audibly playing or was deliberately stopped, false when the caller
     * must fall back to the Streams screen. Every false arm is a case that needs the screen's own
     * messaging: an unreachable service, a stream that errors out, or one that never starts at all.
     */
    suspend fun play(source: StreamSourceEntity): Boolean {
        val outcome = withTimeoutOrNull(PLAY_TIMEOUT_MS) { awaitOutcome(source) }
        if (outcome == null) {
            Timber.w("StreamHeadlessPlayManager: %s did not start within the timeout", source.url)
        }
        return outcome == true
    }

    private suspend fun awaitOutcome(source: StreamSourceEntity): Boolean =
        suspendCancellableCoroutine { continuation ->
            val controller = AudioServiceController(context)
            val session = PlaySession(controller, continuation)
            // A timeout cancels this continuation between two service callbacks, and the controller it
            // holds is the one thing that must not survive that.
            continuation.invokeOnCancellation { session.settle(false) }
            controller.connect(onFailed = { session.settle(false) }) { player ->
                if (isSameStreamActive(player, source.url)) {
                    Timber.i("StreamHeadlessPlayManager: stop already-playing %s", source.url)
                    quiesce(player)
                    session.settle(true)
                } else {
                    Timber.i("StreamHeadlessPlayManager: start %s", source.url)
                    // Listener first: the answer can arrive inside playAudioWithMetadata below.
                    session.awaitStart(player)
                    // Nothing to do on player-ready - the outcome arrives through that listener.
                    controller.playAudioWithMetadata(Uri.parse(source.url), source.title) { }
                }
            }
        }

    /**
     * Whether [url] is the channel the service is on right now. Judged by `playWhenReady` rather than by
     * `isPlaying`, which is false throughout STATE_BUFFERING - and a radio stream buffers for seconds,
     * which is exactly the window in which a user taps the shortcut again to change their mind.
     */
    private fun isSameStreamActive(player: Player, url: String): Boolean =
        player.currentMediaItem?.requestMetadata?.mediaUri?.toString() == url &&
            player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED

    /**
     * Stop the way the on-screen path stops (StreamInlineAudioManager.stopPlaybackKeepingController):
     * `stop()` alone leaves playWhenReady set and the playlist loaded, and the service's own
     * no-active-playback heuristic (`!playWhenReady || mediaItemCount == 0`) then refuses to stopSelf(),
     * leaving the media notification up after the user just silenced it.
     */
    private fun quiesce(player: Player) {
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    /**
     * One playback attempt, settled exactly once. The controller is released on every arm, including
     * success: Media3 puts the session in the foreground itself once it starts playing, so the service
     * outlives its controller (the same guarantee StreamInlineAudioManager.releaseKeepingBackgroundService
     * relies on, S0577). Keeping the binding instead would pin the service for the life of the process
     * and disarm every stopSelf() it has.
     */
    private class PlaySession(
        private val controller: AudioServiceController,
        private val continuation: CancellableContinuation<Boolean>,
    ) {

        private val settled = AtomicBoolean(false)
        private var player: Player? = null

        private val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) settle(true)
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "StreamHeadlessPlayManager: stream failed before it started")
                settle(false)
            }
        }

        fun awaitStart(player: Player) {
            this.player = player
            player.addListener(listener)
            if (player.isPlaying) settle(true)
        }

        fun settle(outcome: Boolean) {
            if (!settled.compareAndSet(false, true)) return
            player?.removeListener(listener)
            player = null
            controller.release()
            if (continuation.isActive) continuation.resume(outcome)
        }
    }

    companion object {

        /**
         * Whether [source] can play without a screen at all. Every false arm is a case strategic §3.1
         * pins to today's behaviour - open the Streams screen and let it decide.
         */
        fun qualifiesForHeadlessPlay(
            source: StreamSourceEntity,
            backgroundAudioEnabled: Boolean,
            hasNetwork: Boolean,
        ): Boolean = source.mediaKind == AUDIO_MEDIA_KIND && backgroundAudioEnabled && hasNetwork

        private const val AUDIO_MEDIA_KIND = "AUDIO"

        /**
         * Generous because a radio stream buffers for seconds over a slow link, and the trampoline shows
         * nothing while it waits, so waiting costs the user no visible delay. Past it the Streams screen
         * takes over, which is what strategic §4 demands instead of a trampoline that hangs.
         */
        private const val PLAY_TIMEOUT_MS = 15_000L
    }
}
