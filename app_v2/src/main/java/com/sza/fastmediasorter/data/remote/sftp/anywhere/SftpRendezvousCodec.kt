package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousPlayPair
import com.sza.fastmediasorter.domain.model.SftpRendezvousReceiver
import com.sza.fastmediasorter.domain.model.SftpRendezvousRecord
import com.sza.fastmediasorter.domain.model.SftpRendezvousRequest
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Drive file form of contract DEVICE-EXCHANGE: the section 8.1 wrapper
 * `{"schemaVersion":1,"type":..,"writtenAt":..,"ttlSeconds":..,"record":{..}}` around a section 6.1, 6.2,
 * 6.3 or 8.3 record. Members are additive: a reader ignores every member it does not know and skips a
 * `type`, a resource `kind` or a request `action` it does not know. It refuses a wrapper whose
 * `schemaVersion` is above its own, because such a writer may have changed the meaning of a known member,
 * and a file above the 16 KiB of section 6. Members are written in one fixed order, which is what the
 * rung 1 vectors `device-exchange/vectors/records-v1.json` freeze byte for byte.
 */
object SftpRendezvousCodec {

    const val SCHEMA_VERSION = 1
    const val MAX_RECORD_BYTES = 16 * 1_024

    const val TYPE_DEVICE = "device"
    const val TYPE_RESOURCE = "resource"
    const val TYPE_BROADCAST = "broadcast"
    const val TYPE_REQUEST = "request"

    private const val FILE_PREFIX = "fmsx-"
    private const val FILE_SUFFIX = ".json"
    private const val MAX_PORT = 65_535

    private const val KEY_SCHEMA = "schemaVersion"
    private const val KEY_TYPE = "type"
    private const val KEY_WRITTEN_AT = "writtenAt"
    private const val KEY_TTL = "ttlSeconds"
    private const val KEY_RECORD = "record"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val KEY_DEVICE_NAME = "deviceName"
    private const val KEY_PRODUCT = "product"
    private const val KEY_PRODUCT_VERSION = "productVersion"
    private const val KEY_PLATFORM = "platform"
    private const val KEY_ROLES = "roles"
    private const val KEY_RECEIVER = "receiver"
    private const val KEY_MODES = "modes"
    private const val KEY_TRANSPORTS = "transports"
    private const val KEY_PLAYS = "plays"
    private const val KEY_MODE = "mode"
    private const val KEY_TRANSPORT = "transport"
    private const val KEY_PRESENCE = "presence"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val KEY_RESOURCE_ID = "resourceId"
    private const val KEY_KIND = "kind"
    private const val KEY_NAME = "name"
    private const val KEY_ACCESS = "access"
    private const val KEY_DESCRIPTOR = "descriptor"
    private const val KEY_ROOT = "root"
    private const val KEY_SHARE_ID = "shareId"
    private const val KEY_PUBLIC_PORT = "publicPort"
    private const val KEY_BROADCAST_ID = "broadcastId"
    private const val KEY_TITLE = "title"
    private const val KEY_STARTED_AT = "startedAt"
    private const val KEY_TO_DEVICE_ID = "toDeviceId"
    private const val KEY_FROM_DEVICE_ID = "fromDeviceId"
    private const val KEY_ACTION = "action"

    private val KNOWN_PRESENCE = setOf(SftpRendezvousDevice.PRESENCE_ONLINE, SftpRendezvousDevice.PRESENCE_OFFLINE)

    /** The section 8.1 file name of the record of [type] keyed by [id]: `fmsx-<type>-<id>.json`. */
    fun fileName(type: String, id: String): String = filePrefix(type) + id + FILE_SUFFIX

    /** The name prefix every file of [type] shares, which is how a listing tells the record types apart. */
    fun filePrefix(type: String): String = "$FILE_PREFIX$type-"

