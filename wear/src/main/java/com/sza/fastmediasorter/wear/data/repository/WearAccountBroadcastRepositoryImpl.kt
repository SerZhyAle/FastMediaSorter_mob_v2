package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoints
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPlaybackResolver
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.data.exchange.WearEntryDirectory
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeBroadcastDirectory
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeDirectoryState
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcastSection
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcasterGroup
import com.sza.fastmediasorter.wear.domain.repository.WearAccountBroadcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4229: the list is the directory of each entry grouped by the device that makes the broadcast, the device's own
 * broadcasts left out (contract DEVICE-EXCHANGE 9.1). Online devices come first, then by name, so a person looking
 * for a broadcast that is on sees it without scrolling past the offline ones.
 */
@Singleton
class WearAccountBroadcastRepositoryImpl @Inject constructor(
    private val directory: WearExchangeBroadcastDirectory,
    private val resolver: WearBroadcastPlaybackResolver,
) : WearAccountBroadcastRepository {

    override fun observe(): Flow<List<WearAccountBroadcastSection>> = directory.observe().map { entries ->
        entries.map(::section)
    }

    override suspend fun resolve(entryIdentity: String, broadcastId: String): WearBroadcastPlayback {
        val record = directory.broadcast(entryIdentity, broadcastId)
        return if (record == null) {
            WearBroadcastPlayback.Unavailable(WearBroadcastPlayback.Reason.UNREACHABLE)
        } else {
            resolver.resolve(record)
        }
    }

    private fun section(held: WearEntryDirectory): WearAccountBroadcastSection {
        val state = held.state
        return WearAccountBroadcastSection(
            entryIdentity = held.entry.identity,
            entryLabel = labelOf(held.entry),
            groups = state?.let { groups(held.entry, it) }.orEmpty(),
            isReachable = held.isReachable,
            isLoaded = state != null,
            failure = held.failure,
        )
    }

    private fun groups(entry: WearExchangeEntry, state: WearExchangeDirectoryState): List<WearBroadcasterGroup> =
        state.broadcasts.values
            .filter { it.deviceId != entry.deviceId }
            .groupBy { it.deviceId }
            .map { (deviceId, records) ->
                val device = state.devices[deviceId]
                WearBroadcasterGroup(
                    deviceName = device?.name,
                    isOnline = device?.isOnline ?: true,
                    broadcasts = records.map { toRow(entry.identity, it) }.sortedBy { it.title.orEmpty().lowercase() },
                )
            }
            .sortedWith(compareByDescending<WearBroadcasterGroup> { it.isOnline }.thenBy { it.deviceName.orEmpty() })

    private fun toRow(entryIdentity: String, record: WearDirectoryBroadcast): WearAccountBroadcast {
        val isProtected = WearBroadcastEndpoints.isProtected(record.descriptor)
        val endpoints = WearBroadcastEndpoints.parse(record.broadcastId, record.descriptor, record.mode)
        val modeOk = endpoints.any { WearBroadcastEndpoints.isPlayableMode(it.mode ?: record.mode) }
        return WearAccountBroadcast(
            entryIdentity = entryIdentity,
            broadcastId = record.broadcastId,
            title = record.title,
            mode = record.mode,
            isProtected = isProtected,
            isPlayable = modeOk && (!isProtected || record.credentials != null),
        )
    }

    // An IPv6 literal is bracketed, as the phone writes the address of an entry.
    private fun labelOf(entry: WearExchangeEntry): String {
        val host = if (':' in entry.host) "[${entry.host}]" else entry.host
        return "$host:${entry.port}"
    }
}
