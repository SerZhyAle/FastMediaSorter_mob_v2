package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One entry's directory as the watch holds it: [state] is null until the first `directory` answer, and
 * [isReachable] is false while the entry's control stream is down (the last [state] is kept for the screen).
 */
data class WearEntryDirectory(
    val entry: WearExchangeEntry,
    val state: WearExchangeDirectoryState?,
    val isReachable: Boolean,
    val failure: WearExchangeMessage? = null,
)

/**
 * The account's broadcast directory for every exchange entry (contract DEVICE-EXCHANGE 7.4 and item CI): `list`,
 * then `subscribe`, then every `changed` applied by revision, with `list` sent again on a gap.
 *
 * It rides the control stream [WearExchangeControlStreams] already holds for the entry. A second authenticated
 * stream of the same device would replace the control stream (7.3) and take the cast the watch may be waiting on
 * with it, which is why this class has no connector and no session opener. Nothing is sent unless a collector is
 * attached, and the streams are held by the presence policy while the app is on screen.
 */
@Singleton
class WearExchangeBroadcastDirectory @Inject constructor(
    private val streams: WearExchangeControlStreams,
    private val store: WearExchangeEntryStore,
) {

    private val latest = ConcurrentHashMap<String, WearExchangeDirectoryState>()

    /** The directory record held now for [broadcastId], or null once it ended or its entry went away. */
    fun broadcast(entryIdentity: String, broadcastId: String): WearDirectoryBroadcast? =
        latest[entryIdentity]?.broadcasts?.get(broadcastId)

    /** S4240: the devices [entryIdentity]'s list holds now, whose keys a listed broadcast's box is checked by. */
    fun devicesHeld(entryIdentity: String): Collection<WearDirectoryDevice>? = latest[entryIdentity]?.devices?.values

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<WearEntryDirectory>> = store.entries
        .flatMapLatest { entries ->
            if (entries.isEmpty()) {
                latest.clear()
                flowOf(emptyList())
            } else {
                combine(combine(entries.map(::watch)) { it.toList() }, streams.failures) { held, failures ->
                    held.map { directory -> directory.copy(failure = failures[directory.entry.identity]) }
                }
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * S4240: the account's devices as [entry]'s directory lists them, null until the first answer - whom a sealed
     * broadcast's box goes to (27 item DK). Nothing here is kept for [broadcast], so a broadcasting watch holds
     * no other broadcast's credentials while nobody looks at the list (7.9).
     */
    fun devices(entry: WearExchangeEntry): Flow<Collection<WearDirectoryDevice>?> =
        watch(entry, publish = false).map { it.state?.devices?.values }.distinctUntilChanged()

    private fun watch(entry: WearExchangeEntry, publish: Boolean = true): Flow<WearEntryDirectory> = channelFlow {
        val holder = Holder(publish)
        send(WearEntryDirectory(entry, null, isReachable = false))
        try {
            streams.live
                .map { it[entry.identity]?.generation }
                .distinctUntilChanged()
                .collectLatest { generation ->
                    if (generation == null) {
                        send(WearEntryDirectory(entry, holder.state, isReachable = false))
                    } else {
                        follow(entry, holder)
                    }
                }
        } finally {
            // A broadcast's credentials live in memory only while a list is being looked at (7.9).
            if (publish) latest.remove(entry.identity)
        }
    }

    // Events are collected before the request goes out, so the answer to it cannot be missed; the block ends when
    // collectLatest cancels it for a newer stream generation or the collector goes away.
    private suspend fun ProducerScope<WearEntryDirectory>.follow(entry: WearExchangeEntry, holder: Holder) =
        coroutineScope {
            val reading = launch(start = CoroutineStart.UNDISPATCHED) {
                streams.events.filter { it.identity == entry.identity }.collect { event ->
                    if (onEnvelope(entry, holder, event.envelope)) {
                        send(WearEntryDirectory(entry, holder.state, isReachable = true))
                    }
                }
            }
            streams.send(entry.identity, LIST)
            streams.send(entry.identity, SUBSCRIBE)
            reading.join()
        }

    // True when the held state changed and the screen must be told.
    private fun onEnvelope(entry: WearExchangeEntry, holder: Holder, envelope: WearExchangeEnvelope): Boolean =
        when (envelope.type) {
            WearExchangeEnvelope.TYPE_DIRECTORY -> {
                holder.replace(entry.identity, WearExchangeDirectoryState.of(envelope))
                true
            }
            WearExchangeEnvelope.TYPE_CHANGED -> applyChange(entry, holder, envelope)
            else -> false
        }

    private fun applyChange(entry: WearExchangeEntry, holder: Holder, envelope: WearExchangeEnvelope): Boolean {
        val held = holder.state ?: return false
        return when (val outcome = held.apply(envelope)) {
            is WearExchangeDirectoryState.Outcome.Applied -> {
                holder.replace(entry.identity, outcome.state)
                true
            }
            WearExchangeDirectoryState.Outcome.Gap -> {
                streams.send(entry.identity, LIST)
                false
            }
            WearExchangeDirectoryState.Outcome.Stale -> false
        }
    }

    private inner class Holder(private val publish: Boolean) {
        @Volatile
        var state: WearExchangeDirectoryState? = null
            private set

        fun replace(identity: String, next: WearExchangeDirectoryState) {
            state = next
            if (publish) latest[identity] = next
        }
    }

    private companion object {
        val LIST = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_LIST)
        val SUBSCRIBE = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_SUBSCRIBE)
    }
}
