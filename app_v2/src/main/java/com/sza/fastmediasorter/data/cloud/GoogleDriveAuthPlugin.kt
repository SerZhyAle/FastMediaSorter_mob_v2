package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import javax.inject.Inject

/**
 * Handles the interactive sign-in flow for Google Drive using GoogleSignIn API.
 */
class GoogleDriveAuthPlugin @Inject constructor(
    private val client: GoogleDriveRestClient
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE

    companion object {
        const val RC_SIGN_IN = 9001
    }

    override fun startInteractiveSignIn(activity: Activity) {
        val signInIntent = client.getSignInIntent()
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override suspend fun processIntentResult(data: Intent?): AuthResult? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            client.handleSignInResult(account)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            AuthResult.Error("Google Sign-in failed (statusCode=${e.statusCode}): ${e.message}")
        }
    }

    override suspend fun handleResume(): AuthResult? {
        // Google Drive uses ActivityResult, not onResume
        return null
    }
}
