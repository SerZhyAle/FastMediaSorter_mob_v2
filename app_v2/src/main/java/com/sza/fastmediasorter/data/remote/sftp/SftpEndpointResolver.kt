package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
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
 * S1006: picks the reachable SFTP endpoint for a resource that carries more than one access path
 * (companion resources import a LAN address and an internet/port-forward address). Given the host:port
 * a caller parsed from a resource path, [resolve] returns the candidate that actually accepts a TCP
 * connection right now - preferring the LAN (contract-first) candidate - so one imported resource works
 * both at home and in transit.
 *
 * The choice is cached per network and cleared on a network change (via [NetworkStateMonitor]); a cold
 * connection in a new network pays one happy-eyeballs probe round, steady-state operations pay nothing.
 * Credentials for every candidate exist (the importer saves one row per host:port), and the host key is
 * the same server key on every address, so a resolved endpoint connects and pins exactly like the primary.
 */
@Singleton
class SftpEndpointResolver @Inject constructor(
    private val resourceDao: ResourceDao,
    private val mdnsDiscovery: CompanionMdnsDiscovery,
    networkStateMonitor: NetworkStateMonitor
) : NetworkStateMonitor.NetworkChangeCallback {

    // Reachable winner per requested "host:port" for the current network epoch. Cleared on network change.
    private val winnerByRequested = ConcurrentHashMap<String, HostPort>()

    init {
        networkStateMonitor.registerCallback(this)
    }

    /**
     * Returns the reachable endpoint for the group the requested host:port belongs to, preferring the
     * LAN candidate. A host that is not part of a multi-path resource resolves to itself unchanged, so
     * manual single-address resources keep their exact behaviour.
     */
    suspend fun resolve(host: String, port: Int): HostPort {
        val requestedKey = key(host, port)
        winnerByRequested[requestedKey]?.let { return it }

        val requested = HostPort(host, port)
        val candidates = candidatesFor(requested)
        if (candidates.size <= 1) {
            winnerByRequested[requestedKey] = requested
            return requested
        }

        val winner = probe(candidates) ?: candidates.first()
        // Cache under every candidate key so a later resolve by any address in the group is a hit.
        candidates.forEach { winnerByRequested[key(it.host, it.port)] = winner }
        return winner
    }

    /**
     * S2488: the whole candidate group in send order - the endpoint reachable now first, the remaining
     * candidates behind it in contract order. Never empty: a host that belongs to no multi-path group
     * returns a single-element list holding the requested pair. Delegates the choice to [resolve], so
     * the per-network cache is shared and a second call costs no extra probe round.
     */
    suspend fun orderedEndpoints(host: String, port: Int): List<HostPort> {
        val candidates = candidatesFor(HostPort(host, port))
        val winner = resolve(host, port)
        return (listOf(winner) + candidates).distinct()
    }

    /**
     * Non-suspending, cache-only variant for synchronous call sites (ExoPlayer data-source factories on
     * the player thread, which must not block on a DB read or a probe). Returns the winner already probed
     * for this network, or the requested endpoint unchanged when none is cached yet - the browse/scan path
     * warms the cache via [resolve] before playback, so the reachable address is normally already known.
     */
    fun resolveCached(host: String, port: Int): HostPort =
        winnerByRequested[key(host, port)] ?: HostPort(host, port)

    override fun onNetworkChanged() {
        winnerByRequested.clear()
    }

    override fun onNetworkLost() {
        winnerByRequested.clear()
    }

    /** Builds the candidate group (primary + alternates) that owns [requested], or a singleton list. */
    private suspend fun candidatesFor(requested: HostPort): List<HostPort> {
        val group = resourceDao.getAllResourcesSync()
            .asSequence()
            .filter { it.type == ResourceType.SFTP }
            .mapNotNull { entity -> groupOf(entity) }
            .firstOrNull { requested in it }
        return group ?: listOf(requested)
    }

    private fun groupOf(entity: ResourceEntity): List<HostPort>? {
        val primaryInfo = SftpPathUtils.parseSftpPath(entity.path) ?: return null
        val primary = HostPort(primaryInfo.host, primaryInfo.port)
        // S1013: a companion discovered on the LAN (matched by host-key fingerprint) is the preferred
        // local candidate, ahead of the config's own addresses - covers a missing/stale LAN address.
        val discovered = entity.hostKeyFingerprint
            ?.let { SshFingerprintNormalizer.canonical(it) }
            ?.let { mdnsDiscovery.endpointForFingerprint(it) }
        val all = (listOfNotNull(discovered) + primary + parseAltPaths(entity.altAccessPaths)).distinct()
        // Resolve when there is a genuine choice (a discovered LAN endpoint or a stored alternate).
        return if (all.size > 1) all else null
    }

    private fun parseAltPaths(serialized: String?): List<HostPort> {
        if (serialized.isNullOrBlank()) return emptyList()
        return serialized.split(';').mapNotNull { raw ->
            val entry = raw.trim()
            val sep = entry.lastIndexOf(':')
            if (sep <= 0 || sep == entry.length - 1) return@mapNotNull null
            val alt = entry.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
            HostPort(entry.substring(0, sep), alt)
        }
    }

    /**
     * Happy-eyeballs: probe every candidate concurrently, prefer the LAN (first) candidate within a
     * short grace window, otherwise take the first candidate (in contract order) that connects.
     * Returns null only when no candidate is reachable, so the caller falls back to the primary and the
     * normal SFTP connect surfaces the real error instead of hanging.
     */
    private suspend fun probe(candidates: List<HostPort>): HostPort? = coroutineScope {
        val jobs = candidates.map { candidate ->
            async(Dispatchers.IO) { if (isReachable(candidate)) candidate else null }
        }
        try {
            withTimeoutOrNull(LAN_GRACE_MS) { jobs.first().await() }?.let { return@coroutineScope it }
            jobs.mapNotNull { it.await() }.firstOrNull()
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    private suspend fun isReachable(endpoint: HostPort): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), PROBE_TIMEOUT_MS)
            }
            true
        } catch (e: IOException) {
            Timber.d("SFTP endpoint probe failed for ${endpoint.host}:${endpoint.port}: ${e.message}")
            false
        }
    }

    private fun key(host: String, port: Int): String = "$host:$port"

    companion object {
        private const val PROBE_TIMEOUT_MS = 2500
        private const val LAN_GRACE_MS = 600L
    }
}
