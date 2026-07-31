package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.databinding.GadgetLauncherNowPlayingBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.widget.AudioNowPlayingSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * S1170: what is playing now, on the launcher desktop, with its transport controls.
 *
 * The one gadget whose buttons cannot be commands. `ExecuteLauncherCommandUseCase` only ever calls
 * `startActivity`, while previous / play-pause / next are `startService` calls on
 * [AudioPlaybackService] - so the gadget sends those service intents itself, reusing the service's own
 * action and command constants rather than retyping their values. Its body tap is a normal command.
 *
 * The widget's favourite toggle is deliberately not reproduced: on the Android home screen it is a
 * self-broadcast the provider handles, and a desktop cell has no provider. The player one tap away owns
 * that action.
 */
class AudioNowPlayingGadget @Inject constructor() : LauncherGadget {

    override val key: String = KEY
    override val defaultSpanW: Int = SPAN_W
    override val defaultSpanH: Int = SPAN_H
    override val labelRes: Int = R.string.widget_audio_now_playing_label
    override val iconRes: Int = R.drawable.ic_widget_random_music
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        AudioNowPlayingGadgetView(container.context, host)

    private companion object {
        const val KEY = "audio_now_playing"

        /** The widget's own declared `targetCellWidth` / `targetCellHeight`. */
        const val SPAN_W = 2
        const val SPAN_H = 1
    }
}

private class AudioNowPlayingGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherNowPlayingBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.nowPlayingBody.setOnClickListener {
            host.run(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_RANDOM_MUSIC))
        }
        binding.nowPlayingPrevious.setOnClickListener {
            send(AudioPlaybackService.WIDGET_COMMAND_PREVIOUS)
        }
        binding.nowPlayingPlayPause.setOnClickListener {
            send(AudioPlaybackService.WIDGET_COMMAND_PLAY_PAUSE)
        }
        binding.nowPlayingNext.setOnClickListener {
            send(AudioPlaybackService.WIDGET_COMMAND_NEXT)
        }
    }

    /**
     * Polls the snapshot the playback service publishes, rather than binding to the service.
     *
     * The snapshot is a SharedPreferences blob with no change feed - the home-screen widget learns about
     * updates because the service pushes a RemoteViews refresh at it, and a desktop cell is not a widget
     * so nothing pushes. Polling inside [LauncherGadgetView.onActive] is bounded by construction: it runs
     * only while this view is attached AND the launcher is STARTED, and the loop dies with the scope.
     */
    override suspend fun CoroutineScope.onActive() {
        while (isActive) {
            render()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private fun render() {
        val snapshot = AudioNowPlayingSnapshotStore.read(context)
        binding.nowPlayingTitle.text = if (snapshot.active && snapshot.title.isNotBlank()) {
            snapshot.title
        } else {
            context.getString(R.string.widget_audio_now_playing_label)
        }
        binding.nowPlayingArtist.isVisible = snapshot.active && snapshot.artist.isNotBlank()
        binding.nowPlayingArtist.text = snapshot.artist
        binding.nowPlayingPlayPause.setImageResource(
            if (snapshot.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        // Transport for a stopped service would be three dead buttons; hide rather than mislead.
        binding.nowPlayingControls.isVisible = snapshot.active
    }

    private fun send(command: String) {
        val intent = Intent(context, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_WIDGET_COMMAND
            putExtra(AudioPlaybackService.EXTRA_WIDGET_COMMAND, command)
        }
        // Never startForegroundService: this only ever reaches a service the user already started by
        // playing something, and a background start of a stopped service would throw on API 26+.
        runCatching { context.startService(intent) }
            .onFailure { binding.nowPlayingControls.isVisible = false }
    }

    private companion object {
        /** Slow on purpose - a title change is not worth a tighter loop on the home screen. */
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
