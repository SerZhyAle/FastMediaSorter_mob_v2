package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.service.WatchListenPlayback
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2881: the shipped playback path behind [WatchListenPlayback].
 *
 * S2550 ADR-4 and ADR-5 together: the watch's address is an ordinary HTTP one, opened exactly as
 * internet radio is. No player, no media-source factory and no custom source are built here - the
 * watch serves self-delimiting ADTS AAC frames, which is what makes the shipped path enough.
 */
@Singleton
class Media3WatchListenPlayback @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WatchListenPlayback {

    /** Built lazily because most processes never listen to a watch at all. */
    private val audioController by lazy { AudioServiceController(context) }

    private var onDropped: (() -> Unit)? = null
    private var onEndedElsewhere: (() -> Unit)? = null

    /**
     * S3164: the session's own looper, so an outward callback leaves the library's event loop.
     *
     * Both callbacks end the whole listening session, which stops the player, clears its playlist and
     * releases the controller - every one of those a re-entrant call into a `MediaSession` that is
     * still iterating its connected controllers. The one that crashed was the release: it removes the
     * controller's record mid-loop and the next statement of `dispatchOnPlayerInfoChanged` dereferences
     * it. Handing the teardown to the next message keeps all three outside the loop.
     */
    private var dispatchHandler: Handler? = null

    /** STATE_IDLE is also where a source starts from, so only an IDLE after READY is an end. */
    private var reachedReady = false

    private val sessionListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.i(error, "The watch's audio stream dropped")
            afterDispatch { onDropped?.invoke() }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                reportEndedElsewhere()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> reachedReady = true
                Player.STATE_ENDED -> reportEndedElsewhere()
                Player.STATE_IDLE -> if (reachedReady) reportEndedElsewhere()
                else -> Unit
            }
        }
    }

    override fun prepareSession() {
        RadioStreamBufferConfig.syncLiveSessionMirror(context, true)
    }

    override fun start(url: String, onPlaying: () -> Unit, onDropped: () -> Unit, onEndedElsewhere: () -> Unit) {
        this.onDropped = onDropped
        this.onEndedElsewhere = onEndedElsewhere
        reachedReady = false
        audioController.playAudio(Uri.parse(url), mimeType = LISTEN_MIME_TYPE) { player ->
            dispatchHandler = Handler(player.applicationLooper)
            player.addListener(sessionListener)
            onPlaying()
        }
    }

    /** Runs [block] on the next message of the player's looper - never inside the event being handled. */
    private fun afterDispatch(block: () -> Unit) {
        Timber.d("S3164: the listening session teardown is handed to the next message of the player's looper")
        val handler = dispatchHandler ?: Handler(Looper.getMainLooper())
        handler.post(block)
    }

    /**
     * S2939: a live microphone stream has no meaningful pause - a paused listener holds the watch's
     * microphone for nobody - so every end the session did not order ends the session.
     */
    private fun reportEndedElsewhere() {
        Timber.i("Playback of the watch's stream ended outside the listening session")
        afterDispatch { onEndedElsewhere?.invoke() }
    }

    override fun stop() {
        // Detached before the player is touched, so this stop is never reported back as one from elsewhere.
        onDropped = null
        onEndedElsewhere = null
        dispatchHandler?.removeCallbacksAndMessages(null)
        dispatchHandler = null
        audioController.player?.let { player ->
            player.removeListener(sessionListener)
            player.stop()
            player.clearMediaItems()
        }
        audioController.release()
        RadioStreamBufferConfig.syncLiveSessionMirror(context, false)
    }

    private companion object {
        // S2550 ADR-4: the watch serves ADTS AAC, and naming it spares the extractor a sniff on a
        // live stream that has no container to read the type from.
        const val LISTEN_MIME_TYPE = "audio/aac"
    }
}
