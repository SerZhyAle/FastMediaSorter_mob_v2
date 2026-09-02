package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * S2093: sends the watch's own settings set to the phone, closing the reverse leg of the exchange.
 *
 * A Data Item rather than a message, mirroring [PublishPlaybackStateUseCase]: the settings are state,
 * so the phone reads the latest set after reconnecting instead of having to be listening at the moment
 * the watch sent it.
 */
class ReportWearSettingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val gatherWearSettingsUseCase: GatherWearSettingsUseCase,
    private val preferencesRepository: WearPreferencesRepository
) {
    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend operator fun invoke() = runCatching {
        // Typed explicitly: this is the durable wire model, and naming it here is what lets the
        // persistence-contract gate see which class the JSON below carries (S1639).
        val gathered: WearSettingsPayload = gatherWearSettingsUseCase()
        Timber.d("S2093: watch reporting settings, stamps=${gathered.fieldTimestamps?.size ?: 0}")
        val payloadBytes = gson.toJson(gathered).toByteArray()
        val sentAt = System.currentTimeMillis()
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_SETTINGS_REPORT,
            sentAt = sentAt,
            data = payloadBytes
        )
        val request = PutDataMapRequest.create(WearDataLayerPaths.SETTINGS_REPORT).apply {
            dataMap.putByteArray("payload", envelopeCodec.encode(envelope))
            // The receiver corrects for clock skew by the difference between this sentAt and its own
            // arrival time, so a delivery the system was free to defer would be read as a skew that
            // large and could invert the merge.
            setUrgent()
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
        // Marked here rather than at each caller, because this is the last step of both directions:
        // the button on the watch calls it, and a phone push is answered with it after the apply.
        preferencesRepository.markSettingsSynced(sentAt)
    }.onFailure { e ->
        // S2029: cancellation is the caller's outcome, not a delivery failure - swallowing it would
        // leave this coroutine looking completed to its parent.
        if (e is CancellationException) throw e
        Timber.e(e, "ReportWearSettingsUseCase failed - watch settings not reported")
    }
}
