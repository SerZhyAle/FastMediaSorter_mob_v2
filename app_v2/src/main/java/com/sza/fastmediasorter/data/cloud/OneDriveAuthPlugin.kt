package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
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
        Timber.d("OneDriveAuthPlugin: starting interactive sign-in")
        authDeferred = CompletableDeferred()
        // Client encapsulates the MSAL UI flow and invokes the callback
        client.signIn(activity) { result ->
            Timber.d("OneDriveAuthPlugin: signIn callback received: $result")
            authDeferred?.complete(result)
        }
    }

    override suspend fun processIntentResult(data: Intent?): AuthResult? {
        // MSAL handles its own internal ActivityResult; we just wait for the callback in handleResume
        return null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun handleResume(): AuthResult? {
        val deferred = authDeferred ?: return null

        // If the deferred is already completed (immediate failure before UI opened), return it
        if (deferred.isCompleted) {
            authDeferred = null
            val result = deferred.getCompleted()
            Timber.d("OneDriveAuthPlugin.handleResume: deferred already completed: $result")
            return result
        }

        // Wait for the MSAL callback to complete (timeout 3 min for user interaction)
        return try {
            val result = withTimeoutOrNull(180_000L) {
                deferred.await()
            }
            authDeferred = null
            if (result == null) {
                Timber.w("OneDriveAuthPlugin.handleResume: timed out waiting for MSAL")
                AuthResult.Error("OneDrive sign-in timed out")
            } else {
                Timber.d("OneDriveAuthPlugin.handleResume: result=$result")
                result
            }
        } catch (e: Exception) {
            authDeferred = null
            Timber.e(e, "OneDriveAuthPlugin.handleResume: exception")
            AuthResult.Error("OneDrive MSAL flow failed: ${e.message}")
        }
    }
}
