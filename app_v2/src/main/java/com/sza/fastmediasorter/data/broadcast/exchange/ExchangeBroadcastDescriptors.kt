package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.BroadcastPendingDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSealDto
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import java.net.URI
import java.time.Instant

/**
 * The producer half of contract DEVICE-EXCHANGE 7.7: a live descriptor with the exchange endpoints added in the
 * order a consumer tries them - the LAN endpoints first, then `RELAY` while an upload runs, then one `TUNNEL` per
 * LAN service. The same list goes into the directory record, the share link and the QR code (S4160).
 */
object ExchangeBroadcastDescriptors {

    const val EXCHANGE_TARGET_LATENCY_MS = 2_000L
    private const val AUDIO_CODEC = "aac"
    private const val VIDEO_CODEC = "h264"

    /**
     * [publicEndpoint] is the `host:port` every client reaches the server by (`welcome.publicEndpoint`);
     * [certFingerprint] is written only for a deployment whose certificate the user accepted by its pin.
     */
    fun withExchange(
        lan: BroadcastDescriptorDto,
        broadcastId: String,
        publicEndpoint: String,
        certFingerprint: String?,
        relayUploading: Boolean,
        sealed: Boolean = true,
    ): BroadcastDescriptorDto {
        val direct = lanEndpoints(lan)
        val relay = BroadcastEndpointDto(
            url = "https://$publicEndpoint/v2/b/$broadcastId/stream",
            transport = BroadcastExchangeEndpoints.TRANSPORT_RELAY,
            mode = lan.mode,
            videoCodec = direct.firstOrNull()?.videoCodec,
            audioCodec = direct.firstOrNull()?.audioCodec,
            isLive = true,
            targetLatencyMs = EXCHANGE_TARGET_LATENCY_MS,
            certFingerprint = certFingerprint,
            seal = BroadcastSealDto(),
        ).takeIf { relayUploading }
        val tunnels = direct.mapNotNull { endpoint ->
            schemeOf(endpoint.url)?.let { scheme ->
                endpoint.copy(
                    url = "fmsx://$publicEndpoint/b/$broadcastId/$scheme",
                    transport = BroadcastExchangeEndpoints.TRANSPORT_TUNNEL,
                    isLive = true,
                    targetLatencyMs = EXCHANGE_TARGET_LATENCY_MS,
                    inner = endpoint.url,
                    certFingerprint = certFingerprint,
                    seal = BroadcastSealDto(),
                )
            }
            // 27 item DP: a server without `sealed` is offered no broadcast tunnel.
        }.distinctBy { it.url }.takeIf { sealed }.orEmpty()
        return lan.copy(endpoints = direct + listOfNotNull(relay) + tunnels)
    }

    /**
     * LIVE-BROADCAST item S: the descriptor of a broadcast that has not started yet. No LAN endpoint exists before
     * the start, so the top-level `url` is the `RELAY` address and a `TUNNEL` keeps the mode's scheme and path on
     * the documentation host (item J). The caller adds `title`, `sourceId` and `auth` with `copy`, and the keys with
     * [withOutwardKeys].
     */
    fun pending(
        broadcastId: String,
        publicEndpoint: String,
        certFingerprint: String?,
        mode: String,
        sealed: Boolean,
        untilMs: Long,
    ): BroadcastDescriptorDto {
        val audioOnly = mode == BroadcastMode.AUDIO_ONLY.name
        val audioCodec = if (mode == BroadcastMode.VIDEO_ONLY.name) null else AUDIO_CODEC
        val videoCodec = if (audioOnly) null else VIDEO_CODEC
        val relay = BroadcastEndpointDto(
            url = "https://$publicEndpoint/v2/b/$broadcastId/stream",
            transport = BroadcastExchangeEndpoints.TRANSPORT_RELAY,
            mode = mode,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            isLive = true,
            targetLatencyMs = EXCHANGE_TARGET_LATENCY_MS,
            certFingerprint = certFingerprint,
            seal = BroadcastSealDto(),
        )
        val scheme =
            if (audioOnly) ExchangeBroadcastPublication.SCHEME_HTTP else ExchangeBroadcastPublication.SCHEME_RTSP
        val tunnel = relay.copy(
            url = "fmsx://$publicEndpoint/b/$broadcastId/$scheme",
            transport = BroadcastExchangeEndpoints.TRANSPORT_TUNNEL,
            inner = if (audioOnly) {
                BroadcastExchangeEndpoints.PENDING_INNER_AUDIO
            } else {
                BroadcastExchangeEndpoints.PENDING_INNER_VIDEO
            },
        ).takeIf { sealed }
        return BroadcastDescriptorDto(
            url = relay.url,
            mode = mode,
            endpoints = listOfNotNull(relay, tunnel),
            isLive = true,
            targetLatencyMs = EXCHANGE_TARGET_LATENCY_MS,
            pending = BroadcastPendingDto(Instant.ofEpochMilli(untilMs).toString()),
        )
    }

    /**
     * 27 item DK: the outward form of [descriptor] - the one a person hands over as a link, a file or a barcode -
     * with the keys written into its sealed endpoints: [relayKey] (base64url) on `RELAY`, [tunnelPin] on `TUNNEL`.
     * A sealed endpoint whose key is missing is left out, since nobody holding only this descriptor could play it.
     * It never goes through the server.
     */
    fun withOutwardKeys(
        descriptor: BroadcastDescriptorDto,
        relayKey: String?,
        tunnelPin: String?,
    ): BroadcastDescriptorDto {
        val endpoints = descriptor.endpoints?.mapNotNull { endpoint ->
            val seal = endpoint.seal
            when {
                seal == null -> endpoint
                endpoint.transport.equals(BroadcastExchangeEndpoints.TRANSPORT_RELAY, ignoreCase = true) ->
                    relayKey?.let { endpoint.copy(seal = seal.copy(key = it)) }
                endpoint.transport.equals(BroadcastExchangeEndpoints.TRANSPORT_TUNNEL, ignoreCase = true) ->
                    tunnelPin?.let { endpoint.copy(seal = seal.copy(pin = it)) }
                else -> endpoint
            }
        }
        return descriptor.copy(endpoints = endpoints)
    }

    /** The loopback port of each LAN service by tunnel scheme, which an `open` for the broadcast reaches (7.5). */
    fun localPorts(lan: BroadcastDescriptorDto): Map<String, Int> = lanEndpoints(lan)
        .mapNotNull { endpoint ->
            val uri = runCatching { URI(endpoint.url) }.getOrNull()
            val scheme = schemeOf(endpoint.url)
            val port = uri?.port?.takeIf { it > 0 }
            if (scheme != null && port != null) scheme to port else null
        }
        .toMap()

    private fun lanEndpoints(lan: BroadcastDescriptorDto): List<BroadcastEndpointDto> =
        lan.getEffectiveEndpoints().filter {
            it.transport.uppercase() in LAN_TRANSPORTS
        }

    private fun schemeOf(url: String): String? =
        runCatching { URI(url).scheme?.lowercase() }.getOrNull()?.takeIf { it in TUNNEL_SCHEMES }

    private val LAN_TRANSPORTS =
        setOf(BroadcastExchangeEndpoints.TRANSPORT_HTTP, BroadcastExchangeEndpoints.TRANSPORT_RTSP)
    private val TUNNEL_SCHEMES =
        setOf(ExchangeBroadcastPublication.SCHEME_HTTP, ExchangeBroadcastPublication.SCHEME_RTSP)
}
