package com.sza.fastmediasorter.ui.cloudfolders

import android.content.Intent

/**
 * Inert twin of the `cloudSdk` recovery-intent extractor (strategic S0403).
 *
 * A build without Play Services auth never raises `UserRecoverableAuthException`, so there is never
 * a consent intent to hand back and the picker falls through to its ordinary error branch.
 */
internal object GoogleAuthRecoveryIntent {

    // The signature is the cloudSdk twin's, shared by FQCN with src/main callers, so the unread
    // parameter and the constant body are the contract of an inert twin, not dead code.
    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
    fun from(error: Throwable): Intent? = null
}
