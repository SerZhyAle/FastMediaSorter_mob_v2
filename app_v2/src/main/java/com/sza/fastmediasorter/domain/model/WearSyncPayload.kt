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
    // S2887: both nullable rather than carrying a Kotlin default, for `endpoints`' reason below - a
    // reference field takes null from an absent key whatever the default says, and a null under a
    // non-null `String` kills the first read rather than the field. Null means the sender wrote no
    // key: `basePath` then reads as "/" and `domain` as "", which is what the defaults claimed.
    @SerializedName("basePath") val basePath: String? = null,
    @SerializedName("domain") val domain: String? = null,        // SMB domain
    @SerializedName("sshPrivateKey") val sshPrivateKey: String? = null, // SFTP key-auth
    // S1555: canonical SHA256 host-key pin (S0046), normalised here so the watch needs no parser.
    // Null keeps the watch permissive, which is what every source saved before this field does.
    @SerializedName("hostKeyFingerprint") val hostKeyFingerprint: String? = null,
    // S2129: the resource's own `ico-NN-NNN` id, resolved to a vector on the watch (ADR-1).
    // Null on a resource that never got one, and absent from an older phone's payload - both
    // cases leave the watch on its type-derived glyph rather than refusing the source.
    @SerializedName("iconId") val iconId: String? = null,
    // S2487: per-resource allowed media types and allFiles flag from phone resource configuration
    @SerializedName("supportedMediaTypes") val supportedMediaTypes: List<String>? = null,
    @SerializedName("allFiles") val allFiles: Boolean? = null,
    // S2488: ordered connection endpoints for this source, first element being the one the phone found
    // reachable when it sent. Nullable because Gson leaves an absent field null whatever the Kotlin
    // default says; `server`/`port` stay authoritative for a payload that carries no list.
    @SerializedName("endpoints") val endpoints: List<WearEndpointPayload>? = null,
    // S2502: when the sender last edited this record, in the sender's own time base. Absent on a
    // payload from a side that predates the stamp, and the receiver then applies the record exactly
    // as it did before this ticket rather than refusing it.
    @SerializedName("lastEditedAt") val lastEditedAt: Long? = null
)

/** S2488: one connection endpoint of a source's address group. */
data class WearEndpointPayload(
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int
)

/**
 * Top-level sync envelope sent from phone to watch via DataClient.putDataItem.
 */
data class WearSyncPayload(
    // S2488: raised to 2 with the `endpoints` field. S2502 raised it to 3 with `lastEditedAt`.
    // S2882 raised it to 4 with `deselectedIds`. Neither side branches on it - it records the
    // contract generation rather than gating anything, and in particular the merge rule reads the
    // presence of the stamps themselves, not this number.
    @SerializedName("version") val version: Int = 4,
    // Epoch ms - used to reject stale replays (> 24 h)
    @SerializedName("sentAt") val sentAt: Long,
    // e.g. "Pixel 8 Pro" - shown on watch during transfer
    @SerializedName("phoneName") val phoneName: String,
    @SerializedName("sources") val sources: List<WearNetworkSourcePayload>,
    // S2885: nullable for the same reason `endpoints` above is - Gson leaves an absent field null
    // whatever the Kotlin default says, so `= emptyList()` protected nothing and the receiver's first
    // dereference killed the whole import. A null here means the sender ships no deletions at all,
    // which for a tombstone list is the same as declaring none, so the receiver reads it as empty.
    @SerializedName("tombstones") val tombstones: List<WearSourceTombstonePayload>? = null,
    // S2882: the ids this phone declares must NOT be on the watch - its registered resources that
    // carry no watch mark. The watch deletes exactly these and nothing else, which is what lets an
    // unticked box reach it; an absence from `sources` cannot say the same, because this catalogue is
    // edited on both sides and the watch has its own records the phone never issued.
    //
    // Nullable, and the null means "this sender said nothing about exclusions", never "exclude
    // nothing": Gson leaves an absent field null whatever the Kotlin default says, so a payload from
    // a phone that predates this field must leave the watch's set untouched.
    @SerializedName("deselectedIds") val deselectedIds: List<String>? = null
)

/** A deleted resource event that must survive a later exchange. */
data class WearSourceTombstonePayload(
    @SerializedName("id") val id: String,
    @SerializedName("deletedAt") val deletedAt: Long
)
