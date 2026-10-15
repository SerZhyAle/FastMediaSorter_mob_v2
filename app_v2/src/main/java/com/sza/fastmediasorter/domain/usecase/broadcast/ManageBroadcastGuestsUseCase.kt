package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastGrants
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastGrant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * The live broadcast's guests (contract DEVICE-EXCHANGE item BD): gives the announced broadcast to a `guest` login
 * of the same server, keeps that guest for the broadcast's source so the next broadcast of it is given again, and
 * withdraws one grant alone.
 */
class ManageBroadcastGuestsUseCase @Inject constructor(
    private val broadcastGrants: ExchangeBroadcastGrants,
    private val announcer: ExchangeBroadcastAnnouncer,
    private val store: ExchangeGrantRepository,
    private val tunnel: SftpServerTunnelManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    val grants: StateFlow<List<ExchangeBroadcastGrant>> get() = broadcastGrants.grants

    /** A broadcast can be given only while it is announced through an exchange server. */
    val available: Boolean get() = announcer.announcedBroadcastId != null

    /**
     * False for a malformed login, with no announced broadcast, when the guest already holds this broadcast or the
     * relay has no room for another key.
     */
    suspend fun give(guest: String): Boolean {
        Timber.d("S4272: phone gives the live broadcast to a guest")
        val login = guest.trim()
        val broadcastId = announcer.announcedBroadcastId
        val sourceKey = announcer.announcedSourceKey
        val added = if (DeviceExchangeAccountEntry.isValidLogin(login) && broadcastId != null && sourceKey != null) {
            broadcastGrants.add(broadcastId, login)?.also { store.keepBroadcastGuest(sourceKey, login) }
        } else {
            null
        }
        added?.let { tunnel.resendGrants() }
        return added != null
    }

    /** The guest's pair and relay key go at once; the server is told when a control stream is live. */
    suspend fun withdraw(grantId: String) {
        val grant = broadcastGrants.grants.value.firstOrNull { it.grantId == grantId } ?: return
        broadcastGrants.remove(grantId)
        announcer.announcedSourceKey?.let { store.forgetBroadcastGuest(it, grant.guest) }
        withContext(ioDispatcher) { tunnel.sendUngrant(grantId) }
    }
}
