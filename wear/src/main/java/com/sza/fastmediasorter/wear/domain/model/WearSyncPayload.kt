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
    // S2887: both nullable rather than carrying a Kotlin default, for `endpoints`' reason below - a
    // reference field takes null from an absent key whatever the default says, and a null under a
    // non-null `String` kills the first read rather than the field. Null means the sender wrote no
    // key: `basePath` then reads as "/" and `domain` as "", which is what the defaults claimed.
    val basePath: String? = null,
    val domain: String? = null,     // SMB domain
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
    // S2882 raised it to 4 with `deselectedIds`. Nothing branches on it - it records the contract
    // generation rather than gating anything, and in particular the merge rule reads the presence of
    // the stamps themselves, not this number.
    val version: Int = 4,
    val sentAt: Long,               // Epoch ms - used to reject stale replays (> 24 h)
    val phoneName: String,          // e.g. "Pixel 8 Pro" - shown on watch during transfer
    val sources: List<WearNetworkSourcePayload>,
    // S2885: nullable because Gson leaves an absent field null whatever the Kotlin default says. A
    // phone older than this field sends no `tombstones` key at all, and `= emptyList()` left the
    // reference null under a non-null type, so the first dereference killed the entire import -
    // sources included. Null means the sender ships no deletions, read as empty by the receiver.
    val tombstones: List<WearSourceTombstonePayload>? = null,
    // S2882: the ids the phone declares must NOT be on this watch - its registered resources that
    // carry no watch mark. Exactly these are deleted here, which is what lets an unticked box on the
    // phone reach this watch; a record's absence from `sources` says nothing, because this catalogue
    // is edited on both sides and sources created here carry ids the phone never issued.
    //
    // Absent on a payload from a phone that predates this field, and the null then means "the sender
    // said nothing about exclusions", never "exclude nothing" - nothing is deleted in that case.
    val deselectedIds: List<String>? = null
)

/** A deleted resource event that must survive a later exchange. */
data class WearSourceTombstonePayload(
    val id: String,
    val deletedAt: Long
)

/**
 * Result returned by ImportNetworkSourcesUseCase after a sync operation.
 *
 * @param removed S2882: sources deleted because the phone declared them unwanted here. Declared last
 *   with a default so the older three-value call sites still build. Counted apart from the others
 *   because the phone cannot derive it - it knows what it declared, never what was here to delete.
 */
data class ImportResult(
    val added: Int,
    val updated: Int,
    val skipped: Int,
    val removed: Int = 0
)
