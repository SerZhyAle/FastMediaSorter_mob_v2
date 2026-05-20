package com.sza.fastmediasorter.domain.model

/**
 * Represents a single network source serialized for Wearable Data Layer transfer.
 * Credentials are included as plaintext - the Data Layer channel is TLS-encrypted;
 * the payload is stored only in EncryptedSharedPreferences on the watch.
 */
data class WearNetworkSourcePayload(
    val id: String,
    val type: String,               // "SMB" | "FTP" | "SFTP"
    val name: String,
    val server: String,
    val port: Int,
    val username: String,
    val password: String,           // Plaintext, resolved from CryptoHelper on phone before sending
    val shareName: String? = null,  // SMB only
    val basePath: String = "/",
    val domain: String = "",        // SMB domain
    val sshPrivateKey: String? = null // SFTP key-auth
)

/**
 * Top-level sync envelope sent from phone to watch via DataClient.putDataItem.
 */
data class WearSyncPayload(
    val version: Int = 1,
    val sentAt: Long,               // Epoch ms - used to reject stale replays (> 24 h)
    val phoneName: String,          // e.g. "Pixel 8 Pro" - shown on watch during transfer
    val sources: List<WearNetworkSourcePayload>
)
