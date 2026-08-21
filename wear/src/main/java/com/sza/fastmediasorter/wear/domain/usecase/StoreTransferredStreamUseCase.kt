package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S1799: stores one stream channel transferred from the phone and answers with the outcome the
 * phone will show the user - stored, updated, or the error that prevented either.
 */
class StoreTransferredStreamUseCase @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val classifier: ClassifyWearStreamMediaKindUseCase
) {

    // The ack is the phone's only view of what happened here; any persistence failure must become
    // an OUTCOME_ERROR answer rather than a crash that leaves the phone waiting out its timeout.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(payload: WearStreamTransferPayload): WearStreamTransferAck {
        if (payload.url.isBlank()) {
            return WearStreamTransferAck(
                requestId = payload.requestId,
                outcome = WearStreamTransferAck.OUTCOME_ERROR,
                message = "blank url"
            )
        }
        val channel = WearStreamChannel(
            id = UUID.randomUUID().toString(),
            name = payload.name.ifBlank { payload.url },
            url = payload.url,
            mediaKind = payload.mediaKind.ifBlank { classifier.classify(payload.url) },
            origin = WearStreamChannel.ORIGIN_PHONE
        )
        return try {
            val added = repository.upsertChannel(channel)
            val outcome = if (added) {
                WearStreamTransferAck.OUTCOME_STORED
            } else {
                WearStreamTransferAck.OUTCOME_UPDATED
            }
            WearStreamTransferAck(requestId = payload.requestId, outcome = outcome)
        } catch (e: Exception) {
            Timber.w(e, "Failed to store transferred stream")
            WearStreamTransferAck(
                requestId = payload.requestId,
                outcome = WearStreamTransferAck.OUTCOME_ERROR,
                message = e.message
            )
        }
    }
}
