package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sza.fastmediasorter.wear.domain.model.WearAnimationPalette
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.model.WearClockTypeface
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec

/**
 * S3557: the watch's copy of the clock-style wire keys, and the only reader of them.
 *
 * The two modules share no artifact, so these key names are the whole contract with the phone's
 * publisher. Read through the JSON tree rather than a reflective DTO: nothing here needs an R8 keep
 * rule, and a key the phone left out or sent as null falls back field by field instead of failing
 * the whole packet.
 *
 * Every value is forced into range on the way in - unknown names become the default member and the
 * tuning is clamped to WAVE-PARTICLES section 3.5 - so no packet can push the renderer outside the
 * contract (rule 15). Only a packet that is not a JSON object at all is refused.
 */
object WearClockStyleCodec {

    private const val KEY_SECONDS_VISIBLE = "secondsVisible"
    private const val KEY_DIAL_COLOR = "dialColor"
    private const val KEY_DIAL_TYPEFACE = "dialTypeface"
    private const val KEY_ANIMATION_PALETTE = "animationPalette"
    private const val KEY_WALLPAPER_INTENSITY = "wallpaperIntensity"
    private const val KEY_WALLPAPER_SPEED = "wallpaperAnimationSpeed"
    private const val KEY_WALLPAPER_DENSITY = "wallpaperParticleDensity"
    private const val KEY_SENT_AT = "sentAt"

    /** The Data Item's `payload` bytes: an event envelope around the style JSON. Null when unreadable. */
    fun decodeEnvelope(
        payload: ByteArray,
        envelopeCodec: WearEventEnvelopeCodec = WearEventEnvelopeCodec()
    ): WearClockStyle? {
        val envelope = decodeEnvelopeOrNull(payload, envelopeCodec) ?: return null
        return decode(envelope.data.decodeToString(), fallbackSentAt = envelope.sentAt)
    }

    /** One style JSON object; [fallbackSentAt] fills a missing `sentAt`. Null when not a JSON object. */
    fun decode(json: String, fallbackSentAt: Long = 0L): WearClockStyle? {
        val obj = parseOrNull(json)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return styleOf(obj, fallbackSentAt)
    }

    private fun styleOf(obj: JsonObject, fallbackSentAt: Long): WearClockStyle = WearClockStyle(
        secondsVisible = obj.primitive(KEY_SECONDS_VISIBLE)?.takeIf { it.isBoolean }?.asBoolean
            ?: WearClockStyle.DEFAULT.secondsVisible,
        // toLong first: an ARGB with its alpha set arrives either signed or as its unsigned value.
        dialColor = obj.primitive(KEY_DIAL_COLOR)?.takeIf { it.isNumber }?.asNumber?.toLong()?.toInt(),
        typeface = WearClockTypeface.fromWireName(obj.string(KEY_DIAL_TYPEFACE)),
        palette = WearAnimationPalette.fromWireName(obj.string(KEY_ANIMATION_PALETTE)),
        wallpaperIntensity = obj.tuning(
            KEY_WALLPAPER_INTENSITY,
            WearClockStyle.INTENSITY_MIN,
            WearClockStyle.INTENSITY_MAX
        ),
        wallpaperAnimationSpeed = obj.tuning(KEY_WALLPAPER_SPEED, WearClockStyle.SPEED_MIN, WearClockStyle.SPEED_MAX),
        wallpaperParticleDensity = obj.tuning(
            KEY_WALLPAPER_DENSITY,
            WearClockStyle.DENSITY_MIN,
            WearClockStyle.DENSITY_MAX
        ),
        sentAt = obj.primitive(KEY_SENT_AT)?.takeIf { it.isNumber }?.asLong ?: fallbackSentAt
    )

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

    /** The stored form: the same keys the phone sends, so one reader serves the wire and the store. */
    fun encode(style: WearClockStyle): String {
        val obj = JsonObject().apply {
            addProperty(KEY_SECONDS_VISIBLE, style.secondsVisible)
            style.dialColor?.let { addProperty(KEY_DIAL_COLOR, it) }
            addProperty(KEY_DIAL_TYPEFACE, style.typeface.wireName)
            addProperty(KEY_ANIMATION_PALETTE, style.palette.name)
            addProperty(KEY_WALLPAPER_INTENSITY, style.wallpaperIntensity)
            addProperty(KEY_WALLPAPER_SPEED, style.wallpaperAnimationSpeed)
            addProperty(KEY_WALLPAPER_DENSITY, style.wallpaperParticleDensity)
            addProperty(KEY_SENT_AT, style.sentAt)
        }
        return obj.toString()
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        get(key)?.takeUnless(JsonElement::isJsonNull)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.asString

    private fun JsonObject.tuning(key: String, min: Float, max: Float): Float {
        val raw = primitive(key)?.takeIf { it.isNumber }?.asFloat
        // A NaN would survive coerceIn untouched, so a non-finite value reads as absent.
        return raw?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: WearClockStyle.TUNING_DEFAULT
    }
}
