package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The embedded SFTP server as the rest of the app may see it: its configuration and its live state. */
interface SftpServerRepository {
    val state: StateFlow<SftpServerState>
    val config: Flow<SftpServerConfig>

    suspend fun clientCredentials(): SftpServerClientCredentials

    /** The pairing code of the running server, or null while it is not running or has no LAN address. */
    suspend fun pairingPayload(): SftpPairingPayload?

    suspend fun setEnabled(enabled: Boolean)
    suspend fun setPort(port: Int)
    suspend fun setAuthMode(mode: SftpServerAuthMode)
    suspend fun setAuthorizedKeys(lines: List<String>)
    suspend fun addRoot(treeUri: String)
    suspend fun removeRoot(treeUri: String)

    /** False when the new password could not be stored; the old one then stays in force. */
    suspend fun regeneratePassword(): Boolean
}
