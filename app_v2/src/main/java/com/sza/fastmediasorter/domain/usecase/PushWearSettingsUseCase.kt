package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import timber.log.Timber
import javax.inject.Inject

class PushWearSettingsUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson
) {
    suspend operator fun invoke(settings: WearSettingsPayload): Result<Unit> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        check(nodes.isNotEmpty()) { "No watch connected" }
        val settingsBytes = gson.toJson(settings).toByteArray(Charsets.UTF_8)
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_SETTINGS,
            sentAt = System.currentTimeMillis(),
            data = settingsBytes
        )
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.SETTINGS_PUSH, envelope)
    }
}
