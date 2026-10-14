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

    @Synchronized
    fun closeAll() {
        bridges.forEach { it.bridge.close() }
        bridges.clear()
    }

    private fun create(key: String, endpoint: WearBroadcastEndpoint.Relay, requestHeader: String?): Held {
        val bridge = WearExchangeLoopbackBridge(LABEL, requestHeader) {
            connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint)
        }
        val held = Held(key, requestHeader != null, bridge)
        bridges.addLast(held)
        while (bridges.size > MAX_BRIDGES) bridges.removeFirst().bridge.close()
        return held
    }

    private class Held(val key: String, val hasHeader: Boolean, val bridge: WearExchangeLoopbackBridge)

    internal companion object {
        const val MAX_BRIDGES = 2
        private const val LABEL = "list"
    }
}
