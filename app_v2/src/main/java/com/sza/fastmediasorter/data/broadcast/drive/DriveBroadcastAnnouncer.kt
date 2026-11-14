package com.sza.fastmediasorter.data.broadcast.drive

import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataFailure
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.domain.model.DriveBroadcastPublishState
import com.sza.fastmediasorter.domain.model.DriveBroadcastPublishState.NotAnnounced
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces this device's live broadcast on the person's Google Drive (contract DEVICE-EXCHANGE items BC and CL):
 * one `fmsx-broadcast-<id>.json` record in the app folder, written when the broadcast starts while the Drive
 * channel is on, renewed at a third of its TTL (item C) and deleted when the broadcast ends, the channel is turned
 * off or the outgoing exchange entry is a guest one. There is no switch of its own: item BC forbids one per kind.
 *
 * The record is an address only. It carries the descriptor the exchange server carries while the broadcast is
 * announced there - so a reader merges both channels by `broadcastId` - and the LAN descriptor otherwise, and never
 * `credentials`, the sealed box, a relay key or a tunnel pin: the Drive path is not sealed (item DO) and the own
 * pair reaches Drive only under an opt-in (item BF) this product does not offer for broadcasts.
 */
// Every argument is a distinct collaborator of the announcement; a holder would hide the graph, not shrink it.
@Suppress("LongParameterList")
@Singleton
class DriveBroadcastAnnouncer @Inject constructor(
    private val store: GoogleDriveSftpRendezvousDataSource,
    private val channel: DriveChannelSettings,
    private val accounts: DeviceExchangeAccountRepository,
    private val exchange: ExchangeBroadcastAnnouncer,
    private val serializer: BroadcastDescriptorSerializer,
    private val ids: SftpRendezvousIdentityStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private var job: Job? = null

    // Writes and deletes run one at a time, so the delete of an ended broadcast is never overtaken by its renewal.
    private val writes = Mutex()

    // The broadcast id whose record this process last wrote; null when none stands.
    private var published: String? = null

    // One id per live session that the exchange server has not named yet, kept across restarts of the loop.
    private var localSession: Pair<Long, String>? = null

    private var staleSwept = false

    private val mutablePublishState = MutableStateFlow<DriveBroadcastPublishState>(DriveBroadcastPublishState.Idle)

    val publishState: StateFlow<DriveBroadcastPublishState> = mutablePublishState.asStateFlow()

    /** Follows [state] for the life of the process; a second call is ignored. */
    @Synchronized
    fun follow(state: StateFlow<BroadcastState>) {
        if (job != null) return
        job = appScope.launch(ioDispatcher) {
            combine(
                state,
                channel.enabled,
                accounts.entries,
                accounts.producer,
                exchange.announcements,
            ) { live, enabled, entries, producer, announced ->
                Inputs(live as? BroadcastState.Live, enabled, producer == null && entries.isNotEmpty(), announced)
            }.collectLatest(::apply)
        }
    }

    private suspend fun apply(inputs: Inputs) {
        val live = inputs.live
        if (live == null || !inputs.enabled) {
            withdraw()
            localSession = null
            mutablePublishState.value = DriveBroadcastPublishState.Idle
            return
        }
        if (inputs.guest) {
            withdraw()
            mutablePublishState.value = NotAnnounced(NotAnnounced.Reason.GUEST)
            return
        }
        val (broadcastId, descriptor) = target(live, inputs.announced)
        if (published != null && published != broadcastId) withdraw()
        if (published == null) mutablePublishState.value = DriveBroadcastPublishState.Announcing
        val startedAtMs = System.currentTimeMillis()
        sweepStale(broadcastId)
        while (currentCoroutineContext().isActive) {
            announce(broadcastId, descriptor, startedAtMs)
            delay(RENEW_INTERVAL_MS)
        }
    }

    /** The exchange id and descriptor while the server carries this session, else a local id and the LAN one. */
    private fun target(
        live: BroadcastState.Live,
        announced: BroadcastDescriptorDto?,
    ): Pair<String, BroadcastDescriptorDto> {
        val onServer = announced?.takeIf { it.url == live.descriptor.url && it.mode == live.descriptor.mode }
        val exchangeId = exchange.announcedBroadcastId
        if (onServer != null && exchangeId != null) return exchangeId to onServer
        val session = localSession?.takeIf { it.first == live.startedAtElapsedRealtimeMs }
            ?: (live.startedAtElapsedRealtimeMs to SftpShareId.generate()).also { localSession = it }
        return session.second to live.descriptor
    }

    private suspend fun announce(broadcastId: String, descriptor: BroadcastDescriptorDto, startedAtMs: Long) {
        val now = System.currentTimeMillis()
        val record = SftpRendezvousBroadcast(
            broadcastId = broadcastId,
            deviceId = ids.deviceId(),
            title = descriptor.title,
            mode = descriptor.mode,
            startedAtMs = startedAtMs,
            updatedAtMs = now,
            descriptorJson = serializer.serialize(descriptor),
            writtenAtMs = now,
            ttlSeconds = RECORD_TTL_SECONDS,
        )
        Timber.d("S4608: Drive broadcast record write mode=${descriptor.mode}")
        writes.withLock {
            store.writeBroadcast(record)
                .onSuccess {
                    published = broadcastId
                    mutablePublishState.value = DriveBroadcastPublishState.Announced
                }
                .onFailure { error ->
                    Timber.i("DriveBroadcastAnnouncer: record skipped (%s)", error.message)
                    mutablePublishState.value = NotAnnounced(reasonOf(error.cause))
                }
        }
    }

    /** Deletes the standing record; it runs to the end even when a newer input cancelled the loop that called it. */
    private suspend fun withdraw() = withContext(NonCancellable) {
        writes.withLock {
            val standing = published ?: return@withLock
            store.deleteBroadcast(standing)
                .onSuccess { published = null }
                .onFailure { error ->
                    // The record expires on Drive's clock after its TTL (item C), so a failed delete is not retried.
                    published = null
                    Timber.i("DriveBroadcastAnnouncer: delete skipped (%s)", error.message)
                }
        }
    }

    /** A device removes its own records a killed process left behind (8.1), once per process. */
    private suspend fun sweepStale(currentId: String) {
        if (staleSwept) return
        staleSwept = true
        val deviceId = ids.deviceId()
        val broadcasts = store.snapshot().getOrNull()?.broadcasts ?: return
        broadcasts.filter { it.record.deviceId == deviceId && it.record.broadcastId != currentId }
            .forEach { entry -> store.delete(entry.fileId) }
    }

    private fun reasonOf(cause: Throwable?): NotAnnounced.Reason =
        if (cause is DriveAppDataFailure.AuthRequired || cause is DriveAppDataFailure.ConsentRequired) {
            NotAnnounced.Reason.NOT_SIGNED_IN
        } else {
            NotAnnounced.Reason.UNREACHABLE
        }

    private data class Inputs(
        val live: BroadcastState.Live?,
        val enabled: Boolean,
        val guest: Boolean,
        val announced: BroadcastDescriptorDto?,
    )

    private companion object {
        // DEVICE-EXCHANGE working values (proposal of 2026-10-23, row C): a broadcast record lives 90 s and is
        // renewed at a third of that.
        const val RECORD_TTL_SECONDS = 90L
        const val RENEW_INTERVAL_MS = 30_000L
    }
}
