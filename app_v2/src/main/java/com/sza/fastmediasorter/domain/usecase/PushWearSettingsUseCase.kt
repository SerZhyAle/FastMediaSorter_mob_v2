package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import javax.inject.Inject

class PushWearSettingsUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson
) {
    suspend operator fun invoke(settings: WearSettingsPayload): Result<Unit> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        check(nodes.isNotEmpty()) { "No watch connected" }
        // S2461: stamped here rather than in the caller, because this is the one place every
        // phone-to-watch settings push passes through - a caller that builds a payload some other way
        // still cannot ship one without a version.
        // Typed explicitly, as the watch's ReportWearSettingsUseCase already is: this is the durable wire
        // model, and naming it here is what lets the persistence-contract gate see which class the JSON
        // below carries (S1639).
        val stamped: WearSettingsPayload = settings.copy(appVersionName = BuildConfig.VERSION_NAME)
        val settingsJson = gson.toJson(stamped)
        val settingsBytes = settingsJson.toByteArray(Charsets.UTF_8)
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_SETTINGS,
            sentAt = System.currentTimeMillis(),
            data = settingsBytes
        )
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.SETTINGS_PUSH, envelope)
    }
}
