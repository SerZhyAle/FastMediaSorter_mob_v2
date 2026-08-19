package com.sza.fastmediasorter.ui.launcher.gadget

import android.app.PendingIntent
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.databinding.GadgetLauncherNowPlayingBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.ActiveSessionNowPlayingSource
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NotificationAccessState
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NowPlayingCommand
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NowPlayingSource
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NowPlayingState
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.OwnSessionNowPlayingSource
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject

/**
 * S1170: what is playing now, on the launcher desktop, with its transport controls.
 *
 * The one gadget whose buttons cannot be commands. `ExecuteLauncherCommandUseCase` only ever calls
 * `startActivity`, while previous / play-pause / next drive a media session - so the gadget sends them
 * through a [NowPlayingSource] instead. Its body tap is a normal command.
 *
 * S0429 put that source behind an interface: where the data comes from is no longer this file's
 * business, only what it renders is.
 *
 * The widget's favourite toggle is deliberately not reproduced: on the Android home screen it is a
 * self-broadcast the provider handles, and a desktop cell has no provider. The player one tap away owns
 * that action.
 */
class AudioNowPlayingGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_AUDIO_NOW_PLAYING
    override val defaultSpanW: Int = SPAN_W
    override val defaultSpanH: Int = SPAN_H
    override val labelRes: Int = R.string.widget_audio_now_playing_label
    override val iconRes: Int = R.drawable.ic_widget_random_music
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        AudioNowPlayingGadgetView(container.context, host)

    private companion object {
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

    private val ownSession: NowPlayingSource = OwnSessionNowPlayingSource(context)
    private val activeSession: NowPlayingSource = ActiveSessionNowPlayingSource(context)

    /**
     * S1177: the open intent of whatever session is currently drawn, or null when nothing external plays.
     *
     * Held from the last render rather than re-read on the tap: re-reading would ask the platform for the
     * sessions again on the main thread, and the answer a user acts on is the one they are looking at.
     */
    private var openIntent: PendingIntent? = null

    /**
     * What the artwork view currently shows - the bitmap itself, or the package name its icon came from.
     *
     * Identity, not equality: two different sessions can publish equal-looking bitmaps, and re-setting an
     * identical one costs nothing anyway. What this exists to skip is the icon lookup below it.
     */
    private var shownArtwork: Any? = null

    init {
        binding.nowPlayingBody.setOnClickListener {
            openPlayingApp()
        }
        binding.nowPlayingPrevious.setOnClickListener {
            send(NowPlayingCommand.PREVIOUS)
        }
        binding.nowPlayingPlayPause.setOnClickListener {
            send(NowPlayingCommand.PLAY_PAUSE)
        }
        binding.nowPlayingNext.setOnClickListener {
            send(NowPlayingCommand.NEXT)
        }
        binding.nowPlayingGrantAccess.setOnClickListener {
            showAccessDisclosure()
        }
    }

    /**
     * The disclosure, and it opens before the system screen rather than after it.
     *
     * A bare jump to the notification-access screen is the failure case Play looks for: the user would
     * be asked for a powerful permission by the system, with the reason nowhere in sight.
     */
    private fun showAccessDisclosure() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.launcher_now_playing_access_title)
            .setMessage(R.string.launcher_now_playing_access_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                // Not every build ships the notification-access screen - a stripped head-unit ROM can
                // omit it. Losing the tap is the right outcome there; crashing the home screen is not.
                runCatching { context.startActivity(NotificationAccessState.settingsIntent()) }
                    .onFailure {
                        Timber.w(
                            "Now Playing: notification access screen unavailable (%s)",
                            it.javaClass.simpleName,
                        )
                    }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(context)
    }

    /**
     * Polls its source rather than subscribing to one.
     *
     * Neither source pushes: the own-session snapshot is a SharedPreferences blob with no change feed -
     * the home-screen widget only learns about updates because the service pushes a RemoteViews refresh
     * at it, and a desktop cell is not a widget - while a foreign session would need a live controller
     * callback the gadget deliberately does not hold. Polling inside [LauncherGadgetView.onActive] is
     * bounded by construction: it runs
     * only while this view is attached AND the launcher is STARTED, and the loop dies with the scope.
     */
    override suspend fun CoroutineScope.onActive() {
        while (isActive) {
            render()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    /**
     * Which source answers right now, and what it answered.
     *
     * Resolved on every call rather than once: the user can grant or revoke notification access in
     * system settings while this gadget is on screen, and a source picked in the constructor would keep
     * answering from an access that is gone. A foreign session wins when there is one - when there is
     * not, this app's own playback still shows, so granting the access never costs the user what they
     * had before it.
     */
    private fun resolve(accessGranted: Boolean): Pair<NowPlayingSource, NowPlayingState> {
        if (accessGranted) {
            val foreign = activeSession.read()
            if (foreign.active) {
                return activeSession to foreign
            }
        }
        return ownSession to ownSession.read()
    }

    private fun render() {
        val accessGranted = NotificationAccessState.isEnabled(context)
        val state = resolve(accessGranted).second
        binding.nowPlayingTitle.text = if (state.active && state.title.isNotBlank()) {
            state.title
        } else {
            context.getString(R.string.widget_audio_now_playing_label)
        }
        binding.nowPlayingArtist.isVisible = state.active && state.artist.isNotBlank()
        binding.nowPlayingArtist.text = state.artist
        openIntent = state.openIntent
        renderArtwork(state)
        binding.nowPlayingPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        // Transport for a stopped service would be three dead buttons; hide rather than mislead.
        binding.nowPlayingControls.isVisible = state.canControl
        // The offer takes the transport row's place instead of sitting under it: the cell is two columns
        // by one row and cannot hold both. So it appears exactly when the gadget has nothing to show and
        // the access that could give it something is off - the moment the missing capability is visible.
        binding.nowPlayingGrantAccess.isVisible = !accessGranted && !state.canControl
    }

    /**
     * S1177: a tap opens the player that is actually playing, and keeps the old route when none is.
     *
     * The owner chose this over always opening the foreign app (2026-08-17): with no external session the
     * cell behaves exactly as it did before this ticket, so no scenario that works today changes. The send
     * is wrapped because a session activity can be revoked between the render and the tap, and a home
     * screen must not die of it.
     */
    private fun openPlayingApp() {
        val intent = openIntent
        if (intent == null) {
            host.run(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_RANDOM_MUSIC))
            return
        }
        runCatching { intent.send() }
            .onFailure {
                Timber.w("Now Playing: session activity refused (%s)", it.javaClass.simpleName)
                host.run(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_RANDOM_MUSIC))
            }
    }

    /**
     * S1177: the cover, the source application's icon, or nothing at all - in that order.
     *
     * A placeholder box is deliberately not the third option: it costs a square of a two-square cell and
     * tells the user nothing the missing cover has not already told them.
     *
     * Guarded by [shownArtwork] because [render] runs every two seconds for as long as the desktop is on
     * screen: without the guard the icon branch would cross a binder and load a resource on every tick,
     * per placed cell, on the main thread. The phase-02 audit caught exactly that, and research 05
     * measured why it matters here - the budget is counted across every placed cell at once, not per
     * visible one.
     */
    private fun renderArtwork(state: NowPlayingState) {
        val key = state.artwork ?: state.sourcePackage
        if (key == shownArtwork) {
            return
        }
        shownArtwork = key
        Timber.d(
            "S1177: now-playing artwork changed - bitmap=%b source=%s",
            state.artwork != null,
            state.sourcePackage,
        )
        val artwork = state.artwork
        if (artwork != null) {
            binding.nowPlayingArtwork.setImageBitmap(artwork)
            binding.nowPlayingArtwork.isVisible = true
            return
        }
        val icon = state.sourcePackage?.let { packageName ->
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
        binding.nowPlayingArtwork.setImageDrawable(icon)
        binding.nowPlayingArtwork.isVisible = icon != null
    }

    private fun send(command: NowPlayingCommand) {
        if (!resolve(NotificationAccessState.isEnabled(context)).first.send(command)) {
            binding.nowPlayingControls.isVisible = false
        }
    }

    private companion object {
        /** Slow on purpose - a title change is not worth a tighter loop on the home screen. */
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
