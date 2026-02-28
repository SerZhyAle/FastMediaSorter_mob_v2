package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.dropbox.core.android.Auth
import com.sza.fastmediasorter.R
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
        Auth.startOAuth2Authentication(activity, appKey)
        isAuthInProgress = true
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
        return try {
            client.finishAuthentication()
        } catch (e: Exception) {
            AuthResult.Error("Dropbox authentication failed: ${e.message}")
        }
    }
}
