package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handles the interactive sign-in flow for OneDrive using MSAL.
 */
class OneDriveAuthPlugin @Inject constructor(
    private val client: OneDriveRestClient
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.ONEDRIVE

    // Use a deferred to await the callback result since MSAL uses async callbacks
    private var authDeferred: CompletableDeferred<AuthResult?>? = null

    override fun startInteractiveSignIn(activity: Activity) {
        authDeferred = CompletableDeferred()
        // Client encapsulates the MSAL UI flow and invokes the callback
        client.signIn(activity) { result ->
            authDeferred?.complete(result)
        }
    }

    override suspend fun processIntentResult(data: Intent?): AuthResult? {
        // MSAL handles its own internal ActivityResult; we just wait for the callback in handleResume
        return null
    }

    override suspend fun handleResume(): AuthResult? {
        val deferred = authDeferred ?: return null
        
        // Wait for the MSAL callback to complete
        // Make sure we don't block forever if MSAL fails silently
        return try {
            val result = deferred.await()
            authDeferred = null // Reset state
            result
        } catch (e: Exception) {
            authDeferred = null
            AuthResult.Error("OneDrive MSAL flow failed: ${e.message}")
        }
    }
}
