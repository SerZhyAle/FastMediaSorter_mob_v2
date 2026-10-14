package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearTouristHealthPayload
import com.sza.fastmediasorter.wear.util.rethrowIfCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * S4150: sends the Tourist heart rate and body temperature to the phone as state.
 *
 * A Data Item rather than a message, mirroring [PublishPlaybackStateUseCase]: the phone needs only the
 * latest reading, and a lost intermediate one is replaced by the next. The build that withholds health
 * features refuses here as well, so the Play declaration does not depend on Tourist staying unreachable.
 */
class PublishTouristHealthUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val capabilities: WearRestrictedCapabilities,
) {
    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend operator fun invoke(heartRateBpm: Int?, bodyTemperatureCelsius: Float?) {
        if (!capabilities.offersHealthFeatures) return
        runCatching {
            val payload: WearTouristHealthPayload = WearTouristHealthPayload(heartRateBpm, bodyTemperatureCelsius)
            val envelope = WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_TOURIST_HEALTH,
                sentAt = System.currentTimeMillis(),
                data = gson.toJson(payload).toByteArray(),
            )
            val request = PutDataMapRequest.create(WearDataLayerPaths.TOURIST_HEALTH).apply {
                dataMap.putByteArray("payload", envelopeCodec.encode(envelope))
                // A deferred delivery would reach the phone after its freshness window and show nothing.
                setUrgent()
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
        }.onFailure { e ->
            e.rethrowIfCancellation()
            Timber.w(e, "Tourist health report not sent to the phone")
        }
    }
}
