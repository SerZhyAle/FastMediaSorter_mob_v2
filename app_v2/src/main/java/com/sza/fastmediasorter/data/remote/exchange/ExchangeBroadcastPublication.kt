package com.sza.fastmediasorter.data.remote.exchange

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
) {

    companion object {
        const val SCHEME_HTTP = "http"
        const val SCHEME_RTSP = "rtsp"
        const val NO_LISTENER_CAP = 0
    }
}
