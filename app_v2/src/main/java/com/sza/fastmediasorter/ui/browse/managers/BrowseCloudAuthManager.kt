package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveAuthPlugin
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages cloud authentication flow in BrowseActivity.
 * Handles sign-in launch and result processing with user feedback.
 */
class BrowseCloudAuthManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    @Suppress("unused") private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient,
    private val callbacks: CloudAuthCallbacks
) {
    
    interface CloudAuthCallbacks {
        fun onAuthenticationSuccess()
        fun onAuthenticationFailure()
    }

    private var isDropboxAuthenticating = false

    /** S0200 Phase 04c: launch Credential Manager sign-in via the identity domain. */
    fun launchGoogleSignIn() {
        coroutineScope.launch {
            try {
                val activity = context as? Activity
                if (activity == null) {
                    Timber.e("launchGoogleSignIn: context is not an Activity — Credential Manager requires one")
                    callbacks.onAuthenticationFailure()
                    return@launch
                }
                val identityRepo: GoogleIdentityRepository = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BrowseIdentityEntryPoint::class.java
                ).identityRepository()
                val result = identityRepo.signInPrimary(activity, GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES)
                when (result) {
                    is IdentitySignInResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.google_drive_signed_in, result.account.email),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationSuccess()
                    }
                    IdentitySignInResult.Cancelled -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.google_sign_in_cancelled),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                    is IdentitySignInResult.Failed -> {
                        Timber.e(result.cause, "Google Sign-In failed via identity domain: ${result.reason}")
                        Toast.makeText(
                            context,
                            context.getString(R.string.google_drive_authentication_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch Google Sign-In")
                Toast.makeText(
                    context,
                    context.getString(R.string.google_drive_authentication_failed),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onAuthenticationFailure()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BrowseIdentityEntryPoint {
        fun identityRepository(): GoogleIdentityRepository
    }
    
    fun launchDropboxSignIn() {
        coroutineScope.launch {
            try {
                val activity = context as? android.app.Activity
                    ?: return@launch callbacks.onAuthenticationFailure().also {
                        Timber.e("launchDropboxSignIn: context is not an Activity")
                    }
                dropboxClient.startPkceAuthentication(activity, context.getString(R.string.dropbox_app_key))
                isDropboxAuthenticating = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start Dropbox authentication")
                Toast.makeText(
                    context,
                    context.getString(R.string.dropbox_authentication_failed),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onAuthenticationFailure()
            }
        }
    }
    
    fun launchOneDriveSignIn() {
        if (context is android.app.Activity) {
            oneDriveClient.signIn(context) { result ->
               coroutineScope.launch {
                   when (result) {
                       is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                           Toast.makeText(
                               context,
                               context.getString(R.string.onedrive_signed_in, result.accountName),
                               Toast.LENGTH_SHORT
                           ).show()
                           callbacks.onAuthenticationSuccess()
                       }
                       is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                           Timber.e("OneDrive sign-in failed: ${result.message}")
                           Toast.makeText(
                               context, 
                               context.getString(R.string.onedrive_authentication_failed),
                               Toast.LENGTH_SHORT
                           ).show()
                           callbacks.onAuthenticationFailure()
                       }
                       is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                           // User cancelled, no toast needed typically
                       }
                   }
               }
            }
        } else {
            Timber.e("Available context is not an Activity, cannot launch OneDrive sign-in")
            callbacks.onAuthenticationFailure()
        }
    }
    
    fun onResume() {
        if (isDropboxAuthenticating) {
            coroutineScope.launch {
                val result = dropboxClient.finishAuthentication()
                when (result) {
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.dropbox_signed_in, result.accountName),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationSuccess()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.dropbox_authentication_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_dropbox_auth_cancelled),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                }
                isDropboxAuthenticating = false
            }
        }
    }

}
