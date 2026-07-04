package com.sza.fastmediasorter.ui.main.helpers

import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioManager
/**
 * S0777: plays an AUDIO channel tapped on the main-window streams panel inline, right in the home
 * window, instead of launching the Streams list screen. Reuses the exact inline-radio engine the
 * Streams screen uses ([StreamInlineAudioManager] - bottom sticky now-playing control, background
 * [AudioServiceController] when persistent audio is on, in-app player otherwise, ICY metadata). A
 * VIDEO/RTSP channel needs a video surface, so it keeps its existing behavior via [onPlayVideo].
 *
 * Owns its lifecycle directly ([DefaultLifecycleObserver]) so the host stays logic-free (Rule 3): an
 * OFF-mode (in-app) stream is stopped when the window backgrounds, an ON-mode (service) stream keeps
 * playing - mirroring [com.sza.fastmediasorter.ui.streams.StreamsActivity].
 */
@UnstableApi
class MainStreamsInlineAudioManager(
    lifecycleOwner: LifecycleOwner,
    private val binding: ActivityMainBinding,
    private val hasNetwork: () -> Boolean,
    private val isPersistentAudioSettingOn: () -> Boolean,
    private val onPlayVideo: (StreamSourceEntity) -> Unit,
) : DefaultLifecycleObserver {

    private val audioController = AudioServiceController(binding.root.context)

    private val inlineAudio = StreamInlineAudioManager(
        lifecycleOwner = lifecycleOwner,
        miniControl = binding.mainStreamMiniControl,
        titleView = binding.tvMainMiniTitle,
        playStopButton = binding.btnMainMiniPlayStop,
        audioController = audioController,
        onPlayingChanged = {},
        onError = { onInlineAudioError() },
    )

    init {
        // S0778: keep the bottom mini-control above the navigation bar / side cutout under edge-to-edge.
        inlineAudio.applyWindowInsets()
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /** Routes a panel channel tap: AUDIO plays inline (toggle off when already playing), the rest defers. */
    fun playChannel(channel: StreamSourceEntity) {
        if (channel.mediaKind != AUDIO_KIND) {
            // VIDEO / RTSP need a video surface - keep the existing behavior (host opens the Streams screen).
            onPlayVideo(channel)
            return
        }
        when {
            // Re-tapping the channel already playing inline stops it (same gesture starts and stops the radio).
            inlineAudio.playingId == channel.id -> inlineAudio.stop()
            !hasNetwork() ->
                Toast.makeText(binding.root.context, R.string.streams_error_no_network, Toast.LENGTH_SHORT).show()
            else -> {
                // Background service only when the user enabled persistent audio AND the flavor supports it.
                val useService = isPersistentAudioSettingOn() && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK
                inlineAudio.play(channel, useBackgroundService = useService)
            }
        }
    }

    private fun onInlineAudioError() {
        Toast.makeText(binding.root.context, R.string.streams_unavailable_title, Toast.LENGTH_SHORT).show()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (inlineAudio.isLocalPlaybackActive) inlineAudio.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        // Service playback survives destroy (rotation / app exit); OFF-mode is already stopped by onStop.
        if (inlineAudio.isServiceAudioActive) {
            inlineAudio.releaseKeepingBackgroundService()
        } else {
            inlineAudio.release()
        }
    }

    private companion object {
        const val AUDIO_KIND = "AUDIO"
    }
}
