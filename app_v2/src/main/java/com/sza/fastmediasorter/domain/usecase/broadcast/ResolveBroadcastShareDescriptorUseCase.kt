package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import javax.inject.Inject

/**
 * The descriptor a broadcast is shared by - link, QR code, JSON: the live one, with the exchange endpoints added
 * while the broadcast is announced through the outgoing entry, so a receiver outside the network reaches it too
 * (S4160, contract DEVICE-EXCHANGE 7.7).
 */
class ResolveBroadcastShareDescriptorUseCase @Inject constructor(
    private val announcer: ExchangeBroadcastAnnouncer,
) {
    operator fun invoke(live: BroadcastDescriptorDto): BroadcastDescriptorDto = announcer.shareDescriptor(live)
}
