package com.sza.fastmediasorter.wear.data.exchange

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live loopback bridges of a broadcast picked from the account's list (contract DEVICE-EXCHANGE 7.7). A player
 * reconnects to the same local port for the whole life of a playback, so a bridge outlives one connection; only the
 * newest [MAX_BRIDGES] are kept, because each one holds a listening port and a thread pool and a person who taps
 * through the list must not leave one behind per tap.
 *
 * A bridge that carries a request header holds a password in memory and is never reused for another pick.
 */
@Singleton
class WearExchangeRelayBridges @Inject constructor(
    private val connector: WearExchangeConnector,
) {

    private val bridges = ArrayDeque<Held>()

    /**
     * The loopback URL a player opens for [endpoint]; each connection the player makes becomes one pinned TLS stream
     * to the server. [requestHeader] is one complete header line added to every request, or null.
     *
     * @throws IOException when no local port could be bound.
     */
    @Synchronized
    @Throws(IOException::class)
    fun open(endpoint: WearBroadcastEndpoint.Relay, requestHeader: String?): String {
        val key = "${endpoint.host}:${endpoint.port}${endpoint.path}|${endpoint.certFingerprint}"
        val reusable = if (requestHeader == null) bridges.firstOrNull { it.key == key && !it.hasHeader } else null
        val held = reusable ?: create(key, endpoint, requestHeader)
        return "http://${WearExchangeRelayEndpoint.LOOPBACK_HOST}:${held.bridge.port}${endpoint.path}"
    }

    /**
     * S4240: the loopback URL of the opening proxy for a sealed [endpoint] (27 item DL), which answers the player
     * with clear media. It holds the group key, so it is never reused for another pick.
     *
     * @throws IOException when no local port could be bound.
     */
    @Synchronized
    @Throws(IOException::class)
    fun openSealed(endpoint: WearBroadcastEndpoint.Relay, groupKey: ByteArray, requestHeader: String?): String {
        val proxy = WearSealedRelayProxy(
            label = LABEL,
            path = endpoint.path,
            host = if (endpoint.port == HTTPS_PORT) endpoint.host else "${endpoint.host}:${endpoint.port}",
            broadcastId = endpoint.broadcastId,
            groupKey = groupKey,
            requestHeader = requestHeader,
        ) {
            connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint)
        }
        keep(Held(key = null, hasHeader = true, bridge = proxy))
        return "http://${WearExchangeRelayEndpoint.LOOPBACK_HOST}:${proxy.port}${endpoint.path}"
    }

    @Synchronized
    fun closeAll() {
        bridges.forEach { it.bridge.close() }
        bridges.clear()
    }

    private fun create(key: String, endpoint: WearBroadcastEndpoint.Relay, requestHeader: String?): Held {
        val bridge = WearExchangeLoopbackBridge(LABEL, requestHeader) {
            connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint)
        }
        return keep(Held(key, requestHeader != null, bridge))
    }

    private fun keep(held: Held): Held {
        bridges.addLast(held)
        while (bridges.size > MAX_BRIDGES) bridges.removeFirst().bridge.close()
        return held
    }

    private class Held(val key: String?, val hasHeader: Boolean, val bridge: WearLoopbackPort)

    internal companion object {
        const val MAX_BRIDGES = 2
        private const val LABEL = "list"
        private const val HTTPS_PORT = 443
    }
}
