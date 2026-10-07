package com.sza.fastmediasorter.data.remote.sftp.server

import com.sza.fastmediasorter.data.repository.settings.SftpExchangeSettingsStore
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the pairing code for the running embedded server from its live addresses and port and the
 * stored login and host-key fingerprint. The host private key stays in [SftpServerIdentityStore]; only
 * its fingerprint leaves.
 *
 * With a usable exchange server configured the code also carries its endpoint and the share id, which
 * makes it a `FMSSFTP2` code (contract ANYWHERE-ACCESS section 4.1); otherwise it is the `FMSSFTP1`
 * code it always was.
 */
@Singleton
class SftpPairingPayloadFactory @Inject constructor(
    private val controller: SftpServerController,
    private val identityStore: SftpServerIdentityStore,
    private val exchangeSettings: SftpExchangeSettingsStore,
) {

    /** Null while the server is not running or the phone has no LAN address to offer. */
    suspend fun create(): SftpPairingPayload? {
        val running = controller.state.value as? SftpServerState.Running
        val hosts = running?.let { controller.advertisedAddresses().ifEmpty { it.addresses } }.orEmpty()
        if (running == null || hosts.isEmpty()) return null
        val credentials = identityStore.clientCredentials()
        val exchange = exchangeSettings.snapshot().takeIf { it.isUsable }
        return SftpPairingPayload(
            hosts = hosts,
            port = running.port,
            username = credentials.username,
            password = credentials.password,
            hostKeyFingerprint = credentials.hostKeyFingerprint,
            exchangeEndpoint = exchange?.endpoint(),
            shareId = exchange?.shareId,
        )
    }

    /** The `FMSSFTP1` form of [create], for recipients whose app predates the v2 code (contract section 8.2). */
    suspend fun createLegacy(): SftpPairingPayload? =
        create()?.copy(exchangeEndpoint = null, shareId = null, driveChannel = false)
}
