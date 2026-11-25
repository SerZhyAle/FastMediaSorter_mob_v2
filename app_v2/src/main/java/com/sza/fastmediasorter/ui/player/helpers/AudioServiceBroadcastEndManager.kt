package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.player.BroadcastEndDecision
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSessions
import com.sza.fastmediasorter.domain.player.BroadcastRemovalHold
import com.sza.fastmediasorter.domain.player.BroadcastStopSignal
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveBroadcastRemovalUseCase
import com.sza.fastmediasorter.domain.usecase.streams.HandleBroadcastStreamStopUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * LIVE-BROADCAST items T and U in the background audio service, for a broadcast the Streams screen left to it
 * (S4609). No screen holds that broadcast, so the service keeps its own removal hold and asks the one after-end
 * reconnect itself: a stop after play becomes an end, a reconnect, or a lost connection for the service's ladder.
 * Owned by one service instance and driven from its main thread.
 */
class AudioServiceBroadcastEndManager @Inject constructor(
    private val handleStop: HandleBroadcastStreamStopUseCase,
    private val observeRemoval: ObserveBroadcastRemovalUseCase,
    private val recordOutcome: RecordStreamPlayOutcomeUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val hold = BroadcastRemovalHold()
    private var watchedId: String? = null
    private var removalWatch: Job? = null
    private var pending: Job? = null

    /**
     * [playedUrl] delivers: its directory entry is watched, and a removal seen while it delivers is only held. A
     * removal arriving while nothing delivers - the ladder in flight - is the end at once, through
     * [onRemovedWhileSilent].
     */
    fun watch(scope: CoroutineScope, playedUrl: String, onRemovedWhileSilent: () -> Unit) {
        val session = BroadcastPlaybackSessions.forUrl(playedUrl) ?: return
        val id = session.broadcastId
        if (id != null && id != watchedId) {
            watchedId = id
            hold.reset()
            removalWatch?.cancel()
            removalWatch = scope.launch {
                observeRemoval(id, session.entryKey).collect { removed ->
                    if (hold.onDirectory(removed)) onRemovedWhileSilent()
                }
            }
        }
        hold.onDelivering()
    }

    /**
     * True when [playedUrl] is a broadcast and its stop is decided here; [onDecision] gets the answer on the main
     * thread. A reconnect is registered as left to the service before it is handed on.
     */
    fun judge(
        scope: CoroutineScope,
        playedUrl: String,
        stopSignal: BroadcastStopSignal,
        onDecision: (BroadcastEndDecision) -> Unit,
    ): Boolean {
        val session = BroadcastPlaybackSessions.forUrl(playedUrl) ?: return false
        val signal = hold.atStop(stopSignal)
        Timber.i("AudioServiceBroadcastEndManager: broadcast stopped - %s (reported %s)", signal, stopSignal)
        pending?.cancel()
        pending = scope.launch {
            val decision = handleStop(session, signal)
            if (decision is BroadcastEndDecision.Reconnect) {
                BroadcastPlaybackSessions.register(decision.url, session.source)
                BroadcastPlaybackSessions.markLeftToService(decision.url)
            }
            onDecision(decision)
        }
        return true
    }

    /** The stop a bad-status [error] reports, or null for any other error; the status is null when none survived. */
    fun badStatusSignal(error: PlaybackException): BroadcastStopSignal? {
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return null
        val code = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
        return BroadcastStopSignal.HttpStatus(code)
    }

    /** A failure that outlived the ladder: true when a removal held while it delivered makes it an end. */
    fun removedAtFailure(): Boolean = hold.atFailure()

    /** The broadcast behind [playedUrl] ended: the outcome is counted and the Streams screen will show it on open. */
    fun recordEnded(playedUrl: String?) {
        val source = playedUrl?.let { BroadcastPlaybackSessions.forUrl(it)?.source } ?: return
        removalWatch?.cancel()
        watchedId = null
        BroadcastPlaybackSessions.markEndedInService(source)
        // The service stops itself right after, and cancelling its own scope would drop this write.
        applicationScope.launch { recordOutcome.recordPlayEnded(source.id) }
    }

    /** The user stopped: a decision landing later must not bring the sound back. */
    fun cancel() {
        pending?.cancel()
        pending = null
    }

    fun release() {
        cancel()
        removalWatch?.cancel()
        removalWatch = null
        watchedId = null
        hold.reset()
    }
}
