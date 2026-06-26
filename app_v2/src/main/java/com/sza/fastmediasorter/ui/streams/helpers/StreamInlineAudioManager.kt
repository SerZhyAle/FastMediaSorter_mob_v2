package com.sza.fastmediasorter.ui.streams.helpers

import android.net.Uri
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.streams.StreamTitleFormatter
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
    // Fired when the inline stream fails to play (no response, dead URL). The Activity surfaces a
    // dialog offering retry / remove-from-list - without this hook the failure only stopped the
    // background service and left the UI silent.
    private val onError: (StreamSourceEntity) -> Unit = {},
    // S0593: fired once per play when the stream actually starts playing (ground-truth "OK" outcome).
    // The Activity forwards it to the ViewModel, which records the green status for this source.
    private val onSuccess: (StreamSourceEntity) -> Unit = {},
) {

    private var currentSource: StreamSourceEntity? = null
    private var player: Player? = null
    // S0577: the in-app player used when background playback is OFF (no foreground service).
    private var localPlayer: ExoPlayer? = null
    // S0577: true while the active stream is owned by the background AudioPlaybackService (ON path).
    private var usingService = false
    private val nowPlaying = MutableStateFlow<String?>(null)
    // S0593: guard so a single play records "OK" once, even though isPlaying can toggle (re-buffer).
    private var successReported = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // S0593: first transition to actually-playing = the stream works here -> record OK once.
            if (isPlaying && !successReported) {
                successReported = true
                val playing = currentSource ?: return
                onSuccess(playing)
            }
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    nowPlaying.value = entry.title?.takeIf { it.isNotBlank() }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val failed = currentSource
            Timber.w(error, "StreamInlineAudioManager: inline audio error - %s", failed?.url)
            stop()
            failed?.let(onError)
        }
    }

    init {
        playStopButton.setOnClickListener { stop() }
        lifecycleOwner.collectOnLifecycle(nowPlaying) { renderTitle() }
    }

    /** Returns the id of the source currently playing inline, or null. */
    val playingId: String? get() = currentSource?.id

    /** S0577: true while an OFF-mode (in-app, non-service) stream is playing. */
    val isLocalPlaybackActive: Boolean get() = localPlayer != null

    /** S0577: true while a stream is owned by the background service (ON path). */
    val isServiceAudioActive: Boolean get() = usingService && currentSource != null

    /** S0577: the background-service player for the exit resolver; null in OFF (local) mode. */
    val activeServicePlayer: Player? get() = if (usingService) player else null

    fun play(source: StreamSourceEntity, useBackgroundService: Boolean) {
        stopPlaybackKeepingController()
        currentSource = source
        successReported = false
        nowPlaying.value = null
        miniControl.isVisible = true
        onPlayingChanged(source.id)
        renderTitle()
        Timber.i("StreamInlineAudioManager: inline audio start - %s (bg=%b)", source.url, useBackgroundService)
        usingService = useBackgroundService
        if (useBackgroundService) {
            audioController.playAudioWithMetadata(Uri.parse(source.url), source.title) { startedPlayer ->
                player = startedPlayer
                startedPlayer.addListener(playerListener)
            }
        } else {
            // Background playback OFF: mirror local audio - play in-app, with no foreground service and
            // no media notification; StreamsActivity stops this on screen leave / background (S0577).
            // The Icy-MetaData request header keeps ICY now-playing flowing on the in-app player too.
            val httpFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            val local = ExoPlayer.Builder(miniControl.context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .build()
            local.setMediaItem(MediaItem.fromUri(source.url))
            local.addListener(playerListener)
            local.prepare()
            local.playWhenReady = true
            localPlayer = local
            player = local
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

    /**
     * S0577: detach without stopping the service player, so a background-continue exit keeps the
     * stream playing. The MediaSessionService survives the controller release.
     */
    fun releaseKeepingBackgroundService() {
        player?.removeListener(playerListener)
        player = null
        usingService = false
        currentSource = null
        nowPlaying.value = null
        onPlayingChanged(null)
        miniControl.isVisible = false
        audioController.release()
    }

    private fun stopPlaybackKeepingController() {
        player?.removeListener(playerListener)
        val local = localPlayer
        if (local != null) {
            // In-app player owns its own resources - release it, don't merely stop it.
            local.release()
            localPlayer = null
        } else {
            // Service player: stop playback but keep the controller connected for the next play().
            player?.stop()
        }
        player = null
        usingService = false
        currentSource = null
        nowPlaying.value = null
        onPlayingChanged(null)
    }

    private fun renderTitle() {
        // S0691: dedup the `Name (Name)` form so the mini-control matches the list/grid rendering.
        val title = currentSource?.title?.let(StreamTitleFormatter::display) ?: return
        val track = nowPlaying.value
        titleView.text = if (track.isNullOrBlank()) title else "$title - $track"
    }
}
