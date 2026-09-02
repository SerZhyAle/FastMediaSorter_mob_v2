package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1861: the ceiling both directions of the bridge refuse above, in bytes.
 *
 * Mirrored by the phone half rather than shared: the two modules compile separately and there is no
 * common artifact between them, so the number is pinned once on each side and must move on both.
 */
const val WEAR_FILE_TRANSFER_MAX_BYTES = 32L * 1024L * 1024L

/**
 * S1861: what one side announces about a file before it opens the channel that carries it.
 *
 * Every field is defaulted because this arrives as JSON written by the other side of the bridge:
 * Gson leaves a missing field untouched, and a null in a non-null Kotlin field would blow up at the
 * first read rather than at parse time.
 */
data class WearFileTransferMetadata(
    /**
     * S1884: correlator the sender picked, echoed back in [WearFileTransferAck]. Empty when the other
     * side is older than this field, which is exactly the sorting transfer that expects no answer.
     */
    @SerializedName("requestId")
    val requestId: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("size")
    val size: Long = 0L,
    @SerializedName("mimeType")
    val mimeType: String? = null,
    /**
     * S1884: the sender wants this shown now rather than kept. False is the shipped S1861 meaning -
     * sort the file into permanent storage - so an announcement without the field keeps behaving as
     * it always did.
     */
    @SerializedName("openNow")
    val openNow: Boolean = false
)

/**
 * S1884: what this side answers the sender after a file it announced has been dealt with.
 *
 * Mirrored field-for-field by the phone half rather than shared - Gson writes these names verbatim on
 * both sides, so renaming one here breaks the other module silently. The outcome strings are the
 * S1944 stream vocabulary reused, so the two "open on watch" routes report in one language.
 */
data class WearFileTransferAck(
    @SerializedName("requestId")
    val requestId: String = "",
    @SerializedName("outcome")
    val outcome: String = ""
) {
    companion object {
        const val OUTCOME_OPENED = "OPENED"
        const val OUTCOME_NOT_FOREGROUND = "NOT_FOREGROUND"
        const val OUTCOME_UNSUPPORTED = "UNSUPPORTED"
        const val OUTCOME_TOO_LARGE = "TOO_LARGE"
        const val OUTCOME_SAVED = "SAVED"

        /**
         * The bytes did not arrive whole. Distinct from silence on purpose: without it the sender
         * waits out its whole ack timeout and then reports that the watch never answered, which is
         * the one thing that did not happen.
         */
        const val OUTCOME_FAILED = "FAILED"
    }
}

/** S1861: how one incoming file ended on this side of the bridge. */
enum class WearFileReceiveOutcome {
    SAVED,

    /**
     * Refused for size - either the declared size was already over the ceiling, or the bytes that
     * actually arrived outran what was declared. The two are one outcome because the user is told
     * the same thing: this file is too big for the bridge.
     */
    REFUSED_TOO_LARGE,

    FAILED
}

/**
 * S1884: the outcome of one incoming file, with the path it landed on when it landed.
 *
 * The same shape the phone half already returns for the mirror direction. The path is carried rather
 * than recomputed by the caller, so the rule deciding where a file goes - preview cache or permanent
 * downloads - lives in one place instead of being restated wherever the file is opened.
 *
 * [declaration] rides along because `WearableListenerService` is recreated per event: by the time the
 * channel closes, the object that saw the announcing message is gone, and the request id it must
 * answer with went with it. Null when the bytes arrived undeclared.
 */
data class WearFileReceiveResult(
    val outcome: WearFileReceiveOutcome,
    val savedPath: String? = null,
    val declaration: WearFileTransferMetadata? = null
)

/** S1861: how one outgoing file ended when this side started the transfer. */
enum class WearFileSendOutcome {
    SENT,
    QUEUED_ON_PHONE,
    NO_DESTINATION,
    UNCONFIRMED,
    TOO_LARGE,
    PHONE_UNREACHABLE,
    FAILED
}

/** Immediate outcome acknowledgement sent by phone to watch after receiving a file. */
data class WearFileReceiveAck(
    @SerializedName("fileName")
    val fileName: String = "",
    @SerializedName("outcome")
    val outcome: String = "",
    @SerializedName("destination")
    val destination: String = ""
) {
    companion object {
        const val OUTCOME_SAVED = "SAVED"
        const val OUTCOME_QUEUED = "QUEUED"
        const val OUTCOME_NO_DESTINATION = "NO_DESTINATION"
        const val OUTCOME_TOO_LARGE = "TOO_LARGE"
        const val OUTCOME_FAILED = "FAILED"
    }
}

/**
 * Deferred upload outcome published as a Data Item by phone to watch.
 * [completedAtMillis] ensures Data Layer publishes an update even for duplicate outcomes.
 */
data class WearFileUploadOutcome(
    @SerializedName("fileName")
    val fileName: String = "",
    @SerializedName("succeeded")
    val succeeded: Boolean = false,
    @SerializedName("destination")
    val destination: String = "",
    @SerializedName("completedAtMillis")
    val completedAtMillis: Long = 0L
)
