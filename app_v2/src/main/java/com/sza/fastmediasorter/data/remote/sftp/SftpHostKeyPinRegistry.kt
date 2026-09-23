package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the stored TOFU host-key pin for every runtime SFTP session (SHARE-SESSION 1.0, guarantee 3).
 *
 * The pin used to travel as an optional `expectedFingerprint` on each of the ~40 places that build a
 * [SftpClient.SftpConnectionInfo], and nearly all of them left it null, so ordinary browse, scan,
 * transfer and playback connected permissively. [SftpClient] now fills the pin here at the pool's entry
 * points, so a call site cannot forget it.
 *
 * Every address of a resource maps to its pin: the primary path, each `altAccessPaths` entry and the LAN
 * endpoint [CompanionMdnsDiscovery] found under that fingerprint - they are one server with one key.
 * The map is a snapshot of the resources table, refreshed on every table change, so a lookup costs no
 * database read on the per-operation path.
 */
@Singleton
class SftpHostKeyPinRegistry @Inject constructor(
    private val resourceDao: ResourceDao,
    private val mdnsDiscovery: CompanionMdnsDiscovery,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val pins = MutableStateFlow<Map<String, String>?>(null)
    private val observing = AtomicBoolean(false)

    /** Returns [info] with the stored pin filled in; a pin the caller supplied is never replaced. */
    suspend fun withPin(info: SftpClient.SftpConnectionInfo): SftpClient.SftpConnectionInfo {
        if (info.expectedFingerprint != null) return info
        return applyPin(info, awaitPins())
    }

    /**
     * Blocking twin of [withPin] for the ExoPlayer data-source path, which already runs blocking network
     * I/O on a loader thread. Blocks only until the first snapshot exists; afterwards it is a map lookup.
     */
    fun withPinBlocking(info: SftpClient.SftpConnectionInfo): SftpClient.SftpConnectionInfo {
        if (info.expectedFingerprint != null) return info
        val loaded = pins.value ?: runBlocking { awaitPins() }
        return applyPin(info, loaded)
    }

    private fun applyPin(
        info: SftpClient.SftpConnectionInfo,
        snapshot: Map<String, String>,
    ): SftpClient.SftpConnectionInfo {
        val pin = snapshot[key(info.host, info.port)] ?: discoveredPin(info.host, info.port, snapshot)
        Timber.d("S3415: host-key pin for ${info.host}:${info.port} -> ${pin ?: "none (permissive)"}")
        return if (pin == null) info else info.copy(expectedFingerprint = pin)
    }

    private fun discoveredPin(host: String, port: Int, snapshot: Map<String, String>): String? {
        val endpoint = HostPort(host, port)
        return snapshot.values.distinct().firstOrNull { mdnsDiscovery.endpointForFingerprint(it) == endpoint }
    }

    private suspend fun awaitPins(): Map<String, String> {
        ensureObserving()
        return pins.filterNotNull().first()
    }

    private fun ensureObserving() {
        if (!observing.compareAndSet(false, true)) return
        applicationScope.launch {
            resourceDao.getAllResources()
                .catch { e ->
                    // Degrade to the pre-pin behaviour (as S0046 does for an unparseable pin) instead of
                    // leaving every SFTP connect suspended on a snapshot that will never arrive.
                    Timber.e(e, "SFTP host-key pin snapshot unavailable; connecting without stored pins")
                    pins.value = pins.value ?: emptyMap()
                }
                .collect { resources -> pins.value = buildPins(resources) }
        }
    }

    internal companion object {
        fun key(host: String, port: Int): String = "${host.lowercase()}:$port"

        /**
         * One entry per address of every pinned SFTP resource. Resources are applied in id order, so when
         * two resources disagree on one address the newer one (the later pairing) wins.
         */
        fun buildPins(resources: List<ResourceEntity>): Map<String, String> {
            val result = HashMap<String, String>()
            resources.asSequence()
                .filter { it.type == ResourceType.SFTP }
                .sortedBy { it.id }
                .forEach { entity ->
                    val pin = SshFingerprintNormalizer.canonical(entity.hostKeyFingerprint) ?: return@forEach
                    addressesOf(entity).forEach { address ->
                        val previous = result.put(address, pin)
                        if (previous != null && previous != pin) {
                            Timber.w("SFTP host-key pins disagree for $address; using resource id=${entity.id}")
                        }
                    }
                }
            return result
        }

        private fun addressesOf(entity: ResourceEntity): List<String> {
            val primary = SftpPathUtils.parseSftpPath(entity.path)?.let { key(it.host, it.port) }
            val alternates = entity.altAccessPaths.orEmpty().split(';').mapNotNull { raw -> parseAltPath(raw.trim()) }
            return (listOfNotNull(primary) + alternates).distinct()
        }

        private fun parseAltPath(entry: String): String? {
            val sep = entry.lastIndexOf(':')
            val port = if (sep > 0) entry.substring(sep + 1).toIntOrNull() else null
            return port?.let { key(entry.substring(0, sep), it) }
        }
    }
}
