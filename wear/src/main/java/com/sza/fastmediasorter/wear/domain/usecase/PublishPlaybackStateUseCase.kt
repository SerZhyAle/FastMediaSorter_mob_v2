package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackStatePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class PublishPlaybackStateUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend operator fun invoke(state: WearPlaybackStatePayload) = runCatching {
        val stateBytes = gson.toJson(state).toByteArray()
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PLAYBACK_STATE,
            sentAt = System.currentTimeMillis(),
            data = stateBytes
        )
        val envelopeBytes = envelopeCodec.encode(envelope)
        val request = PutDataMapRequest.create(WearDataLayerPaths.PLAYBACK_STATE).apply {
            dataMap.putByteArray("payload", envelopeBytes)
            setUrgent()
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
    }.onFailure { e ->
        // S2029: closing the player cancels the scope this publish runs in, and runCatching catches
        // that CancellationException like any other failure. Two things went wrong at once - the log
        // claimed a delivery failure that never happened, and swallowing the cancellation left the
        // coroutine looking completed to its parent. Cancellation is the caller's outcome, not ours.
        if (e is CancellationException) {
            throw e
        }
        Timber.e(e, "PublishPlaybackStateUseCase failed - state not published")
    }
}
