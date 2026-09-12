package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import javax.inject.Inject

/**
 * S2550: ends the listening session on every connected watch.
 *
 * Sent without knowing what the watch is running - Phase 04 made the stop path idempotent precisely
 * so the phone never has to ask first, which is what lets the drop handler send it on a link the
 * phone can no longer confirm anything about.
 */
class StopWatchListeningUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository
) {

    suspend operator fun invoke(requestId: String): Result<Unit> = runCatching {
        for (node in wearableRepository.getConnectedNodes()) {
            wearableRepository.sendListenStop(node.id, requestId)
        }
    }
}
