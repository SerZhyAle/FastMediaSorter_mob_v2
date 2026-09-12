package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.model.WearEndpointGroup
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
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

/**
 * S2488: picks the address a source actually answers at, before a session is opened.
 *
 * The companion's listening endpoint moves - measured on the owner's machine, its stored port and the
 * port it listens on differed - and a watch holding one frozen pair has no way to notice. The winner
 * is remembered per network link, so only a cold open pays a probe round and browsing pays nothing.
 */
@Singleton
class WearEndpointResolver @Inject constructor(
    private val channelMonitor: WearNetworkChannelMonitor
) {

    private data class CachedWinner(val endpoint: WearEndpoint, val channel: WearNetworkChannel)

    private val winnerBySourceId = ConcurrentHashMap<String, CachedWinner>()

    /**
     * Returns [source] with `server`/`port` set to the reachable endpoint of its group. A group of one
     * is returned untouched and never probed. When nothing answers, the group's first element is used
     * so the real connect error surfaces at the session instead of being hidden here.
     */
    suspend fun resolve(source: NetworkSource): NetworkSource {
        val candidates = WearEndpointGroup.candidatesFor(source)
        if (candidates.size <= 1) return source

        Timber.d("S2488: resolving endpoints for source %s from %d candidates", source.id, candidates.size)

        // The value is read, never collected: a resolver that owned a scope would outlive its callers.
        val currentChannel = channelMonitor.channel.value
        val cached = winnerBySourceId[source.id]
        val winner = if (cached != null && cached.channel == currentChannel) {
            cached.endpoint
        } else {
            val probed = probe(candidates) ?: candidates.first()
            winnerBySourceId[source.id] = CachedWinner(probed, currentChannel)
            probed
        }
        return source.copy(server = winner.host, port = winner.port)
    }

    /**
     * Probes every candidate at once, prefers the first one when it answers inside the grace window,
     * otherwise takes the first candidate in contract order that answered.
     */
    private suspend fun probe(candidates: List<WearEndpoint>): WearEndpoint? = coroutineScope {
        val jobs = candidates.map { candidate ->
            async(Dispatchers.IO) { if (isReachable(candidate)) candidate else null }
        }
        try {
            withTimeoutOrNull(PREFERRED_GRACE_MS) { jobs.first().await() }?.let { return@coroutineScope it }
            jobs.mapNotNull { it.await() }.firstOrNull()
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    private suspend fun isReachable(endpoint: WearEndpoint): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
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
