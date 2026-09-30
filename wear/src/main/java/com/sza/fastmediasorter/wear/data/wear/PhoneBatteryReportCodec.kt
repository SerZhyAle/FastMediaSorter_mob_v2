package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sza.fastmediasorter.wear.domain.model.PhoneBatteryReport
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec

/**
 * S3764: the watch's copy of the phone battery wire keys, and the only reader of them.
 *
 * The two modules share no artifact, so these key names are the whole contract with the phone's
 * publisher (the phone writes the same three keys with Gson). Shaped after [WearClockStyleCodec]
 * with one deliberate difference: a battery report has no safe per-field fallback - a half-readable
 * percent would draw a wrong bar - so a packet missing or overflowing any field is refused whole
 * instead of falling back field by field.
 */
object PhoneBatteryReportCodec {

    private const val KEY_PERCENT = "percent"
    private const val KEY_IS_CHARGING = "isCharging"
    private const val KEY_TIMESTAMP_MS = "timestampMs"

    private const val PERCENT_MIN = 0
    private const val PERCENT_MAX = 100

    /** The Data Item's `payload` bytes: an event envelope around the report JSON. Null when unreadable. */
    fun decodeEnvelope(
        payload: ByteArray,
        envelopeCodec: WearEventEnvelopeCodec = WearEventEnvelopeCodec()
    ): PhoneBatteryReport? {
        val envelope = decodeEnvelopeOrNull(payload, envelopeCodec) ?: return null
        return decode(envelope.data.decodeToString())
    }

    /** One report JSON object. Null when not a JSON object or any field is missing or out of range. */
    fun decode(json: String): PhoneBatteryReport? {
        val obj = parseOrNull(json)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return reportOf(obj)
    }

    /** Single expression on purpose: every missing or out-of-range field folds into the null result. */
    private fun reportOf(obj: JsonObject): PhoneBatteryReport? = obj.number(KEY_PERCENT)
        ?.toInt()
        ?.takeIf { it in PERCENT_MIN..PERCENT_MAX }
        ?.let { percent ->
            obj.primitive(KEY_IS_CHARGING)?.takeIf { it.isBoolean }?.asBoolean?.let { isCharging ->
                obj.number(KEY_TIMESTAMP_MS)?.toLong()?.let { timestampMs ->
                    PhoneBatteryReport(percent = percent, isCharging = isCharging, timestampMs = timestampMs)
                }
            }
        }

    /** The stored form: the same keys the phone sends, so one reader serves the wire and the store. */
    fun encode(report: PhoneBatteryReport): String {
        val obj = JsonObject().apply {
            addProperty(KEY_PERCENT, report.percent)
            addProperty(KEY_IS_CHARGING, report.isCharging)
            addProperty(KEY_TIMESTAMP_MS, report.timestampMs)
        }
        return obj.toString()
    }

    /**
     * The envelope codec throws on anything but a well-formed envelope object; checking the shape
     * first keeps the failures it can still raise to the two argument-level ones caught here.
     */
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

    private fun JsonObject.number(key: String): Number? =
        primitive(key)?.takeIf { it.isNumber }?.asNumber
}
