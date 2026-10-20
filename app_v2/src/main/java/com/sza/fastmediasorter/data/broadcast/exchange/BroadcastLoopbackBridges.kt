package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackPairs
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4160: the live loopback bridges of the process, one per exchange endpoint. A player reconnects to the same
 * local port for the whole life of a channel, so a bridge outlives one playback and is reused; only the most
 * recent [MAX_BRIDGES] are kept, the oldest closed, since each one holds a listening port and a thread.
 */
@Singleton
class BroadcastLoopbackBridges @Inject constructor(
    private val connector: ExchangeConnector,
) {

    private val bridges = object : LinkedHashMap<String, LoopbackEndpoint>(MAX_BRIDGES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoopbackEndpoint>): Boolean {
            val evict = size > MAX_BRIDGES
            if (evict) eldest.value.close()
            return evict
        }
    }

    /**
     * The loopback port of the forwarder for [tunnel]; each accepted connection is one `connect {broadcastId}`. A
     * sealed tunnel runs a TLS client inside that stream which accepts only the leaf pinned by `tunnelPin` (27 item
     * DM); a different leaf ends the connection.
     */
    @Throws(IOException::class)
    fun tunnelPort(tunnel: BroadcastExchangeEndpoint.Tunnel): Int {
        val key = "tunnel|${tunnel.server.label}|${tunnel.scheme}|${tunnel.certFingerprint}|${tunnel.broadcastId}" +
            "|${tunnel.tunnelPin}"
        return portFor(key, label("tunnel", tunnel.server, tunnel.broadcastId)) {
            val stream = DeviceExchangeAccountClient.connectByBroadcastId(
                connector = connector,
                host = tunnel.server.host,
                port = tunnel.server.port,
                broadcastId = tunnel.broadcastId,
                scheme = tunnel.scheme,
                trust = ExchangeTrust.Verified(tunnel.certFingerprint),
            )
            tunnel.tunnelPin?.let { pin ->
                val tls = BroadcastTunnelTls.connect(pin, stream.input, stream.output, stream)
                ExchangeConnection(tls, pin)
            } ?: stream
        }
    }

    /** The loopback port of the pinning proxy for [relay]; each accepted connection is one pinned TLS stream. */
    @Throws(IOException::class)
    fun relayPort(relay: BroadcastExchangeEndpoint.Relay): Int {
        val key = "relay|${relay.server.label}|${relay.certFingerprint}|${relay.broadcastId}"
        return portFor(key, label("relay", relay.server, relay.broadcastId)) {
            connector.connect(relay.server.host, relay.server.port, ExchangeTrust.Verified(relay.certFingerprint))
        }
    }

    /**
     * The loopback port of the opening proxy for a sealed [relay] (27 item DL): each accepted connection is its own
     * listen stream through the server's TLS, answered with clear media.
     */
    @Throws(IOException::class)
    @Synchronized
    fun sealedRelayPort(relay: BroadcastExchangeEndpoint.Relay, groupKey: ByteArray): Int {
        // A new box brings a new group key, and a proxy holding the old one could open no later epoch.
        val keyId = E2eBytes.hex(E2eRelayRecords.keyId(groupKey))
        val key = "sealed|${relay.server.label}|${relay.certFingerprint}|${relay.broadcastId}|$keyId"
        val path = runCatching { URI(relay.url).rawPath }.getOrNull().orEmpty()
        return bridges.getOrPut(key) {
            SealedRelayProxy(
                label = label("sealed relay", relay.server, relay.broadcastId),
                path = path,
                host = relay.server.label,
                broadcastId = relay.broadcastId,
                groupKey = groupKey,
                // The pair is read per listen stream, so a password typed after the proxy opened still reaches it.
                authorization = {
                    BroadcastPlaybackPairs.forUrl(relay.url)
                        ?.let { "${BroadcastDigest.HEADER_AUTHORIZATION}: ${BroadcastDigest.basic(it)}" }
                },
            ) {
                connector.connect(relay.server.host, relay.server.port, ExchangeTrust.Verified(relay.certFingerprint))
            }
        }.port
    }

    @Synchronized
    private fun portFor(key: String, label: String, opener: () -> ExchangeConnection): Int =
        bridges.getOrPut(key) { BroadcastLoopbackBridge(label, opener) }.port

    // The key holds the broadcast id and is never logged; the label names it by its redacted form only.
    private fun label(kind: String, server: ExchangeServer, broadcastId: String) =
        "$kind ${server.label} ${BroadcastExchangeEndpoints.redact(broadcastId)}"

    private companion object {
        const val MAX_BRIDGES = 4
        const val LOAD_FACTOR = 0.75f
    }
}
