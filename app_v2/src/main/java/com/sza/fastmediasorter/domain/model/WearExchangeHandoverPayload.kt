package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S4158: one exchange account entry of this phone handed to the paired watch, so the watch enrolls into the same
 * account with a pairing code (contract DEVICE-EXCHANGE 5.3) and never sees the password.
 *
 * [pinnedCertificate] is the phone entry's trust decision: the leaf the user accepted, or null where the platform
 * trusts the server. [pinnedKey] is that leaf's public-key fingerprint (19 item AY), so the watch accepts a same-key
 * renewal and refuses any other leaf; an older phone sends none and the watch learns it on its first connect.
 *
 * Mirrored verbatim in the watch module - the two modules share no code, so these field names and this version
 * are the entire contract.
 */
data class WearExchangeHandoverPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("login") val login: String,
    @SerializedName("pinnedCertificate") val pinnedCertificate: String?,
    @SerializedName("pairingCode") val pairingCode: String,
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("pinnedKey") val pinnedKey: String? = null,
) {

    // The code is a credential and the address and login are private (contract section 7.9).
    override fun toString(): String =
        "WearExchangeHandoverPayload(requestId=$requestId, formatVersion=$formatVersion, port=$port)"

    companion object {
        /** Bumped whenever a field changes meaning; the other side refuses anything it does not know. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** The watch's answer to one hand-over. [reason] is a [WearExchangeHandoverReasons] value or a 7.10 reason. */
data class WearExchangeHandoverAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("enrolled") val enrolled: Boolean,
    @SerializedName("reason") val reason: String? = null,
    /** The enrolled watch's `deviceId`; set on success only. */
    @SerializedName("deviceId") val deviceId: String? = null,
    /** `kem` of the watch's `e2eKey`, which the phone trusts over the directory (DEVICE-EXCHANGE 27 item DG). */
    @SerializedName("e2eKem") val e2eKem: String? = null,
)

/** The watch-side refusal vocabulary, mirrored verbatim in the watch module. */
object WearExchangeHandoverReasons {
    const val MALFORMED = "malformed"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val CODE_EXPIRED = "code_expired"
    const val CERTIFICATE_UNTRUSTED = "certificate_untrusted"
    const val CERTIFICATE_CHANGED = "certificate_changed"
    const val UNREACHABLE = "unreachable"
    const val UNANSWERED = "unanswered"
    const val TLS_FAILED = "tls-failed"
    const val TOKEN_NOT_STORED = "token-not-stored"
}

/** What parsing one received hand-over produced. Parsing never throws at the caller. */
sealed interface WearExchangeHandoverParseResult {

    data class Parsed(val payload: WearExchangeHandoverPayload) : WearExchangeHandoverParseResult

    /** [requestId] is the sender's, so the refusal carries the id the sending side's correlator waits on. */
    data class UnsupportedVersion(val version: Int, val requestId: String) : WearExchangeHandoverParseResult

    data object Malformed : WearExchangeHandoverParseResult
}

/** S4158: reads one hand-over and its answer off the Data Layer and writes them onto it. */
object WearExchangeHandoverCodec {

    fun parse(bytes: ByteArray, gson: Gson): WearExchangeHandoverParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearExchangeHandoverPayload::class.java)
        }.getOrNull()
        // The version is judged before completeness: a newer format may rename the fields this build requires, and
        // the refusal is only worth sending when it carries the id the sending side is waiting on.
        return when {
            payload == null -> WearExchangeHandoverParseResult.Malformed
            payload.formatVersion != WearExchangeHandoverPayload.FORMAT_VERSION -> unsupported(payload)
            !payload.isComplete() -> WearExchangeHandoverParseResult.Malformed
            else -> WearExchangeHandoverParseResult.Parsed(payload)
        }
    }

    private fun unsupported(payload: WearExchangeHandoverPayload): WearExchangeHandoverParseResult {
        // Gson ignores Kotlin nullability, so a missing key arrives as null in a non-null field.
        val requestId: String? = payload.requestId
        return if (requestId.isNullOrBlank()) {
            WearExchangeHandoverParseResult.Malformed
        } else {
            WearExchangeHandoverParseResult.UnsupportedVersion(payload.formatVersion, requestId)
        }
    }

    fun serialize(payload: WearExchangeHandoverPayload, gson: Gson): ByteArray =
        compact(gson).toJson(payload.copy(formatVersion = WearExchangeHandoverPayload.FORMAT_VERSION)).toByteArray()

    /** Null for an answer that is not one; the waiting side then times out as for a lost one. */
    fun parseAck(bytes: ByteArray, gson: Gson): WearExchangeHandoverAck? {
        val ack = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearExchangeHandoverAck::class.java)
        }.getOrNull()
        // Gson ignores Kotlin nullability, so a missing key arrives as null in a non-null field.
        val requestId: String? = ack?.requestId
        return ack?.takeIf { requestId != null }
    }

    fun serializeAck(ack: WearExchangeHandoverAck, gson: Gson): ByteArray = compact(gson).toJson(ack).toByteArray()

    // Gson fills fields by reflection and honours neither Kotlin nullability nor defaults.
    private fun WearExchangeHandoverPayload.isComplete(): Boolean {
        val required: List<String?> = listOf(requestId, host, login, pairingCode)
        return required.none { it.isNullOrBlank() } && port in VALID_PORTS
    }

    // The injected Gson escapes `=` and `<`; this wire is not a web page.
    private fun compact(gson: Gson): Gson = gson.newBuilder().disableHtmlEscaping().create()

    private const val MAX_PORT = 65_535
    private val VALID_PORTS = 1..MAX_PORT
}
