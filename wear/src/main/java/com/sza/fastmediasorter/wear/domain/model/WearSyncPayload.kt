package com.sza.fastmediasorter.wear.domain.model

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
    val sshPrivateKey: String? = null, // SFTP key-auth
    // S1555: canonical SHA256 host-key pin, already normalised by the phone. Absent on payloads
    // from an older phone, which keeps the permissive behaviour rather than refusing to connect.
    val hostKeyFingerprint: String? = null,
    // S2129: the resource's own `ico-NN-NNN` id. Absent on payloads from a phone older than this
    // field, which keeps the type-derived glyph rather than failing the import.
    val iconId: String? = null,
    // S2487: per-resource allowed media types and allFiles flag from phone resource configuration
    val supportedMediaTypes: List<String>? = null,
    val allFiles: Boolean? = null,
    // S2488: ordered connection endpoints, first being the one the phone found reachable when it sent.
    // Absent on a payload that resolved no group, and then `server`/`port` stay authoritative.
    val endpoints: List<WearEndpointPayload>? = null,
    // S2502: when the sender last edited this record, in the sender's own time base. Absent on a
    // payload from a side that predates the stamp, and the receiver then applies the record exactly
    // as it did before this ticket rather than refusing it.
    val lastEditedAt: Long? = null
)

/** S2488: one connection endpoint of a source's address group. Wire names must match the phone's. */
data class WearEndpointPayload(
    val host: String,
    val port: Int
)

/**
 * Top-level sync envelope sent from phone to watch via DataClient.putDataItem.
 */
data class WearSyncPayload(
    // S2488: raised to 2 with the `endpoints` field. S2502 raised it to 3 with `lastEditedAt`.
    // Nothing branches on it - it records the contract generation rather than gating anything, and in
    // particular the merge rule reads the presence of the stamps themselves, not this number.
    val version: Int = 3,
    val sentAt: Long,               // Epoch ms - used to reject stale replays (> 24 h)
    val phoneName: String,          // e.g. "Pixel 8 Pro" - shown on watch during transfer
    val sources: List<WearNetworkSourcePayload>,
    val tombstones: List<WearSourceTombstonePayload> = emptyList()
)

/** A deleted resource event that must survive a later exchange. */
data class WearSourceTombstonePayload(
    val id: String,
    val deletedAt: Long
)

/**
 * Result returned by ImportNetworkSourcesUseCase after a sync operation.
 */
data class ImportResult(
    val added: Int,
    val updated: Int,
    val skipped: Int
)
