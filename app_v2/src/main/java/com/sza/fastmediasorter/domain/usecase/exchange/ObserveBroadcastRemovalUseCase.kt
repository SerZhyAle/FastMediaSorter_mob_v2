package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcasts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S4444, LIVE-BROADCAST items T and U: whether the account directory, read complete, has removed a broadcast it listed
 * before - true once removed, false again when the producer announces it anew (DEVICE-EXCHANGE item DX). Nothing is
 * emitted until the broadcast was listed once. A listing that is still pending or misses an unreachable server proves
 * nothing: the last directory of a lost server is kept, and absence from a partial listing is not a removal.
 *
 * S4607, DEVICE-EXCHANGE item BJ: a channel bound to the entry that listed it is judged by that entry's directory
 * alone - another entry's answer, pending state or outage says nothing about it. An unbound channel keeps the
 * account-wide rule above.
 */
class ObserveBroadcastRemovalUseCase @Inject constructor(
    private val observeAccountBroadcasts: ObserveAccountBroadcastsUseCase,
) {

    operator fun invoke(broadcastId: String, entryKey: String? = null): Flow<Boolean> = observeAccountBroadcasts()
        .filter { listing -> if (entryKey == null) listing.isComplete() else entryKey in listing.listedSources }
        .map { listing ->
            listing.items.any { it.broadcastId == broadcastId && (entryKey == null || it.entryKey == entryKey) }
        }
        .dropWhile { listed -> !listed }
        .map { listed -> !listed }
        .distinctUntilChanged()

    private fun ExchangeDeviceBroadcasts.isComplete(): Boolean = !pending && !incomplete
}
