package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent

/**
 * Interface that encapsulates the interactive (UI-based) authentication flow 
 * for a specific cloud provider.
 *
 * This allows unifying the logic inside Activities/Fragments so they don't have
 * to know about `GoogleSignIn`, `Auth.startOAuth2Authentication`, or MSAL directly.
 */
interface InteractiveCloudAuthenticator {
    
    /** The cloud provider this authenticator handles. */
    val provider: CloudProvider

    /**
     * Starts the interactive sign-in flow.
     * This may launch an Intent (Google Drive), open a browser (Dropbox), 
     * or show a custom UI (MSAL for OneDrive).
     *
     * @param activity The Activity context required to start the flow.
     */
    fun startInteractiveSignIn(activity: Activity)

    /**
     * Handles the Intent result of an authentication flow (e.g. Google Drive).
     *
     * @param data The Intent data from the ActivityResult.
     * @return [AuthResult] if the result was handled, or null if it's not relevant to this provider.
     */
    suspend fun processIntentResult(data: Intent?): AuthResult?

    /**
     * Handles the `onResume` lifecycle event, if applicable 
     * (e.g. Dropbox returning from a browser intent).
     *
     * @return [AuthResult] if an authentication result was processed, or null otherwise.
     */
    suspend fun handleResume(): AuthResult?

    /**
     * Returns a result if the auth flow failed synchronously before any Activity transition
     * (e.g. MSAL certificate hash mismatch causing an immediate onError callback).
     * Default implementation returns null (most providers don't fail before leaving the screen).
     */
    fun consumeImmediateResult(): AuthResult? = null
}
