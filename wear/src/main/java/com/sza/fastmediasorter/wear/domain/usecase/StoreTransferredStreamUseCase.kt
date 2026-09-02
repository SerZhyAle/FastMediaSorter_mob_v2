package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamStoreResult
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S1799: stores one stream channel transferred from the phone and answers with the outcome the
 * phone will show the user - stored, updated, or the error that prevented either.
 *
 * S1944: also hands back the channel it stored, because the phone can now ask for it to be opened and
 * the caller needs the same record the list would have used - not the raw payload.
 */
class StoreTransferredStreamUseCase @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val classifier: ClassifyWearStreamMediaKindUseCase,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase
) {

    // The ack is the phone's only view of what happened here; any persistence failure must become
    // an OUTCOME_ERROR answer rather than a crash that leaves the phone waiting out its timeout.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(payload: WearStreamTransferPayload): WearStreamStoreResult {
        if (payload.url.isBlank()) {
            return WearStreamStoreResult(
                WearStreamTransferAck(
                    requestId = payload.requestId,
                    outcome = WearStreamTransferAck.OUTCOME_ERROR,
                    message = "blank url"
                ),
                channel = null,
            )
        }
        val channel = WearStreamChannel(
            id = UUID.randomUUID().toString(),
            name = payload.name.ifBlank { payload.url },
            url = payload.url,
            // S1944: classify whenever the value is not one this watch acts on - blank or unrecognised
            // alike. Before, only a blank was reclassified, so a non-blank typo was stored raw and then
            // routed to the audio player without the URL ever being consulted.
            mediaKind = payload.mediaKind.takeIf { it.isRecognisedKind() } ?: classifier.classify(payload.url),
            origin = WearStreamChannel.ORIGIN_PHONE
        )
        return try {
            val added = repository.upsertChannel(channel)
            val outcome = if (added) {
                WearStreamTransferAck.OUTCOME_STORED
            } else {
                WearStreamTransferAck.OUTCOME_UPDATED
            }
            requestWearTileRefreshUseCase(WearTileKind.STREAM)
            WearStreamStoreResult(WearStreamTransferAck(requestId = payload.requestId, outcome = outcome), channel)
        } catch (e: Exception) {
            Timber.w(e, "Failed to store transferred stream")
            WearStreamStoreResult(
                WearStreamTransferAck(
                    requestId = payload.requestId,
                    outcome = WearStreamTransferAck.OUTCOME_ERROR,
                    message = e.message
                ),
                channel = null,
            )
        }
    }
}

/** The three kinds the watch's players actually branch on; anything else is a value to re-derive. */
private fun String.isRecognisedKind(): Boolean =
    equals(ClassifyWearStreamMediaKindUseCase.VIDEO, ignoreCase = true) ||
        equals(ClassifyWearStreamMediaKindUseCase.AUDIO, ignoreCase = true) ||
        equals(ClassifyWearStreamMediaKindUseCase.RTSP, ignoreCase = true)
