package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the live broadcast's sealed box carries (contract DEVICE-EXCHANGE item DK): the relay's group keys, the
 * tunnel's TLS identity and, for a protected broadcast, the own credential pair (item BF). One owner for the three,
 * so the producer that seals the record asks one place.
 */
@Singleton
class BroadcastSealMaterial @Inject constructor(
    val relayKeys: ExchangeRelayKeys,
    val tunnelIdentities: BroadcastTunnelIdentities,
    val credentials: BroadcastCredentialRegistry,
)
