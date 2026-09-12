package com.sza.fastmediasorter.wear.domain.model

import android.content.IntentSender

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
    val finalName: String? = null,
    /** The destination on the phone where the file landed or was queued; null when local or unconfirmed. */
    val destination: String? = null,
    /**
     * The system confirmation to show for [WearFileOperationOutcome.NEEDS_CONSENT]; null otherwise.
     *
     * Carried on the result rather than raised from the use case because the use case is domain and
     * returns a [kotlinx.coroutines.flow.Flow] - it has no Activity to start a dialog from. The
     * screen does, so the request travels up as data and the screen decides when to show it.
     */
    val consentRequest: IntentSender? = null,
    /**
     * The operation to run again after the owner confirms, when it is not the one that was asked for.
     *
     * A move whose transfer already succeeded has only its source left to remove, so repeating the
     * move would send the same bytes to the phone a second time. Null means "retry what was asked".
     */
    val retryAs: WearFileOperation? = null
)

/** Why one file of a batch ended the way it did. */
enum class WearFileOperationOutcome {
    SUCCEEDED,
    QUEUED_ON_PHONE,
    NO_DESTINATION,
    UNCONFIRMED,

    /** The file's storage class does not allow this operation - it was never touched. */
    REFUSED_UNSUPPORTED,

    /**
     * The row belongs to another app, and the owner has not answered the system's confirmation yet.
     *
     * Not a failure and not a refusal: the same call succeeds once the owner confirms, so the file
     * is left exactly as it was and the operation is retried rather than reported as lost.
     */
    NEEDS_CONSENT,

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

    /**
     * S2142: the file reached the phone and the «Send to..» action there is waiting for a tap.
     *
     * Its own outcome rather than [SUCCEEDED], because the send has not happened yet: strategic 6
     * question 5 settles that the watch reports the errand queued and never claims that an external
     * application received anything.
     */
    AWAITING_PHONE_ACTION,

    FAILED,

    CANCELLED
}
