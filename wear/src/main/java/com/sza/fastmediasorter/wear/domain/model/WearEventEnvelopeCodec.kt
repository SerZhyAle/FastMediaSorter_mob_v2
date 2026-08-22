package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Owns the compact wire representation of [WearEventEnvelope] without changing application Gson.
 */
class WearEventEnvelopeCodec {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun encode(envelope: WearEventEnvelope): ByteArray {
        val json = JsonObject().apply {
            addProperty(EVENT_TYPE, envelope.eventType)
            addProperty(SCHEMA_VERSION, envelope.schemaVersion)
            addProperty(SENT_AT, envelope.sentAt)
            addProperty(DATA, Base64.getEncoder().encodeToString(envelope.data))
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    fun decode(encoded: ByteArray): WearEventEnvelope {
        val json = JsonParser.parseString(encoded.toString(Charsets.UTF_8)).asJsonObject
        return WearEventEnvelope(
            eventType = json.required(EVENT_TYPE).asString,
            schemaVersion = json.required(SCHEMA_VERSION).asInt,
            sentAt = json.required(SENT_AT).asLong,
            data = Base64.getDecoder().decode(json.required(DATA).asString)
        )
    }

    private fun JsonObject.required(name: String): JsonElement =
        get(name)?.takeUnless(JsonElement::isJsonNull)
            ?: throw IllegalArgumentException("Wear envelope is missing $name")

    private companion object {
        const val EVENT_TYPE = "eventType"
        const val SCHEMA_VERSION = "schemaVersion"
        const val SENT_AT = "sentAt"
        const val DATA = "data"
    }
}
