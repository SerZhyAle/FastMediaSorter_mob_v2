package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackStatePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class PublishPlaybackStateUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    suspend operator fun invoke(state: WearPlaybackStatePayload) = runCatching {
        val stateBytes = gson.toJson(state).toByteArray()
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PLAYBACK_STATE,
            sentAt = System.currentTimeMillis(),
            data = stateBytes
        )
        val envelopeBytes = gson.toJson(envelope).toByteArray()
        val request = PutDataMapRequest.create(WearDataLayerPaths.PLAYBACK_STATE).apply {
            dataMap.putByteArray("payload", envelopeBytes)
            setUrgent()
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
    }.onFailure { e ->
        Timber.e(e, "S0111: PublishPlaybackStateUseCase failed — state not published")
    }
}
