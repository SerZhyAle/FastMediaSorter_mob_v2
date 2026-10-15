package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback.Reason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Whether a LAN endpoint answers from where the watch is now; a watch on LTE or another Wi-Fi gets `false`. */
interface WearLanEndpointProbe {

    fun answers(host: String, port: Int): Boolean
}

@Singleton
class SocketWearLanEndpointProbe @Inject constructor() : WearLanEndpointProbe {

    override fun answers(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS) }
        true
    } catch (e: IOException) {
        Timber.v("SocketWearLanEndpointProbe: a LAN endpoint did not answer - %s", e.javaClass.simpleName)
        false
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 1_500
    }
}

/**
 * Turns one directory record into what a player opens (contract DEVICE-EXCHANGE 7.7 and 23 items CH and CI,
 * LIVE-BROADCAST item M). The endpoints are tried in the producer's order, LAN first and then the relay, and
 * every call starts at the top again because a reconnect must (7.7).
 *
 * A protected broadcast is answered only on the relay, with the Basic header the pinning proxy adds inside TLS:
 * the watch does not run Digest on the LAN endpoints and never plays one without the challenge, and the password
 * reaches neither the URL nor a log. No password is ever typed on the watch (item CF), so a protected record
 * without `credentials` is unavailable and not a prompt.
 */
@Singleton
class WearBroadcastPlaybackResolver @Inject constructor(
    private val bridges: WearExchangeRelayBridges,
    private val probe: WearLanEndpointProbe,
) {

    suspend fun resolve(record: WearDirectoryBroadcast): WearBroadcastPlayback = withContext(Dispatchers.IO) {
        val all = WearBroadcastEndpoints.parse(record.broadcastId, record.descriptor, record.mode)
        val playable = all.filter { WearBroadcastEndpoints.isPlayableMode(it.mode ?: record.mode) }
        when {
            all.isEmpty() -> WearBroadcastPlayback.Unavailable(Reason.NO_ENDPOINT)
            playable.isEmpty() -> WearBroadcastPlayback.Unavailable(Reason.MODE)
            else -> pick(record, playable)
        }
    }

    private fun pick(record: WearDirectoryBroadcast, endpoints: List<WearBroadcastEndpoint>): WearBroadcastPlayback {
        val isProtected = WearBroadcastEndpoints.isProtected(record.descriptor)
        val playback = endpoints.firstNotNullOfOrNull { endpoint ->
            when (endpoint) {
                is WearBroadcastEndpoint.Lan -> lan(record, endpoint, isProtected)
                is WearBroadcastEndpoint.Relay -> relay(record, endpoint, isProtected)
            }
        }
        val missing = if (isProtected) Reason.PROTECTED else Reason.UNREACHABLE
        Timber.d("S4229: a listed broadcast resolved - playable=${playback is WearBroadcastPlayback.Playable}")
        return playback ?: WearBroadcastPlayback.Unavailable(missing)
    }

    private fun lan(record: WearDirectoryBroadcast, endpoint: WearBroadcastEndpoint.Lan, isProtected: Boolean) =
        if (!isProtected && probe.answers(endpoint.host, endpoint.port)) {
            playable(record, endpoint, endpoint.url)
        } else {
            null
        }

    private fun relay(record: WearDirectoryBroadcast, endpoint: WearBroadcastEndpoint.Relay, isProtected: Boolean) =
        if (isProtected && record.credentials == null) {
            // The challenge cannot be answered, and an unauthenticated request would be refused anyway (item M).
            WearBroadcastPlayback.Unavailable(Reason.PROTECTED)
        } else {
            openRelay(record, endpoint, record.credentials?.takeIf { isProtected })
        }

    private fun openRelay(
        record: WearDirectoryBroadcast,
        endpoint: WearBroadcastEndpoint.Relay,
        credentials: WearBroadcastCredentials?,
    ): WearBroadcastPlayback = try {
        val header = credentials?.let(::basicHeader)
        // 27 item DL: a sealed relay is no stream for any player, so it plays only through the opening proxy.
        val groupKey = endpoint.groupKey?.let(E2eBytes::fromB64url)
        val url = if (groupKey == null) {
            bridges.open(endpoint, header)
        } else {
            bridges.openSealed(endpoint, groupKey, header)
        }
        playable(record, endpoint, url)
    } catch (e: IOException) {
        Timber.w("WearBroadcastPlaybackResolver: no loopback bridge - %s", e.javaClass.simpleName)
        WearBroadcastPlayback.Unavailable(Reason.UNREACHABLE)
    }

    private fun playable(record: WearDirectoryBroadcast, endpoint: WearBroadcastEndpoint, url: String) =
        WearBroadcastPlayback.Playable(url, record.title, WearBroadcastEndpoints.isVideo(endpoint.mode ?: record.mode))

    private fun basicHeader(credentials: WearBroadcastCredentials): String {
        val pair = "${credentials.user}:${credentials.password}".toByteArray(Charsets.UTF_8)
        return "Authorization: Basic ${Base64.getEncoder().encodeToString(pair)}"
    }
}
