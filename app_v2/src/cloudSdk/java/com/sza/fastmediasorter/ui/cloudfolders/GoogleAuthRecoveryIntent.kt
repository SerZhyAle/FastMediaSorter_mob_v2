package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Intent
import com.google.android.gms.auth.UserRecoverableAuthException

/**
 * Extracts the user-consent recovery intent Play Services attaches to an authorization failure
 * (strategic S0403).
 *
 * `GoogleDriveFolderPickerViewModel` used to catch `UserRecoverableAuthException` directly in three
 * places, which was its only tie to a proprietary SDK. Narrowing that to one throwable-to-intent
 * question keeps the whole picker in `src/main`, where the `cloudNoSdk` twin under the same FQCN
 * simply never finds a recovery intent.
 */
internal object GoogleAuthRecoveryIntent {

    /** The consent intent for [error], or null when it is not a recoverable authorization failure. */
    fun from(error: Throwable): Intent? = (error as? UserRecoverableAuthException)?.intent
}
