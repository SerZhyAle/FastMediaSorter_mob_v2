package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots

/**
 * S3558: the watch's copy of the face-slot wire keys, and the only reader of them.
 *
 * Shaped like [WearClockStyleCodec] for the same reasons: the modules share no artifact, the JSON tree
 * needs no R8 keep rule, and a slot the phone left out or named with an id this build does not know
 * falls back to that slot's own default instead of failing the whole packet. Only a packet that is not
 * a JSON object at all is refused.
 */
object WearFaceSlotsCodec {

    private const val KEY_SLOT_PREFIX = "slot"
    private const val KEY_SENT_AT = "sentAt"

    /** The Data Item's `payload` bytes: an event envelope around the slots JSON. Null when unreadable. */
    fun decodeEnvelope(
        payload: ByteArray,
        envelopeCodec: WearEventEnvelopeCodec = WearEventEnvelopeCodec()
    ): WearFaceSlots? {
        val envelope = decodeEnvelopeOrNull(payload, envelopeCodec) ?: return null
        return decode(envelope.data.decodeToString(), fallbackSentAt = envelope.sentAt)
    }

    /** One slots JSON object; [fallbackSentAt] fills a missing `sentAt`. Null when not a JSON object. */
    fun decode(json: String, fallbackSentAt: Long = 0L): WearFaceSlots? {
        val obj = parseOrNull(json)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val options = WearFaceSlots.DEFAULT_OPTIONS.mapIndexed { index, default ->
            WearFaceSlotOption.fromWireId(obj.string(keyFor(index + 1))) ?: default
        }
        val sentAt = obj.primitive(KEY_SENT_AT)?.takeIf { it.isNumber }?.asLong ?: fallbackSentAt
        return WearFaceSlots(options, sentAt)
    }

    /** The stored form: the same keys the phone sends, so one reader serves the wire and the store. */
    fun encode(slots: WearFaceSlots): String {
        val obj = JsonObject().apply {
            slots.options.forEachIndexed { index, option -> addProperty(keyFor(index + 1), option.wireId) }
            addProperty(KEY_SENT_AT, slots.sentAt)
        }
        return obj.toString()
    }

    private fun keyFor(slot: Int): String = KEY_SLOT_PREFIX + slot

    /** Same shape check as [WearClockStyleCodec]: the envelope codec throws on anything else. */
    private fun decodeEnvelopeOrNull(payload: ByteArray, envelopeCodec: WearEventEnvelopeCodec): WearEventEnvelope? {
        if (parseOrNull(payload.decodeToString())?.isJsonObject != true) return null
        return try {
            envelopeCodec.decode(payload)
        } catch (expected: IllegalArgumentException) {
            // A missing field, bad Base64 or a non-numeric version: nothing in it can be applied.
            null
        } catch (expected: UnsupportedOperationException) {
            // A field that is an array or object where a scalar belongs.
            null
        }
    }

    private fun parseOrNull(json: String): JsonElement? = try {
        JsonParser.parseString(json)
    } catch (expected: JsonParseException) {
        null
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        get(key)?.takeUnless(JsonElement::isJsonNull)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.asString
}
