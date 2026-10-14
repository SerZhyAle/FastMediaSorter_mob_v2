package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import java.net.URI
import java.security.MessageDigest

/**
 * S4160: one way into a live broadcast, read from a stored descriptor endpoint (`LIVE-BROADCAST` 3.2 and
 * `DEVICE-EXCHANGE` 7.7). Only the transports this app plays are modelled; `P2P` and anything unknown are
 * skipped, never an error (strategic 5.3).
 */
sealed interface BroadcastExchangeEndpoint {

    /** A direct LAN endpoint (`HTTP` or `RTSP`), played as it is. */
    data class Lan(val url: String) : BroadcastExchangeEndpoint

    /** The fan-out relay: `https://<publicEndpoint>/v2/b/<broadcastId>/stream`. */
    data class Relay(
        val url: String,
        val server: ExchangeServer,
        val broadcastId: String,
        val certFingerprint: String?,
    ) : BroadcastExchangeEndpoint

    /** A broadcast tunnel: `fmsx://<publicEndpoint>/b/<broadcastId>/<scheme>`, played as [inner] via a forwarder. */
    data class Tunnel(
        val server: ExchangeServer,
        val broadcastId: String,
        val scheme: String,
        val inner: String,
        val certFingerprint: String?,
    ) : BroadcastExchangeEndpoint
}

/** An exchange server as a descriptor names it - `host:port` of its `publicEndpoint`. */
data class ExchangeServer(val host: String, val port: Int) {
    val label: String get() = if (port == DEFAULT_TLS_PORT) host else "$host:$port"

    companion object {
        const val DEFAULT_TLS_PORT = 443
    }
}

/** Parsing, ordering and the log-safe forms of the endpoints of a stored channel. */
object BroadcastExchangeEndpoints {

    const val TRANSPORT_HTTP = "HTTP"
    const val TRANSPORT_RTSP = "RTSP"
    const val TRANSPORT_RELAY = "RELAY"
    const val TRANSPORT_TUNNEL = "TUNNEL"
    const val LOOPBACK_HOST = "127.0.0.1"

    private const val SCHEME_FMSX = "fmsx"
    private const val SCHEME_HTTPS = "https"
    private const val LOOPBACK_SCHEME = "http"
    private const val RELAY_PATH_PREFIX = "/v2/b/"
    private const val RELAY_PATH_SUFFIX = "/stream"
    private const val TUNNEL_PATH_PREFIX = "b"
    private const val TUNNEL_PATH_SEGMENTS = 3
    private const val REDACTED_HEX_CHARS = 8
    private val TUNNEL_SCHEMES = setOf("http", "rtsp")

    /**
     * The endpoints of a channel in the order the producer listed them, which is the order to try (7.7). A row
     * with no stored list is the single endpoint it always was: its [fallbackUrl].
     */
    fun playOrder(stored: String?, fallbackUrl: String): List<BroadcastExchangeEndpoint> =
        BroadcastEndpointCodec.decode(stored)?.mapNotNull(::parse) ?: listOf(BroadcastExchangeEndpoint.Lan(fallbackUrl))

    /** The exchange server a stored channel plays through, for its caption; null for a LAN-only channel. */
    fun exchangeServer(stored: String?): ExchangeServer? =
        BroadcastEndpointCodec.decode(stored)?.asSequence()?.mapNotNull(::parse)?.firstNotNullOfOrNull {
            when (it) {
                is BroadcastExchangeEndpoint.Relay -> it.server
                is BroadcastExchangeEndpoint.Tunnel -> it.server
                is BroadcastExchangeEndpoint.Lan -> null
            }
        }

