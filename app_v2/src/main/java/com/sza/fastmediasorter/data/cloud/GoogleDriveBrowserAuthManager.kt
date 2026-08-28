package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent

/** Raised when no browser can service the Google Drive authorization request. */
class GoogleDriveBrowserUnavailableException(message: String) : IllegalStateException(message)

/**
 * Browser-based Google Drive authorization contract (strategic S0403).
 *
 * The implementation drives AppAuth; `src/cloudSdk` supplies [GoogleDriveBrowserAuthManagerImpl],
 * `src/cloudNoSdk` supplies [NoOpGoogleDriveBrowserAuthManager]. AppAuth is Apache-2.0 and would
 * pass F-Droid on licence grounds, but Google Drive is a removed capability on foss, so the library
 * is off that flavor's classpath and the shared source set must not name it.
 *
 * All nine members are on the contract because all nine have a `src/main` call site.
 */
interface GoogleDriveBrowserAuthManager {

    /** Start the browser authorization flow. Throws [GoogleDriveBrowserUnavailableException]. */
    fun startInteractiveSignIn(activity: Activity)

    /** Finish the flow from the redirect intent delivered to the completion activity. */
    suspend fun completeAuthorizationIntent(intent: Intent): AuthResult

    /** Take the result parked by [completeAuthorizationIntent], clearing it. */
    fun consumePendingInteractiveResult(): AuthResult?

    /** True when a credential is loaded in memory. */
    fun hasActiveSession(): Boolean

    /** Account label of the active or stored credential, without activating it. */
    fun peekStoredAccountEmail(): String?

    /** True when a credential is persisted for [accountEmail], or for any account when null. */
    fun hasStoredCredentials(accountEmail: String? = null): Boolean

    /** Load the persisted credential into memory. False when there is nothing to load. */
    suspend fun ensureActiveFromStored(accountEmail: String? = null): Boolean

    /** Load a credential from its serialized blob. False when the blob is absent or malformed. */
    fun restoreCredentialBlob(credentialsJson: String): Boolean

    /** Refresh and return an access token, or null when the session cannot be refreshed. */
    suspend fun getFreshAccessToken(): String?
}
