package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single network source serialized for Wearable Data Layer transfer.
 * Credentials are included as plaintext - the Data Layer channel is TLS-encrypted;
 * the payload is stored only in EncryptedSharedPreferences on the watch.
 *
 * S1631: keys pinned - the watch reads this contract by its real field names.
 */
data class WearNetworkSourcePayload(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,               // "SMB" | "FTP" | "SFTP"
    @SerializedName("name") val name: String,
    @SerializedName("server") val server: String,
    @SerializedName("port") val port: Int,
    @SerializedName("username") val username: String,
    // Plaintext, resolved from CryptoHelper on phone before sending
    @SerializedName("password") val password: String,
    @SerializedName("shareName") val shareName: String? = null,  // SMB only
    @SerializedName("basePath") val basePath: String = "/",
    @SerializedName("domain") val domain: String = "",           // SMB domain
    @SerializedName("sshPrivateKey") val sshPrivateKey: String? = null, // SFTP key-auth
    // S1555: canonical SHA256 host-key pin (S0046), normalised here so the watch needs no parser.
    // Null keeps the watch permissive, which is what every source saved before this field does.
    @SerializedName("hostKeyFingerprint") val hostKeyFingerprint: String? = null
)

/**
 * Top-level sync envelope sent from phone to watch via DataClient.putDataItem.
 */
data class WearSyncPayload(
    @SerializedName("version") val version: Int = 1,
    // Epoch ms - used to reject stale replays (> 24 h)
    @SerializedName("sentAt") val sentAt: Long,
    // e.g. "Pixel 8 Pro" - shown on watch during transfer
    @SerializedName("phoneName") val phoneName: String,
    @SerializedName("sources") val sources: List<WearNetworkSourcePayload>
)
