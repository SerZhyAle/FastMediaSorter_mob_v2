package com.sza.fastmediasorter.domain.model.transfer

import org.json.JSONArray
import org.json.JSONObject

/**
 * S3040: the domain vocabulary of a cross-device transfer packet held in Google Drive AppData.
 *
 * A packet is asynchronous by design - the sender leaves it in the user's own `appDataFolder` and
 * the receiver picks it up whenever it next syncs, so every field the receiver needs to decide
 * without contacting the sender travels inside the manifest rather than beside it.
 */

/** What a packet carries: an exported settings payload, or one or more media files. */
enum class CrossDevicePayloadKind {
    SETTINGS,
    MEDIA_FILES
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

/**
 * The manifest stored as `manifest.json` beside a packet's payload files.
 *
 * [targetDeviceName] is null for a broadcast packet any of the user's devices may claim; a named
 * target is a hint the receiver filters on, never an access control - the whole queue lives in one
 * private AppData space the user already owns.
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
    val status: CrossDevicePacketStatus = CrossDevicePacketStatus.PENDING
) {

    /** True once [nowEpochMs] is past the packet's TTL, which makes it eligible for purging. */
    fun isExpiredAt(nowEpochMs: Long): Boolean =
        nowEpochMs - createdAtEpochMs > ttlDays * MILLIS_PER_DAY

    companion object {
        const val DEFAULT_TTL_DAYS: Int = 7
        const val MANIFEST_FILE_NAME: String = "manifest.json"
        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

        private const val KEY_PACKET_ID = "packetId"
        private const val KEY_CREATED_AT = "createdAtEpochMs"
        private const val KEY_SENDER = "senderDeviceName"
        private const val KEY_TARGET = "targetDeviceName"
        private const val KEY_PAYLOAD_KIND = "payloadKind"
        private const val KEY_FILE_NAMES = "fileNames"
        private const val KEY_TOTAL_SIZE = "totalSizeBytes"
        private const val KEY_TTL_DAYS = "ttlDays"
        private const val KEY_STATUS = "status"

        /** Serialize [manifest] to the JSON text written as `manifest.json`. */
        fun toJson(manifest: CrossDevicePacketManifest): String =
            JSONObject().apply {
                put(KEY_PACKET_ID, manifest.packetId)
                put(KEY_CREATED_AT, manifest.createdAtEpochMs)
                put(KEY_SENDER, manifest.senderDeviceName)
                put(KEY_TARGET, manifest.targetDeviceName ?: JSONObject.NULL)
                put(KEY_PAYLOAD_KIND, manifest.payloadKind.name)
                put(KEY_FILE_NAMES, JSONArray(manifest.fileNames))
                put(KEY_TOTAL_SIZE, manifest.totalSizeBytes)
                put(KEY_TTL_DAYS, manifest.ttlDays)
                put(KEY_STATUS, manifest.status.name)
            }.toString()

        /**
         * Parse `manifest.json` text, or null when it is not a manifest this build can read.
         *
         * A packet written by a newer build whose payload kind or status is unknown here is
         * rejected rather than coerced: a wrong kind would be applied to the wrong subsystem.
         */
        fun fromJson(raw: String): CrossDevicePacketManifest? = runCatching {
            val json = JSONObject(raw)
            CrossDevicePacketManifest(
                packetId = json.getString(KEY_PACKET_ID),
                createdAtEpochMs = json.getLong(KEY_CREATED_AT),
                senderDeviceName = json.getString(KEY_SENDER),
                targetDeviceName = json.optString(KEY_TARGET).takeIf { it.isNotEmpty() },
                payloadKind = CrossDevicePayloadKind.valueOf(json.getString(KEY_PAYLOAD_KIND)),
                fileNames = readFileNames(json.optJSONArray(KEY_FILE_NAMES)),
                totalSizeBytes = json.optLong(KEY_TOTAL_SIZE),
                ttlDays = json.optInt(KEY_TTL_DAYS, DEFAULT_TTL_DAYS),
                status = CrossDevicePacketStatus.valueOf(
                    json.optString(KEY_STATUS).ifEmpty { CrossDevicePacketStatus.PENDING.name }
                )
            )
        }.getOrNull()

        private fun readFileNames(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotEmpty() }
            }
        }
    }
}
