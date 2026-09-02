package com.sza.fastmediasorter.data.cloud

import android.app.Activity

/**
 * OneDrive provider contract (strategic S0403).
 *
 * Seamed out of the concrete Microsoft Graph client so a flavor that does not link MSAL still
 * compiles: `src/cloudSdk` supplies [OneDriveRestClientImpl], `src/cloudNoSdk` supplies
 * [NoOpOneDriveRestClient]. AGP mounts exactly one of the two per flavor.
 *
 * Only two members sit beyond [CloudStorageClient], because only two are called from `src/main`.
 * The MSAL-typed `handleAuthenticationResult` deliberately does NOT appear here - its single caller
 * is the auth coordinator, which moves into `src/cloudSdk` with the implementation, so keeping it
 * off the contract is what stops `IAuthenticationResult` leaking back into the shared source set.
 */
interface OneDriveRestClient : CloudStorageClient {

    /** Launch the interactive MSAL sign-in; the result arrives through [callback]. */
    fun signIn(activity: Activity, callback: (AuthResult) -> Unit)

    /** Account label for the active session, or `null` when unauthenticated. */
    fun getAccountEmail(): String?
}
