package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S4228: the PIN of one protected exchange folder handed to the watch (contract DEVICE-EXCHANGE 23 item CG, 17 item
 * AJ). The folder itself reaches the watch in the selection push; this message carries only what must never sit in
 * that persistent DataItem, keyed by the folder's identity - [fingerprint] and [root] (20 item BA).
 *
 * Mirrored verbatim from the phone module's copy - the two modules share no code, so these field names and this
 * version are the entire contract.
 */
data class WearResourcePassPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("fingerprint") val fingerprint: String,
    @SerializedName("root") val root: String,
    @SerializedName("exchangeAccount") val exchangeAccount: String,
    @SerializedName("resourceId") val resourceId: String,
    @SerializedName("pin") val pin: String,
) {

    // The PIN is a credential; the root and the account name a private folder.
    override fun toString(): String = "WearResourcePassPayload(requestId=$requestId, formatVersion=$formatVersion)"

    companion object {
        /** Bumped whenever a field changes meaning; the other side refuses anything it does not know. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** The watch's answer to one pass. [reason] is a [WearResourcePassReasons] value when [stored] is false. */
data class WearResourcePassAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("stored") val stored: Boolean,
    @SerializedName("reason") val reason: String? = null,
)

/** The watch-side refusal vocabulary, mirrored verbatim from the phone module's copy. */
object WearResourcePassReasons {
    const val MALFORMED = "malformed"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val NOT_STORED = "not_stored"
}

/** What parsing one received pass produced. Parsing never throws at the caller. */
sealed interface WearResourcePassParseResult {

    data class Parsed(val payload: WearResourcePassPayload) : WearResourcePassParseResult

    /** [requestId] is the sender's, so the refusal carries the id the phone's correlator waits on. */
    data class UnsupportedVersion(val version: Int, val requestId: String) : WearResourcePassParseResult

    data object Malformed : WearResourcePassParseResult
}

/** S4228: reads one pass and its answer off the Data Layer and writes them onto it. */
object WearResourcePassCodec {

    fun parse(bytes: ByteArray, gson: Gson): WearResourcePassParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearResourcePassPayload::class.java)
        }.getOrNull()
        // The version is judged before completeness: a newer format may rename the fields this build requires, and
        // the refusal is only worth sending when it carries the id the phone is waiting on.
        return when {
            payload == null -> WearResourcePassParseResult.Malformed
            payload.formatVersion != WearResourcePassPayload.FORMAT_VERSION -> unsupported(payload)
            !payload.isComplete() -> WearResourcePassParseResult.Malformed
            else -> WearResourcePassParseResult.Parsed(payload)
        }
    }

    private fun unsupported(payload: WearResourcePassPayload): WearResourcePassParseResult {
        // Gson ignores Kotlin nullability, so a missing key arrives as null in a non-null field.
        val requestId: String? = payload.requestId
        return if (requestId.isNullOrBlank()) {
            WearResourcePassParseResult.Malformed
        } else {
            WearResourcePassParseResult.UnsupportedVersion(payload.formatVersion, requestId)
        }
    }

    fun serialize(payload: WearResourcePassPayload, gson: Gson): ByteArray =
        compact(gson).toJson(payload.copy(formatVersion = WearResourcePassPayload.FORMAT_VERSION)).toByteArray()

    /** Null for an answer that is not one. */
    fun parseAck(bytes: ByteArray, gson: Gson): WearResourcePassAck? {
        val ack = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearResourcePassAck::class.java)
        }.getOrNull()
        // Gson ignores Kotlin nullability, so a missing key arrives as null in a non-null field.
        val requestId: String? = ack?.requestId
        return ack?.takeIf { requestId != null }
    }

    fun serializeAck(ack: WearResourcePassAck, gson: Gson): ByteArray = compact(gson).toJson(ack).toByteArray()

    // Gson fills fields by reflection and honours neither Kotlin nullability nor defaults.
    private fun WearResourcePassPayload.isComplete(): Boolean {
        val required: List<String?> = listOf(requestId, fingerprint, root, exchangeAccount, resourceId, pin)
        return required.none { it.isNullOrBlank() }
    }

    // The injected Gson escapes `=` and `<`; this wire is not a web page.
    private fun compact(gson: Gson): Gson = gson.newBuilder().disableHtmlEscaping().create()
}
