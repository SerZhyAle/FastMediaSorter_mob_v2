package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.domain.model.WearStreamPinsDeltaPayload
import timber.log.Timber
import javax.inject.Inject

/**
 * S2497: applies stream pin changes originating from the watch.
 *
 * Each item in the delta updates the durable user state by its canonical folded identity key.
 * When the local database updates, [PushWearStreamPinsUseCase.observeAndPush] automatically observes
 * the change and pushes the canonical updated pinned set back to the watch.
 */
class ApplyWatchStreamPinsDeltaUseCase @Inject constructor(
    private val streamSourceRepository: StreamSourceRepository
) {

    suspend operator fun invoke(payload: WearStreamPinsDeltaPayload) {
        Timber.d("ApplyWatchStreamPinsDeltaUseCase: applying ${payload.items.size} stream pin delta items")
        for (item in payload.items) {
            val identity = StreamChannelIdentity.of(item.urlOrIdentity)
            if (item.isPinned) {
                streamSourceRepository.pinByIdentity(identity)
            } else {
                streamSourceRepository.unpinByIdentity(identity)
            }
        }
    }
}
