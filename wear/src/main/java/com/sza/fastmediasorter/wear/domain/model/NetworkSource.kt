package com.sza.fastmediasorter.wear.domain.model

import java.util.UUID

/**
 * Type of network storage source.
 */
enum class NetworkSourceType {
    SMB,
    FTP,
    SFTP,
    GOOGLE_DRIVE
}

/**
 * Network storage source configuration.
 */
data class NetworkSource(
    val id: String = UUID.randomUUID().toString(),
    val type: NetworkSourceType,
    val name: String,
    val server: String,
    val port: Int = when(type) {
        NetworkSourceType.SMB -> 445
        NetworkSourceType.FTP -> 21
        NetworkSourceType.SFTP -> 22
        NetworkSourceType.GOOGLE_DRIVE -> 443
    },
    val username: String,
    val password: String, // Encrypted in storage
    val shareName: String? = null,   // SMB only
    val basePath: String = "/",
    // S1555: canonical SHA256 host-key pin set on the phone; null means permissive, as before.
    val hostKeyFingerprint: String? = null,
    val domain: String = "",         // SMB domain
    val sshPrivateKey: String? = null, // SFTP key-auth
    // S2129: `ico-NN-NNN` as sent by the phone, kept opaque here and resolved to a vector only at
    // draw time, so an id this build does not carry degrades to the type glyph instead of failing.
    val iconId: String? = null
)
