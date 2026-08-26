package com.sza.fastmediasorter.wear.domain.model

/**
 * What happened to one file of a batch.
 *
 * One record per file rather than one verdict per run: a batch is expected to succeed partly, and
 * the user has to be able to read which item failed without opening a system log.
 */
data class WearFileOperationResult(
    val fileName: String,
    val outcome: WearFileOperationOutcome,
    /** The name the file ended up with when a conflict forced a suffix; null when nothing renamed it. */
    val finalName: String? = null
)

/** Why one file of a batch ended the way it did. */
enum class WearFileOperationOutcome {
    SUCCEEDED,

    /** The file's storage class does not allow this operation - it was never touched. */
    REFUSED_UNSUPPORTED,

    /** Over the transfer channel's ceiling; refused before any byte was read. */
    REFUSED_TOO_LARGE,

    /** The paired phone did not answer, so a move left its source in place. */
    PHONE_UNREACHABLE,

    /** The phone was in the foreground and put the file on screen there and then. */
    OPENED_ON_PHONE,

    /** The phone was not in the foreground, so it posted a notification the user can tap. */
    NOTIFIED_ON_PHONE,

    /**
     * The phone could neither show the file nor announce it - its notifications are off.
     *
     * Kept apart from [PHONE_UNREACHABLE]: the phone answered, so telling the user to bring it closer
     * would send them after the wrong fix (strategic 11 criterion 9).
     */
    REFUSED_PHONE_NOTIFICATIONS_OFF,

    FAILED,

    CANCELLED
}
