package com.sza.fastmediasorter.ui.streams.helpers

import android.net.Uri
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
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

    /**
     * S0778: the bottom sticky mini-control is the last child of an edge-to-edge content column, so
     * under targetSdk 35 it drew beneath the navigation bar / a side display cutout and its stop button
     * was untappable. Pad it inside the safe rect on every edge except top (the toolbar owns the status
     * bar). The shared helper clamps to the live inset values and re-applies on rotation, so a landscape
     * side cutout is covered too. Wiring lives here, not in the Activity (Rule 3).
     */
    fun applyWindowInsets() {
        miniControl.applySystemBarInsetPadding(applyTop = false) { left, top, right, bottom ->
            Timber.d("S0778: now-playing panel insets t=$top b=$bottom l=$left r=$right")
        }
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
                // S0874: the service connect is async - a stop()/newer play() during the connect window
                // clears currentSource. Without this guard the late callback leaves an orphaned playing
                // service player with all inline-UI state cleared (no reachable stop). Bail if stale.
                if (currentSource?.id != source.id) {
                    startedPlayer.stop()
                    return@playAudioWithMetadata
                }
                player = startedPlayer
                startedPlayer.addListener(playerListener)
            }
        } else {
            // Background playback OFF: mirror local audio - play in-app, with no foreground service and
            // no media notification; StreamsActivity stops this on screen leave / background (S0577).
            // The Icy-MetaData request header keeps ICY now-playing flowing on the in-app player too.
            val httpFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            // S0896: this OFF-mode local player had no audio-focus/becoming-noisy handling, unlike
            // its service-mode twin (audioController.playAudioWithMetadata -> AudioPlaybackService).
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            val local = ExoPlayer.Builder(miniControl.context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            Timber.d("S0896: StreamInlineAudioManager OFF-mode player built with handleAudioFocus")
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
            // Service player: quiesce it (stop + drop playWhenReady + clear the playlist) but keep the
            // controller connected for the next play(). This lets AudioPlaybackService.onTaskRemoved's
            // no-active-playback heuristic (!playWhenReady || mediaItemCount == 0) stopSelf() the service.
            player?.let { p ->
                p.playWhenReady = false
                p.stop()
                p.clearMediaItems()
                Timber.d("S0900: service player quiesced on stop")
            }
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
