package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import timber.log.Timber
import javax.inject.Inject

class SendPlaybackCommandUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson
) {
    suspend operator fun invoke(command: WearPlaybackCommand): Result<Unit> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        if (nodes.isEmpty()) error("No watch connected")
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PLAYBACK_CMD,
            sentAt = System.currentTimeMillis(),
            data = gson.toJson(command.name).toByteArray()
        )
        val envelopeBytes = gson.toJson(envelope).toByteArray()
        for (node in nodes) {
            wearableRepository.sendMessage(node.id, WearDataLayerPaths.PLAYBACK_CMD, envelopeBytes)
        }
    }
}
