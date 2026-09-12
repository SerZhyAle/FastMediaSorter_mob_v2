package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import javax.inject.Inject

/**
 * S2550 pillar A: asks every connected watch to let this phone listen to its microphone.
 *
 * It starts nothing. ADR-6 puts a tap on the watch between this command and the microphone, so what
 * this returns says only that the ask reached a watch; the answer arrives separately on
 * `WearSyncEvents.listenAckFlow` carrying the address to play or the reason there is none.
 *
 * [requestId] is supplied by the caller rather than minted here, because the caller is the one that
 * has to recognise its own answer on a replaying flow.
 */
class StartWatchListeningUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository
) {

    suspend operator fun invoke(requestId: String): Result<Unit> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        if (nodes.isEmpty()) error("No watch connected")
        for (node in nodes) {
            wearableRepository.sendListenStart(node.id, requestId)
        }
    }
}
