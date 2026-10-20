package com.sza.fastmediasorter.domain.usecase.sftpserver

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeGrantEnvelopes
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpExchangeProducerSource
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerController
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.domain.model.ExchangeGrant
import com.sza.fastmediasorter.domain.model.ExchangeGrants
import com.sza.fastmediasorter.domain.model.ExchangeLinkCreated
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * The producer's outward access for one shared folder (contract DEVICE-EXCHANGE items BD and BE): a guest grant to a
 * `guest` account of the same server, or a public link with a password; each with its own login confined to the
 * folder, each withdrawn alone. A grant works on this device's SFTP server the moment it is stored; the exchange
 * server is told now when a control stream is live, and again after every `welcome`.
 */
class ManageExchangeGrantsUseCase @Inject constructor(
    private val store: ExchangeGrantRepository,
    private val server: SftpServerController,
    private val identity: SftpServerIdentityStore,
    private val tunnel: SftpServerTunnelManager,
    private val producer: SftpExchangeProducerSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun grants(rootUri: String): Flow<List<ExchangeGrant>> =
        store.grants.map { all -> all.filter { it.rootUri == rootUri } }

    /** The grant ids the exchange server answered `granted` for on the current control stream. */
    val confirmed: StateFlow<Set<String>> get() = tunnel.grantedIds

    /** Null when the secret store refused the password: no grant is kept that nobody could log in with. */
    suspend fun grantGuest(rootUri: String, rootName: String?, guestLogin: String): ExchangeGrant? {
        val grant = ExchangeGrants.newGuest(rootUri, rootName, guestLogin)
        return grant.takeIf { keep(it, ExchangeGrants.generatePassword()) }
    }

    /**
     * A link to the folder: [typedPassword] when the owner typed one long enough, a generated one otherwise. Null
     * when the share is not running or the secret store refused the password.
     */
    suspend fun createLink(rootUri: String, rootName: String?, typedPassword: String?): ExchangeLinkCreated? {
        val password = typedPassword?.takeIf(ExchangeGrants::isAcceptableLinkPassword)
            ?: ExchangeGrants.generatePassword()
        val grant = ExchangeGrants.newLink(rootUri, rootName)
        val code = linkCodeFor(grant)
        return if (code != null && keep(grant, password)) ExchangeLinkCreated(grant, code, password) else null
    }

    /** The code of a link made earlier, as it reads with the server's current addresses. */
    suspend fun linkCode(grantId: String): String? =
        store.snapshot().firstOrNull { it.grantId == grantId && it.kind == ExchangeGrant.Kind.LINK }
            ?.let { linkCodeFor(it) }

    /** Withdraws the grant everywhere: the login stops at once, its sessions end and the server drops it. */
    suspend fun revoke(grantId: String) {
        val grant = store.snapshot().firstOrNull { it.grantId == grantId } ?: return
        Timber.d("S4224: grant withdrawn kind=${grant.kind}")
        store.remove(grantId)
        server.refreshGrantLogins()
        server.closeSessionsOf(grant.login)
        withContext(ioDispatcher) { tunnel.sendControl(ExchangeGrantEnvelopes.ungrant(grantId)) }
    }

    private suspend fun keep(grant: ExchangeGrant, password: String): Boolean {
        val kept = store.add(grant, password)
        if (kept) {
            Timber.d("S4224: grant kept kind=${grant.kind}")
            server.refreshGrantLogins()
            tunnel.resendGrants()
        }
        return kept
    }

    // ANYWHERE-ACCESS item K: the link's own login, no `pw`, the outgoing server and the link's own share id.
    private suspend fun linkCodeFor(grant: ExchangeGrant): String? {
        val running = server.state.value as? SftpServerState.Running ?: return null
        return SftpPairingPayload(
            hosts = running.addresses,
            port = running.port,
            username = grant.login,
            password = null,
            hostKeyFingerprint = identity.clientCredentials().hostKeyFingerprint,
            exchangeEndpoint = producer.outgoingEntry()?.let { endpoint(it.host, it.port) },
            shareId = grant.linkShareId,
        ).encode()
    }

    private fun endpoint(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"
}
