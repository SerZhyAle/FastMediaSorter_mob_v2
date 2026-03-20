package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.security.MessageDigest
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
        logDebugGoogleSignInEnvironment(activity)

        // Sign out first to clear stale cached state that causes immediate silent failures
        val signInOptions = client.getSignInOptions()
        val gsiClient = GoogleSignIn.getClient(activity, signInOptions)
        gsiClient.signOut().addOnCompleteListener {
            Timber.d("Google Sign-In: signOut before interactive flow completed (success=${it.isSuccessful})")
            val signInIntent = gsiClient.signInIntent
            if (BuildConfig.DEBUG) {
                Timber.d(
                    "Google Sign-In debug: launch intent action=%s component=%s package=%s",
                    signInIntent.action,
                    signInIntent.component?.flattenToShortString(),
                    signInIntent.`package`
                )
            }
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
                val friendlyMsg = mapGoogleSignInError(e.statusCode, data)
                AuthResult.Error(friendlyMsg)
            }
        } catch (e: Exception) {
            val msg = "Google Sign-In unexpected error: ${e.javaClass.simpleName}: ${e.message}"
            Timber.e(e, msg)
            AuthResult.Error(msg)
        }
    }

    private fun mapGoogleSignInError(statusCode: Int, data: Intent?): String = when (statusCode) {
        10 -> // DEVELOPER_ERROR: SHA-1 not registered or package name mismatch
            "Google Sign-In configuration error (code 10).\n\n" +
            "Current package: ${BuildConfig.APPLICATION_ID}.\n" +
            "Current build type/flavor: ${BuildConfig.BUILD_TYPE}/${BuildConfig.FLAVOR}.\n" +
            "Ensure this package + SHA-1 pair is registered in Google Cloud Console (OAuth Android client)."
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

    private fun logDebugGoogleSignInEnvironment(activity: Activity) {
        if (!BuildConfig.DEBUG) return

        val gmsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        val gmsStatusName = when (gmsStatus) {
            ConnectionResult.SUCCESS -> "SUCCESS"
            ConnectionResult.SERVICE_MISSING -> "SERVICE_MISSING"
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "SERVICE_VERSION_UPDATE_REQUIRED"
            ConnectionResult.SERVICE_DISABLED -> "SERVICE_DISABLED"
            ConnectionResult.SERVICE_INVALID -> "SERVICE_INVALID"
            else -> "CODE_$gmsStatus"
        }

        val sha1 = runCatching { computeSigningSha1(activity) }
            .getOrElse { "error:${it.javaClass.simpleName}" }

        val webClientId = runCatching { activity.getString(R.string.google_web_client_id) }
            .getOrElse { "error:${it.javaClass.simpleName}" }

        Timber.i(
            "Google Sign-In debug: package=%s buildType=%s flavor=%s webClientId=%s gms=%s(%d) sha1=%s",
            activity.packageName,
            BuildConfig.BUILD_TYPE,
            BuildConfig.FLAVOR,
            webClientId,
            gmsStatusName,
            gmsStatus,
            sha1
        )
    }

    private fun computeSigningSha1(activity: Activity): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo ?: return "unavailable"
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners ?: emptyArray()
            } else {
                signingInfo.signingCertificateHistory ?: emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager
                .getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
                .signatures ?: emptyArray()
        }

        val first = signatures.firstOrNull()
            ?: return "unavailable"

        val digest = MessageDigest.getInstance("SHA1").digest(first.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
