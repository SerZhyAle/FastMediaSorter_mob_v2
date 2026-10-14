package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastOpening
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.repository.WearAccountBroadcastRepository
import javax.inject.Inject

/**
 * S4229: what a tap on a listed broadcast leads to - the player to open, or the reason nothing plays.
 *
 * The address is ephemeral: a loopback port that exists only while its bridge does, so it goes through
 * [PrepareEphemeralStreamPlaybackUseCase], which writes neither the play count nor the home screen's recent row.
 * The list never starts a broadcast by itself (contract DEVICE-EXCHANGE item CI); this runs on the person's tap.
 */
class PlayAccountBroadcastUseCase @Inject constructor(
    private val repository: WearAccountBroadcastRepository,
    private val prepare: PrepareEphemeralStreamPlaybackUseCase,
) {

    suspend operator fun invoke(broadcast: WearAccountBroadcast, fallbackTitle: String): WearBroadcastOpening =
        when (val resolved = repository.resolve(broadcast.entryIdentity, broadcast.broadcastId)) {
            is WearBroadcastPlayback.Playable -> WearBroadcastOpening.Open(
                prepare(url = resolved.url, title = resolved.title ?: fallbackTitle, isVideo = resolved.isVideo),
            )
            is WearBroadcastPlayback.Unavailable -> WearBroadcastOpening.Unavailable(resolved.reason)
        }
}
