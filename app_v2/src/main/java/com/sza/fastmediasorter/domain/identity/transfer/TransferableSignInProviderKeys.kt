package com.sza.fastmediasorter.domain.identity.transfer

/**
 * Provider keys used inside a [TransferableSignInRecord] (S2101).
 *
 * These values are part of the persisted format. A build reading a record written by an older or
 * newer build matches on these literals, so renaming one silently orphans every record already
 * stored on a user's device while passing every compile-time check.
 */
object TransferableSignInProviderKeys {

    /** The primary Google identity bound through Credential Manager - identity envelope only. */
    const val GOOGLE_PRIMARY: String = "google_primary"

    /** The Google Drive browser OAuth fallback, which stores its own refresh token. */
    const val GOOGLE_DRIVE_BROWSER: String = "google_drive_browser"

    /** Dropbox, which stores its own refresh token. */
    const val DROPBOX: String = "dropbox"
}
