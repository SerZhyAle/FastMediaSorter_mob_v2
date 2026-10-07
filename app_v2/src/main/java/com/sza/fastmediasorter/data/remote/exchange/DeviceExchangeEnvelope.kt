package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.IOException

/**
 * One envelope of the exchange-server wire of contract DEVICE-EXCHANGE section 7 (`schemaVersion` 2).
 * [members] holds every member except `schemaVersion` and `type`, in the order they are written; which
 * members are meaningful depends on [type]. Records of section 6 travel inside it as JSON objects built by
 * the shared record codec, so the server wire and the Drive files carry the same bodies.
 */
data class DeviceExchangeEnvelope(
    val type: String,
    val members: JsonObject = JsonObject(),
) {

    /** An unknown type is not an error (section 7.1): the caller ignores it. */
    val isKnown: Boolean get() = type in KNOWN_TYPES

    fun string(key: String): String? = primitive(key)?.takeIf(JsonPrimitive::isString)?.asString

    fun int(key: String): Int? = primitive(key)?.takeIf(JsonPrimitive::isNumber)?.asString?.toIntOrNull()

    fun boolean(key: String): Boolean? = primitive(key)?.takeIf(JsonPrimitive::isBoolean)?.asBoolean

    fun obj(key: String): JsonObject? = members.get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun primitive(key: String): JsonPrimitive? =
        members.get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive

    fun encode(): String {
        val json = JsonObject()
        json.addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        json.addProperty(KEY_TYPE, type)
        members.entrySet().forEach { (key, value) ->
            if (key != KEY_SCHEMA_VERSION && key != KEY_TYPE) json.add(key, value)
        }
        return json.toString()
    }

    // Passwords, pairing codes, tokens, share ids and broadcast ids must never reach a log (section 7.9).
    override fun toString(): String = "DeviceExchangeEnvelope(type=$type)"

    companion object {
        const val SCHEMA_VERSION = 2

        /** The only older version a peer may speak; it is answered, any other is closed silently (7.1). */
        const val SCHEMA_VERSION_LEGACY = 1

        const val TYPE_ENROLL = "enroll"
        const val TYPE_ENROLLED = "enrolled"
        const val TYPE_HELLO = "hello"
        const val TYPE_WELCOME = "welcome"
        const val TYPE_REFUSED = "refused"
        const val TYPE_KEEPALIVE = "keepalive"
        const val TYPE_BYE = "bye"
        const val TYPE_LIST = "list"
        const val TYPE_DIRECTORY = "directory"
        const val TYPE_SUBSCRIBE = "subscribe"
        const val TYPE_CHANGED = "changed"
        const val TYPE_PUBLISH = "publish"
        const val TYPE_PUBLISHED = "published"
        const val TYPE_UNPUBLISH = "unpublish"
        const val TYPE_BROADCAST_START = "broadcast-start"
        const val TYPE_BROADCAST_STARTED = "broadcast-started"
        const val TYPE_BROADCAST_UPDATE = "broadcast-update"
        const val TYPE_BROADCAST_END = "broadcast-end"
        const val TYPE_PAIRING_REQUEST = "pairing-request"
        const val TYPE_PAIRING_CODE = "pairing-code"
        const val TYPE_REVOKE = "revoke"
        const val TYPE_CONNECT = "connect"
        const val TYPE_CONNECTED = "connected"
        const val TYPE_OPEN = "open"
        const val TYPE_ATTACH = "attach"
        const val TYPE_UPLOAD = "upload"
        const val TYPE_UPLOAD_ACCEPTED = "upload-accepted"
        const val TYPE_CAST = "cast"
        const val TYPE_CAST_OFFER = "cast-offer"
        const val TYPE_CAST_ANSWER = "cast-answer"
        const val TYPE_CAST_RESULT = "cast-result"
        const val TYPE_CAST_STOP = "cast-stop"

        const val REASON_BAD_CREDENTIALS = "bad-credentials"
        const val REASON_DEVICE_REVOKED = "device-revoked"
        const val REASON_VERSION_UNSUPPORTED = "version-unsupported"
        const val REASON_TLS_REQUIRED = "tls-required"
        const val REASON_ID_COLLISION = "id-collision"
        const val REASON_CAPACITY = "capacity"
        const val REASON_PORT_UNAVAILABLE = "port-unavailable"
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_RATE_LIMITED = "rate-limited"
        const val REASON_DECLINED = "declined"
        const val REASON_TIMEOUT = "timeout"
        const val REASON_UNSUPPORTED = "unsupported"

        const val KEY_REASON = "reason"

        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_TYPE = "type"

        val KNOWN_TYPES: Set<String> = setOf(
            TYPE_ENROLL, TYPE_ENROLLED, TYPE_HELLO, TYPE_WELCOME, TYPE_REFUSED, TYPE_KEEPALIVE, TYPE_BYE,
            TYPE_LIST, TYPE_DIRECTORY, TYPE_SUBSCRIBE, TYPE_CHANGED, TYPE_PUBLISH, TYPE_PUBLISHED,
            TYPE_UNPUBLISH, TYPE_BROADCAST_START, TYPE_BROADCAST_STARTED, TYPE_BROADCAST_UPDATE,
            TYPE_BROADCAST_END, TYPE_PAIRING_REQUEST, TYPE_PAIRING_CODE, TYPE_REVOKE, TYPE_CONNECT,
            TYPE_CONNECTED, TYPE_OPEN, TYPE_ATTACH, TYPE_UPLOAD, TYPE_UPLOAD_ACCEPTED, TYPE_CAST,
            TYPE_CAST_OFFER, TYPE_CAST_ANSWER, TYPE_CAST_RESULT, TYPE_CAST_STOP,
        )

        /** An envelope of [type] whose members [build] writes, in the order it writes them. */
        fun of(type: String, build: JsonObject.() -> Unit = {}): DeviceExchangeEnvelope =
            DeviceExchangeEnvelope(type, JsonObject().apply(build))

        /** `refused` with [reason]; the version refusal carries this side's `schemaVersion` (amendment P). */
        fun refused(reason: String): DeviceExchangeEnvelope = of(TYPE_REFUSED) { addProperty(KEY_REASON, reason) }

        /**
         * Parses one frame body. Throws [DeviceExchangeLegacyPeerException] for a `schemaVersion` 1 peer,
         * which the caller answers with `refused {reason:"version-unsupported"}` before closing, and
         * [DeviceExchangeWireException] for everything else that ends the stream: not one JSON object, any
         * other `schemaVersion`, no `type`. Unknown members are kept and ignored.
         */
        fun decode(body: String): DeviceExchangeEnvelope {
            val json = runCatching { JsonParser.parseString(body) }.getOrNull()
                ?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            val schema = json?.get(KEY_SCHEMA_VERSION)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
                ?.takeIf(JsonPrimitive::isNumber)?.asString?.toIntOrNull()
            val type = json?.get(KEY_TYPE)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
                ?.takeIf(JsonPrimitive::isString)?.asString?.takeIf(String::isNotEmpty)
            val violation = when {
                json == null -> DeviceExchangeWireException("envelope is not one JSON object")
                schema == SCHEMA_VERSION_LEGACY -> DeviceExchangeLegacyPeerException()
                schema != SCHEMA_VERSION -> DeviceExchangeWireException("unsupported schemaVersion")
                type == null -> DeviceExchangeWireException("envelope has no type")
                else -> null
            }
            if (violation != null || json == null || type == null) {
                throw violation ?: DeviceExchangeWireException("invalid envelope")
            }
            val members = json.deepCopy().apply {
                remove(KEY_SCHEMA_VERSION)
                remove(KEY_TYPE)
            }
            return DeviceExchangeEnvelope(type, members)
        }
    }
}

/** A wire violation that ends the stream (contract DEVICE-EXCHANGE section 7.1). */
open class DeviceExchangeWireException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The peer speaks the superseded `schemaVersion` 1 wire; section 7.1 answers it before closing. */
class DeviceExchangeLegacyPeerException : DeviceExchangeWireException("peer speaks schemaVersion 1")
