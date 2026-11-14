package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback.Reason
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Whether a LAN endpoint serves from where the watch is now; a watch on LTE or another Wi-Fi gets `false`. */
interface WearLanEndpointProbe {

    fun answers(endpoint: WearBroadcastEndpoint.Lan): Boolean
}

/**
 * A LAN endpoint answers when its first request is met with a status line below 400. A TCP connect alone proves
 * nothing: a listening socket whose owner serves nothing completes the handshake just the same, and the player then
 * waits out its read timeout instead of the resolver moving on to the relay. Only the status line is read and the
 * socket is closed, so the probe never holds the producer's listener slot.
 */
@Singleton
class SocketWearLanEndpointProbe internal constructor(
    private val connectTimeoutMs: Int,
    private val statusTimeoutMs: Int,
) : WearLanEndpointProbe {

    @Inject
    constructor() : this(CONNECT_TIMEOUT_MS, STATUS_TIMEOUT_MS)

    override fun answers(endpoint: WearBroadcastEndpoint.Lan): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMs)
            socket.soTimeout = statusTimeoutMs
            socket.getOutputStream().apply {
                write(firstRequest(endpoint).toByteArray(Charsets.US_ASCII))
                flush()
            }
            isServing(statusLine(socket.getInputStream()))
        }
    } catch (e: IOException) {
        Timber.v("SocketWearLanEndpointProbe: a LAN endpoint did not answer - %s", e.javaClass.simpleName)
        false
    }

    private fun firstRequest(endpoint: WearBroadcastEndpoint.Lan): String {
        val uri = runCatching { URI(endpoint.url) }.getOrNull()
        return if (uri?.scheme.equals(SCHEME_RTSP, ignoreCase = true)) {
            "DESCRIBE ${endpoint.url} RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\n\r\n"
        } else {
            val path = uri?.rawPath.orEmpty().ifEmpty { "/" } + uri?.rawQuery?.let { "?$it" }.orEmpty()
            "GET $path HTTP/1.1\r\nHost: ${uri?.rawAuthority ?: endpoint.host}\r\nConnection: close\r\n\r\n"
        }
    }

    // Bounded byte by byte: a peer that streams without a newline must not hold the resolver past its read timeout.
    private fun statusLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < MAX_STATUS_LINE) {
            val next = input.read()
            if (next < 0 || next == LINE_FEED) {
                break
            }
            line.append(next.toChar())
        }
        return line.toString().trimEnd('\r')
    }

    // `HTTP/1.1 200 OK` and `RTSP/1.0 200 OK` alike: the second token is the status.
    private fun isServing(line: String): Boolean {
        val status = line.split(' ', limit = STATUS_PARTS).getOrNull(1)?.toIntOrNull()
        val serving = status != null && status in 1 until FIRST_ERROR_STATUS
        if (!serving) {
            Timber.v("SocketWearLanEndpointProbe: a LAN endpoint accepted and sent no usable status (%s)", status)
        }
        return serving
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 1_500
        const val STATUS_TIMEOUT_MS = 3_000
        const val MAX_STATUS_LINE = 64
        const val LINE_FEED = '\n'.code
        const val STATUS_PARTS = 3
        const val FIRST_ERROR_STATUS = 400
        const val SCHEME_RTSP = "rtsp"
    }
}

