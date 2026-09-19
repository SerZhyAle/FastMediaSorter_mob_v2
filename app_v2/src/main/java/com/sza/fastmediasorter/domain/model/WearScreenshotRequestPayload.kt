package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S3110: the phone asking the paired watch for a picture of its own screen.
 *
 * The ask carries no payload of substance - it exists so the watch can answer the right request when
 * two arrive close together, and so a build that predates the format can refuse by version instead of
 * acting on a shape it does not understand.
 *
 * Mirrored rather than shared because `app_v2` does not depend on `:wear` - the same reason
 * [WearClipboardTextPayload] exists twice. Field names are pinned on both sides, so the two copies
 * agree on the wire even after R8 renames one of them.
 */
data class WearScreenshotRequestPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("requestedAtEpochMillis") val requestedAtEpochMillis: Long
) {

    companion object {

        /** The one format this build understands; anything else is answered with a refusal. */
        const val FORMAT_VERSION: Int = 1
    }
}

/**
 * The watch's answer to one screenshot request. Mirrored on the other module.
 *
 * [fileName] is filled whenever a file left the watch, refusal included: an unconfirmed send may still
 * land on the phone, and the name is the only thing tying that arrival to this request.
 */
data class WearScreenshotRequestAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("captured") val captured: Boolean,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("reason") val reason: String? = null
)

/** What parsing one screenshot request produced. Parsing never throws at the caller. */
sealed interface WearScreenshotRequestParseResult {

    data class Parsed(val payload: WearScreenshotRequestPayload) : WearScreenshotRequestParseResult

    data class UnsupportedVersion(val version: Int) : WearScreenshotRequestParseResult

    data object Malformed : WearScreenshotRequestParseResult
}

/** What parsing one screenshot ack produced. Parsing never throws at the caller. */
sealed interface WearScreenshotAckParseResult {

    data class Parsed(val ack: WearScreenshotRequestAck) : WearScreenshotAckParseResult

    data object Malformed : WearScreenshotAckParseResult
}

/**
 * The refusal vocabulary, written as the literals the other module matches on.
 *
 * The two modules share no code, so these strings are the contract; the watch keeps its own copy
 * under the same names.
 */
object WearScreenshotRefusalReasons {
    const val MALFORMED = "malformed"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val NO_FOREGROUND_SCREEN = "no_foreground_screen"
    const val CAPTURE_FAILED = "capture_failed"
    const val SEND_FAILED = "send_failed"
}

/** S3110: reads one screenshot request off the wire and reads the answer back off it. */
object WearScreenshotRequestCodec {

    fun parse(bytes: ByteArray, gson: Gson): WearScreenshotRequestParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearScreenshotRequestPayload::class.java)
        }.getOrNull()

        // Gson fills fields by reflection and honours neither Kotlin nullability nor Kotlin defaults,
        // so a JSON object missing a key yields an instance whose non-null field is null.
        val requestId: String? = payload?.requestId

        return when {
            payload == null || requestId == null -> WearScreenshotRequestParseResult.Malformed
            payload.formatVersion != WearScreenshotRequestPayload.FORMAT_VERSION ->
                WearScreenshotRequestParseResult.UnsupportedVersion(payload.formatVersion)
            else -> WearScreenshotRequestParseResult.Parsed(payload)
        }
    }

    fun parseAck(bytes: ByteArray, gson: Gson): WearScreenshotAckParseResult {
        val ack = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearScreenshotRequestAck::class.java)
        }.getOrNull()

        // A refusal the watch could not correlate carries an empty request id by design, so only a
        // missing object or a missing id field is malformed here.
        return if (ack?.requestId == null) {
            WearScreenshotAckParseResult.Malformed
        } else {
            WearScreenshotAckParseResult.Parsed(ack)
        }
    }

    /**
     * HTML escaping off, for the reason the clipboard codec records: the injected Gson escapes `=`, `<`
     * and `'` into six-byte sequences, and a file name holding none of them still pays for the check on
     * a wire that is not a web page.
     */
    fun serialize(payload: WearScreenshotRequestPayload, gson: Gson): ByteArray =
        gson.newBuilder().disableHtmlEscaping().create()
            .toJson(payload.copy(formatVersion = WearScreenshotRequestPayload.FORMAT_VERSION))
            .toByteArray()

    fun serializeAck(ack: WearScreenshotRequestAck, gson: Gson): ByteArray =
        gson.newBuilder().disableHtmlEscaping().create().toJson(ack).toByteArray()
}
