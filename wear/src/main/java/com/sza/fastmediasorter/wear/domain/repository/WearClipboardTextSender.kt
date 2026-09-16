package com.sza.fastmediasorter.wear.domain.repository

/**
 * S3109: carries this watch's text clipboard to the paired phone.
 *
 * Declared in the domain and implemented in `data` because the delivery needs the Data Layer, which
 * is not something a use case may reach for.
 */
interface WearClipboardTextSender {

    /** Sends [text] and waits for the phone's answer. */
    suspend fun send(text: String): WearClipboardTextOutcome
}

/**
 * What one clipboard round trip produced.
 *
 * A sealed type rather than a boolean, for the reason S1802 recorded on the log report: the screen
 * has to tell the wearer why nothing arrived, and a boolean collapses exactly the distinctions the
 * wording needs.
 */
sealed interface WearClipboardTextOutcome {

    /** The phone put the text on its clipboard and said so. */
    data object Delivered : WearClipboardTextOutcome

    /** The clipboard held nothing to send. */
    data object NothingToSend : WearClipboardTextOutcome

    /** No node is connected - the phone is absent, asleep or out of range. */
    data object NoConnectedPhone : WearClipboardTextOutcome

    /** The message left the watch, but nothing answered within the timeout. */
    data object PhoneDidNotAnswer : WearClipboardTextOutcome

    /** The phone answered and declined, carrying a reason the UI can phrase. */
    data class PhoneRefused(val reason: String) : WearClipboardTextOutcome
}
