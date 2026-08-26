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
    val outcome: WearFileTransferOutcome = WearFileTransferOutcome.QUEUED
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
    val openNow: Boolean = false
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

    FAILED
}

/** S1861: the outcome of one watch -> phone file, with the path it landed on when it landed. */
data class WearFileReceiveResult(
    val outcome: WearFileReceiveOutcome,
    val savedPath: String? = null
)
