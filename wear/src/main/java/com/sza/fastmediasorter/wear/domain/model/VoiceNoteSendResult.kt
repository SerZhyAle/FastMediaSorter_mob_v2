package com.sza.fastmediasorter.wear.domain.model

/**
 * S1862: how one attempt to hand a note to the phone ended, in the shape the screen shows it.
 *
 * Section 11 criterion 6 requires success, an out-of-reach phone and a failure to be told apart
 * without reading a log. [PhoneUnreachable] is deliberately not a kind of [Failed]: it resolves by
 * itself when the link returns and is what the pending queue is keyed on, while a [Failed] note will
 * not fix itself by waiting.
 */
sealed interface VoiceNoteSendResult {

    data object Sent : VoiceNoteSendResult

    data object PhoneUnreachable : VoiceNoteSendResult

    /** Over the bridge ceiling of [WEAR_FILE_TRANSFER_MAX_BYTES]; retrying sends the same bytes. */
    data object TooLarge : VoiceNoteSendResult

    data class Failed(val reason: VoiceNoteSendFailureReason) : VoiceNoteSendResult
}

/**
 * A reason rather than a message, for the same purpose as VoiceRecordingErrorReason: the screen owns
 * the wording, so a string built down here could not be localized where it is displayed.
 */
enum class VoiceNoteSendFailureReason {

    /** The id addresses no note - it was removed between the request and this attempt. */
    NOTE_MISSING,

    /** The bridge refused or dropped out. The transport has already logged what it saw. */
    TRANSPORT_FAILED
}

/**
 * The single place the transport's outcome becomes a user-facing result. Kept as one function so a
 * new [WearFileSendOutcome] value breaks exactly one exhaustive `when` instead of several.
 *
 * No import: [WearFileSendOutcome] is declared in this same package, next to the ceiling both sides
 * of the bridge refuse above.
 */
fun WearFileSendOutcome.toVoiceNoteSendResult(): VoiceNoteSendResult = when (this) {
    WearFileSendOutcome.SENT,
    WearFileSendOutcome.QUEUED_ON_PHONE -> VoiceNoteSendResult.Sent
    WearFileSendOutcome.UNCONFIRMED,
    WearFileSendOutcome.PHONE_UNREACHABLE -> VoiceNoteSendResult.PhoneUnreachable
    WearFileSendOutcome.TOO_LARGE -> VoiceNoteSendResult.TooLarge
    WearFileSendOutcome.NO_DESTINATION,
    WearFileSendOutcome.FAILED -> VoiceNoteSendResult.Failed(VoiceNoteSendFailureReason.TRANSPORT_FAILED)
}
