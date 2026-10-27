package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastVerifier
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast

/**
 * A live broadcast this device announces through its outgoing entry (contract DEVICE-EXCHANGE 7.4 and 7.5):
 * the section 6.3 record sent as `broadcast-start`, the listener cap (`0` = none, the watch sends 4) and the
 * loopback port of the broadcast's own server per tunnel `scheme`, which an `open` for this broadcast reaches.
 */
data class ExchangeBroadcastPublication(
    val record: SftpRendezvousBroadcast,
    val maxListeners: Int,
    val localPorts: Map<String, Int>,
    /** S4258, DEVICE-EXCHANGE item BF: one per pair that may listen through the relay; empty when unprotected. */
    val verifiers: List<BroadcastVerifier> = emptyList(),
    /** S4440: what the announcer knew about the link's protection when it made this publication; logged only. */
    val link: BroadcastLinkTrace? = null,
) {

    companion object {
        const val SCHEME_HTTP = "http"
        const val SCHEME_RTSP = "rtsp"
        const val NO_LISTENER_CAP = 0
    }
}

/**
 * S4440: the remembered link mode next to the choice that protects the live link, which differ when a broadcast
 * started under a prepared link. Kept in memory for the announce log line; it is never sent.
 */
data class BroadcastLinkTrace(
    val remembered: BroadcastLinkPasswordMode,
    val live: BroadcastLinkChoice?,
)
