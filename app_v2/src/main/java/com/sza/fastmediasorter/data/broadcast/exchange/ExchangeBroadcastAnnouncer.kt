package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.domain.usecase.broadcast.ResolveBroadcastLinkChoiceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces this device's live broadcast through the outgoing exchange entry (contract DEVICE-EXCHANGE 7.4, 7.6,
 * 7.7): a fresh `broadcastId` per broadcast, the descriptor with its exchange endpoints, the relay upload (ADTS
 * for `AUDIO_ONLY`, MPEG-TS for video), an update on every change of the LAN address, and the end when the
 * broadcast stops or the entry stops being the outgoing one (item AC). Without an outgoing entry it does nothing
 * (section 9.7), and a `guest` outgoing entry counts as none: the server refuses `broadcast-start` and the relay
 * upload from a guest (19 item AO).
 *
 * Every broadcast kind - sound, camera, screen, stored file - is announced from its start with no separate switch
 * (item DT), and an entry made outgoing while a broadcast is live announces it at once through the `producer` flow.
 */
// Every argument is a distinct collaborator of one announcement; a holder would hide the graph, not shrink it.
@Suppress("LongParameterList")
@Singleton
class ExchangeBroadcastAnnouncer @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val producer: SftpServerTunnelManager,
    private val uploader: ExchangeRelayUploader,
    seals: BroadcastSealMaterial,
    private val videoFeed: ExchangeVideoRelayFeed,
    private val serializer: BroadcastDescriptorSerializer,
    private val grants: ExchangeBroadcastGrants,
    private val grantRepository: ExchangeGrantRepository,
    private val resolveLinkChoice: ResolveBroadcastLinkChoiceUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val relayKeys = seals.relayKeys
    private val tunnelIdentities = seals.tunnelIdentities
    private val credentials = seals.credentials

    private val announced = MutableStateFlow<BroadcastDescriptorDto?>(null)
    private var job: Job? = null
    private var announcedRevision = NOT_ANNOUNCED

    // Written by the collector, read by the cast sender on another thread.
    @Volatile
    private var current: Announcement? = null

    /** The `broadcastId` the live broadcast is announced under, which a `cast` names (7.8); null when none is. */
    val announcedBroadcastId: String? get() = current?.broadcastId?.takeIf { announced.value != null }

    /** The key the announced broadcast's source keeps its guests under (item BD); null when nothing is announced. */
    val announcedSourceKey: String? get() = current?.sourceKey?.takeIf { announced.value != null }

    /** The announced descriptor with its exchange endpoints, null when nothing is announced. */
    val announcedDescriptor: BroadcastDescriptorDto? get() = announced.value

    /** [announcedDescriptor] as a flow: the share panel re-renders when the announcement, and with it `auth`, lands. */
    val announcements: StateFlow<BroadcastDescriptorDto?> get() = announced

    private val mutablePublishState =
        MutableStateFlow<ExchangeBroadcastPublishState>(ExchangeBroadcastPublishState.Idle)

    /** Item DU: where the live broadcast stands on the server; the one source of every surface that shows it. */
    val publishState: StateFlow<ExchangeBroadcastPublishState> = mutablePublishState.asStateFlow()

    /** Follows [state] for the life of the process; a second call is ignored. */
    @Synchronized
    fun follow(state: StateFlow<BroadcastState>) {
        if (job != null) return
        job = appScope.launch {
            launch {
                combine(state, accounts.entries, accounts.producer, producer.broadcastWire, uploader.accepted) {
                        live, entries, entry, wire, accepted ->
                    val relayed = accepted != null && accepted == current?.broadcastId
                    ExchangeBroadcastPublishStates.of(live is BroadcastState.Live, entries, entry, wire, relayed)
                }.collect {
                    Timber.d("S4419: phone exchange publish state ${it::class.simpleName}")
                    mutablePublishState.value = it
                }
            }
            combine(state, accounts.producer, uploader.accepted, credentials.revision) { live, entry, accepted, _ ->
                Triple(live as? BroadcastState.Live, entry, accepted)
            }.collect { (live, entry, accepted) -> apply(live, entry, accepted) }
        }
    }

    /**
     * The descriptor the share link and the QR code carry (S4160): the live one with the exchange endpoints while
     * it is announced, so a user outside the account receives it through the server as well. S4240, 27 item DK:
     * it carries the hand-out group key on `RELAY` and the tunnel pin on `TUNNEL`, so it opens the broadcast by
     * itself - which is why it travels only by the person's hand and is never logged.
     */
    fun shareDescriptor(live: BroadcastDescriptorDto): BroadcastDescriptorDto {
        val descriptor = announced.value?.takeIf { it.url == live.url && it.mode == live.mode }
        val broadcastId = current?.broadcastId
        return if (descriptor == null || broadcastId == null) {
            live
        } else {
            val relayKey = relayKeys.linkKey(broadcastId, OUTWARD_LINK_ID)?.let(E2eBytes::b64url)
            val tunnelPin = tunnelIdentities.current(broadcastId)?.pin
            ExchangeBroadcastDescriptors.withOutwardKeys(descriptor, relayKey, tunnelPin)
        }
    }

    /**
     * S4261: the descriptor handed to the person's own watch (LIVE-BROADCAST item P). The watch is a device of the
     * same person, so it opens a sealed relay with the own group key and keeps playing when the outward link is
     * revoked (DEVICE-EXCHANGE items DK and DL). Like [shareDescriptor] it opens the broadcast by itself and is
     * never logged.
     */
    fun ownDeviceDescriptor(live: BroadcastDescriptorDto): BroadcastDescriptorDto {
        val descriptor = announced.value?.takeIf { it.url == live.url && it.mode == live.mode }
        val broadcastId = current?.broadcastId
        return if (descriptor == null || broadcastId == null) {
            live
        } else {
            val relayKey = relayKeys.ownKey(broadcastId)?.let(E2eBytes::b64url)
            val tunnelPin = tunnelIdentities.current(broadcastId)?.pin
            ExchangeBroadcastDescriptors.withOutwardKeys(descriptor, relayKey, tunnelPin)
        }
    }

    private suspend fun apply(live: BroadcastState.Live?, entry: DeviceExchangeAccountEntry?, accepted: String?) {
        if (live == null || entry == null) {
            end()
            return
        }
        val key = Announcement.Key(live.startedAtElapsedRealtimeMs, entry.identity, live.descriptor.mode)
        val held = current?.takeIf { it.key == key } ?: begin(key, live, entry)
        // 27 item DM: a tunnel is offered only with its own TLS identity, which only a sealing server needs.
        val tunnelIdentity = if (entry.serverSeals) tunnelIdentities.ensure(held.broadcastId) else null
        val descriptor = ExchangeBroadcastDescriptors.withExchange(
            lan = live.descriptor,
            broadcastId = held.broadcastId,
            publicEndpoint = entry.publicEndpoint ?: "${entry.host}:${entry.port}",
            certFingerprint = entry.pinnedCertificate,
            relayUploading = accepted == held.broadcastId,
            sealed = tunnelIdentity != null,
        ).copy(auth = credentials.authMarker())
        // A renewed link password leaves the descriptor as it was but changes the relay's verifiers (item BF).
        val revision = credentials.revision.value
        if (descriptor == announced.value && revision == announcedRevision) return
        val verifiers = credentials.verifiers()
        announced.value = descriptor
        announcedRevision = revision
        producer.announceBroadcast(publication(held, entry, descriptor).copy(verifiers = verifiers))
    }

    private suspend fun begin(
        key: Announcement.Key,
        live: BroadcastState.Live,
        entry: DeviceExchangeAccountEntry,
    ): Announcement {
        if (current != null) end()
        val sourceKey = live.descriptor.sourceId ?: live.descriptor.mode
        val started = Announcement(key, SftpShareId.generate(), System.currentTimeMillis(), sourceKey)
        current = started
        // DEVICE-EXCHANGE item BF: a broadcast announced through an exchange server is protected, unless the person
        // chose an open link (item DV, S4405).
        credentials.begin(started.broadcastId, resolveLinkChoice())
        relayKeys.begin(started.broadcastId)
        // The hand-out key exists before the first upload stream, so every epoch's ring already carries it (DL).
        relayKeys.linkKey(started.broadcastId, OUTWARD_LINK_ID)
        // Item BD: the source's kept guests get this broadcast again, each with a new pair and key, before the
        // first upload so the first epoch's ring carries their keys too.
        grants.clear()
        grantRepository.broadcastGuests(sourceKey).forEach { guest -> grants.add(started.broadcastId, guest) }
        val source = loopbackSource(live.descriptor)
        val id = started.broadcastId
        when {
            live.descriptor.mode != BroadcastMode.AUDIO_ONLY.name -> uploader.startVideo(entry, id, videoFeed)
            source != null -> uploader.start(entry, id, source)
        }
        return started
    }

    private fun end() {
        current ?: return
        current = null
        announced.value = null
        uploader.stop()
        relayKeys.end()
        tunnelIdentities.end()
        credentials.end()
        grants.clear()
        announcedRevision = NOT_ANNOUNCED
        producer.endBroadcast()
    }

    private fun publication(
        held: Announcement,
        entry: DeviceExchangeAccountEntry,
        descriptor: BroadcastDescriptorDto,
    ) = ExchangeBroadcastPublication(
        record = SftpRendezvousBroadcast(
            broadcastId = held.broadcastId,
            deviceId = entry.deviceId,
            title = descriptor.title,
            mode = descriptor.mode,
            startedAtMs = held.startedAtMs,
            updatedAtMs = System.currentTimeMillis(),
            descriptorJson = serializer.serialize(descriptor),
            writtenAtMs = 0L,
            ttlSeconds = 0L,
        ),
        maxListeners = ExchangeBroadcastPublication.NO_LISTENER_CAP,
        localPorts = ExchangeBroadcastDescriptors.localPorts(descriptor),
    )

    // The LAN server listens on every interface, so loopback reaches it whichever address the descriptor names.
    private fun loopbackSource(descriptor: BroadcastDescriptorDto): String? = descriptor.getEffectiveEndpoints()
        .firstOrNull { it.transport.equals(BroadcastExchangeEndpoints.TRANSPORT_HTTP, ignoreCase = true) }
        ?.let { runCatching { URI(it.url) }.getOrNull() }
        ?.takeIf { it.port > 0 }
        ?.let { "http://${BroadcastExchangeEndpoints.LOOPBACK_HOST}:${it.port}${it.rawPath.orEmpty()}" }

    private data class Announcement(
        val key: Key,
        val broadcastId: String,
        val startedAtMs: Long,
        val sourceKey: String,
    ) {
        data class Key(val startedAtElapsedMs: Long, val entryIdentity: String, val mode: String)
    }

    private companion object {
        // Every hand-out of one broadcast - link, file or barcode - shares one group key: none is withdrawn alone.
        const val OUTWARD_LINK_ID = "outward"
        const val NOT_ANNOUNCED = -1L
    }
}
