package com.sza.fastmediasorter.domain.model

/**
 * What one clipboard push produced.
 *
 * A sealed type rather than a boolean, for the reason S1802 recorded on the log report: the screen
 * has to tell the owner why nothing arrived, and a boolean collapses exactly the distinctions the
 * wording needs.
 */
sealed interface PhoneClipboardSendOutcome {

    /** The watch put the text on its clipboard and said so. */
    data object Delivered : PhoneClipboardSendOutcome

    /** This phone's clipboard held nothing to send. */
    data object NothingToSend : PhoneClipboardSendOutcome

    /** No node is connected - the watch is absent, asleep or out of range. */
    data object NoConnectedWatch : PhoneClipboardSendOutcome

    /** The message left the phone, but nothing answered within the timeout. */
    data object WatchDidNotAnswer : PhoneClipboardSendOutcome

    /** The watch answered and declined, carrying a reason the UI can phrase. */
    data class WatchRefused(val reason: String) : PhoneClipboardSendOutcome
}
