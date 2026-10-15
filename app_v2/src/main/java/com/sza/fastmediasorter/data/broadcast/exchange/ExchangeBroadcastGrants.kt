package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastGrant
import com.sza.fastmediasorter.domain.model.SftpShareId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The guest grants of the live broadcast (contract DEVICE-EXCHANGE items BD and BF), in memory only: each owns a
 * credential pair in [credentials] and a relay group key in [relayKeys], and both go when the grant does.
 */
@Singleton
class ExchangeBroadcastGrants @Inject constructor(
    private val credentials: BroadcastCredentialRegistry,
    private val relayKeys: ExchangeRelayKeys,
) {

    private val held = MutableStateFlow<List<ExchangeBroadcastGrant>>(emptyList())

    val grants: StateFlow<List<ExchangeBroadcastGrant>> = held.asStateFlow()

    /**
     * Gives [broadcastId] to [guest]; null when the broadcast is not protected, the relay ring is full or the guest
     * already holds a grant of it.
     */
    @Synchronized
    fun add(broadcastId: String, guest: String): ExchangeBroadcastGrant? {
        if (held.value.any { it.broadcastId == broadcastId && it.guest == guest }) return null
        val grantId = SftpShareId.generate()
        val pair = credentials.addGuest(grantId)
        val key = pair?.let { relayKeys.linkKey(broadcastId, grantId) }
        if (key == null) credentials.removeGuest(grantId)
        return key?.let {
            ExchangeBroadcastGrant(grantId, guest, broadcastId).also { grant -> held.update { all -> all + grant } }
        }
    }

    @Synchronized
    fun remove(grantId: String) {
        val grant = held.value.firstOrNull { it.grantId == grantId } ?: return
        credentials.removeGuest(grantId)
        relayKeys.dropLink(grant.broadcastId, grantId)
        held.update { all -> all.filterNot { it.grantId == grantId } }
    }

    /** Drops every grant; the broadcast's own end already dropped their pairs and keys. */
    @Synchronized
    fun clear() {
        held.value.forEach { credentials.removeGuest(it.grantId) }
        held.value = emptyList()
    }

    fun markGranted(grantId: String) = mark { it.grantId == grantId }

    /** The server answers a refused `grant` without its id, so the oldest unanswered grant takes it. */
    fun markRefusedPending() {
        val oldest = held.value.firstOrNull { it.state == ExchangeBroadcastGrant.State.PENDING } ?: return
        mark(ExchangeBroadcastGrant.State.REFUSED) { it.grantId == oldest.grantId }
    }

    private fun mark(
        state: ExchangeBroadcastGrant.State = ExchangeBroadcastGrant.State.GRANTED,
        match: (ExchangeBroadcastGrant) -> Boolean,
    ) {
        held.update { all -> all.map { if (match(it)) it.copy(state = state) else it } }
    }
}
