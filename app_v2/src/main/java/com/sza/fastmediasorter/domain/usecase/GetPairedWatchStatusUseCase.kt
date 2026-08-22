package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import javax.inject.Inject

/**
 * Answer "which watch is on the link right now" for the Wear companion settings group (S1885).
 *
 * Reads the same connected-node list the four send paths already use rather than introducing an
 * "identify yourself" exchange: the bridge answers this question directly, so a new event, schema
 * and reply timeout would buy nothing.
 */
class GetPairedWatchStatusUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
) {

    suspend operator fun invoke(): PairedWatchStatus {
        val name = wearableRepository.getConnectedNodes()
            .firstOrNull()
            ?.displayName
            ?.takeIf { it.isNotBlank() }
        // A blank display name is treated as no watch: a row naming an empty string reads as a bug,
        // and "not connected" is the honest answer when the bridge cannot say who answered.
        return if (name == null) PairedWatchStatus.NotConnected else PairedWatchStatus.Connected(name)
    }
}
