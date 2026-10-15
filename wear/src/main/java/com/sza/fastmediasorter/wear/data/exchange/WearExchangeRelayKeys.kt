package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRandom
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import javax.inject.Inject
import javax.inject.Singleton

/** One epoch of a sealed relay upload: its number, its key, and the group key its ring seals that key under. */
class WearExchangeRelayRing(val epoch: Long, val epochKey: ByteArray, val groupKey: ByteArray)

/**
 * The keys of the one broadcast the watch relays (contract DEVICE-EXCHANGE 27 items DK and DL), mirrored from the
 * phone's `ExchangeRelayKeys` without its outward links: the watch hands out no share link, so its ring holds the
 * own devices' group key alone. Every upload stream opens a new epoch; dropping an own device replaces the group
 * key, so the next epoch excludes it.
 */
@Singleton
class WearExchangeRelayKeys @Inject constructor() {

    private class State(val broadcastId: String, var ownKey: ByteArray, var epoch: Long, var members: Set<String>?)

    private var state: State? = null

    @Synchronized
    fun begin(broadcastId: String) {
        state = State(broadcastId, newKey(), 0L, null)
    }

    @Synchronized
    fun end() {
        state = null
    }

    /** `relayKey` of the record's box (item DK); null when [broadcastId] is not the live one. */
    @Synchronized
    fun ownKey(broadcastId: String): ByteArray? = held(broadcastId)?.ownKey?.copyOf()

    /** A new epoch for the next upload stream, or null when [broadcastId] is not the live one. */
    @Synchronized
    fun newEpoch(broadcastId: String): WearExchangeRelayRing? = held(broadcastId)?.let {
        it.epoch++
        WearExchangeRelayRing(it.epoch, newKey(), it.ownKey.copyOf())
    }

    /**
     * Records the own devices the record's box now goes to. True when one that was there is gone: the group key is
     * then replaced, and the caller sends the new box before a replacement upload opens the next epoch.
     */
    @Synchronized
    fun membersChanged(broadcastId: String, members: Set<String>): Boolean {
        val held = held(broadcastId) ?: return false
        val dropped = held.members?.any { it !in members } == true
        held.members = members
        if (dropped) held.ownKey = newKey()
        return dropped
    }

    /** False once the group key [ring] was sealed under was replaced: its upload gives way to a new epoch. */
    @Synchronized
    fun isCurrent(broadcastId: String, ring: WearExchangeRelayRing): Boolean =
        held(broadcastId)?.ownKey?.contentEquals(ring.groupKey) == true

    private fun held(broadcastId: String): State? = state?.takeIf { it.broadcastId == broadcastId }

    private fun newKey(): ByteArray = E2eRandom.secure.next(E2eRelayRecords.GROUP_KEY_LENGTH)
}
