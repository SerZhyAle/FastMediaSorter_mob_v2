package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Where the live broadcast stands on the exchange server (contract DEVICE-EXCHANGE item DU), for the screen. */
class ObserveExchangeBroadcastPublishStateUseCase @Inject constructor(
    private val announcer: ExchangeBroadcastAnnouncer,
) {
    operator fun invoke(): StateFlow<ExchangeBroadcastPublishState> = announcer.publishState
}
