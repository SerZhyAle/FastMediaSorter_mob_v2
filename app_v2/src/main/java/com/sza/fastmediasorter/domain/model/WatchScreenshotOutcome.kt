package com.sza.fastmediasorter.domain.model

/**
 * S3110: what one request for a picture of the watch's screen produced.
 *
 * A sealed type rather than a boolean, for the reason [PhoneClipboardSendOutcome] gives: the screen
 * has to tell the owner why no picture arrived, and "the watch app is closed" is the one answer they
 * can act on.
 */
sealed interface WatchScreenshotOutcome {

    /** The watch photographed its screen and sent the file; it arrives on the phone's file route. */
    data class Captured(val fileName: String) : WatchScreenshotOutcome

    /** No node is connected - the watch is absent, asleep or out of range. */
    data object NoConnectedWatch : WatchScreenshotOutcome

    /** The request left the phone, but nothing answered within the timeout. */
    data object WatchDidNotAnswer : WatchScreenshotOutcome

    /** Nothing was on the watch's screen to photograph, because the app was not open there. */
    data object WatchAppNotOpen : WatchScreenshotOutcome

    /** The watch answered and declined for some other reason the UI can phrase. */
    data class WatchRefused(val reason: String) : WatchScreenshotOutcome
}
