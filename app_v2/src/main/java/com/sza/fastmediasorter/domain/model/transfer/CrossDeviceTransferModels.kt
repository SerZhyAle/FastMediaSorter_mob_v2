package com.sza.fastmediasorter.domain.model.transfer

/**
 * S3040: the domain vocabulary of a cross-device transfer packet held in Google Drive AppData.
 *
 * A packet is asynchronous by design - the sender leaves it in the user's own `appDataFolder` and
 * the receiver picks it up whenever it next syncs, so every field the receiver needs to decide
 * without contacting the sender travels inside the manifest rather than beside it.
 *
 * S4231: the shipped manifest is version 1 of contract DEVICE-EXCHANGE item CN; version 2 adds
 * members a v1 reader ignores and kinds a v1 reader skips.
 */

/** What a packet carries. `SETTINGS` and `MEDIA_FILES` are the v1 kinds; the rest need `schemaVersion` 2. */
enum class CrossDevicePayloadKind {
    SETTINGS,
    MEDIA_FILES,
    FILES,
    CLIPBOARD,
    REQUEST,
    REPLY
}

/** Where a packet stands in the queue, as recorded in its own manifest. */
enum class CrossDevicePacketStatus {
    PENDING,
    CLAIMED,
    EXPIRED
}

/** What the receiver asked for: keep the packet in the cloud, or take it and free the quota. */
enum class CrossDeviceTransferOption {
    ACCEPT,
    ACCEPT_AND_DELETE
}

/** What one accepted packet produced: the files written, or the settings import having run. */
data class CrossDeviceReceiveOutcome(
    val packetId: String,
    val writtenPaths: List<String>,
    val settingsApplied: Boolean
)

/** One payload file as a v2 manifest describes it; [sha256] is lower-case hex over the bytes. */
data class CrossDevicePacketFile(
    val name: String,
    val size: Long,
    val sha256: String?,
    val mimeType: String = DEFAULT_MIME_TYPE,
    val clipKind: ClipKind? = null
) {
    companion object {
        const val DEFAULT_MIME_TYPE: String = "application/octet-stream"
    }
}

/**
 * The manifest stored as `manifest.json` beside a packet's payload files.
 *
 * [targetDeviceName] is null for a broadcast packet any of the user's devices may claim; a named
 * target is a hint the receiver filters on, never an access control - the whole queue lives in one
 * private AppData space the user already owns. When [targetDeviceId] is present it decides the
 * target and [targetDeviceName] is only shown; it is still written because the shipped reader
 * filters on it.
 */
data class CrossDevicePacketManifest(
    val packetId: String,
    val createdAtEpochMs: Long,
    val senderDeviceName: String,
    val targetDeviceName: String?,
    val payloadKind: CrossDevicePayloadKind,
    val fileNames: List<String>,
    val totalSizeBytes: Long,
    val ttlDays: Int = DEFAULT_TTL_DAYS,
    val status: CrossDevicePacketStatus = CrossDevicePacketStatus.PENDING,
    val schemaVersion: Int = SCHEMA_V1,
    val senderDeviceId: String? = null,
    val targetDeviceId: String? = null,
    val files: List<CrossDevicePacketFile> = emptyList(),
    val inReplyTo: String? = null,
    val ttlSeconds: Long? = null,
    val request: DriveChannelRequest? = null,
    val reply: DriveChannelReplyHeader? = null
) {

    companion object {
        const val DEFAULT_TTL_DAYS: Int = 7
        const val MANIFEST_FILE_NAME: String = "manifest.json"
        const val SCHEMA_V1: Int = 1
        const val SCHEMA_V2: Int = 2

        /** Serialize [manifest] to the JSON text written as `manifest.json`. */
        fun toJson(manifest: CrossDevicePacketManifest): String = CrossDevicePacketCodec.toJson(manifest)

        /** Parse `manifest.json` text, or null when it is not a manifest this build can read. */
        fun fromJson(raw: String): CrossDevicePacketManifest? = CrossDevicePacketCodec.fromJson(raw)
    }
}
