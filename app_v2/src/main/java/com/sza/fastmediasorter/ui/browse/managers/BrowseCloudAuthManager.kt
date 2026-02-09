package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
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
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient,
    private val googleSignInLauncher: ActivityResultLauncher<Intent>,
    private val callbacks: CloudAuthCallbacks
) {
    
    interface CloudAuthCallbacks {
        fun onAuthenticationSuccess()
        fun onAuthenticationFailure()
    }
    
    private var isDropboxAuthenticating = false
    
    fun launchGoogleSignIn() {
        coroutineScope.launch {
            try {
                val signInIntent = googleDriveClient.getSignInIntent()
                googleSignInLauncher.launch(signInIntent)
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
    
    fun handleGoogleSignInResult(data: Intent?) {
        coroutineScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                
                // Update Google Drive client with new credentials
                val authResult = googleDriveClient.handleSignInResult(account)
                
                when (authResult) {
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.google_drive_signed_in, account.email ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationSuccess()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                        Timber.e("Failed to update Google Drive credentials: ${authResult.message}")
                        Toast.makeText(
                            context,
                            context.getString(R.string.google_drive_authentication_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                    com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.google_drive_authentication_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                }
            } catch (e: ApiException) {
                Timber.e(e, "Google Sign-In failed: ${e.statusCode}")
                Toast.makeText(
                    context,
                    context.getString(R.string.google_drive_authentication_failed),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onAuthenticationFailure()
            }
        }
    }
    
    fun launchDropboxSignIn() {
        coroutineScope.launch {
            try {
                // Use simple OAuth2 authentication without explicit scopes
                // Scopes are configured in Dropbox App Console
                com.dropbox.core.android.Auth.startOAuth2Authentication(
                    context,
                    context.getString(R.string.dropbox_app_key)
                )
                isDropboxAuthenticating = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start Dropbox authentication")
                Toast.makeText(
                    context,
                    context.getString(R.string.dropbox_authentication_failed) + ": ${e.message}",
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
                               context.getString(R.string.onedrive_authentication_failed) + ": ${result.message}",
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
                            context.getString(R.string.dropbox_authentication_failed) + ": ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        callbacks.onAuthenticationFailure()
                    }
                    is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                        // User cancelled or auth didn't complete
                        // Don't show error toast here as it might just be a normal resume
                    }
                }
                isDropboxAuthenticating = false
            }
        }
    }
}
