package com.sza.fastmediasorter.wear.domain.model

import java.util.UUID

/**
 * Type of network storage source.
 */
enum class NetworkSourceType {
    SMB,
    FTP,
    SFTP
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
    val iconId: String? = null,
    // S2487: per-resource allowed media types and allFiles flag from phone resource configuration
    val supportedMediaTypes: List<String>? = null,
    val allFiles: Boolean = false,
    // S2488: the address group this source may be reached at, ordered, first being the one the phone
    // found reachable when it sent. Null for a source added by hand and for one stored before this
    // field existed - both then behave as a group of one, which is [server]:[port].
    val endpoints: List<WearEndpoint>? = null,
    // S2502: when this record was last edited, in this watch's own time base. Null for a source stored
    // before this field existed and for one an exchange applied from a sender that carries no stamps -
    // the merge rule reads that absence as "the age of this record cannot be stated", never as zero.
    val lastEditedAt: Long? = null,
    // S4228: the exchange binding (contract DEVICE-EXCHANGE 23 item CG) - an entry identity in the form
    // WearExchangeEntry.identity, and the producer's resourceId. Both null for a source with no account behind it.
    val exchangeAccount: String? = null,
    val exchangeResourceId: String? = null,
    // S4228: set on the phone; deletion is refused and hidden on the watch (20 item BH).
    val readOnly: Boolean = false,
    // S4228: the root asks for its original PIN (17 item AG), kept apart in the secret store by [identityRoot].
    val pinProtected: Boolean = false
) {
    /** S4228: the root half of the folder's identity (20 item BA); the other half is [hostKeyFingerprint]. */
    val identityRoot: String
        get() = basePath.trim('/')
}

/**
 * S2488: one endpoint of a source's address group. Declared in this package deliberately - the
 * `-keep` rule at `wear/proguard-rules.pro` covers `domain.model.**`, which is what lets these
 * models round-trip as JSON without pinning every field name.
 */
data class WearEndpoint(
    val host: String,
    val port: Int
)
