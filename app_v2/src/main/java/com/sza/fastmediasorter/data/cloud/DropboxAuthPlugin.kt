package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.dropbox.core.android.Auth
import com.sza.fastmediasorter.R
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles the interactive sign-in flow for Dropbox using its SDK.
 */
class DropboxAuthPlugin @Inject constructor(
    private val client: DropboxClient
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.DROPBOX

    // Need a flag to track if we started auth so we know when to check in onResume
    private var isAuthInProgress = false

    override fun startInteractiveSignIn(activity: Activity) {
        val appKey = activity.getString(R.string.dropbox_app_key)
        Timber.d("DropboxAuthPlugin: starting OAuth2 authentication, appKey=${appKey.take(6)}...")
        try {
            Auth.startOAuth2Authentication(activity, appKey)
            isAuthInProgress = true
            Timber.d("DropboxAuthPlugin: OAuth2 authentication started successfully")
        } catch (e: Exception) {
            Timber.e(e, "DropboxAuthPlugin: failed to start OAuth2 authentication")
            throw e  // re-throw so UnifiedCloudAuthManager.startInteractiveSignIn catches it
        }
    }

    override suspend fun processIntentResult(data: Intent?): AuthResult? {
        // Dropbox does not use ActivityResult for the initial login
        return null
    }

    override suspend fun handleResume(): AuthResult? {
        if (!isAuthInProgress) {
            return null
        }

        isAuthInProgress = false
        Timber.d("DropboxAuthPlugin.handleResume: finishing authentication")
        return try {
            val result = client.finishAuthentication()
            Timber.d("DropboxAuthPlugin.handleResume: result=$result")
            result
        } catch (e: Exception) {
            Timber.e(e, "DropboxAuthPlugin.handleResume: finishAuthentication failed")
            AuthResult.Error("Dropbox authentication failed: ${e.message}")
        }
    }
}
