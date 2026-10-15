package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRandom
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import javax.inject.Inject
import javax.inject.Singleton

/** One epoch of a sealed relay upload: its number, its key, and the group keys its ring seals that key under. */
class ExchangeRelayRing(val epoch: Long, val epochKey: ByteArray, val groupKeys: List<ByteArray>)

/**
 * The keys of the one broadcast this device relays (contract DEVICE-EXCHANGE 27 items DK and DL): the own devices'
 * group key that the record's box carries, one group key per outward link, and the epoch counter. Every upload
 * stream opens a new epoch; dropping an own device replaces the own group key, so the next epoch excludes it.
 */
@Singleton
class ExchangeRelayKeys @Inject constructor() {

    private class State(val broadcastId: String, var ownKey: ByteArray, var epoch: Long, var members: Set<String>?) {
        val links = linkedMapOf<String, ByteArray>()
    }

    @Volatile
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
    fun ownKey(broadcastId: String): ByteArray? = state?.takeIf { it.broadcastId == broadcastId }?.ownKey?.copyOf()

    /** The group key of an outward link of [broadcastId], made on first use (item DK). */
    @Synchronized
    fun linkKey(broadcastId: String, linkId: String): ByteArray? =
        state?.takeIf { it.broadcastId == broadcastId }?.links?.getOrPut(linkId) { newKey() }?.copyOf()

    @Synchronized
    fun dropLink(broadcastId: String, linkId: String) {
        state?.takeIf { it.broadcastId == broadcastId }?.links?.remove(linkId)
    }

    /** A new epoch for the next upload stream, or null when [broadcastId] is not the live one. */
    @Synchronized
    fun newEpoch(broadcastId: String): ExchangeRelayRing? = state?.takeIf { it.broadcastId == broadcastId }?.let {
        it.epoch++
        val groups = (listOf(it.ownKey) + it.links.values).take(E2eRelayRecords.MAX_RING_ENTRIES)
        ExchangeRelayRing(it.epoch, newKey(), groups.map(ByteArray::copyOf))
    }

    /**
     * Records the own devices the record's box now goes to. True when one that was there is gone: the own group
     * key is then replaced, and the caller sends the new box before a replacement upload opens the next epoch.
     */
    @Synchronized
    fun membersChanged(broadcastId: String, members: Set<String>): Boolean {
        val held = state?.takeIf { it.broadcastId == broadcastId } ?: return false
        val dropped = held.members?.any { it !in members } == true
        held.members = members
        if (dropped) held.ownKey = newKey()
        return dropped
    }

    /** False once the own group key [ring] was sealed under was replaced: its upload must give way to a new epoch. */
    @Synchronized
    fun isCurrent(broadcastId: String, ring: ExchangeRelayRing): Boolean =
        state?.takeIf { it.broadcastId == broadcastId }?.ownKey?.contentEquals(ring.groupKeys.firstOrNull()) == true

    private fun newKey(): ByteArray = E2eRandom.secure.next(E2eRelayRecords.GROUP_KEY_LENGTH)
}
