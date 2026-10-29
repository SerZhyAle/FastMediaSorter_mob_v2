package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S4248: one scheduled message travelling from the phone to the paired watch - a title, an optional
 * text and an optional picture already shrunk for the watch screen.
 *
 * Its own payload rather than a `WearEventEnvelope`: Gson writes the envelope's `ByteArray` as a JSON
 * array of numbers, several bytes per pixel byte, and the picture would not fit one Data Layer message.
 * Here the picture travels as base64 text.
 *
 * Mirrored rather than shared because `app_v2` does not depend on `:wear`. Field names and refusal
 * literals are pinned on both sides, so the two copies agree on the wire even after R8 renames one.
 */
data class WearScheduledMessagePayload(
    @SerializedName("messageId") val messageId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("imageJpegBase64") val imageJpegBase64: String? = null,
) {

    companion object {

        /** The one format this build understands; anything else is answered with a refusal. */
        const val FORMAT_VERSION: Int = 1

        /** Kept below the platform's 100 KB message cap so the send fails here, by name, not inside GMS. */
        const val MAX_MESSAGE_BYTES: Int = 90_000
    }
}

/** The watch's answer to one scheduled message: shown, or the reason it was not. */
data class WearScheduledMessageReceipt(
    @SerializedName("messageId") val messageId: String,
    @SerializedName("shown") val shown: Boolean,
    @SerializedName("reason") val reason: String? = null,
)

/** The refusal vocabulary, written as the literals the other module matches on. */
object WearScheduledMessageReceiptReasons {
    const val NO_NOTIFICATION_PERMISSION = "no_notification_permission"
    const val MALFORMED = "malformed"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val NOT_OFFERED = "not_offered"
}

/** What parsing one received message produced. Parsing never throws at the caller. */
sealed interface WearScheduledMessageParseResult {

    data class Parsed(val payload: WearScheduledMessagePayload) : WearScheduledMessageParseResult

    /** [messageId] is what the phone's waiting run matches the refusal by. */
    data class UnsupportedVersion(val version: Int, val messageId: String) : WearScheduledMessageParseResult

    /** [messageId] is null when the bytes carried no readable id; such a message stays unanswered by design. */
    data class Malformed(val messageId: String? = null) : WearScheduledMessageParseResult

    /** Never read for an id: the phone's encoder refuses to send a message this large. */
    data object TooLarge : WearScheduledMessageParseResult
}

/** S4248: reads one scheduled message off the wire and writes it, and its receipt, onto it. */
object WearScheduledMessageCodec {

    /** Null when the encoded message would exceed [WearScheduledMessagePayload.MAX_MESSAGE_BYTES]. */
    fun encode(payload: WearScheduledMessagePayload, gson: Gson): ByteArray? =
        plain(gson).toJson(payload.copy(formatVersion = WearScheduledMessagePayload.FORMAT_VERSION))
            .toByteArray()
            .takeIf { it.size <= WearScheduledMessagePayload.MAX_MESSAGE_BYTES }

    fun parse(bytes: ByteArray, gson: Gson): WearScheduledMessageParseResult {
        if (bytes.size > WearScheduledMessagePayload.MAX_MESSAGE_BYTES) {
            return WearScheduledMessageParseResult.TooLarge
        }
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearScheduledMessagePayload::class.java)
        }.getOrNull()
        // Gson honours neither Kotlin nullability nor defaults, so a missing key reads as null here.
        val title: String? = payload?.title
        val messageId: String? = payload?.messageId
        return when {
            payload == null || title == null || messageId == null ->
                WearScheduledMessageParseResult.Malformed(messageId)
            payload.formatVersion != WearScheduledMessagePayload.FORMAT_VERSION ->
                WearScheduledMessageParseResult.UnsupportedVersion(payload.formatVersion, messageId)
            else -> WearScheduledMessageParseResult.Parsed(payload)
        }
    }

    fun encodeReceipt(receipt: WearScheduledMessageReceipt, gson: Gson): ByteArray =
        plain(gson).toJson(receipt).toByteArray()

    /** Null for bytes that do not carry a message id. */
    fun parseReceipt(bytes: ByteArray, gson: Gson): WearScheduledMessageReceipt? {
        val receipt = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearScheduledMessageReceipt::class.java)
        }.getOrNull()
        val messageId: String? = receipt?.messageId
        return receipt?.takeIf { messageId != null }
    }

    // HTML escaping off: base64 carries `=`, which the injected Gson would expand to six bytes each.
    private fun plain(gson: Gson): Gson = gson.newBuilder().disableHtmlEscaping().create()
}
