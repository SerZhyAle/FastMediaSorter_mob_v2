package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The descriptor a broadcast is shared by - link, QR code, JSON: the live one, with the exchange endpoints added
 * while the broadcast is announced through the outgoing entry, so a receiver outside the network reaches it too
 * (S4160, contract DEVICE-EXCHANGE 7.7).
 */
class ResolveBroadcastShareDescriptorUseCase @Inject constructor(
    private val announcer: ExchangeBroadcastAnnouncer,
    private val credentials: BroadcastCredentialRegistry,
) {
    operator fun invoke(live: BroadcastDescriptorDto): BroadcastDescriptorDto = announcer.shareDescriptor(live)

    /** Emits whenever the shared descriptor may have changed, so a shown link, QR code or password is redrawn. */
    val changes: Flow<BroadcastDescriptorDto?> get() = announcer.announcements

    /**
     * The outward link's password while the shared descriptor carries `auth`, null for a LAN-only broadcast. Both
     * must hold: the registry begins a moment before the announcement that puts `auth` into the descriptor lands.
     */
    fun linkPassword(live: BroadcastDescriptorDto): String? =
        credentials.linkPair?.password?.takeIf { invoke(live).isProtected }

    /**
     * S4405: the shared descriptor carries the exchange endpoints. Apart from [linkPassword], because a broadcast
     * with an open link is announced and has no password - Guests and the full Copy link follow this one.
     */
    fun isAnnounced(live: BroadcastDescriptorDto): Boolean =
        invoke(live).endpoints.orEmpty().any { endpoint ->
            BroadcastExchangeEndpoints.TRANSPORT_RELAY.equals(endpoint.transport, ignoreCase = true) ||
                BroadcastExchangeEndpoints.TRANSPORT_TUNNEL.equals(endpoint.transport, ignoreCase = true)
        }
}
