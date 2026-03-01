package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import timber.log.Timber
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
        // Sign out first to clear stale cached state that causes immediate silent failures
        val signInOptions = client.getSignInOptions()
        val gsiClient = GoogleSignIn.getClient(activity, signInOptions)
        gsiClient.signOut().addOnCompleteListener {
            Timber.d("Google Sign-In: signOut before interactive flow completed (success=${it.isSuccessful})")
            val signInIntent = gsiClient.signInIntent
            activity.startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override suspend fun processIntentResult(data: Intent?): AuthResult? {
        Timber.d("GoogleDriveAuthPlugin.processIntentResult: hasData=${data != null}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            Timber.i("Google Sign-In succeeded: email=${account?.email}")
            client.handleSignInResult(account)
        } catch (e: ApiException) {
            val statusName = CommonStatusCodes.getStatusCodeString(e.statusCode)
            val msg = "Google Sign-In failed: statusCode=${e.statusCode} ($statusName), message=${e.message}"
            Timber.e(e, msg)
            AuthResult.Error(msg)
        } catch (e: Exception) {
            val msg = "Google Sign-In unexpected error: ${e.javaClass.simpleName}: ${e.message}"
            Timber.e(e, msg)
            AuthResult.Error(msg)
        }
    }

    override suspend fun handleResume(): AuthResult? {
        // Google Drive uses ActivityResult, not onResume
        return null
    }
}
