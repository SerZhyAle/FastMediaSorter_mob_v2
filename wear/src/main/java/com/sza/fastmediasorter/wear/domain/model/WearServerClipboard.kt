package com.sza.fastmediasorter.wear.domain.model

import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeParseException

/** S4227: the kind of one clipboard value (contract DEVICE-EXCHANGE 22 item BU), with its wire spelling. */
enum class WearServerClipboardKind(val wire: String) {
    TEXT("text"),
    IMAGE("image"),
    FILE("file");

    companion object {
        /** Null for a kind this build does not know: such a value is not received at all. */
        fun fromWire(value: String?): WearServerClipboardKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * S4227: one value of the clipboard on the server, as the server described it (22 items BU and BY).
 *
 * [storedAt] is the server's own clock and the only clock that orders values (22 item BX); the parsed
 * form is null when the server wrote something this build cannot read, which ranks it below any other.
 */
data class WearServerClipboardValue(
    val clipId: String,
    val kind: WearServerClipboardKind,
    val size: Long,
    val sha256: String,
    val storedAt: String?,
    val sourceDeviceId: String?,
    val sourceDeviceName: String?,
    val mimeType: String?,
    val name: String?,
) {

    val storedAtMillis: Long?
        get() = storedAt?.let(::parseInstantMillis)

    // Contract 22 item CC: the content, its name, its hash and its id never reach a log.
    override fun toString(): String = "WearServerClipboardValue(kind=$kind, size=$size)"

    private companion object {
        fun parseInstantMillis(text: String): Long? = try {
            Instant.parse(text).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

/**
 * S4227: what the watch is about to send (22 item BW), checked on the device before the server sees it.
 *
 * A `clipboard-put` that breaks these rules is an invalid envelope and closes the stream, so the
 * watch refuses it itself and names the refusal instead of losing the connection.
 */
data class WearServerClipboardOffer(
    val kind: WearServerClipboardKind,
    val size: Long,
    val mimeType: String?,
    val name: String?,
) {

    fun validate(): Boolean = size >= 1 && nameFitsKind() && mimeTypeFitsKind()

    private fun nameFitsKind(): Boolean = when (kind) {
        WearServerClipboardKind.TEXT -> name == null
        WearServerClipboardKind.IMAGE -> name == null || isValidName(name)
        WearServerClipboardKind.FILE -> name != null && isValidName(name)
    }

    private fun mimeTypeFitsKind(): Boolean = when (kind) {
        WearServerClipboardKind.TEXT -> mimeType == null
        WearServerClipboardKind.IMAGE, WearServerClipboardKind.FILE -> mimeType != null && isValidMimeType(mimeType)
    }

    // Name must stay free of the details: a log line carries the kind and the size only (22 item CC).
    override fun toString(): String = "WearServerClipboardOffer(kind=$kind, size=$size)"

    companion object {
        private const val MAX_NAME_BYTES = 255
        private const val MAX_MIME_TYPE_BYTES = 127
        private val MIME_TYPE_SHAPE = Regex("^[^/\\s]+/[^/\\s]+$")

        fun isValidName(name: String): Boolean = name.isNotEmpty() &&
            name != "." &&
            name != ".." &&
            name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }

        fun isValidMimeType(mimeType: String): Boolean =
            mimeType.toByteArray(Charsets.UTF_8).size <= MAX_MIME_TYPE_BYTES && MIME_TYPE_SHAPE.matches(mimeType)
    }
}

/**
 * S4227: why one entry did not take or give a value - one constant per row of contract 25 item CZ the
 * watch can meet, plus the local classes the watch names itself.
 */
enum class WearServerClipboardFailure {
    NOT_PERMITTED,
    LIMIT_REACHED,
    CAPACITY,
    UNAVAILABLE,
    DEVICE_REVOKED,
    SIGNED_OUT,
    BAD_CREDENTIALS,
    VERSION_UNSUPPORTED,
    UNREACHABLE,
    UNANSWERED,
    TLS_FAILED,
    CERTIFICATE_CHANGED,

    /** The entry holds no device token on this watch. */
    NOT_ENROLLED,

    /** The fresh `welcome` lists no `clipboard`: the server is older than 0.24 or turned it off. */
    NO_CLIPBOARD_FEATURE,

    /** The stream ended before the declared size, or a cancel closed it; nothing was placed. */
    TRANSFER_BROKEN,

    /** The bytes arrived whole but their SHA-256 differs from the server's; nothing was placed. */
    HASH_MISMATCH,

    /** The file on the watch could not be read, or the value breaks the rules of 22 item BW. */
    SOURCE_UNREADABLE,

    /** The value could not be written on the watch; nothing was placed. */
    SAVE_FAILED,

    /** No Wi-Fi or mobile network came up on the watch; nothing was sent or received. */
    NO_NETWORK,

    /** The watch holds no entry that may take part (no member account whose server lists the clipboard). */
    NO_ENTRY,

    /** S4240: the fresh `welcome` lists no `sealed`, so no value goes through it (`server-no-e2e`, 27 item DP). */
    NO_SEALED_FEATURE,

    /** No other device of the account can open a box, or the value came in clear (`e2e-missing`). */
    E2E_MISSING,

    /** The box or a segment did not open, or the clear size or SHA-256 differed; nothing was placed. */
    SEALED_FAILED,

    /** A refusal reason this build does not know (7.10): the generic refusal text. */
    OTHER_REFUSAL;

    companion object {
        private val BY_REASON = mapOf(
            "not-permitted" to NOT_PERMITTED,
            "limit-reached" to LIMIT_REACHED,
            "capacity" to CAPACITY,
            "unavailable" to UNAVAILABLE,
            "device-revoked" to DEVICE_REVOKED,
            "signed-out" to SIGNED_OUT,
            "bad-credentials" to BAD_CREDENTIALS,
            "version-unsupported" to VERSION_UNSUPPORTED,
        )

        fun fromReason(reason: String?): WearServerClipboardFailure = BY_REASON[reason] ?: OTHER_REFUSAL
    }
}

/** S4227: one entry that did not answer or refused, named by the address the person connected to (25 item DA). */
data class WearServerClipboardEntryFailure(
    val label: String,
    val failure: WearServerClipboardFailure,
)

/**
 * S4227: where a received value is being written - a pending media row or a temp file in app storage. Nothing
 * becomes visible before [commit]; [discard] removes every trace and is safe to call twice.
 */
interface WearServerClipboardSink {
    val output: OutputStream

    /** Places the value; null when the platform refused the last step, in which case nothing is placed. */
    fun commit(): WearServerClipboardSaved?

    fun discard()
}

/** S4227: a placed value - [contentUri] for a media row, [path] for a file in app storage. */
data class WearServerClipboardSaved(
    val displayName: String,
    val path: String?,
    val contentUri: String?,
)

/** S4227: how "send clipboard to server" ended across every eligible entry (22 item CA). */
data class WearServerClipboardSendReport(
    val delivered: List<String>,
    val failed: List<WearServerClipboardEntryFailure>,
    val noEntry: Boolean = false,
    val noNetwork: Boolean = false,
    /** Set when the watch refused the value itself, before any server was asked. */
    val localFailure: WearServerClipboardFailure? = null,
) {
    /** At least one entry answered `clipboard-stored`. */
    val sent: Boolean
        get() = delivered.isNotEmpty()
}

/** S4227: how "get clipboard from server" ended (22 items BY and CA). */
sealed interface WearServerClipboardReceiveReport {

    /** [skipped] names the entries that did not answer or refused; the value came from [entryLabel]. */
    data class Received(
        val value: WearServerClipboardValue,
        val saved: WearServerClipboardSaved,
        val entryLabel: String,
        val skipped: List<WearServerClipboardEntryFailure>,
    ) : WearServerClipboardReceiveReport

    data class Empty(val skipped: List<WearServerClipboardEntryFailure>) : WearServerClipboardReceiveReport

    /** The caller gave no place for the value - the watch lacks the space it declared; nothing was read. */
    data class Declined(val value: WearServerClipboardValue) : WearServerClipboardReceiveReport

    data object NoEntry : WearServerClipboardReceiveReport

    data object NoNetwork : WearServerClipboardReceiveReport

    data class Failed(val failures: List<WearServerClipboardEntryFailure>) : WearServerClipboardReceiveReport
}

/** S4227: what "get clipboard from server" ended as, and what to open when the watch can show it. */
data class WearServerClipboardGetResult(
    val report: WearServerClipboardReceiveReport,
    /** Null when nothing was received, or when the watch saves this kind and cannot show it. */
    val openRequest: WearFileOpenRequest? = null,
    /** Set when the value was declined because the watch has less free space than it declared. */
    val refusedForSpace: Boolean = false,
)
