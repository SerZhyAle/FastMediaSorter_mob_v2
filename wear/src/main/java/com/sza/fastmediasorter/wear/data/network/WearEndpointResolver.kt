package com.sza.fastmediasorter.wear.data.network

import android.net.Network
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.model.WearEndpointGroup
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** S4228: how an SFTP session reaches its folder - one of its addresses, or a tunnel of the watch's own account. */
sealed interface WearSftpRoute {

    /** The source to open, with `server`/`port` set to the address that answered when the route is direct. */
    val source: NetworkSource

    data class Direct(override val source: NetworkSource) : WearSftpRoute

    /** Contract DEVICE-EXCHANGE 7.5 through [entry], the watch's entry of the account that publishes the folder. */
    data class Tunnel(
        override val source: NetworkSource,
        val entry: WearExchangeEntry,
        val resourceId: String,
    ) : WearSftpRoute
}

/**
 * S4228: an exchange folder that answers at none of its addresses, and the watch holds no entry of the account it
 * belongs to - so there is no tunnel to try. The browse error says to connect the watch to the server on the phone.
 */
class WearExchangeEntryMissingException : IOException("no exchange entry of the folder's account on this watch")

/** S4228: the watch is online only through the phone's Bluetooth proxy; an exchange folder needs Wi-Fi or LTE. */
class WearExchangeNeedsOwnNetworkException : IOException("exchange folders open only over the watch's own network")

/**
 * S2488: picks the address a source actually answers at, before a session is opened.
 *
 * The companion's listening endpoint moves - measured on the owner's machine, its stored port and the
 * port it listens on differed - and a watch holding one frozen pair has no way to notice. The winner
 * is remembered per network link, so only a cold open pays a probe round and browsing pays nothing.
 */
@Singleton
class WearEndpointResolver @Inject constructor(
    private val channelMonitor: WearNetworkChannelMonitor,
    private val entryStore: WearExchangeEntryStore
) {

    private data class CachedWinner(val endpoint: WearEndpoint, val channel: WearNetworkChannel)

    /** S4228: the route an exchange folder took on [channel]; a null [endpoint] means the tunnel. */
    private data class CachedRoute(val endpoint: WearEndpoint?, val channel: WearNetworkChannel)

    private val winnerBySourceId = ConcurrentHashMap<String, CachedWinner>()
    private val routeBySourceId = ConcurrentHashMap<String, CachedRoute>()

    /**
     * Returns [source] with `server`/`port` set to the reachable endpoint of its group. A group of one
     * is returned untouched and never probed. When nothing answers, the group's first element is used
     * so the real connect error surfaces at the session instead of being hidden here.
     */
    suspend fun resolve(source: NetworkSource, network: Network? = null): NetworkSource {
        val candidates = WearEndpointGroup.candidatesFor(source)
        if (candidates.size <= 1) return source

        // The value is read, never collected: a resolver that owned a scope would outlive its callers.
        val currentChannel = channelMonitor.channel.value
        // S4230: a probe on a granted network never trusts a winner measured on the default route, which on a
        // watch is often the Bluetooth proxy; the fresh winner is still remembered for the channel.
        val cached = winnerBySourceId[source.id]?.takeIf { network == null }
        val winner = if (cached != null && cached.channel == currentChannel) {
            cached.endpoint
        } else {
            val probed = probe(candidates, network) ?: candidates.first()
            winnerBySourceId[source.id] = CachedWinner(probed, currentChannel)
            probed
        }
        return source.copy(server = winner.host, port = winner.port)
    }

    /**
     * S4228: the route of an SFTP session (contract DEVICE-EXCHANGE 20 item BB). A source with no exchange binding
     * resolves as [resolve] does. An exchange folder tries its direct addresses first - even a single one, since the
     * tunnel is the alternative - then the tunnel through the watch's entry of the same account. The choice is
     * remembered per network link, as the address winner is.
     *
     * @throws WearExchangeEntryMissingException when no address answered and the watch holds no entry to tunnel by.
     */
    suspend fun route(source: NetworkSource, network: Network? = null): WearSftpRoute {
        val account = source.exchangeAccount
        val resourceId = source.exchangeResourceId
        if (account == null || resourceId == null) return WearSftpRoute.Direct(resolve(source, network))

        val currentChannel = channelMonitor.channel.value
        // Contract 23 item CF: an exchange folder's data never rides the Bluetooth proxy; a granted network is not it.
        if (network == null && currentChannel.kind == WearNetworkChannelKind.BLUETOOTH) {
            throw WearExchangeNeedsOwnNetworkException()
        }
        val cached = routeBySourceId[source.id]?.takeIf { network == null && it.channel == currentChannel }
        val endpoint = if (cached != null) cached.endpoint else probe(WearEndpointGroup.candidatesFor(source), network)
        val tunnelEntry = if (endpoint == null) entryStore.all().firstOrNull { it.identity == account } else null
        if (endpoint == null && tunnelEntry == null) {
            routeBySourceId.remove(source.id)
            throw WearExchangeEntryMissingException()
        }
        routeBySourceId[source.id] = CachedRoute(endpoint, currentChannel)
        return if (tunnelEntry != null) {
            WearSftpRoute.Tunnel(source, tunnelEntry, resourceId)
        } else {
            val winner = checkNotNull(endpoint)
            WearSftpRoute.Direct(source.copy(server = winner.host, port = winner.port))
        }
    }

    /** S4228: drops the remembered route of [sourceId] after it failed, so the next open looks again. */
    fun forgetRoute(sourceId: String) {
        routeBySourceId.remove(sourceId)
    }

    /**
     * Probes every candidate at once, prefers the first one when it answers inside the grace window,
     * otherwise takes the first candidate in contract order that answered.
     */
    private suspend fun probe(candidates: List<WearEndpoint>, network: Network?): WearEndpoint? = coroutineScope {
        val jobs = candidates.map { candidate ->
            async(Dispatchers.IO) { if (isReachable(candidate, network)) candidate else null }
        }
        try {
            withTimeoutOrNull(PREFERRED_GRACE_MS) { jobs.first().await() }?.let { return@coroutineScope it }
            jobs.mapNotNull { it.await() }.firstOrNull()
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    private suspend fun isReachable(endpoint: WearEndpoint, network: Network?): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                network?.bindSocket(socket)
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), PROBE_TIMEOUT_MS)
            }
            true
        } catch (e: IOException) {
            Timber.d("Endpoint probe failed for ${endpoint.host}:${endpoint.port}: ${e.message}")
            false
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 2500
        const val PREFERRED_GRACE_MS = 600L
    }
}
