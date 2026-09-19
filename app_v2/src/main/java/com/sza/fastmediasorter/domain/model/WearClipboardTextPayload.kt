package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S3109: one text clipboard travelling between the phone and the paired watch.
 *
 * One payload for both directions rather than two: the wire carries the same four facts whichever
 * side read the clipboard, and a second type would only duplicate the parse rules that refuse a
 * malformed, unversioned or oversized text.
 *
 * Mirrored rather than shared because `app_v2` does not depend on `:wear` - the same reason
 * [WearSystemInfoReportPayload] exists twice. Field names are pinned on both sides, so the two copies
 * agree on the wire even after R8 renames one of them.
 */
data class WearClipboardTextPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("sourceDeviceModel") val sourceDeviceModel: String,
    @SerializedName("capturedAtEpochMillis") val capturedAtEpochMillis: Long,
    @SerializedName("text") val text: String
) {

    companion object {

        /** The one format this build understands; anything else is answered with a refusal. */
        const val FORMAT_VERSION: Int = 1

        /**
         * The longest text this route carries.
         *
         * A Data Layer message is capped by the platform at 100 KB, and a clipboard above this
         * ceiling is refused by name before it is sent rather than failing somewhere inside GMS with
         * nothing the sender could show the owner.
         */
        const val MAX_TEXT_LENGTH: Int = 20_000
    }
}

/** The receiving side's answer to one clipboard. Mirrored on the other module. */
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

/**
 * The refusal vocabulary, written as the literals the other module matches on.
 *
 * The two modules share no code, so these strings are the contract; the watch keeps its own copy
 * under the same names.
 */
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
