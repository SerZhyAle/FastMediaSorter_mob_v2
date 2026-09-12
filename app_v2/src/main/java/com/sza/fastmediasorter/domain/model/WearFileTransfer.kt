package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1861: how one queued phone -> watch transfer ended, or that it has not ended yet.
 *
 * The failing values are distinct rather than one `FAILED` because the user is told the outcome
 * (goal 5) and the three causes need different words: a watch that is not on the wrist, a file the
 * bridge refuses to carry, and a transfer the user stopped are not the same event.
 */
enum class WearFileTransferOutcome {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    CANCELLED,
    WATCH_UNREACHABLE,
    TOO_LARGE,
    FAILED;

    val isTerminal: Boolean
        get() = this != QUEUED && this != RUNNING
}

/**
 * S1861: one file on the phone -> watch queue, with the byte counter the UI draws its progress from.
 */
data class WearFileTransferItem(
    val id: String,
    val sourcePath: String,
    val displayName: String,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val outcome: WearFileTransferOutcome = WearFileTransferOutcome.QUEUED,
    val mediaType: MediaType? = null
)

/**
 * S1861: the whole transfer queue as one immutable snapshot.
 *
 * The queue is exposed as a single value rather than a stream of events because the transfer outlives
 * the screen that started it: a UI that attaches late must be able to draw the current picture from
 * the first emission alone, without having witnessed the events that produced it.
 */
data class WearFileTransferState(
    val items: List<WearFileTransferItem> = emptyList()
) {
    val running: WearFileTransferItem?
        get() = items.firstOrNull { it.outcome == WearFileTransferOutcome.RUNNING }

    val pending: List<WearFileTransferItem>
        get() = items.filterNot { it.outcome.isTerminal }
}

/**
 * S1861: the ceiling both directions of the bridge refuse above, in bytes (strategic spec 3.2).
 *
 * Mirrored by the watch half rather than shared: the two modules compile separately and there is no
 * common artifact between them, so the number is pinned once on each side and must move on both.
 */
const val WEAR_FILE_TRANSFER_MAX_BYTES = 32L * 1024L * 1024L

/**
 * S1861: what the phone announces about a file before it opens the channel that carries it.
 *
 * The field names are the wire format shared with the watch half's own copy of this shape - Gson
 * writes them verbatim on both sides, so renaming one here breaks the other module silently.
 */
data class WearFileTransferMetadata(
    @SerializedName("requestId")
    val requestId: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("size")
    val size: Long = 0L,
    @SerializedName("mimeType")
    val mimeType: String? = null,
    @SerializedName("openNow")
    val openNow: Boolean = false,
    /**
     * S2142: the «Send to..» receiver the watch is asking this phone to hand the file to.
     *
     * Null is the shipped S1861 meaning - file it into the configured destination and nothing else -
     * so a watch older than this field keeps behaving as it always did. The id is the receiver key
     * this phone already stores in its own switches, so nothing has to be translated on arrival.
     */
    @SerializedName("sendToReceiverId")
    val sendToReceiverId: String? = null
)

/** Correlated acknowledgement sent by the watch after a phone-initiated file transfer. */
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
         * S1884: the watch received a truncated file. Distinct from silence on purpose - without it
         * this side waits out the whole ack timeout and then reports that the watch never answered,
         * which is the one thing that did not happen.
         */
        const val OUTCOME_FAILED = "FAILED"
    }
}

/** S1861: how one watch -> phone file ended on the phone. */
enum class WearFileReceiveOutcome {
    SAVED,

    /**
     * Refused for size - the declaration was already over the ceiling, or the arriving bytes outran
     * what was declared. One outcome for both, because the user is told the same thing either way.
     */
    REFUSED_TOO_LARGE,

    NO_DESTINATION,

    /**
     * S2044: the bytes are on the phone and an upload to a remote destination has been enqueued.
     *
     * Not a final outcome, and deliberately not reported as [SAVED]: at the moment the channel
     * closes the file has not reached the destination the user configured, and only the upload
     * worker learns whether it ever does.
     */
    QUEUED_FOR_UPLOAD,

    FAILED
}

/** S1861: the outcome of one watch -> phone file, with the path it landed on when it landed. */
data class WearFileReceiveResult(
    val outcome: WearFileReceiveOutcome,
    val savedPath: String? = null,
    val destinationName: String? = null
)

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

        /** S2142: the file is here and the «Send to..» action is waiting for a tap on this phone. */
        const val OUTCOME_AWAITING_SEND_TO = "AWAITING_SEND_TO"

        /** S2142: the errand arrived, but this phone may post no notification to offer it with. */
        const val OUTCOME_NOTIFICATIONS_OFF = "NOTIFICATIONS_OFF"
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
