package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S3109: one text clipboard travelling between this watch and the paired phone.
 *
 * One payload for both directions rather than two: the wire carries the same four facts whichever
 * side read the clipboard, and a second type would only duplicate the parse rules that refuse a
 * malformed, unversioned or oversized text.
 *
 * Mirrored verbatim from the phone module's copy - the two modules share no code, so these field
 * names and this version are the entire contract.
 */
data class WearClipboardTextPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("sourceDeviceModel") val sourceDeviceModel: String,
    @SerializedName("capturedAtEpochMillis") val capturedAtEpochMillis: Long,
    @SerializedName("text") val text: String
) {

    companion object {

        /** Bumped whenever a field changes meaning; the other side refuses anything it does not know. */
        const val FORMAT_VERSION: Int = 1

        /**
         * The longest text this route carries.
         *
         * A Data Layer message is capped by the platform at 100 KB, and a clipboard above this
         * ceiling is refused by name before it is sent rather than failing somewhere inside GMS with
         * nothing the sender could show the wearer.
         */
        const val MAX_TEXT_LENGTH: Int = 20_000
    }
}

/** The receiving side's answer to one clipboard. Mirrored on the phone module. */
data class WearClipboardTextAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("reason") val reason: String? = null
)

/** What parsing one received clipboard produced. Parsing never throws at the caller. */
sealed interface WearClipboardTextParseResult {

    data class Parsed(val payload: WearClipboardTextPayload) : WearClipboardTextParseResult

    data class UnsupportedVersion(val version: Int) : WearClipboardTextParseResult

    data object Malformed : WearClipboardTextParseResult

    data object TooLong : WearClipboardTextParseResult
}

/** The refusal vocabulary, mirrored verbatim from the phone module's copy. */
object WearClipboardTextRefusalReasons {
    const val MALFORMED = "malformed"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val TOO_LONG = "too_long"
    const val EMPTY_TEXT = "empty_text"
    const val CLIPBOARD_UNAVAILABLE = "clipboard_unavailable"
}

/** S3109: reads one clipboard off the wire and writes the answer back onto it. */
object WearClipboardTextCodec {

    fun parse(bytes: ByteArray, gson: Gson): WearClipboardTextParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearClipboardTextPayload::class.java)
        }.getOrNull()

        // Gson fills fields by reflection and honours neither Kotlin nullability nor Kotlin defaults,
        // so a JSON object missing a key yields an instance whose non-null field is null.
        val text: String? = payload?.text
        val requestId: String? = payload?.requestId

        return when {
            payload == null || text == null || requestId == null ->
                WearClipboardTextParseResult.Malformed
            payload.formatVersion != WearClipboardTextPayload.FORMAT_VERSION ->
                WearClipboardTextParseResult.UnsupportedVersion(payload.formatVersion)
            text.length > WearClipboardTextPayload.MAX_TEXT_LENGTH ->
                WearClipboardTextParseResult.TooLong
            else -> WearClipboardTextParseResult.Parsed(payload)
        }
    }

    /**
     * HTML escaping off, for the reason the report codec records: the injected Gson escapes `=`, `<`
     * and `'` into six-byte sequences, and a clipboard holding a URL would serialise to several times
     * its size for a wire that is not a web page.
     */
    fun serialize(payload: WearClipboardTextPayload, gson: Gson): ByteArray =
        gson.newBuilder().disableHtmlEscaping().create()
            .toJson(payload.copy(formatVersion = WearClipboardTextPayload.FORMAT_VERSION))
            .toByteArray()

    fun serializeAck(ack: WearClipboardTextAck, gson: Gson): ByteArray =
        gson.newBuilder().disableHtmlEscaping().create().toJson(ack).toByteArray()
}
