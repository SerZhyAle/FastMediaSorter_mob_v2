package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.IOException

/**
 * One envelope of the exchange-server wire (contract ANYWHERE-ACCESS section 6). Every type shares
 * this shape; which members are meaningful depends on [type]. Members are written in the order the
 * FMS_W reference server writes them, so the joint golden frames stay byte-stable on both sides.
 */
data class ExchangeEnvelope(
    val type: String,
    val password: String? = null,
    val shareId: String? = null,
    val keepaliveSeconds: Int? = null,
    val port: Int? = null,
    val resumeToken: String? = null,
    val claim: JsonObject? = null,
    val reason: String? = null,
    val tunnelId: String? = null,
) {

    /** An unknown type is not an error (section 6.1): the caller ignores it. */
    val isKnown: Boolean get() = type in KNOWN_TYPES

    fun encode(): String {
        val json = JsonObject()
        json.addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        json.addProperty(KEY_TYPE, type)
        password?.let { json.addProperty(KEY_PASSWORD, it) }
        shareId?.let { json.addProperty(KEY_SHARE_ID, it) }
        keepaliveSeconds?.let { json.addProperty(KEY_KEEPALIVE, it) }
        port?.let { json.addProperty(KEY_PORT, it) }
        resumeToken?.let { json.addProperty(KEY_RESUME_TOKEN, it) }
        claim?.let { json.add(KEY_CLAIM, it) }
        reason?.let { json.addProperty(KEY_REASON, it) }
        tunnelId?.let { json.addProperty(KEY_TUNNEL_ID, it) }
        return json.toString()
    }

    // The password must never reach a log through an accidental string template.
    override fun toString(): String = "ExchangeEnvelope(type=$type)"

    companion object {
        const val SCHEMA_VERSION = 1

        const val TYPE_REGISTER = "register"
        const val TYPE_REGISTERED = "registered"
        const val TYPE_REFUSED = "refused"
        const val TYPE_KEEPALIVE = "keepalive"
        const val TYPE_UNREGISTER = "unregister"
        const val TYPE_OPEN = "open"
        const val TYPE_ATTACH = "attach"
        const val TYPE_CONNECT = "connect"
        const val TYPE_CONNECTED = "connected"

        const val REASON_BAD_PASSWORD = "bad-password"
        const val REASON_ID_COLLISION = "id-collision"
        const val REASON_CAPACITY = "capacity"
        const val REASON_PORT_UNAVAILABLE = "port-unavailable"
        const val REASON_TLS_REQUIRED = "tls-required"
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_RATE_LIMITED = "rate-limited"

        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_TYPE = "type"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SHARE_ID = "shareId"
        private const val KEY_KEEPALIVE = "keepaliveSeconds"
        private const val KEY_PORT = "port"
        private const val KEY_RESUME_TOKEN = "resumeToken"
        private const val KEY_CLAIM = "claim"
        private const val KEY_REASON = "reason"
        private const val KEY_TUNNEL_ID = "tunnelId"

        private val KNOWN_TYPES = setOf(
            TYPE_REGISTER, TYPE_REGISTERED, TYPE_REFUSED, TYPE_KEEPALIVE, TYPE_UNREGISTER,
            TYPE_OPEN, TYPE_ATTACH, TYPE_CONNECT, TYPE_CONNECTED,
        )

        /**
         * Parses one frame body. Throws [ExchangeWireException] for anything that must end the stream:
         * not one JSON object, a `schemaVersion` other than [SCHEMA_VERSION], no `type`. Unknown members
         * are ignored.
         */
        fun decode(body: String): ExchangeEnvelope {
            val json = runCatching { JsonParser.parseString(body) }.getOrNull()
                ?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            val type = json?.stringOrNull(KEY_TYPE)?.takeIf(String::isNotEmpty)
            val violation = when {
                json == null -> "envelope is not one JSON object"
                json.intOrNull(KEY_SCHEMA_VERSION) != SCHEMA_VERSION -> "unsupported schemaVersion"
                type == null -> "envelope has no type"
                else -> null
            }
            if (violation != null || json == null || type == null) {
                throw ExchangeWireException(violation ?: "invalid envelope")
            }
            return ExchangeEnvelope(
                type = type,
                password = json.stringOrNull(KEY_PASSWORD),
                shareId = json.stringOrNull(KEY_SHARE_ID),
                keepaliveSeconds = json.intOrNull(KEY_KEEPALIVE),
                port = json.intOrNull(KEY_PORT),
                resumeToken = json.stringOrNull(KEY_RESUME_TOKEN),
                claim = json.get(KEY_CLAIM)?.takeIf(JsonElement::isJsonObject)?.asJsonObject,
                reason = json.stringOrNull(KEY_REASON),
                tunnelId = json.stringOrNull(KEY_TUNNEL_ID),
            )
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? =
            get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive

        private fun JsonObject.stringOrNull(key: String): String? =
            primitive(key)?.takeIf(JsonPrimitive::isString)?.asString

        private fun JsonObject.intOrNull(key: String): Int? =
            primitive(key)?.takeIf(JsonPrimitive::isNumber)?.asString?.toIntOrNull()
    }
}

/** A wire violation that ends the stream (contract ANYWHERE-ACCESS section 6.1), so it is a stream failure. */
class ExchangeWireException(message: String, cause: Throwable? = null) : IOException(message, cause)
