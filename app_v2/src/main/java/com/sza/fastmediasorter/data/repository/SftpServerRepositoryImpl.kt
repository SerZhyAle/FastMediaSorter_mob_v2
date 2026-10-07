package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.remote.sftp.server.SftpPairingPayloadFactory
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerController
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.SftpExchangeSettingsStore
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import com.sza.fastmediasorter.domain.repository.SftpServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpServerRepositoryImpl @Inject constructor(
    private val controller: SftpServerController,
    private val settingsStore: SftpServerSettingsStore,
    private val identityStore: SftpServerIdentityStore,
    private val pairingPayloadFactory: SftpPairingPayloadFactory,
    private val exchangeStore: SftpExchangeSettingsStore,
    private val tunnelManager: SftpServerTunnelManager,
) : SftpServerRepository {

    override val state: StateFlow<SftpServerState> get() = controller.state
    override val config: Flow<SftpServerConfig> get() = settingsStore.values

    override suspend fun clientCredentials(): SftpServerClientCredentials = identityStore.clientCredentials()

    override suspend fun pairingPayload(): SftpPairingPayload? = pairingPayloadFactory.create()

    override suspend fun setEnabled(enabled: Boolean) = settingsStore.setEnabled(enabled)

    override suspend fun setPort(port: Int) = settingsStore.setPort(port)

    override suspend fun setAuthMode(mode: SftpServerAuthMode) = settingsStore.setAuthMode(mode)

    override suspend fun setAuthorizedKeys(lines: List<String>) = settingsStore.setAuthorizedKeys(lines)

    override suspend fun addRoot(treeUri: String) = settingsStore.addRoot(treeUri)

    override suspend fun removeRoot(treeUri: String) = settingsStore.removeRoot(treeUri)

    override suspend fun regeneratePassword(): Boolean = identityStore.regeneratePassword()

    override val exchangeConfig: Flow<SftpExchangeConfig> get() = exchangeStore.values
    override val tunnelState: StateFlow<SftpTunnelState> get() = tunnelManager.state

    override suspend fun setExchangeEnabled(enabled: Boolean) = exchangeStore.setEnabled(enabled)

    override suspend fun setExchangeServer(host: String, port: Int) = exchangeStore.setServer(host, port)

    override suspend fun setExchangePassword(password: String): Boolean = exchangeStore.setPassword(password)

    override suspend fun rotateShareId() = exchangeStore.rotateShareId()

    override suspend fun trustExchangeServerAgain() = exchangeStore.clearCertificatePin()

    override fun applyExchangeSettings() = controller.restartTunnel()

    override suspend fun legacyPairingPayload(): SftpPairingPayload? = pairingPayloadFactory.createLegacy()
}
