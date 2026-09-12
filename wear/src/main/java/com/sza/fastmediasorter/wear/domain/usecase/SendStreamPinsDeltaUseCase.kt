package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.repository.WearStreamPinsRepository
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearStreamPinsDeltaPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * S2497: sends queued watch stream pin changes to the phone via MessageClient.
 */
class SendStreamPinsDeltaUseCase @Inject constructor(
    private val pinsRepository: WearStreamPinsRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend operator fun invoke(): Result<Int> = runCatching {
        val delta = pinsRepository.getPendingDelta()
        if (delta.isEmpty()) return Result.success(0)

        val payload = WearStreamPinsDeltaPayload(items = delta)
        val envelopeBytes = envelopeCodec.encode(
            WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_STREAM_PINS_DELTA,
                sentAt = System.currentTimeMillis(),
                data = gson.toJson(payload).toByteArray(Charsets.UTF_8)
            )
        )

        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) error("No phone connected")

        for (node in nodes) {
            Wearable.getMessageClient(context)
                .sendMessage(node.id, WearDataLayerPaths.STREAM_PINS_DELTA, envelopeBytes)
                .await()
        }

        pinsRepository.clearPendingDelta()
        delta.size
    }
}
