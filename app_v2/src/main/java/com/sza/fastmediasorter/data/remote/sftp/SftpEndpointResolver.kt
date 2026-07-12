package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.utils.SftpPathUtils
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
        Timber.d("S1006: resolved ${winner.host}:${winner.port} from ${candidates.size} candidates")
        return winner
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
        val all = (listOf(primary) + parseAltPaths(entity.altAccessPaths)).distinct()
        // Only groups with a genuine alternate need resolution.
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
