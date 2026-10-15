package com.sza.fastmediasorter.domain.model.transfer

import org.json.JSONArray
import org.json.JSONObject

/**
 * S4231: `manifest.json` and `listing.json` on the wire (contract DEVICE-EXCHANGE items CN and CO).
 *
 * The writer emits every version 1 member unchanged and adds `schemaVersion: 2` as soon as it uses
 * anything newer; the reader ignores members it does not know and skips - returns null for - a
 * manifest whose kind is unknown or whose names would address something outside the packet folder.
 */
object CrossDevicePacketCodec {

    private const val KEY_PACKET_ID = "packetId"
    private const val KEY_CREATED_AT = "createdAtEpochMs"
    private const val KEY_SENDER = "senderDeviceName"
    private const val KEY_TARGET = "targetDeviceName"
    private const val KEY_PAYLOAD_KIND = "payloadKind"
    private const val KEY_FILE_NAMES = "fileNames"
    private const val KEY_TOTAL_SIZE = "totalSizeBytes"
    private const val KEY_TTL_DAYS = "ttlDays"
    private const val KEY_STATUS = "status"
    private const val KEY_SCHEMA = "schemaVersion"
    private const val KEY_SENDER_ID = "senderDeviceId"
    private const val KEY_TARGET_ID = "targetDeviceId"
    private const val KEY_FILES = "files"
    private const val KEY_IN_REPLY_TO = "inReplyTo"
    private const val KEY_TTL_SECONDS = "ttlSeconds"
    private const val KEY_REQUEST = "request"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_REASON = "reason"
    private const val KEY_FINAL = "final"
    private const val KEY_AS_OF = "asOf"
    private const val KEY_NAME = "name"
    private const val KEY_SIZE = "size"
    private const val KEY_SHA256 = "sha256"
    private const val KEY_MIME = "mimeType"
    private const val KEY_CLIP_KIND = "clipKind"
    private const val KEY_ACTION = "action"
    private const val KEY_RESOURCE_ID = "resourceId"
    private const val KEY_PATH = "path"
    private const val KEY_PATHS = "paths"
    private const val KEY_MODIFIED_AFTER = "modifiedAfter"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_DIR = "dir"
    private const val KEY_MODIFIED_AT = "modifiedAt"
    private const val ACTION_LIST = "list"
    private const val ACTION_FETCH = "fetch"
    private const val ACTION_CHANGES = "changes"

    private val v1Kinds = setOf(CrossDevicePayloadKind.SETTINGS, CrossDevicePayloadKind.MEDIA_FILES)

    fun toJson(manifest: CrossDevicePacketManifest): String {
        val json = JSONObject().apply {
            put(KEY_PACKET_ID, manifest.packetId)
            put(KEY_CREATED_AT, manifest.createdAtEpochMs)
            put(KEY_SENDER, manifest.senderDeviceName)
            put(KEY_TARGET, manifest.targetDeviceName ?: JSONObject.NULL)
            put(KEY_PAYLOAD_KIND, manifest.payloadKind.name)
            put(KEY_FILE_NAMES, JSONArray(manifest.fileNames))
            put(KEY_TOTAL_SIZE, manifest.totalSizeBytes)
            put(KEY_TTL_DAYS, manifest.ttlDays)
            put(KEY_STATUS, manifest.status.name)
        }
        if (usesVersion2(manifest)) writeVersion2(json, manifest)
        return json.toString()
    }

    /** Parse `manifest.json` text, or null when it is not a manifest this build can read. */
    fun fromJson(raw: String): CrossDevicePacketManifest? =
        runCatching { readManifest(JSONObject(raw)) }.getOrNull()?.takeIf(::hasSafeNames)

    fun listingToJson(listing: DriveListing): String = JSONObject().apply {
        put(KEY_AS_OF, listing.asOf)
        put(
            KEY_ENTRIES,
            JSONArray().apply {
                listing.entries.forEach { entry ->
                    put(
                        JSONObject()
                            .put(KEY_NAME, entry.name)
                            .put(KEY_DIR, entry.dir)
                            .put(KEY_SIZE, entry.size)
                            .put(KEY_MODIFIED_AT, entry.modifiedAt)
                    )
                }
            }
        )
    }.toString()

