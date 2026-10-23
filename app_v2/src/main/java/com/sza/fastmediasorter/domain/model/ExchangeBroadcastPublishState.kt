package com.sza.fastmediasorter.domain.model

/**
 * Where this device's live broadcast stands on the exchange server (contract DEVICE-EXCHANGE 1.6 item DU), the one
 * source every surface reads. [server] is the label item BN allows - `host:port` of the outgoing entry, an IPv6
 * host in brackets - and no state ever carries a token, a key or a password.
 */
sealed interface ExchangeBroadcastPublishState {

    /** No broadcast is live. */
    data object Idle : ExchangeBroadcastPublishState

    /** The broadcast is live on this network only, and [reason] says why the server does not get it. */
    data class LocalOnly(val reason: Reason) : ExchangeBroadcastPublishState {
        enum class Reason {
            /** The device holds no exchange entry at all. */
            NO_ENTRY,

            /** Every entry is a guest one, which may not broadcast (19 item AO). */
            GUEST_ONLY,
        }
    }

    /** Sent or being sent; the server has not answered `broadcast-started` yet. */
    data class Announcing(val server: String) : ExchangeBroadcastPublishState

    /** The server answered `broadcast-started`; [relayed] when it also accepted the relay upload. */
    data class OnServer(val server: String, val relayed: Boolean) : ExchangeBroadcastPublishState

    /** The server refused or could not be reached; the broadcast on this network goes on regardless. */
    data class Failed(val server: String, val message: ExchangeMessage) : ExchangeBroadcastPublishState
}
