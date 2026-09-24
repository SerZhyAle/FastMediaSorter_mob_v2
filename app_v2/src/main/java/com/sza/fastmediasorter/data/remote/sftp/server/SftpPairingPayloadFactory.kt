package com.sza.fastmediasorter.data.remote.sftp.server

import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the pairing code for the running embedded server from its live addresses and port and the
 * stored login and host-key fingerprint. The host private key stays in [SftpServerIdentityStore]; only
 * its fingerprint leaves.
 */
@Singleton
class SftpPairingPayloadFactory @Inject constructor(
    private val controller: SftpServerController,
    private val identityStore: SftpServerIdentityStore,
) {

    /** Null while the server is not running or the phone has no LAN address to offer. */
    suspend fun create(): SftpPairingPayload? {
        val running = controller.state.value as? SftpServerState.Running
        val hosts = running?.let { controller.advertisedAddresses().ifEmpty { it.addresses } }.orEmpty()
        if (running == null || hosts.isEmpty()) return null
        val credentials = identityStore.clientCredentials()
        return SftpPairingPayload(
            hosts = hosts,
            port = running.port,
            username = credentials.username,
            password = credentials.password,
            hostKeyFingerprint = credentials.hostKeyFingerprint,
        )
    }
}
