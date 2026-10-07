package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource.Entry
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousRequest
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.isFreshAt
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The consumer side of the Drive channel of contract DEVICE-EXCHANGE section 8: the `sftp-share` resource
 * records of the account's devices, keyed by the host-key fingerprint inside their `access.descriptor`, and
 * the device records that say whether their producer is up. Records of every product are read alike.
 *
 * An announced endpoint is only ever a candidate - the resolver races it on the stored pin exactly like
 * an mDNS-discovered one, so a stale or forged address can cost a probe but never a session. Reads are
 * cache-only on the resolution path; Drive is asked at most once per [REFRESH_INTERVAL_MS], in the
 * background, except after every candidate failed, where one awaited refresh may still save the
 * operation.
 */
@Singleton
class SftpRendezvousDirectory @Inject constructor(
    private val store: GoogleDriveSftpRendezvousDataSource,
    private val verdicts: SftpRendezvousVerdicts,
    private val ids: SftpRendezvousIdentityStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** One share as the account's records describe it: its resource, its device (if listed) and its endpoints. */
    private data class Producer(
        val resource: Entry<SftpRendezvousResource>,
        val device: Entry<SftpRendezvousDevice>?,
        val endpoints: List<HostPort>,
    )

    @Volatile
    private var producers: Map<String, Producer> = emptyMap()

    @Volatile
    private var lastRefreshMs: Long? = null

    private val refreshLock = Mutex()
    private val lastRequestMs = ConcurrentHashMap<String, Long>()

    internal var nowMs: () -> Long = System::currentTimeMillis

    /** Every endpoint the share with canonical fingerprint [pin] last published, stale ones included. */
    fun endpointsFor(pin: String): List<HostPort> = producers[pin]?.endpoints.orEmpty()

    /** The canonical fingerprint whose resource record lists [endpoint]; a connect to it is verified on that pin. */
    fun fingerprintForEndpoint(endpoint: HostPort): String? =
        producers.entries.firstOrNull { endpoint in it.value.endpoints }?.key

    /** Refreshes in the background when the cache is older than the interval; never blocks the caller. */
    fun refreshInBackground() {
        if (!isOlderThan(REFRESH_INTERVAL_MS)) return
        appScope.launch(ioDispatcher) { refreshIfOlderThan(REFRESH_INTERVAL_MS) }
    }

    /** A network change can mean the producer moved too: the next lookup asks Drive again. */
    fun invalidate() {
        lastRefreshMs = null
    }

    /**
     * Awaited refresh for the moment every candidate failed: the producer may have published a new
     * address since the last background refresh, and only a fresh read can still save this operation.
     */
    suspend fun refreshAfterFailure() = refreshIfOlderThan(FAILURE_REFRESH_INTERVAL_MS)

    private suspend fun refreshIfOlderThan(intervalMs: Long) {
        if (!isOlderThan(intervalMs)) return
        refreshLock.withLock {
            if (!isOlderThan(intervalMs)) return@withLock
            Timber.d("S4110: consumer refreshes the Drive device-exchange records")
            lastRefreshMs = nowMs()
            store.snapshot()
                .onSuccess { snapshot ->
                    producers = index(snapshot)
                    removeOwnExpired(snapshot.requests)
                }
                .onFailure { Timber.i("SftpRendezvousDirectory: refresh skipped (%s)", it.message) }
        }
    }

    /**
     * Every candidate of the group pinned to [pin] failed. A producer whose device is `online` and whose
     * records are inside their TTL is up but not reachable from here - that verdict is recorded on
     * [endpoints]; a stale or offline one is asked by its `deviceId`, at most once per request TTL, to
     * publish again.
     */
    suspend fun onNoCandidateAnswered(pin: String, endpoints: Collection<HostPort>) {
        val producer = producers[pin]
        val now = nowMs()
        when {
            producer == null -> Timber.i("SftpRendezvousDirectory: no Drive record names this share")
            isLive(producer, now) -> {
                Timber.d("S4110: producer online but unreachable from this network")
                verdicts.markUnreachableFromHere(endpoints)
            }
            else -> askToAnnounce(producer.resource.record.deviceId, now)
        }
    }

    private suspend fun askToAnnounce(toDeviceId: String, now: Long) {
        val previous = lastRequestMs[toDeviceId]
        if (previous != null && now - previous < REQUEST_TTL_SECONDS * MILLIS_PER_SECOND) return
        lastRequestMs[toDeviceId] = now
        val request = SftpRendezvousRequest(
            toDeviceId = toDeviceId,
            action = SftpRendezvousRequest.ACTION_ANNOUNCE,
            fromDeviceId = ids.deviceId(),
            writtenAtMs = now,
            ttlSeconds = REQUEST_TTL_SECONDS,
        )
        store.writeRequest(request)
            .onSuccess { Timber.i("SftpRendezvousDirectory: asked a producer to publish again") }
            .onFailure { Timber.i("SftpRendezvousDirectory: request skipped (%s)", it.message) }
    }

    private fun isLive(producer: Producer, now: Long): Boolean {
        val device = producer.device ?: return false
        return device.record.isOnline && device.record.isFreshAt(now, device.modifiedMs) &&
            producer.resource.record.isFreshAt(now, producer.resource.modifiedMs)
    }

    private fun isOlderThan(intervalMs: Long): Boolean {
        val last = lastRefreshMs ?: return true
        return nowMs() - last >= intervalMs
    }

    // The newest record wins when two resources carry one host key, e.g. a share recreated on a phone whose
    // old record has not been removed yet.
    private fun index(snapshot: GoogleDriveSftpRendezvousDataSource.Snapshot): Map<String, Producer> {
        val devices = snapshot.devices.associateBy { it.record.deviceId }
        return snapshot.resources
            .sortedBy { it.record.updatedAtMs }
            .mapNotNull { resource ->
                val payload = SftpPairingPayload.decode(resource.record.descriptor) ?: return@mapNotNull null
                val pin = SshFingerprintNormalizer.canonical(payload.hostKeyFingerprint) ?: return@mapNotNull null
                val endpoints = payload.hosts.map { HostPort(it, payload.port) }
                pin to Producer(resource, devices[resource.record.deviceId], endpoints)
            }
            .toMap()
    }

    // A device deletes only its own files (section 8.1), so only the expired requests this one wrote go.
    private suspend fun removeOwnExpired(requests: List<Entry<SftpRendezvousRequest>>) {
        val own = ids.deviceId()
        val now = nowMs()
        requests.filter { it.record.fromDeviceId == own && !it.record.isFreshAt(now, it.modifiedMs) }
            .forEach { entry ->
                store.delete(entry.fileId)
                    .onFailure { Timber.i("SftpRendezvousDirectory: expired request kept (%s)", it.message) }
            }
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 2 * 60 * 1_000L
        const val FAILURE_REFRESH_INTERVAL_MS = 30 * 1_000L
        const val REQUEST_TTL_SECONDS = 10 * 60L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
