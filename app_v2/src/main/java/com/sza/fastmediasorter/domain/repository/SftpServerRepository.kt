package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.domain.model.SftpTunnelState
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

    /** The user's opt-in to put the share's address and password on Google Drive; off by default. */
    val driveAccessShared: Flow<Boolean>
    suspend fun setDriveAccessShared(shared: Boolean)

    suspend fun setPort(port: Int)
    suspend fun setAuthMode(mode: SftpServerAuthMode)
    suspend fun setAuthorizedKeys(lines: List<String>)
    suspend fun addRoot(treeUri: String)
    suspend fun removeRoot(treeUri: String)

    /** False when the new password could not be stored; the old one then stays in force. */
    suspend fun regeneratePassword(): Boolean

    /** The exchange server of contract ANYWHERE-ACCESS: its configuration and the registration on it. */
    val exchangeConfig: Flow<SftpExchangeConfig>
    val tunnelState: StateFlow<SftpTunnelState>

    suspend fun setExchangeEnabled(enabled: Boolean)

    /** Issues a new share id; every code shared before stops opening this server. */
    suspend fun rotateShareId()

    /**
     * Leaves as a producer: unpublishes on the live control stream and stops publishing. True when the server
     * was told, false when the leave ended on this device only; never retried.
     */
    suspend fun withdrawExchangePublication(): Boolean

    /**
     * Unpublishes the share only (item DT): a live broadcast stays announced on the same control stream. True when
     * the server was told, false when the switch-off ended on this device only; never retried.
     */
    suspend fun unpublishExchangeFolder(): Boolean

    /** Re-registers with the current exchange settings while the server runs; nothing otherwise. */
    fun applyExchangeSettings()

    /** The running server's code in the `FMSSFTP1` form, for recipients on older app versions. */
    suspend fun legacyPairingPayload(): SftpPairingPayload?
}