    fun encode(record: SftpRendezvousRecord): String {
        val (type, body) = when (record) {
            is SftpRendezvousDevice -> TYPE_DEVICE to deviceBody(record)
            is SftpRendezvousResource -> TYPE_RESOURCE to resourceBody(record)
            is SftpRendezvousBroadcast -> TYPE_BROADCAST to broadcastBody(record)
            is SftpRendezvousRequest -> TYPE_REQUEST to requestBody(record)
        }
        return JsonObject().apply {
            addProperty(KEY_SCHEMA, SCHEMA_VERSION)
            addProperty(KEY_TYPE, type)
            addProperty(KEY_WRITTEN_AT, Iso8601Utc.format(record.writtenAtMs))
            addProperty(KEY_TTL, record.ttlSeconds)
            add(KEY_RECORD, body)
        }.toString()
    }

    /** Null for anything this build cannot use: refused, skipped, damaged or foreign. */
    fun decode(raw: String): SftpRendezvousRecord? {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_RECORD_BYTES) return null
        // A file half-written by an interrupted upload or a foreign file is skipped, never fatal.
        val wrapper = runCatching { JsonParser.parseString(raw) }.getOrNull() as? JsonObject
        return wrapper?.let(::decodeWrapper)
    }

    private fun decodeWrapper(wrapper: JsonObject): SftpRendezvousRecord? {
        val schema = wrapper.long(KEY_SCHEMA)?.takeIf { it in 1..SCHEMA_VERSION }
        val ttl = wrapper.long(KEY_TTL)
        val writtenAt = wrapper.string(KEY_WRITTEN_AT)?.takeIf { schema != null }?.let(Iso8601Utc::parse)
        val body = (wrapper.get(KEY_RECORD) as? JsonObject)?.takeIf { ttl != null }
        if (writtenAt == null || ttl == null || body == null) return null
        return when (wrapper.string(KEY_TYPE)) {
            TYPE_DEVICE -> decodeDevice(body, writtenAt, ttl)
            TYPE_RESOURCE -> decodeResource(body, writtenAt, ttl)
            TYPE_BROADCAST -> decodeBroadcast(body, writtenAt, ttl)
            TYPE_REQUEST -> decodeRequest(body, writtenAt, ttl)
            else -> null
        }
    }

    private fun deviceBody(device: SftpRendezvousDevice) = JsonObject().apply {
        addProperty(KEY_DEVICE_ID, device.deviceId)
        addProperty(KEY_DEVICE_NAME, device.deviceName)
        addProperty(KEY_PRODUCT, device.product)
        device.productVersion?.let { addProperty(KEY_PRODUCT_VERSION, it) }
        device.platform?.let { addProperty(KEY_PLATFORM, it) }
        add(KEY_ROLES, stringArray(device.roles))
        device.receiver?.let { add(KEY_RECEIVER, receiverBody(it)) }
        addProperty(KEY_PRESENCE, device.presence)
        addProperty(KEY_UPDATED_AT, Iso8601Utc.format(device.updatedAtMs))
    }

    private fun receiverBody(receiver: SftpRendezvousReceiver) = JsonObject().apply {
        add(KEY_MODES, stringArray(receiver.modes))
        add(KEY_TRANSPORTS, stringArray(receiver.transports))
        receiver.plays?.let { plays ->
            add(
                KEY_PLAYS,
                JsonArray().apply {
                    plays.forEach { pair ->
                        add(
                            JsonObject().apply {
                                addProperty(KEY_MODE, pair.mode)
                                addProperty(KEY_TRANSPORT, pair.transport)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun resourceBody(resource: SftpRendezvousResource) = JsonObject().apply {
        addProperty(KEY_RESOURCE_ID, resource.resourceId)
        addProperty(KEY_DEVICE_ID, resource.deviceId)
        addProperty(KEY_KIND, resource.kind)
        addProperty(KEY_NAME, resource.name)
        addProperty(KEY_UPDATED_AT, Iso8601Utc.format(resource.updatedAtMs))
        resource.presence?.let { addProperty(KEY_PRESENCE, it) }
        resource.descriptor?.let { descriptor ->
            add(
                KEY_ACCESS,
                JsonObject().apply {
                    addProperty(KEY_DESCRIPTOR, descriptor)
                    resource.root?.let { addProperty(KEY_ROOT, it) }
                }
            )
        }
        resource.shareId?.let { addProperty(KEY_SHARE_ID, it) }
        resource.publicPort?.let { addProperty(KEY_PUBLIC_PORT, it) }
    }

    private fun broadcastBody(broadcast: SftpRendezvousBroadcast) = JsonObject().apply {
        addProperty(KEY_BROADCAST_ID, broadcast.broadcastId)
        addProperty(KEY_DEVICE_ID, broadcast.deviceId)
        broadcast.title?.let { addProperty(KEY_TITLE, it) }
        addProperty(KEY_MODE, broadcast.mode)
        broadcast.startedAtMs?.let { addProperty(KEY_STARTED_AT, Iso8601Utc.format(it)) }
        addProperty(KEY_UPDATED_AT, Iso8601Utc.format(broadcast.updatedAtMs))
        add(KEY_DESCRIPTOR, JsonParser.parseString(broadcast.descriptorJson))
    }

    private fun requestBody(request: SftpRendezvousRequest) = JsonObject().apply {
        addProperty(KEY_TO_DEVICE_ID, request.toDeviceId)
        addProperty(KEY_ACTION, request.action)
        request.fromDeviceId?.let { addProperty(KEY_FROM_DEVICE_ID, it) }
    }

    private fun decodeDevice(body: JsonObject, writtenAt: Long, ttl: Long): SftpRendezvousDevice? {
        val deviceId = body.string(KEY_DEVICE_ID)
        val name = body.string(KEY_DEVICE_NAME)
        val product = body.string(KEY_PRODUCT)
        if (deviceId.isNullOrBlank() || name == null || product.isNullOrBlank()) return null
        return SftpRendezvousDevice(
            deviceId = deviceId,
            deviceName = name,
            product = product,
            productVersion = body.string(KEY_PRODUCT_VERSION),
            platform = body.string(KEY_PLATFORM),
            roles = body.strings(KEY_ROLES),
            presence = body.presence(),
            updatedAtMs = body.string(KEY_UPDATED_AT)?.let(Iso8601Utc::parse) ?: writtenAt,
            writtenAtMs = writtenAt,
            ttlSeconds = ttl,
            receiver = (body.get(KEY_RECEIVER) as? JsonObject)?.let(::decodeReceiver),
        )
    }

    private fun decodeReceiver(receiver: JsonObject): SftpRendezvousReceiver {
        // A pair with a blank member names nothing a broadcaster could match, so only that pair is dropped.
        val plays = (receiver.get(KEY_PLAYS) as? JsonArray)?.mapNotNull { element ->
            val pair = element as? JsonObject
            val mode = pair?.string(KEY_MODE)?.takeIf(String::isNotBlank)
            val transport = pair?.string(KEY_TRANSPORT)?.takeIf(String::isNotBlank)
            if (mode != null && transport != null) SftpRendezvousPlayPair(mode, transport) else null
        }
        return SftpRendezvousReceiver(receiver.strings(KEY_MODES), receiver.strings(KEY_TRANSPORTS), plays)
    }

    /**
     * A record without `access.descriptor` still decodes: its producer kept the credentials off Drive
     * (section 15 item X), so the share is listed and cannot be attached from this record.
     */
    private fun decodeResource(body: JsonObject, writtenAt: Long, ttl: Long): SftpRendezvousResource? {
        val isShare = body.string(KEY_KIND) == SftpRendezvousResource.KIND_SFTP_SHARE
        val name = body.string(KEY_NAME)
        val resourceId = body.string(KEY_RESOURCE_ID)?.takeIf { it.isNotBlank() && name != null && isShare }
        val deviceId = body.string(KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }
        if (resourceId == null || deviceId == null) return null
        val access = body.get(KEY_ACCESS) as? JsonObject
        val descriptor = access?.string(KEY_DESCRIPTOR)?.takeIf(String::isNotBlank)
        return SftpRendezvousResource(
            resourceId = resourceId,
            deviceId = deviceId,
            kind = SftpRendezvousResource.KIND_SFTP_SHARE,
            name = name.orEmpty(),
            descriptor = descriptor,
            updatedAtMs = body.string(KEY_UPDATED_AT)?.let(Iso8601Utc::parse) ?: writtenAt,
            writtenAtMs = writtenAt,
            ttlSeconds = ttl,
            shareId = body.string(KEY_SHARE_ID)?.takeIf(String::isNotBlank),
            publicPort = body.long(KEY_PUBLIC_PORT)?.takeIf { it in 1..MAX_PORT }?.toInt(),
            root = access?.string(KEY_ROOT)?.takeIf { descriptor != null && it.isNotBlank() },
            presence = body.get(KEY_PRESENCE)?.let { body.presence() },
        )
    }

    private fun decodeBroadcast(body: JsonObject, writtenAt: Long, ttl: Long): SftpRendezvousBroadcast? {
        val broadcastId = body.string(KEY_BROADCAST_ID)?.takeIf(String::isNotBlank)
        val deviceId = body.string(KEY_DEVICE_ID)?.takeIf(String::isNotBlank)
        val mode = body.string(KEY_MODE)?.takeIf(String::isNotBlank)
        val descriptor = (body.get(KEY_DESCRIPTOR) as? JsonObject)?.takeIf { mode != null }
        if (broadcastId == null || deviceId == null || descriptor == null) return null
        return SftpRendezvousBroadcast(
            broadcastId = broadcastId,
            deviceId = deviceId,
            title = body.string(KEY_TITLE),
            mode = checkNotNull(mode),
            startedAtMs = body.string(KEY_STARTED_AT)?.let(Iso8601Utc::parse),
            updatedAtMs = body.string(KEY_UPDATED_AT)?.let(Iso8601Utc::parse) ?: writtenAt,
            descriptorJson = descriptor.toString(),
            writtenAtMs = writtenAt,
            ttlSeconds = ttl,
        )
    }

    private fun decodeRequest(body: JsonObject, writtenAt: Long, ttl: Long): SftpRendezvousRequest? {
        val to = body.string(KEY_TO_DEVICE_ID)
        val action = body.string(KEY_ACTION)
        if (to.isNullOrBlank() || action != SftpRendezvousRequest.ACTION_ANNOUNCE) return null
        return SftpRendezvousRequest(to, action, body.string(KEY_FROM_DEVICE_ID), writtenAt, ttl)
    }

    // An unknown presence value proves nothing about now, so it reads as offline.
    private fun JsonObject.presence(): String =
        string(KEY_PRESENCE)?.takeIf { it in KNOWN_PRESENCE } ?: SftpRendezvousDevice.PRESENCE_OFFLINE

    private fun stringArray(values: List<String>) = JsonArray().apply { values.forEach(::add) }

    private fun JsonObject.strings(key: String): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { it.stringOrNull() }.orEmpty()

    private fun JsonObject.string(key: String): String? = get(key)?.stringOrNull()

    private fun JsonElement.stringOrNull(): String? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    /** UTC ISO 8601 with `Z` (section 6); written with milliseconds, read with or without them. */
    private object Iso8601Utc {
        private const val WITH_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        private const val WITHOUT_MILLIS = "yyyy-MM-dd'T'HH:mm:ss'Z'"

        fun format(epochMs: Long): String = formatter(WITH_MILLIS).format(Date(epochMs))

        fun parse(value: String): Long? = listOf(WITH_MILLIS, WITHOUT_MILLIS).firstNotNullOfOrNull { pattern ->
            try {
                formatter(pattern).parse(value)?.time
            } catch (_: ParseException) {
                null
            }
        }

        // SimpleDateFormat is not thread-safe, so every call gets its own instance.
        private fun formatter(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
    }
}
