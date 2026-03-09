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
            Timber.e(e, "Google Sign-In failed: statusCode=${e.statusCode} ($statusName)")
            if (e.statusCode == 12501) {
                // SIGN_IN_CANCELLED — user dismissed the dialog intentionally
                Timber.i("Google Sign-In: user cancelled")
                AuthResult.Cancelled
            } else {
                val friendlyMsg = mapGoogleSignInError(e.statusCode)
                AuthResult.Error(friendlyMsg)
            }
        } catch (e: Exception) {
            val msg = "Google Sign-In unexpected error: ${e.javaClass.simpleName}: ${e.message}"
            Timber.e(e, msg)
            AuthResult.Error(msg)
        }
    }

    private fun mapGoogleSignInError(statusCode: Int): String = when (statusCode) {
        10 -> // DEVELOPER_ERROR: SHA-1 not registered or package name mismatch
            "Google Sign-In configuration error (code 10).\n\n" +
            "The app's signing certificate is not registered in Google Cloud Console.\n" +
            "Register SHA-1 fingerprint for package \"com.sza.fastmediasorter\" at " +
            "console.cloud.google.com → APIs & Services → Credentials → OAuth 2.0 Client IDs, " +
            "then re-download google-services.json."
        4 -> // SIGN_IN_REQUIRED
            "Google account sign-in required. Please try again."
        5 -> // INVALID_ACCOUNT
            "Invalid Google account. Please choose a different account."
        7 -> // NETWORK_ERROR
            "Network error. Check your internet connection and try again."
        8 -> // INTERNAL_ERROR
            "Google Sign-In internal error. Please try again later."
        12501 -> // SIGN_IN_CANCELLED
            "Sign-in was cancelled."
        12502 -> // SIGN_IN_CURRENTLY_IN_PROGRESS
            "Sign-in already in progress. Please wait."
        16 -> // API_NOT_CONNECTED
            "Google Play Services not connected. Check if Google Play Services is up to date."
        else -> {
            val statusName = CommonStatusCodes.getStatusCodeString(statusCode)
            "Google Sign-In failed (code $statusCode: $statusName). Please try again."
        }
    }

    override suspend fun handleResume(): AuthResult? {
        // Google Drive uses ActivityResult, not onResume
        return null
    }
}
