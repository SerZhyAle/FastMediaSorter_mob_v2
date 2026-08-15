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
            // S1661: the enum itself, not its `name`. Both write the same JSON string today, but only this
            // form goes through Gson's enum adapter, which is the only reader of the @SerializedName pins on
            // WearPlaybackCommand - `name` would hand the watch whatever the field is called at runtime.
            data = gson.toJson(command).toByteArray()
        )
        val envelopeBytes = gson.toJson(envelope).toByteArray()
        for (node in nodes) {
            wearableRepository.sendMessage(node.id, WearDataLayerPaths.PLAYBACK_CMD, envelopeBytes)
        }
    }
}
