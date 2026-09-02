package com.sza.fastmediasorter.core.share

import androidx.annotation.StringRes

/**
 * What happened to one «Send to..» request (S1884 ADR-4).
 *
 * Replaces the boolean the handler contract used to return. That boolean meant "an Activity was
 * launched", which a receiver doing its own work - the paired watch - cannot express: it knows
 * whether the file actually opened, and the dispatcher had no way to hear it.
 */
sealed interface ShareTargetOutcome {

    /**
     * An Activity was started and the request is now out of this app's hands. Every package-backed
     * receiver ends here on success: whether the user completes the send in the other app is
     * unknowable from here, so nothing is claimed about it.
     */
    data object Launched : ShareTargetOutcome

    /** The receiver did the work itself and knows it succeeded. [message] is shown when present. */
    data class Delivered(val message: String? = null) : ShareTargetOutcome

    /** The request failed. [messageRes] is shown when present, otherwise the generic failure text. */
    data class Failed(@get:StringRes val messageRes: Int? = null) : ShareTargetOutcome

    /** Nothing was attempted and nothing is to be said about it. */
    data object NotAttempted : ShareTargetOutcome
}

/**
 * Translates the launch-or-not boolean every package-backed receiver still speaks into the outcome
 * the dispatcher reads. Declared once so the eleven shipped handlers keep reporting exactly what
 * they reported before this contract widened (S1884 phase 01).
 */
fun Boolean.asLaunchOutcome(): ShareTargetOutcome =
    if (this) ShareTargetOutcome.Launched else ShareTargetOutcome.Failed()
