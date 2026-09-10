package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.Uri
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

    private val errorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.i(error, "The watch's audio stream dropped")
            onDropped?.invoke()
        }
    }

    override fun prepareSession() {
        RadioStreamBufferConfig.syncLiveSessionMirror(context, true)
    }

    override fun start(url: String, onPlaying: () -> Unit, onDropped: () -> Unit) {
        this.onDropped = onDropped
        audioController.playAudio(Uri.parse(url), mimeType = LISTEN_MIME_TYPE) { player ->
            player.addListener(errorListener)
            onPlaying()
        }
    }

    override fun stop() {
        onDropped = null
        audioController.player?.let { player ->
            player.removeListener(errorListener)
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
