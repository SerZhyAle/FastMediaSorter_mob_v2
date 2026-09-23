package com.sza.fastmediasorter.broadcast

import java.net.URI
import java.net.URISyntaxException

/**
 * Moves the RTSP-Server library's endpoint onto the address [com.sza.fastmediasorter.core.network.LanAddressResolver]
 * chose. The library picks its own host: it skips loopback, but falls back to `0.0.0.0` when no
 * interface qualifies and accepts a mobile-carrier interface, and LIVE-BROADCAST producer rule 1 forbids
 * handing either out.
 */
internal object RtspEndpointAddress {

    fun onLanHost(libraryEndpoint: String, lanHost: String): String? {
        val parsed = if (isPublishable(lanHost)) parseOrNull(libraryEndpoint) else null
        return parsed
            ?.takeIf { uri -> !uri.scheme.isNullOrBlank() && uri.port > 0 }
            ?.let { uri -> "${uri.scheme}://$lanHost:${uri.port}${uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"}" }
    }

    // An endpoint the library formed badly is not publishable; the caller ends the session on null.
    private fun parseOrNull(endpoint: String): URI? = try {
        URI(endpoint)
    } catch (_: URISyntaxException) {
        null
    }

    private fun isPublishable(host: String): Boolean =
        host.isNotBlank() && host != ANY_ADDRESS && !host.startsWith(LOOPBACK_PREFIX) && host != "localhost"

    private const val ANY_ADDRESS = "0.0.0.0"
    private const val LOOPBACK_PREFIX = "127."
}
