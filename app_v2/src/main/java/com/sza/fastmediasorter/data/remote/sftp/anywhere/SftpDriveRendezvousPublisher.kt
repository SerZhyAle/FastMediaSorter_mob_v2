package com.sza.fastmediasorter.data.remote.sftp.anywhere

import android.os.Build
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousRequest
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.isFreshAt
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The producer side of the Drive channel of contract DEVICE-EXCHANGE section 8: while the embedded SFTP
 * server runs, this device's record says `online` and the share's resource record carries its current
 * direct endpoints, rewritten when the addresses change, when the announce interval elapses and when a
 * consumer asks. On stop the device record turns `offline` and the resource record stays, because a stopped
 * share is listed as offline, not as gone (section 9 rule 3).
 *
 * The record carries `access` - the endpoints and the password - only while the user has opted in to it
 * (section 15 item X); otherwise it lists the share without them, and a change of the opt-in rewrites it at
 * the next poll, so turning it off takes the password back off Drive.
 *
 * Rendezvous only - nothing is relayed through Drive. With no `drive.appdata` token the store answers
 * every call with a failure and the loop just retries at the announce interval, so a device without a
 * Google account pays one silent token lookup per interval and nothing else.
 */
@Singleton
class SftpDriveRendezvousPublisher @Inject constructor(
    private val store: GoogleDriveSftpRendezvousDataSource,
    private val identityStore: SftpServerIdentityStore,
    private val settings: SftpServerSettingsStore,
    private val ids: SftpRendezvousIdentityStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private var job: Job? = null

    /** Starts (or restarts) publishing a server on [port] reachable on whatever [addresses] returns at each tick. */
    @Synchronized
    fun start(port: Int, addresses: () -> List<String>) {
        job?.cancel()
        job = appScope.launch(ioDispatcher) { run(port, addresses) }
    }

    /** Stops publishing and marks this device offline, unless a restart claimed the channel meanwhile. */
    @Synchronized
    fun stop() {
        val running = job ?: return
        job = null
        running.cancel()
        appScope.launch(ioDispatcher) {
            running.join()
            if (isIdle()) writeDevice(ids.deviceId(), SftpRendezvousDevice.PRESENCE_OFFLINE, System.currentTimeMillis())
        }
    }

    @Synchronized
    private fun isIdle(): Boolean = job == null

    private suspend fun run(port: Int, addresses: () -> List<String>) {
        val fingerprint = SshFingerprintNormalizer.canonical(identityStore.hostKeyFingerprint()) ?: return
        val deviceId = ids.deviceId()
        val binding = ids.resourceIdFor(fingerprint)
        cleanUp(deviceId, binding.retiredResourceId)
        val handled = HashSet<String>()
        var lastAttemptMs: Long? = null
        var published: Publication? = null
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val shared = settings.driveAccessShared.first()
            val current = Publication(shared, if (shared) addresses() else emptyList())
            val due = lastAttemptMs == null || now - lastAttemptMs >= ANNOUNCE_INTERVAL_MS
            val changed = published != null && current != published
            val asked = published != null && wasAsked(deviceId, handled)
            if (due || changed || asked) {
                published = current.takeIf { announce(deviceId, binding.resourceId, port, it, now) }
                lastAttemptMs = now
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun announce(
        deviceId: String,
        resourceId: String,
        port: Int,
        publication: Publication,
        now: Long,
    ): Boolean {
        Timber.d("S4110: publisher writes the device and resource records on Drive")
        Timber.d("S4129: resource record access follows the Drive opt-in")
        val deviceWritten = writeDevice(deviceId, SftpRendezvousDevice.PRESENCE_ONLINE, now)
        // A shared phone with no LAN address has nothing a consumer could dial; its device record alone says it
        // is up. An unshared record carries no address anyway, so it is written regardless.
        if (!deviceWritten || (publication.shared && publication.hosts.isEmpty())) return deviceWritten
        val descriptor = if (publication.shared) descriptor(port, publication.hosts) else null
        val resource = SftpRendezvousResource(
            resourceId = resourceId,
            deviceId = deviceId,
            kind = SftpRendezvousResource.KIND_SFTP_SHARE,
            name = Build.MODEL.orEmpty(),
            descriptor = descriptor,
            updatedAtMs = now,
            writtenAtMs = now,
            ttlSeconds = RECORD_TTL_SECONDS,
        )
        return store.writeResource(resource)
            .onSuccess { Timber.i("SftpDriveRendezvousPublisher: published %d endpoint(s)", publication.hosts.size) }
            .onFailure { Timber.i("SftpDriveRendezvousPublisher: resource record skipped (%s)", it.message) }
            .isSuccess
    }

    private suspend fun descriptor(port: Int, hosts: List<String>): String {
        val credentials = identityStore.clientCredentials()
        return SftpPairingPayload(
            hosts = hosts,
            port = port,
            username = credentials.username,
            password = credentials.password,
            hostKeyFingerprint = credentials.hostKeyFingerprint,
        ).encode()
    }

    private suspend fun writeDevice(deviceId: String, presence: String, now: Long): Boolean {
        val device = SftpRendezvousDevice(
            deviceId = deviceId,
            deviceName = Build.MODEL.orEmpty(),
            product = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
            productVersion = BuildConfig.VERSION_NAME,
            platform = SftpRendezvousDevice.PLATFORM_ANDROID,
            roles = listOf(SftpRendezvousDevice.ROLE_RESOURCE_PRODUCER, SftpRendezvousDevice.ROLE_RESOURCE_CONSUMER),
            presence = presence,
            updatedAtMs = now,
            writtenAtMs = now,
            ttlSeconds = RECORD_TTL_SECONDS,
        )
        return store.writeDevice(device)
            .onFailure { Timber.i("SftpDriveRendezvousPublisher: %s device record skipped (%s)", presence, it.message) }
            .isSuccess
    }

    /**
     * A device deletes only its own files (section 8.1): the record of a share it replaced, and the expired
     * requests it wrote as a consumer.
     */
    private suspend fun cleanUp(deviceId: String, retiredResourceId: String?) {
        retiredResourceId?.let { retired ->
            store.deleteResource(retired)
                .onFailure { Timber.i("SftpDriveRendezvousPublisher: retired resource kept (%s)", it.message) }
        }
        val requests = store.snapshot(requestsOnly = true).getOrNull()?.requests ?: return
        val now = System.currentTimeMillis()
        requests.filter { it.record.fromDeviceId == deviceId && !it.record.isFreshAt(now, it.modifiedMs) }
            .forEach { entry -> store.delete(entry.fileId) }
    }

    /** A request is deleted by its writer, so a handled one is only remembered, never deleted here. */
    private suspend fun wasAsked(deviceId: String, handled: MutableSet<String>): Boolean {
        val requests = store.snapshot(requestsOnly = true).getOrNull()?.requests ?: return false
        val now = System.currentTimeMillis()
        val fresh = requests.filter { entry ->
            entry.name !in handled && entry.record.toDeviceId == deviceId &&
                entry.record.action == SftpRendezvousRequest.ACTION_ANNOUNCE &&
                entry.record.isFreshAt(now, entry.modifiedMs)
        }
        handled.retainAll(requests.map { it.name }.toSet())
        handled.addAll(fresh.map { it.name })
        return fresh.isNotEmpty()
    }

    /** What the last successful announce put on Drive: whether `access` went with it, and its endpoints. */
    private data class Publication(val shared: Boolean, val hosts: List<String>)

    companion object {
        // Start values; the rung 3 device test measures battery against discovery delay before they freeze.
        // The announce interval stays at most a third of the TTL, as amendment 0.12 C of the contract asks.
        const val POLL_INTERVAL_MS = 2 * 60 * 1_000L
        const val ANNOUNCE_INTERVAL_MS = 10 * 60 * 1_000L
        const val RECORD_TTL_SECONDS = 30 * 60L
    }
}
