package com.sza.fastmediasorter.data.broadcast.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import timber.log.Timber

/**
 * S4440: the one log line of a `broadcast-start` or `broadcast-update` that was written, so a joint run can set what
 * the phone sent beside what the exchange server stored. Built from the record that goes on the wire, not from the
 * intent behind it. It names shapes only: no password, no `key`, no `pin`, no URL, no descriptor body - a broadcast
 * id is a capability, so only its first characters appear. Members are read by name from the JSON, so a descriptor
 * written by another build, or one missing a member, still yields a line instead of an exception on the write path.
 */
object BroadcastAnnounceTrace {

    private const val LOGGED_ID_CHARS = 8
    private const val UNKNOWN = "-"
    private const val AUTH_ABSENT = "absent"
    private const val AUTH_UNREADABLE = "unreadable"
    private const val SEAL_PRESENT = "seal"
    private const val SEAL_ABSENT = "plain"

    fun describe(type: String, publication: ExchangeBroadcastPublication): String {
        val record = publication.record
        val descriptor = parse(record.descriptorJson)
        val link = publication.link
        return buildString {
            append("type=").append(type)
            append(" id=").append(record.broadcastId.take(LOGGED_ID_CHARS))
            append(" mode=").append(record.mode)
            append(" remembered=").append(link?.remembered?.name ?: UNKNOWN)
            append(" link=").append(label(link?.live))
            append(" auth=").append(descriptor?.let(::auth) ?: AUTH_UNREADABLE)
            append(" endpoints=").append(descriptor?.let(::endpoints) ?: UNKNOWN)
            append(" verifiers=").append(publication.verifiers.size)
            append(" box=").append(if (record.sealed != null) "yes" else "no")
        }
    }

    private fun parse(json: String): JsonObject? = try {
        JsonParser.parseString(json).takeIf(JsonElement::isJsonObject)?.asJsonObject
    } catch (e: JsonSyntaxException) {
        Timber.d(e, "BroadcastAnnounceTrace: the descriptor of the record is unreadable")
        null
    }

    private fun auth(descriptor: JsonObject): String {
        val auth = descriptor.get("auth")?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return AUTH_ABSENT
        return text(auth, "scheme") ?: UNKNOWN
    }

    private fun endpoints(descriptor: JsonObject): String {
        val list = descriptor.get("endpoints")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        val described = list?.toList().orEmpty().filter(JsonElement::isJsonObject).map { element ->
            val endpoint = element.asJsonObject
            val seal = if (endpoint.get("seal")?.isJsonObject == true) SEAL_PRESENT else SEAL_ABSENT
            "${text(endpoint, "transport")?.uppercase() ?: UNKNOWN}:$seal"
        }
        return if (described.isEmpty()) UNKNOWN else described.joinToString(",")
    }

    private fun text(from: JsonObject, name: String): String? =
        from.get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun label(choice: BroadcastLinkChoice?): String = when (choice) {
        BroadcastLinkChoice.Generated -> "Generated"
        is BroadcastLinkChoice.Custom -> "Custom"
        BroadcastLinkChoice.Open -> "Open"
        null -> UNKNOWN
    }
}
