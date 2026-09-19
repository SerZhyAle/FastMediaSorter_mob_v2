package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection

/**
 * S3108: carries the finished system-information report to the paired phone.
 *
 * Declared in the domain and implemented in `data` because the rendering needs this module's own
 * string resources and the delivery needs the Data Layer, and neither belongs in a use case.
 */
interface WearSystemInfoReportSender {

    /** Renders [sections] to text, sends it, and waits for the phone's answer. */
    suspend fun send(sections: List<WearSystemInfoSection>): WearSystemInfoReportOutcome
}

/**
 * What one report round trip produced.
 *
 * A sealed type rather than a boolean, for the reason S1802 recorded on the log report: the screen
 * has to tell the user why nothing arrived, and a boolean collapses exactly the distinctions the
 * wording needs. A silently non-working button is what the strategic risk list names first.
 */
sealed interface WearSystemInfoReportOutcome {

    /** The phone stored the report and said so. */
    data object Delivered : WearSystemInfoReportOutcome

    /** No node is connected - the phone is absent, asleep or out of range. */
    data object NoConnectedPhone : WearSystemInfoReportOutcome

    /** The message left the watch, but nothing answered within the timeout. */
    data object PhoneDidNotAnswer : WearSystemInfoReportOutcome

    /** The phone answered and declined, carrying a reason the UI can phrase. */
    data class PhoneRefused(val reason: String) : WearSystemInfoReportOutcome
}
