package com.sza.fastmediasorter.ui.player.helpers

import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.domain.player.BroadcastEndDecision
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSession
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSessions
import com.sza.fastmediasorter.domain.player.BroadcastReconnectBudget
import com.sza.fastmediasorter.domain.player.BroadcastRemovalHold
import com.sza.fastmediasorter.domain.player.BroadcastStopSignal
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveBroadcastRemovalUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveLiveBroadcastSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.HandleBroadcastStreamStopUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.streams.StreamUnavailableDialog
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4444, LIVE-BROADCAST item T in the fullscreen player: a broadcast row's stop becomes the ended state with an
 * explicit reconnect, one reconnect that plays on, or a bounded lost-connection reconnect. The reconnect stays in
 * the player (ADR-3) so a broadcast that answers again does not drop fullscreen, and every reconnect resolves the
 * row from the top of its endpoint list (DEVICE-EXCHANGE 7.7) - the stored address alone may be a relay only a
 * loopback bridge can play.
 */
class PlayerBroadcastEndManager @Inject constructor(
    private val handleStop: HandleBroadcastStreamStopUseCase,
    private val recordOutcome: RecordStreamPlayOutcomeUseCase,
    private val observeRemoval: ObserveBroadcastRemovalUseCase,
) : DefaultLifecycleObserver {

    private var activity: PlayerActivity? = null
    private val budget = BroadcastReconnectBudget()
    private val hold = BroadcastRemovalHold()
    private var currentPath: String? = null
    private var watchedId: String? = null
    private var removalWatch: Job? = null
    private var pending: Job? = null
    private var dialog: AlertDialog? = null

    fun bind(host: PlayerActivity) {
        activity = host
        host.lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        dialog?.dismiss()
        dialog = null
        activity = null
    }

    /**
     * A stream reached READY: a broadcast row refills its reconnect budget and watches the directory. A removal seen
     * while it delivers is only held (item U).
     */
    fun onReady(path: String) {
        val session = BroadcastPlaybackSessions.forUrl(path) ?: return
        currentPath = path
        budget.reset()
        val id = session.broadcastId
        val host = activity
        if (host != null && id != null && id != watchedId) {
            watchedId = id
            hold.reset()
            removalWatch?.cancel()
            removalWatch = host.collectOnLifecycle(observeRemoval(id)) { removed ->
                val readNow = hold.onDirectory(removed)
                Timber.d("S4448: player directory removed=%s read now=%s", removed, readNow)
                if (readNow) {
                    currentPath?.let { playing -> onStreamStopped(playing, BroadcastStopSignal.DirectoryRemoved) }
                }
            }
        }
        hold.onDelivering()
    }

    /** True when [path] is a broadcast row and its stop is handled here; false leaves it to the caller. */
    fun onStreamStopped(path: String, stopSignal: BroadcastStopSignal): Boolean {
        val session = BroadcastPlaybackSessions.forUrl(path)
        val host = activity
        if (session == null || host == null) return false
        val signal = hold.atStop(stopSignal)
        Timber.i("PlayerBroadcastEndManager: broadcast stopped - %s (reported %s)", signal, stopSignal)
        Timber.d("S4444: player broadcast stop %s", signal)
        pending?.cancel()
        pending = host.lifecycleScope.launch {
            when (val decision = handleStop(session, signal)) {
                BroadcastEndDecision.Ended -> showEnded(host, session)
                is BroadcastEndDecision.Reconnect -> play(host, session, decision.url)
                BroadcastEndDecision.LostConnection -> retryOrAsk(host, session)
            }
        }
        return true
    }

    /** True when [path] is a broadcast row whose failure outlived the retry ladder; it is answered here. */
    fun onFailureSurfaced(path: String): Boolean {
        val session = BroadcastPlaybackSessions.forUrl(path)
        val host = activity
        if (session == null || host == null) return false
        showUnavailable(host, session)
        return true
    }

    private suspend fun retryOrAsk(host: PlayerActivity, session: BroadcastPlaybackSession) {
        if (budget.tryConsume()) {
            delay(budget.delayMs())
            val url = handleStop.reconnect(session)
            if (url != null) play(host, session, url) else retryOrAsk(host, session)
        } else {
            showUnavailable(host, session)
        }
    }

    private fun play(host: PlayerActivity, session: BroadcastPlaybackSession, url: String) {
        dialog?.dismiss()
        BroadcastPlaybackSessions.register(url, session.source)
        host.playVideo(url)
    }

    private suspend fun showEnded(host: PlayerActivity, session: BroadcastPlaybackSession) {
        // The ended broadcast stays ended: a later directory removal must not raise the same dialog again. A reconnect
        // that plays re-arms the watch on its READY.
        removalWatch?.cancel()
        watchedId = null
        recordOutcome.recordPlayEnded(session.source.id)
        host._videoPlayerManager?.pause()
        if (host.isInPip()) return
        dialog?.dismiss()
        dialog = StreamUnavailableDialog.showEnded(
            activity = host,
            channelTitle = session.source.title,
            removable = !session.isLiveRow(),
            onReconnect = { reconnectOnRequest(host, session) },
            onRemove = { removeAndLeave(host, session) },
            onDismiss = { host.finish() },
        )
    }

    private fun showUnavailable(host: PlayerActivity, session: BroadcastPlaybackSession) {
        host._videoPlayerManager?.pause()
        if (host.isInPip()) return
        dialog?.dismiss()
        dialog = StreamUnavailableDialog.show(
            activity = host,
            channelTitle = session.source.title,
            offline = false,
            onRetry = {
                budget.reset()
                pending = host.lifecycleScope.launch { retryOrAsk(host, session) }
            },
            onRemove = { removeAndLeave(host, session) },
            onDismiss = { host.finish() },
        )
    }

    private fun reconnectOnRequest(host: PlayerActivity, session: BroadcastPlaybackSession) {
        Timber.d("S4444: player reconnect requested from the ended dialog")
        pending = host.lifecycleScope.launch {
            val url = handleStop.reconnect(session)
            if (url != null) play(host, session, url) else showEnded(host, session)
        }
    }

    private fun removeAndLeave(host: PlayerActivity, session: BroadcastPlaybackSession) {
        host.viewModel.removeStreamSource(session.source)
        host.finish()
    }

    // A picture-in-picture window has no room for a dialog; the paused frame stays, as for a stream failure.
    private fun PlayerActivity.isInPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    private fun BroadcastPlaybackSession.isLiveRow(): Boolean =
        source.id.startsWith(ObserveLiveBroadcastSourcesUseCase.ID_PREFIX)
}