    fun parse(dto: BroadcastEndpointDto): BroadcastExchangeEndpoint? {
        val fingerprint = dto.certFingerprint?.trim()?.takeIf { it.isNotEmpty() }
        return when (dto.transport.trim().uppercase()) {
            TRANSPORT_HTTP, TRANSPORT_RTSP ->
                dto.url.trim().takeIf { it.isNotEmpty() }?.let(BroadcastExchangeEndpoint::Lan)
            TRANSPORT_RELAY -> parseRelay(dto.url.trim(), fingerprint)
            TRANSPORT_TUNNEL -> parseTunnel(dto.url.trim(), dto.inner?.trim(), fingerprint)
            else -> null
        }
    }

    private fun parseRelay(url: String, fingerprint: String?): BroadcastExchangeEndpoint.Relay? {
        val uri = uriOrNull(url)?.takeIf { it.scheme.equals(SCHEME_HTTPS, ignoreCase = true) }
        val server = uri?.let(::serverOf) ?: return null
        val broadcastId = uri.rawPath.orEmpty()
            .takeIf { it.startsWith(RELAY_PATH_PREFIX) && it.endsWith(RELAY_PATH_SUFFIX) }
            ?.removePrefix(RELAY_PATH_PREFIX)?.removeSuffix(RELAY_PATH_SUFFIX)
            ?.takeIf { it.isNotEmpty() && !it.contains('/') }
        return broadcastId?.let { BroadcastExchangeEndpoint.Relay(url, server, it, fingerprint) }
    }

    private fun parseTunnel(url: String, inner: String?, fingerprint: String?): BroadcastExchangeEndpoint.Tunnel? {
        val uri = uriOrNull(url)?.takeIf { it.scheme.equals(SCHEME_FMSX, ignoreCase = true) }
        val server = uri?.let(::serverOf) ?: return null
        val segments = uri.rawPath.orEmpty().trim('/').split('/')
        // `inner` is never omitted (LIVE-BROADCAST J): without it the path the player needs cannot be rebuilt.
        val valid = segments.size == TUNNEL_PATH_SEGMENTS && segments[0] == TUNNEL_PATH_PREFIX &&
            segments[1].isNotEmpty() && segments[2].lowercase() in TUNNEL_SCHEMES &&
            !inner.isNullOrEmpty() && uriOrNull(inner)?.scheme != null
        return if (valid) {
            BroadcastExchangeEndpoint.Tunnel(server, segments[1], segments[2].lowercase(), inner.orEmpty(), fingerprint)
        } else {
            null
        }
    }

    /**
     * [inner] with its host and port replaced by the loopback forwarder's, scheme, path and query kept
     * (`DEVICE-EXCHANGE` 7.7) - the consumer never dials the host of `inner` itself.
     */
    fun rewriteInner(inner: String, forwarderPort: Int): String? =
        uriOrNull(inner)?.takeIf { it.scheme != null }?.let { loopback(it, it.scheme, forwarderPort) }

    /** The relay URL as the player sees it behind the pinning proxy: the same path on the loopback port. */
    fun loopbackRelayUrl(relay: BroadcastExchangeEndpoint.Relay, proxyPort: Int): String? =
        uriOrNull(relay.url)?.let { loopback(it, LOOPBACK_SCHEME, proxyPort) }

    private fun loopback(uri: URI, scheme: String, port: Int): String {
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$scheme://$LOOPBACK_HOST:$port${uri.rawPath.orEmpty()}$query"
    }

    /**
     * A `broadcastId` is a capability (`DEVICE-EXCHANGE` 1.2), so a log names it only by the first 8 hex of its
     * SHA-256 (7.9, item AF); neither the id nor a URL carrying it is ever logged.
     */
    fun redact(broadcastId: String): String =
        MessageDigest.getInstance("SHA-256").digest(broadcastId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(REDACTED_HEX_CHARS)

    private fun serverOf(uri: URI): ExchangeServer? {
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: ExchangeServer.DEFAULT_TLS_PORT
        return ExchangeServer(host, port)
    }

    // A stored value that is not a URI is an endpoint this app cannot use, skipped like an unknown transport.
    private fun uriOrNull(value: String): URI? = runCatching { URI(value) }.getOrNull()
}
