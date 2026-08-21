package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class SendFavoritesDeltaUseCase @Inject constructor(
    private val favoritesRepository: WearFavoritesRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend operator fun invoke(): Result<Int> = runCatching {
        val delta = favoritesRepository.getPendingDelta()
        if (delta.isEmpty()) return Result.success(0)

        val payload = WearFavoritesDeltaPayload(items = delta)
        val envelopeBytes = envelopeCodec.encode(
            WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_FAVORITES,
                sentAt = System.currentTimeMillis(),
                data = gson.toJson(payload).toByteArray()
            )
        )

        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) error("No phone connected")

        for (node in nodes) {
            Wearable.getMessageClient(context)
                .sendMessage(node.id, WearDataLayerPaths.FAVORITES_DELTA, envelopeBytes)
                .await()
        }

        favoritesRepository.clearPendingDelta()
        delta.size
    }
}
