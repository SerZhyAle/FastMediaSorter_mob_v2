package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBroadcastSeals
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRecipients
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.wear.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionStateHolder
import com.sza.fastmediasorter.wear.domain.broadcast.WearExchangeListenerCap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the watch's live `AUDIO_ONLY` broadcast on the exchange server through the outgoing entry (contract
 * DEVICE-EXCHANGE 7.4, items AC and M): `broadcast-start` once per control-stream connection, `broadcast-update`
 * when the relay share of the cap, the relay endpoint or the record's box changes, `broadcast-end` when the
 * broadcast stops or the outgoing mark moves to another entry. The relay upload runs for the same span.
 *
 * S4240: the record carries the box of 27 item DK over the relay's group key, sealed to the account's devices as
 * the outgoing entry's directory lists them; a new box goes out whenever that set changes.
 *
 * State is touched only from [follow]'s single collector, so it needs no lock.
 */
@Singleton
class WearExchangeBroadcastAnnouncer @Inject constructor(
    private val broadcast: WearBroadcastSessionStateHolder,
    private val store: WearExchangeEntryStore,
    private val streams: WearExchangeControlStreams,
    private val uploader: WearExchangeRelayUploader,
    private val directory: WearExchangeBroadcastDirectory,
    private val e2e: WearExchangeE2e,
    private val keys: WearExchangeRelayKeys,
) {

    private var current: Announced? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun follow() {
        // A guest outgoing entry counts as none: the server refuses `broadcast-start` from it (19 item AO).
        val outgoing = store.entries.map { entries ->
            entries.firstOrNull(WearExchangeEntry::outgoing)?.takeIf(WearExchangeEntry::mayProduce)
        }
        // The account's devices are followed only while a broadcast is live: nobody else needs the list.
        val devices = combine(broadcast.state.map { it is WearBroadcastSessionState.Live }, outgoing) { live, entry ->
            entry.takeIf { live }
        }
            .distinctUntilChanged { old, new -> old?.identity == new?.identity }
            .flatMapLatest { entry -> entry?.let(directory::devices) ?: flowOf(null) }
        combine(
            combine(broadcast.state, broadcast.lanListeners, ::Pair),
            outgoing,
            uploader.accepted,
            streams.live,
            devices,
        ) { (state, humans), entry, accepted, live, listed ->
            Snapshot(state as? WearBroadcastSessionState.Live, humans, entry, accepted, live, listed)
        }.collect(::apply)
    }

    private suspend fun apply(snapshot: Snapshot) {
        val live = snapshot.live
        val entry = snapshot.outgoing
        current?.let { running ->
            val moved = live == null || entry == null || running.entry.identity != entry.identity
            if (moved || running.live != live) {
                end(running)
                current = null
            }
        }
        if (live != null && entry != null) {
            val announced = current ?: begin(entry, live)?.also { current = it }
            announced?.let { announce(it, snapshot) }
        }
    }

    private fun begin(entry: WearExchangeEntry, live: WearBroadcastSessionState.Live): Announced? {
        val lan = WearExchangeBroadcastRecords.parseLan(live.descriptorJson) ?: return null
        val broadcastId = WearExchangeBroadcastRecords.newBroadcastId()
        keys.begin(broadcastId)
        uploader.start(entry, broadcastId, live.endpoint.url)
        return Announced(entry, live, lan, broadcastId, System.currentTimeMillis())
    }

    private suspend fun announce(announced: Announced, snapshot: Snapshot) {
        val stream = snapshot.streams[announced.entry.identity] ?: return
        val relay = snapshot.accepted == announced.broadcastId
        val cap = WearExchangeListenerCap.maxListeners(snapshot.humans)
        val isStart = announced.sentGeneration != stream.generation
        snapshot.devices?.let { reseal(announced, it) }
        val changed = announced.sentCap != cap || announced.sentRelay != relay ||
            announced.sentBoxKey != announced.boxKey
        if (isStart || changed) {
            val publication = WearExchangeBroadcastPublication(
                broadcastId = announced.broadcastId,
                deviceId = announced.entry.deviceId,
                lan = announced.lan,
                publicEndpoint = stream.publicEndpoint ?: "${announced.entry.host}:${announced.entry.port}",
                certFingerprint = announced.entry.pinnedCertificate,
                startedAtMs = announced.startedAtMs,
            )
            val type = if (isStart) {
                WearExchangeEnvelope.TYPE_BROADCAST_START
            } else {
                WearExchangeEnvelope.TYPE_BROADCAST_UPDATE
            }
            val record = WearExchangeBroadcastRecords.record(
                publication,
                relay,
                System.currentTimeMillis(),
                announced.box,
            )
            val envelope = WearExchangeEnvelope.of(type) {
                add(KEY_BROADCAST, record)
                addProperty(KEY_MAX_LISTENERS, cap)
            }
            if (streams.send(announced.entry.identity, envelope)) {
                Timber.i("WearExchangeBroadcastAnnouncer: %s, cap %d, relay %b", type, cap, relay)
                announced.sentGeneration = stream.generation
                announced.sentCap = cap
                announced.sentRelay = relay
                announced.sentBoxKey = announced.boxKey
            }
        }
    }

    // Item DK: the box goes to the account's devices; a device that left takes the group key with it, so the next
    // box carries a new one and the uploader's next epoch excludes the device (item DL).
    private suspend fun reseal(announced: Announced, devices: Collection<WearDirectoryDevice>) {
        val sealing = e2e.sealing(announced.entry, devices)
        val recipients = sealing.recipients
        keys.membersChanged(announced.broadcastId, recipients.map(E2eRecipients::keyOf).toSet())
        val relayKey = keys.ownKey(announced.broadcastId)
        val self = sealing.self
        val key = boxKeyOf(sealing, relayKey)
        if (key == announced.boxKey) return
        Timber.d("S4240: watch seals its broadcast box to ${recipients.size} devices")
        announced.boxKey = key
        announced.box = if (self == null || recipients.isEmpty() || relayKey == null) {
            null
        } else {
            val payload = JsonObject().apply { addProperty(E2eBroadcastSeals.KEY_RELAY_KEY, E2eBytes.b64url(relayKey)) }
            E2eSealedBox.seal(payload, E2eSealedBox.broadcastContext(announced.broadcastId), self, recipients)
        }
    }

    private fun end(announced: Announced) {
        uploader.stop()
        keys.end()
        if (announced.sentGeneration != null) {
            streams.send(
                announced.entry.identity,
                WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_BROADCAST_END) {
                    addProperty(KEY_BROADCAST_ID, announced.broadcastId)
                },
            )
        }
    }

    private class Snapshot(
        val live: WearBroadcastSessionState.Live?,
        val humans: Int,
        val outgoing: WearExchangeEntry?,
        val accepted: String?,
        val streams: Map<String, WearExchangeLiveStream>,
        val devices: Collection<WearDirectoryDevice>?,
    )

    private class Announced(
        val entry: WearExchangeEntry,
        val live: WearBroadcastSessionState.Live,
        val lan: BroadcastDescriptorDto,
        val broadcastId: String,
        val startedAtMs: Long,
    ) {
        var sentGeneration: Long? = null
        var sentCap: Int? = null
        var sentRelay: Boolean? = null
        var box: JsonObject? = null
        var boxKey: String? = null
        var sentBoxKey: String? = null
    }

    internal companion object {
        const val KEY_BROADCAST = "broadcast"
        const val KEY_BROADCAST_ID = "broadcastId"
        const val KEY_MAX_LISTENERS = "maxListeners"

        internal fun boxKeyOf(sealing: WearSealing, relayKey: ByteArray?): String =
            E2eRecipients.keyOf(sealing.self, sealing.recipients) +
                "/" + relayKey?.let { E2eBytes.hex(E2eBytes.sha256(it)) }
    }
}
