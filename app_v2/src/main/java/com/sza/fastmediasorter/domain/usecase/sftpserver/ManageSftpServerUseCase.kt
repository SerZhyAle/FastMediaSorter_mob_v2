package com.sza.fastmediasorter.domain.usecase.sftpserver

import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerState
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
}
