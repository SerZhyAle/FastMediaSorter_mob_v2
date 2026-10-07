package com.sza.fastmediasorter.domain.usecase.sftpserver

import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpExchangePasswordPolicy
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import com.sza.fastmediasorter.domain.repository.SftpServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The settings surface's single entry point to the embedded SFTP server: read its state and
 * configuration, change the configuration. Starting and stopping go through the foreground service,
 * which owns the server's lifetime.
 */
class ManageSftpServerUseCase @Inject constructor(
    private val repository: SftpServerRepository,
) {
    val state: StateFlow<SftpServerState> get() = repository.state
    val config: Flow<SftpServerConfig> get() = repository.config

    suspend fun clientCredentials(): SftpServerClientCredentials = repository.clientCredentials()

    /** The running server's pairing code as the text a QR code carries, or null while none can be made. */
    suspend fun pairingCode(): String? = repository.pairingPayload()?.encode()

    suspend fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)

    /**
     * Returns false, changing nothing, for a port outside
     * [SftpServerConfig.MIN_PORT]..[SftpServerConfig.MAX_PORT].
     */
    suspend fun setPort(port: Int): Boolean {
        if (port !in SftpServerConfig.MIN_PORT..SftpServerConfig.MAX_PORT) return false
        repository.setPort(port)
        return true
    }

    suspend fun setAuthMode(mode: SftpServerAuthMode) = repository.setAuthMode(mode)

    suspend fun setAuthorizedKeys(lines: List<String>) = repository.setAuthorizedKeys(lines)

    suspend fun addRoot(treeUri: String) = repository.addRoot(treeUri)

    suspend fun removeRoot(treeUri: String) = repository.removeRoot(treeUri)

    suspend fun regeneratePassword(): Boolean = repository.regeneratePassword()

    val exchangeConfig: Flow<SftpExchangeConfig> get() = repository.exchangeConfig
    val tunnelState: StateFlow<SftpTunnelState> get() = repository.tunnelState

    /** The `FMSSFTP1` code of the running server, or null while none can be made. */
    suspend fun legacyPairingCode(): String? = repository.legacyPairingPayload()?.encode()

    suspend fun setExchangeEnabled(enabled: Boolean) {
        repository.setExchangeEnabled(enabled)
        repository.applyExchangeSettings()
    }

    /** Returns false, changing nothing, for a blank host or a port outside 1..65535. */
    suspend fun setExchangeServer(host: String, port: Int): Boolean {
        if (host.isBlank() || port !in 1..SftpExchangeConfig.MAX_PORT) return false
        repository.setExchangeServer(host, port)
        repository.applyExchangeSettings()
        return true
    }

    /** Returns false, storing nothing, for a password below the owner's floor or a refused Keystore write. */
    suspend fun setExchangePassword(password: String): Boolean {
        val stored = SftpExchangePasswordPolicy.accepts(password) && repository.setExchangePassword(password)
        if (stored) repository.applyExchangeSettings()
        return stored
    }

    suspend fun rotateShareId() {
        repository.rotateShareId()
        repository.applyExchangeSettings()
    }

    suspend fun trustExchangeServerAgain() {
        repository.trustExchangeServerAgain()
        repository.applyExchangeSettings()
    }
}
