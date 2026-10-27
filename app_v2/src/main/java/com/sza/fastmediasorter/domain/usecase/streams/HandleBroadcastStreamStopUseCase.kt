package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.domain.player.BroadcastEndDecision
import com.sza.fastmediasorter.domain.player.BroadcastEndPolicy
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSession
import com.sza.fastmediasorter.domain.player.BroadcastStopSignal
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import javax.inject.Inject

/**
 * S4444: what a host does when a played broadcast row stops (LIVE-BROADCAST item T). A signal that is an end on its
 * own needs no question; any other gets exactly one reconnect, and only an answer that plays turns into an address -
 * resolved from the top of the row's endpoint list, as every reconnect must be (DEVICE-EXCHANGE 7.7).
 */
class HandleBroadcastStreamStopUseCase @Inject constructor(
    private val check: CheckBroadcastAfterEndUseCase,
    private val resolve: ResolveBroadcastPlaybackUseCase,
) {

    suspend operator fun invoke(session: BroadcastPlaybackSession, signal: BroadcastStopSignal): BroadcastEndDecision {
        val answer = if (BroadcastEndPolicy.needsCheck(signal)) {
            check(session.source.url, session.source.broadcastEndpoints)
        } else {
            null
        }
        return BroadcastEndPolicy.decide(signal, answer)
            ?: reconnect(session)?.let(BroadcastEndDecision::Reconnect)
            ?: BroadcastEndDecision.LostConnection
    }

    /** The address the row plays at now, or null when nothing in its list plays. */
    suspend fun reconnect(session: BroadcastPlaybackSession): String? {
        val source = session.source
        return (resolve(source.url, source.broadcastEndpoints, source.id) as? Playback.Playable)?.url
    }
}
