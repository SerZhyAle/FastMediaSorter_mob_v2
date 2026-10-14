package com.sza.fastmediasorter.ui.exchange.helpers

import com.sza.fastmediasorter.domain.exchange.ExchangeCastCoordinator
import javax.inject.Inject

/**
 * S4116: the one call the cast notification's trampoline makes - the user's answer to an offer (DEVICE-EXCHANGE
 * 7.8). It exists so the trampoline holds no domain collaborator of its own (Rule 3).
 */
class ExchangeCastOfferManager @Inject constructor(
    private val coordinator: ExchangeCastCoordinator,
) {

    suspend fun answer(broadcastId: String, accepted: Boolean) = coordinator.answer(broadcastId, accepted)
}
