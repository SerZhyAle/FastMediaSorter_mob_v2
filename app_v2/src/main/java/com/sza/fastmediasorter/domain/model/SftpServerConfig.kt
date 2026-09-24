package com.sza.fastmediasorter.domain.model

/**
 * The embedded SFTP server configuration as the rest of the app sees it, decrypted and in memory.
 * `SftpServerSettingsStore` owns persistence; the password is stored there only as ciphertext.
 */
data class SftpServerConfig(
    val enabled: Boolean,
    val port: Int,
    val authMode: SftpServerAuthMode,
    val username: String,
    /** Plain password, or null when none was set yet or the stored cipher text is unreadable. */
    val password: String?,
    /** OpenSSH `authorized_keys` lines accepted in [SftpServerAuthMode.PUBLIC_KEY]. */
    val authorizedKeys: List<String>,
    /** SAF tree URIs, in the order the user picked them; the first one is the login directory. */
    val rootUris: List<String>,
) {
    companion object {
        /**
         * Fixed default with no fallback port: an unprivileged app cannot bind below 1024, and a
         * port that silently moved on conflict would invalidate every pairing code already shared.
         */
        const val DEFAULT_PORT = 2222
        const val MIN_PORT = 1024
        const val MAX_PORT = 65_535
    }
}

enum class SftpServerAuthMode { PASSWORD, PUBLIC_KEY }

/** What a client needs to log in to the embedded server; host addresses belong to the running server. */
data class SftpServerClientCredentials(
    val username: String,
    val authMode: SftpServerAuthMode,
    /** Present only in [SftpServerAuthMode.PASSWORD]. */
    val password: String?,
    /** Canonical `SHA256:<base64-no-padding>` fingerprint of the host public key. */
    val hostKeyFingerprint: String,
)

/** What the embedded SFTP server is doing right now, as the settings surface and the notification show it. */
sealed interface SftpServerState {
    object Stopped : SftpServerState

    object Starting : SftpServerState

    /** [addresses] are the LAN addresses a client can reach; empty when the phone is on no LAN. */
    data class Running(val port: Int, val addresses: List<String>) : SftpServerState

    data class Failed(val reason: SftpServerFailure) : SftpServerState
}

enum class SftpServerFailure {
    /** This build or this Android version cannot run the server. */
    UNAVAILABLE,

    /** No shared folder is picked, or every picked folder lost its access grant. */
    NO_SHARED_FOLDERS,

    /** Password mode without a password, or key mode without an accepted key. */
    NO_CREDENTIAL,

    /** Another app already listens on the configured port. */
    PORT_IN_USE,

    START_FAILED,
}
