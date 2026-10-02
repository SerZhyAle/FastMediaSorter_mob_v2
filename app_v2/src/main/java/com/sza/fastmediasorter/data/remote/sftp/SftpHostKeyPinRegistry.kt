package com.sza.fastmediasorter.data.remote.sftp

import android.database.SQLException
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
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

    /**
     * Trust on first use (FMSCFG): stores [fingerprint] on every SFTP resource that owns [host]:[port]
     * and has no pin yet. The write is fill-only, so a stored pin is never replaced - a changed key is
     * refused, never re-learned (SHARE-SESSION rule 7). Hands off to the application scope because the
     * pool calls it under its per-host connect lock.
     */
    fun recordFirstUse(host: String, port: Int, fingerprint: String) {
        val canonical = SshFingerprintNormalizer.canonical(fingerprint) ?: return
        Timber.d("S4031: first-use host key offered for $host:$port")
        applicationScope.launch {
            try {
                val owners = unpinnedOwnersOf(resourceDao.getAllResourcesSync(), key(host, port))
                val written = owners.sumOf { id -> resourceDao.fillHostKeyFingerprint(id, canonical) }
                if (written > 0) Timber.i("SFTP host key pinned on first use for $host:$port ($written resource(s))")
            } catch (e: SQLException) {
                // Safe default: the resource stays unpinned and the next successful connect retries the write.
                Timber.e(e, "SFTP first-use host-key pin not stored for $host:$port")
            } catch (e: IllegalStateException) {
                e.rethrowIfCancellation()
                Timber.e(e, "SFTP first-use host-key pin not stored for $host:$port")
            }
        }
    }

    /**
     * S4037: replace the stored pin for [resourceId] and every SFTP resource sharing one of its
     * addresses - SHARE-SESSION rule 7's single confirmed exception to fill-only TOFU. The caller
     * has already shown both fingerprints and received an explicit confirmation; nothing here may
     * be called from a reconnect or first-use path. Returns the number of resources whose pin was
     * replaced, 0 when the fingerprint is not canonical, the resource is unknown or not SFTP.
     */
    suspend fun repin(resourceId: Long, fingerprint: String): Int {
        val canonical = SshFingerprintNormalizer.canonical(fingerprint)
        val owners = canonical?.let { ownersForRepin(resourceId) }.orEmpty()
        val written = canonical?.let { pin -> owners.sumOf { resourceDao.replaceHostKeyFingerprint(it, pin) } } ?: 0
        if (written > 0) Timber.i("SFTP host key re-pinned for resource id=$resourceId ($written resource(s) updated)")
        return written
    }

    /** Owners of every address the anchor resource dials; empty when the anchor is unknown or not SFTP. */
    private suspend fun ownersForRepin(resourceId: Long): List<Long> {
        val resources = resourceDao.getAllResourcesSync()
        return resources.firstOrNull { it.id == resourceId }
            ?.takeIf { it.type == ResourceType.SFTP }
            ?.let { anchor -> addressesOf(anchor).flatMap { ownersOfAddress(resources, it) }.distinct() }
            .orEmpty()
    }

    /**
     * S4037: how many resources a confirmed re-pin on [resourceId] would cover - the number the
     * mismatch dialog names before the user confirms (strategic ADR-3: the fan-out must be visible
     * in advance). 0 when the resource is unknown or not SFTP.
     */
    suspend fun affectedResourceCount(resourceId: Long): Int = ownersForRepin(resourceId).size

    /**
     * S4037: the resource whose stored pin is [expectedFingerprint] - the anchor a surface hands to
     * [repin] when it knows only the mismatch pair. A transfer or playback error does not say which
     * resource dialled the server, but the pin the connection was checked against identifies the
     * server uniquely, so the pair alone is enough. Null when no SFTP resource carries that pin.
     */
    suspend fun anchorForPin(expectedFingerprint: String): Long? {
        val canonical = SshFingerprintNormalizer.canonical(expectedFingerprint) ?: return null
        return anchorOfPin(resourceDao.getAllResourcesSync(), canonical)
    }

    private fun applyPin(
        info: SftpClient.SftpConnectionInfo,
        snapshot: Map<String, String>,
    ): SftpClient.SftpConnectionInfo {
        val pin = snapshot[key(info.host, info.port)] ?: discoveredPin(info.host, info.port, snapshot)
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

        /** Ids of the SFTP resources without a usable pin that dial [address] as primary or alternate. */
        fun unpinnedOwnersOf(resources: List<ResourceEntity>, address: String): List<Long> =
            resources.filter { entity ->
                entity.type == ResourceType.SFTP &&
                    SshFingerprintNormalizer.canonical(entity.hostKeyFingerprint) == null &&
                    address in addressesOf(entity)
            }.map { it.id }

        /** S4037: ids of every SFTP resource whose primary or alternate address set contains [address]. */
        fun ownersOfAddress(resources: List<ResourceEntity>, address: String): List<Long> =
            resources.filter { entity ->
                entity.type == ResourceType.SFTP && address in addressesOf(entity)
            }.map { it.id }

        /** S4037: lowest id among the SFTP resources whose canonical pin equals [canonicalPin], or null. */
        fun anchorOfPin(resources: List<ResourceEntity>, canonicalPin: String): Long? =
            resources
                .filter { entity ->
                    entity.type == ResourceType.SFTP &&
                        SshFingerprintNormalizer.canonical(entity.hostKeyFingerprint) == canonicalPin
                }
                .minOfOrNull { it.id }

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
