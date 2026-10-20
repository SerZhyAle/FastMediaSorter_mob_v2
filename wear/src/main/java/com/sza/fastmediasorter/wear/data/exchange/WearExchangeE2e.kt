package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBroadcastSeals
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eRecipients
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import javax.inject.Inject
import javax.inject.Singleton

/** Who a box of one watch entry goes to, and the check a box's sender must pass (contract items DG and DH). */
class WearSealing(
    /** The watch as a sender and reader; null for an entry enrolled without a key, which seals nothing. */
    val self: E2eSelf?,
    val recipients: List<E2eRecipient>,
    private val trusted: Map<String, String>,
) {
    fun trusts(from: String, fromKey: ByteArray): Boolean = E2eRecipients.trusts(trusted, from, fromKey)

    /**
     * Item DK: a copy of [descriptor] with the keys of the broadcast's [box] written into its sealed endpoints'
     * `seal`, so every later step reads them from there. A box that does not open leaves those endpoints keyless,
     * and a keyless sealed endpoint is never played.
     */
    fun openBroadcast(broadcastId: String, box: JsonElement?, descriptor: JsonObject?): JsonObject? {
        val opened = self?.takeIf { box != null }
            ?.let { E2eSealedBox.open(box, E2eSealedBox.broadcastContext(broadcastId), it, ::trusts) }
            as? E2eOpenResult.Opened
        return descriptor?.deepCopy()?.also { E2eBroadcastSeals.withKeys(it, opened?.payload) }
    }
}

/**
 * S4240: the watch's side of the trust rules, the same shared rules the phone applies. [devices] is always the
 * account's whole list - a `directory` answer or the state `changed` keeps - so a kept key whose device is gone
 * was removed from the account and is dropped (item DG).
 */
@Singleton
class WearExchangeE2e @Inject constructor(
    private val store: WearExchangeE2eStore,
) {

    suspend fun sealing(entry: WearExchangeEntry, devices: Collection<WearDirectoryDevice>): WearSealing {
        val peers = devices.map { it.peer() }
        val listed = peers.map { it.deviceId }.toSet()
        val held = store.trusted(entry.deviceId).filterKeys { it in listed }
        val trusted = held + E2eRecipients.learned(entry.deviceId, peers, held)
        if (trusted != store.trusted(entry.deviceId)) store.saveTrusted(entry.deviceId, trusted)
        val self = store.existing(entry.deviceId)?.let { E2eSelf(entry.deviceId, it) }
        val recipients = if (self == null) emptyList() else E2eRecipients.recipients(entry.deviceId, peers, trusted)
        return WearSealing(self, recipients, trusted)
    }

    /** A broadcast offered by `cast` (7.8) opens with the keys the watch already trusts; it seals nothing. */
    suspend fun trustedSealing(entry: WearExchangeEntry): WearSealing {
        val self = store.existing(entry.deviceId)?.let { E2eSelf(entry.deviceId, it) }
        return WearSealing(self, emptyList(), store.trusted(entry.deviceId))
    }
}
