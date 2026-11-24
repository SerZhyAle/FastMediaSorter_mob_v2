package com.sza.fastmediasorter.ui.streams.helpers

import androidx.appcompat.app.AppCompatActivity
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
import com.sza.fastmediasorter.ui.streams.StreamUnavailableDialog
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4444, LIVE-BROADCAST item T on the Streams screen: an audio broadcast playing inline - in-app or through the
 * background service, whose end and errors reach this screen through its controller - stops as ended with an
 * explicit reconnect, plays on after one reconnect, or gets the bounded reconnect of a lost connection. The
 * [Host] owns the audio and the row; this class owns only the decision wiring.
 */
class StreamBroadcastEndManager @Inject constructor(
    private val handleStop: HandleBroadcastStreamStopUseCase,
    private val recordOutcome: RecordStreamPlayOutcomeUseCase,
    private val observeRemoval: ObserveBroadcastRemovalUseCase,
) {

    /** What the Streams screen does with the decision; each call names the session the stop belongs to. */
    interface Host {
        fun stopAudio()
        fun playResolved(session: BroadcastPlaybackSession, url: String)
        fun replayFromTop(session: BroadcastPlaybackSession)
        fun showUnavailable(session: BroadcastPlaybackSession)
        fun remove(session: BroadcastPlaybackSession)
    }

    private val budget = BroadcastReconnectBudget()
    private val hold = BroadcastRemovalHold()
    private var playingUrl: String? = null
    private var removalWatch: Job? = null
    private var pending: Job? = null

    /**
     * The inline audio started playing [playedUrl]: a broadcast row refills its budget and watches the directory. A
     * removal seen while it plays is only held (item U).
     */
    fun onPlaying(owner: AppCompatActivity, playedUrl: String, host: Host) {
        val session = BroadcastPlaybackSessions.forUrl(playedUrl) ?: return
        budget.reset()
        val id = session.broadcastId
        if (playedUrl != playingUrl) {
            playingUrl = playedUrl
            Timber.d("S4607: inline removal watch, bound to an entry=${session.entryKey != null}")
            hold.reset()
            removalWatch?.cancel()
            removalWatch = id?.let {
                owner.collectOnLifecycle(observeRemoval(it, session.entryKey)) { removed ->
                    val readNow = hold.onDirectory(removed)
                    if (readNow) {
                        onStopped(owner, playedUrl, BroadcastStopSignal.DirectoryRemoved, host)
                    }
                }
            }
        }
        hold.onDelivering()
    }

    /** The inline audio stopped for any reason: nothing it played is watched any more. */
    fun onStopAll() {
        stopWatching()
        pending?.cancel()
    }

    // Called from inside the pending decision too, which must not cancel itself.
    private fun stopWatching() {
        removalWatch?.cancel()
        removalWatch = null
        playingUrl = null
    }

    fun onStopped(owner: AppCompatActivity, playedUrl: String, stopSignal: BroadcastStopSignal, host: Host) {
        val session = BroadcastPlaybackSessions.forUrl(playedUrl) ?: return
        val signal = hold.atStop(stopSignal)
        Timber.i("StreamBroadcastEndManager: broadcast stopped - %s (reported %s)", signal, stopSignal)
        pending?.cancel()
        pending = owner.lifecycleScope.launch {
            when (val decision = handleStop(session, signal)) {
                BroadcastEndDecision.Ended -> showEnded(owner, session, host)
                is BroadcastEndDecision.Reconnect -> {
                    BroadcastPlaybackSessions.register(decision.url, session.source)
                    host.playResolved(session, decision.url)
                }
                BroadcastEndDecision.LostConnection -> retryOrAsk(session, host)
            }
        }
    }

    /**
     * The screen came to the front: a broadcast the background service ended while no screen held it gets its ended
     * dialog now. The service already stopped the sound and counted the outcome.
     */
    fun onScreenStarted(owner: AppCompatActivity, host: Host) {
        val source = BroadcastPlaybackSessions.consumeEndedInService() ?: return
        Timber.d("S4874: Streams opened on a broadcast the service ended, showing the ended dialog")
        val session = BroadcastPlaybackSession(source, null)
        StreamUnavailableDialog.showEnded(
            activity = owner,
            channelTitle = source.title,
            removable = !source.id.startsWith(ObserveLiveBroadcastSourcesUseCase.ID_PREFIX),
            onReconnect = { host.replayFromTop(session) },
            onRemove = { host.remove(session) },
            onDismiss = {},
        )
    }

    /**
     * A broadcast row's failure that outlived the retry ladder: the ended dialog when the directory removed the
     * broadcast while it delivered (item U), else the unavailable one. False for any other row.
     */
    fun onFailureSurfaced(owner: AppCompatActivity, playedUrl: String, host: Host): Boolean {
        val session = BroadcastPlaybackSessions.forUrl(playedUrl) ?: return false
        pending?.cancel()
        Timber.d("S4874: inline broadcast failure surfaced, reading the held removal")
        if (hold.atFailure()) {
            Timber.i("StreamBroadcastEndManager: broadcast failed after the directory removed it - ended")
            pending = owner.lifecycleScope.launch { showEnded(owner, session, host) }
        } else {
            stopWatching()
            host.showUnavailable(session)
        }
        return true
    }

    private suspend fun retryOrAsk(session: BroadcastPlaybackSession, host: Host) {
        if (budget.tryConsume()) {
            delay(budget.delayMs())
            host.replayFromTop(session)
        } else {
            stopWatching()
            host.stopAudio()
            host.showUnavailable(session)
        }
    }

    private suspend fun showEnded(owner: AppCompatActivity, session: BroadcastPlaybackSession, host: Host) {
        stopWatching()
        host.stopAudio()
        recordOutcome.recordPlayEnded(session.source.id)
        StreamUnavailableDialog.showEnded(
            activity = owner,
            channelTitle = session.source.title,
            removable = !session.source.id.startsWith(ObserveLiveBroadcastSourcesUseCase.ID_PREFIX),
            onReconnect = { host.replayFromTop(session) },
            onRemove = { host.remove(session) },
            onDismiss = {},
        )
    }
}
