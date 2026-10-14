package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * S4158: one envelope of the exchange-server wire of contract DEVICE-EXCHANGE section 7 (`schemaVersion` 2), the
 * subset the watch speaks to enroll, revoke itself, hold a control stream, upload a broadcast and answer a cast.
 * Mirrored from the phone's `DeviceExchangeEnvelope` -
 * the two modules share no code, so the contract is the only thing keeping them equal.
 */
data class WearExchangeEnvelope(
    val type: String,
    val members: JsonObject = JsonObject(),
) {

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

    // Pairing codes and tokens must never reach a log (section 7.9).
    override fun toString(): String = "WearExchangeEnvelope(type=$type)"

    companion object {
        const val SCHEMA_VERSION = 2

        const val TYPE_ENROLL = "enroll"
        const val TYPE_ENROLLED = "enrolled"
        const val TYPE_HELLO = "hello"
        const val TYPE_WELCOME = "welcome"
        const val TYPE_REFUSED = "refused"
        const val TYPE_BYE = "bye"
        const val TYPE_REVOKE = "revoke"
        const val TYPE_KEEPALIVE = "keepalive"
        const val TYPE_BROADCAST_START = "broadcast-start"
        const val TYPE_BROADCAST_STARTED = "broadcast-started"
        const val TYPE_BROADCAST_UPDATE = "broadcast-update"
        const val TYPE_BROADCAST_END = "broadcast-end"
        const val TYPE_UPLOAD = "upload"
        const val TYPE_UPLOAD_ACCEPTED = "upload-accepted"
        const val TYPE_CAST_OFFER = "cast-offer"
        const val TYPE_CAST_ANSWER = "cast-answer"
        const val TYPE_CAST_STOP = "cast-stop"

        const val REASON_ID_COLLISION = "id-collision"
        const val KEY_REASON = "reason"

        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_TYPE = "type"

        fun of(type: String, build: JsonObject.() -> Unit = {}): WearExchangeEnvelope =
            WearExchangeEnvelope(type, JsonObject().apply(build))

        /** Parses one frame body; anything but a `schemaVersion` 2 object with a `type` ends the stream (7.1). */
        fun decode(body: String): WearExchangeEnvelope {
            val json = runCatching { JsonParser.parseString(body) }.getOrNull()
                ?.takeIf(JsonElement::isJsonObject)?.asJsonObject
                ?: throw WearExchangeWireException("envelope is not one JSON object")
            val schema = json.get(KEY_SCHEMA_VERSION)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
                ?.takeIf(JsonPrimitive::isNumber)?.asString?.toIntOrNull()
            val type = json.get(KEY_TYPE)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
                ?.takeIf(JsonPrimitive::isString)?.asString?.takeIf(String::isNotEmpty)
            if (schema != SCHEMA_VERSION || type == null) {
                throw WearExchangeWireException("unsupported schemaVersion or no type")
            }
            val members = json.deepCopy().apply {
                remove(KEY_SCHEMA_VERSION)
                remove(KEY_TYPE)
            }
            return WearExchangeEnvelope(type, members)
        }
    }
}

/**
 * The framing of contract DEVICE-EXCHANGE section 7.1: a 4-byte unsigned big-endian length, then that many bytes
 * of one UTF-8 JSON object - at most [MAX_FRAME_BODY] until the stream is authenticated (`welcome` or `enrolled`
 * included) and [AUTHENTICATED_MAX_FRAME_BODY] after it, in both directions.
 */
object WearExchangeFrameCodec {

    const val MAX_FRAME_BODY = 16_384
    const val AUTHENTICATED_MAX_FRAME_BODY = 262_144
    private const val LENGTH_BYTES = 4

    fun write(output: OutputStream, envelope: WearExchangeEnvelope, maxBody: Int = MAX_FRAME_BODY) {
        val body = envelope.encode().toByteArray(Charsets.UTF_8)
        if (body.size > maxBody) throw WearExchangeWireException("envelope above the frame limit")
        output.write(ByteBuffer.allocate(LENGTH_BYTES + body.size).putInt(body.size).put(body).array())
        output.flush()
    }

    /** The next envelope, or null at a clean end of the stream before a frame starts. */
    fun read(input: InputStream, maxBody: Int = MAX_FRAME_BODY): WearExchangeEnvelope? {
        val data = DataInputStream(input)
        val first = data.read()
        if (first < 0) return null
        val rest = ByteArray(LENGTH_BYTES - 1)
        val body = try {
            data.readFully(rest)
            val length = ByteBuffer.wrap(byteArrayOf(first.toByte()) + rest).int
            if (length !in 1..maxBody) throw WearExchangeWireException("frame length out of range")
            ByteArray(length).also(data::readFully)
        } catch (truncated: EOFException) {
            throw WearExchangeWireException("stream ended inside a frame", truncated)
        }
        return WearExchangeEnvelope.decode(body.toString(Charsets.UTF_8))
    }
}

/** A wire violation that ends the stream (contract DEVICE-EXCHANGE section 7.1). */
class WearExchangeWireException(message: String, cause: Throwable? = null) : IOException(message, cause)