    fun listingFromJson(raw: String): DriveListing? = runCatching {
        val json = JSONObject(raw)
        val array = json.optJSONArray(KEY_ENTRIES) ?: JSONArray()
        val entries = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { entry ->
                DriveListingEntry(
                    name = entry.getString(KEY_NAME),
                    dir = entry.optBoolean(KEY_DIR),
                    size = entry.optLong(KEY_SIZE),
                    modifiedAt = entry.optString(KEY_MODIFIED_AT)
                )
            }
        }
        val safe = entries.filter { DrivePacketNames.isSafeBaseName(it.name) }
        DriveListing(asOf = json.getString(KEY_AS_OF), entries = safe)
    }.getOrNull()

    private fun usesVersion2(manifest: CrossDevicePacketManifest): Boolean =
        manifest.schemaVersion >= CrossDevicePacketManifest.SCHEMA_V2 ||
            manifest.payloadKind !in v1Kinds ||
            manifest.senderDeviceId != null ||
            manifest.targetDeviceId != null ||
            manifest.files.isNotEmpty() ||
            manifest.inReplyTo != null ||
            manifest.ttlSeconds != null ||
            manifest.request != null ||
            manifest.reply != null

    private fun writeVersion2(json: JSONObject, manifest: CrossDevicePacketManifest) {
        json.put(KEY_SCHEMA, CrossDevicePacketManifest.SCHEMA_V2)
        manifest.senderDeviceId?.let { json.put(KEY_SENDER_ID, it) }
        manifest.targetDeviceId?.let { json.put(KEY_TARGET_ID, it) }
        if (manifest.files.isNotEmpty()) json.put(KEY_FILES, JSONArray(manifest.files.map(::fileToJson)))
        manifest.inReplyTo?.let { json.put(KEY_IN_REPLY_TO, it) }
        manifest.ttlSeconds?.let { json.put(KEY_TTL_SECONDS, it) }
        manifest.request?.let { json.put(KEY_REQUEST, requestToJson(it)) }
        manifest.reply?.let { reply ->
            json.put(KEY_OUTCOME, reply.outcome.wire)
            reply.reason?.let { json.put(KEY_REASON, it.wire) }
            json.put(KEY_FINAL, reply.final)
            reply.asOf?.let { json.put(KEY_AS_OF, it) }
        }
    }

    private fun fileToJson(file: CrossDevicePacketFile): JSONObject = JSONObject().apply {
        put(KEY_NAME, file.name)
        put(KEY_SIZE, file.size)
        file.sha256?.let { put(KEY_SHA256, it) }
        put(KEY_MIME, file.mimeType)
        file.clipKind?.let { put(KEY_CLIP_KIND, it.wire) }
    }

    private fun requestToJson(request: DriveChannelRequest): JSONObject = JSONObject().apply {
        put(KEY_RESOURCE_ID, request.resourceId)
        when (request) {
            is DriveChannelRequest.ListFolder -> put(KEY_ACTION, ACTION_LIST).put(KEY_PATH, request.path)
            is DriveChannelRequest.Fetch -> put(KEY_ACTION, ACTION_FETCH).put(KEY_PATHS, JSONArray(request.paths))
            is DriveChannelRequest.Changes -> {
                put(KEY_ACTION, ACTION_CHANGES)
                put(KEY_PATH, request.path)
                request.modifiedAfter?.let { put(KEY_MODIFIED_AFTER, it) }
            }
        }
    }

    // Throws on a missing required member or an unknown kind/status; fromJson turns that into a skip.
    private fun readManifest(json: JSONObject): CrossDevicePacketManifest {
        val kind = CrossDevicePayloadKind.valueOf(json.getString(KEY_PAYLOAD_KIND))
        val schema = json.optInt(KEY_SCHEMA, CrossDevicePacketManifest.SCHEMA_V1)
        require(kind in v1Kinds || schema >= CrossDevicePacketManifest.SCHEMA_V2) { "new kind without schemaVersion" }
        return CrossDevicePacketManifest(
            packetId = json.getString(KEY_PACKET_ID),
            createdAtEpochMs = json.getLong(KEY_CREATED_AT),
            senderDeviceName = json.getString(KEY_SENDER),
            targetDeviceName = optNullableString(json, KEY_TARGET),
            payloadKind = kind,
            fileNames = readStrings(json.optJSONArray(KEY_FILE_NAMES)),
            totalSizeBytes = json.optLong(KEY_TOTAL_SIZE),
            ttlDays = json.optInt(KEY_TTL_DAYS, CrossDevicePacketManifest.DEFAULT_TTL_DAYS),
            status = CrossDevicePacketStatus.valueOf(
                json.optString(KEY_STATUS).ifEmpty { CrossDevicePacketStatus.PENDING.name }
            ),
            schemaVersion = schema,
            senderDeviceId = optNullableString(json, KEY_SENDER_ID),
            targetDeviceId = optNullableString(json, KEY_TARGET_ID),
            files = readFiles(json.optJSONArray(KEY_FILES)),
            inReplyTo = optNullableString(json, KEY_IN_REPLY_TO),
            ttlSeconds = if (json.has(KEY_TTL_SECONDS)) json.getLong(KEY_TTL_SECONDS) else null,
            request = json.optJSONObject(KEY_REQUEST)?.let(::readRequest),
            reply = readReply(json)
        )
    }

    private fun readReply(json: JSONObject): DriveChannelReplyHeader? {
        val outcome = optNullableString(json, KEY_OUTCOME)?.let(DriveReplyOutcome::fromWire) ?: return null
        return DriveChannelReplyHeader(
            outcome = outcome,
            reason = optNullableString(json, KEY_REASON)?.let(DriveRefusalReason::fromWire),
            final = json.optBoolean(KEY_FINAL, true),
            asOf = optNullableString(json, KEY_AS_OF)
        )
    }

    private fun readRequest(json: JSONObject): DriveChannelRequest? {
        val resourceId = json.getString(KEY_RESOURCE_ID)
        return when (json.optString(KEY_ACTION)) {
            ACTION_LIST -> DriveChannelRequest.ListFolder(resourceId, json.optString(KEY_PATH))
            ACTION_FETCH -> DriveChannelRequest.Fetch(resourceId, readStrings(json.optJSONArray(KEY_PATHS)))
            ACTION_CHANGES -> DriveChannelRequest.Changes(
                resourceId = resourceId,
                path = json.optString(KEY_PATH),
                modifiedAfter = optNullableString(json, KEY_MODIFIED_AFTER)
            )
            else -> null
        }
    }

    private fun readFiles(array: JSONArray?): List<CrossDevicePacketFile> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val file = array.getJSONObject(index)
            CrossDevicePacketFile(
                name = file.getString(KEY_NAME),
                size = file.getLong(KEY_SIZE),
                sha256 = optNullableString(file, KEY_SHA256)?.lowercase(),
                mimeType = optNullableString(file, KEY_MIME) ?: CrossDevicePacketFile.DEFAULT_MIME_TYPE,
                clipKind = optNullableString(file, KEY_CLIP_KIND)?.let(ClipKind::fromWire)
            )
        }
    }

    private fun hasSafeNames(manifest: CrossDevicePacketManifest): Boolean =
        DrivePacketNames.isSafePacketId(manifest.packetId) &&
            manifest.fileNames.all(DrivePacketNames::isSafeBaseName) &&
            (manifest.files.isEmpty() || manifest.files.map { it.name } == manifest.fileNames)

    // isNull first: Android's optString reads the JSONObject.NULL toJson writes as "null".
    private fun optNullableString(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key).ifEmpty { null }

    private fun readStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotEmpty() }
        }
    }
}
