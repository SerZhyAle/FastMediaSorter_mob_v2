package com.sza.fastmediasorter.ui.streams.helpers

import android.net.Uri
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/**
 * Owns the bottom sticky mini-control for inline radio playback. An AUDIO source plays through the
 * background-capable [AudioServiceController] (so it survives screen-off), the list stays visible and
 * interactive, and ICY now-playing metadata flows into the control text. All inline-audio logic lives
 * here - the Activity only forwards taps (Rule 3).
 */
@UnstableApi
class StreamInlineAudioManager(
    lifecycleOwner: LifecycleOwner,
    private val miniControl: View,
    private val titleView: TextView,
    private val playStopButton: ImageButton,
    private val audioController: AudioServiceController,
    private val onPlayingChanged: (String?) -> Unit,
) {

    private var currentSource: StreamSourceEntity? = null
    private var player: Player? = null
    private val nowPlaying = MutableStateFlow<String?>(null)

    private val metadataListener = object : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    nowPlaying.value = entry.title?.takeIf { it.isNotBlank() }
                }
            }
        }
    }

    init {
        playStopButton.setOnClickListener { stop() }
        lifecycleOwner.collectOnLifecycle(nowPlaying) { renderTitle() }
    }

    /** Returns the id of the source currently playing inline, or null. */
    val playingId: String? get() = currentSource?.id

    fun play(source: StreamSourceEntity) {
        stopPlaybackKeepingController()
        currentSource = source
        nowPlaying.value = null
        miniControl.isVisible = true
        onPlayingChanged(source.id)
        renderTitle()
        Timber.i("StreamInlineAudioManager: inline audio start - %s", source.url)
        Timber.d("S0565: inline audio start %s", source.url)
        audioController.playAudioWithMetadata(Uri.parse(source.url), source.title) { startedPlayer ->
            player = startedPlayer
            startedPlayer.addListener(metadataListener)
        }
    }

    fun stop() {
        stopPlaybackKeepingController()
        miniControl.isVisible = false
    }

    /** Releases the service connection; call from the Activity's onDestroy. */
    fun release() {
        stop()
        audioController.release()
    }

    private fun stopPlaybackKeepingController() {
        player?.removeListener(metadataListener)
        player?.stop()
        player = null
        currentSource = null
        nowPlaying.value = null
        onPlayingChanged(null)
    }

    private fun renderTitle() {
        val title = currentSource?.title ?: return
        val track = nowPlaying.value
        titleView.text = if (track.isNullOrBlank()) title else "$title - $track"
    }
}
