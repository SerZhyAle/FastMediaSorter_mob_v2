package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.remote.sftp.server.SftpPairingPayloadFactory
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerController
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerState
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
}