/**
 * Turns one directory record into what a player opens (contract DEVICE-EXCHANGE 7.7 and 23 items CH and CI,
 * LIVE-BROADCAST item M). The endpoints are tried in the producer's order, LAN first and then the relay; a
 * player walks on from a failed endpoint, and a reconnect starts at the top again because 7.7 requires it. A LAN
 * endpoint is passed over unless it answers its first request, so a phone that accepts connections and serves
 * nothing sends the watch on to the relay.
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

    /**
     * Resolves [record] from the playable endpoint at [startAt]; a player whose endpoint failed passes the index after
     * it, a reconnect passes 0 (S4610). Past the last endpoint the broadcast is unreachable.
     */
    suspend fun resolve(record: WearDirectoryBroadcast, startAt: Int = 0): WearBroadcastPlayback =
        withContext(Dispatchers.IO) {
            resolveNow(record, startAt)
        }

    private fun resolveNow(record: WearDirectoryBroadcast, startAt: Int): WearBroadcastPlayback {
        val schema = WearBroadcastEndpoints.schemaVersion(record.descriptor)
        if (schema > WearBroadcastEndpoints.SUPPORTED_SCHEMA_VERSION) {
            Timber.w(
                "WearBroadcastPlaybackResolver: LIVE-BROADCAST descriptor schemaVersion %d is unsupported " +
                    "(format-unsupported)",
                schema,
            )
            return WearBroadcastPlayback.Unavailable(Reason.FORMAT_UNSUPPORTED)
        }
        val all = WearBroadcastEndpoints.parse(record.broadcastId, record.descriptor, record.mode)
        val playable = all.filter { WearBroadcastEndpoints.isPlayableMode(it.mode ?: record.mode) }
        return when {
            all.isEmpty() -> WearBroadcastPlayback.Unavailable(Reason.NO_ENDPOINT)
            playable.isEmpty() -> WearBroadcastPlayback.Unavailable(Reason.MODE)
            else -> pick(record, playable, startAt)
        }
    }

    private fun pick(
        record: WearDirectoryBroadcast,
        endpoints: List<WearBroadcastEndpoint>,
        startAt: Int,
    ): WearBroadcastPlayback {
        val isProtected = WearBroadcastEndpoints.isProtected(record.descriptor)
        val playback = endpoints.indices.drop(startAt.coerceAtLeast(0)).firstNotNullOfOrNull { index ->
            val route = WearBroadcastRoute(record, index)
            when (val endpoint = endpoints[index]) {
                is WearBroadcastEndpoint.Lan -> lan(route, endpoint, isProtected)
                is WearBroadcastEndpoint.Relay -> relay(route, endpoint, isProtected)
            }
        }
        val missing = if (isProtected) Reason.PROTECTED else Reason.UNREACHABLE
        return playback ?: WearBroadcastPlayback.Unavailable(missing)
    }

    private fun lan(
        route: WearBroadcastRoute,
        endpoint: WearBroadcastEndpoint.Lan,
        isProtected: Boolean,
    ): WearBroadcastPlayback? {
        val serving = !isProtected && probe.answers(endpoint)
        return if (serving) {
            playable(route, endpoint, endpoint.url)
        } else {
            null
        }
    }

    private fun relay(route: WearBroadcastRoute, endpoint: WearBroadcastEndpoint.Relay, isProtected: Boolean) =
        if (isProtected && route.record.credentials == null) {
            // The challenge cannot be answered, and an unauthenticated request would be refused anyway (item M).
            WearBroadcastPlayback.Unavailable(Reason.PROTECTED)
        } else {
            openRelay(route, endpoint, route.record.credentials?.takeIf { isProtected })
        }

    private fun openRelay(
        route: WearBroadcastRoute,
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
        playable(route, endpoint, url)
    } catch (e: IOException) {
        Timber.w("WearBroadcastPlaybackResolver: no loopback bridge - %s", e.javaClass.simpleName)
        WearBroadcastPlayback.Unavailable(Reason.UNREACHABLE)
    }

    private fun playable(route: WearBroadcastRoute, endpoint: WearBroadcastEndpoint, url: String) =
        WearBroadcastPlayback.Playable(
            url = url,
            title = route.record.title,
            isVideo = WearBroadcastEndpoints.isVideo(endpoint.mode ?: route.record.mode),
            route = route,
        )

    private fun basicHeader(credentials: WearBroadcastCredentials): String {
        val pair = "${credentials.user}:${credentials.password}".toByteArray(Charsets.UTF_8)
        return "Authorization: Basic ${Base64.getEncoder().encodeToString(pair)}"
    }
}
