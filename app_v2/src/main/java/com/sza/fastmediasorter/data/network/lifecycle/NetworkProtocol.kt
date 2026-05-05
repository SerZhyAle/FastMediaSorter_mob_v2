package com.sza.fastmediasorter.data.network.lifecycle

/**
 * Identifies a network/cloud protocol family that owns a [NetworkConnectionGate]. S0067.
 */
enum class NetworkProtocol {
    SMB,
    SFTP,
    FTP,
    CLOUD;

    companion object {
        /**
         * Map a resource URI to its protocol, case-insensitively. Returns `null` for local
         * paths or any unrecognized scheme.
         */
        fun fromUri(uri: String): NetworkProtocol? {
            val lower = uri.lowercase()
            return when {
                lower.startsWith("smb://")   -> SMB
                lower.startsWith("sftp://")  -> SFTP
                lower.startsWith("ftp://")   -> FTP
                lower.startsWith("cloud://") -> CLOUD
                else                         -> null
            }
        }
    }
}
