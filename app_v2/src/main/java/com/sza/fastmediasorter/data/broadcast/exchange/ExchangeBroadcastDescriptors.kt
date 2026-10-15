package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSealDto
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import java.net.URI

/**
 * The producer half of contract DEVICE-EXCHANGE 7.7: a live descriptor with the exchange endpoints added in the
 * order a consumer tries them - the LAN endpoints first, then `RELAY` while an upload runs, then one `TUNNEL` per
 * LAN service. The same list goes into the directory record, the share link and the QR code (S4160).
 */
object ExchangeBroadcastDescriptors {

    const val EXCHANGE_TARGET_LATENCY_MS = 2_000L

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
