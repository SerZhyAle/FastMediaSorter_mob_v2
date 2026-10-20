package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry

/**
 * How a tunnel connection to an exchange server is routed (contract DEVICE-EXCHANGE 7.5).
 */
sealed interface ExchangeTunnelRoute {

    /**
     * An authenticated tunnel to a resource of the device's own account by its [resourceId].
     * The stream sends `hello` with [entry]'s token and validates the server certificate
     * with [entry]'s pinned certificate and key before sending `connect {resourceId}`.
     */
    data class OwnAccount(
        val entry: DeviceExchangeAccountEntry,
        val resourceId: String,
    ) : ExchangeTunnelRoute

    /**
     * An outsider tunnel by [shareId], unauthenticated on the exchange control plane.
     * [trust] dictates certificate verification (e.g. pinned certificate if known, or platform trust).
     */
    data class Outsider(
        val shareId: String,
        val trust: ExchangeTrust,
    ) : ExchangeTunnelRoute
}

/**
 * Resolves whether a tunnel address maps to an authenticated own-account resource or an outsider share.
 */
fun interface ExchangeTunnelRouter {
    fun route(tunnelHost: String, port: Int): ExchangeTunnelRoute?
}
