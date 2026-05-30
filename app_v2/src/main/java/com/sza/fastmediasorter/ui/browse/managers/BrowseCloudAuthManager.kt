package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.AuthResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveInteractiveSignInCoordinator
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
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
    private var isGoogleDriveAuthenticating = false

    /** S0294: reuse the shared capability-based Google Drive auth router for Browse / Player. */
    fun launchGoogleSignIn() {
        coroutineScope.launch {
            try {
                val activity = context as? Activity
                if (activity == null) {
                    Timber.e("launchGoogleSignIn: context is not an Activity - interactive Google auth requires one")
                    callbacks.onAuthenticationFailure()
                    return@launch
                }
                val signInCoordinator = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BrowseIdentityEntryPoint::class.java
                ).interactiveSignInCoordinator()

                when (val startResult = signInCoordinator.start(activity)) {
                    is GoogleDriveInteractiveSignInCoordinator.StartResult.Immediate -> {
                        handleGoogleAuthResult(startResult.result)
                    }

                    GoogleDriveInteractiveSignInCoordinator.StartResult.AwaitResume -> {
                        isGoogleDriveAuthenticating = true
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch Google Sign-In")
                Toast.makeText(
                    context,
                    context.getString(R.string.s0294_google_drive_browser_launch_failed_message),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onAuthenticationFailure()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BrowseIdentityEntryPoint {
        fun interactiveSignInCoordinator(): GoogleDriveInteractiveSignInCoordinator
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
        if (isGoogleDriveAuthenticating) {
            coroutineScope.launch {
                val result = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BrowseIdentityEntryPoint::class.java
                ).interactiveSignInCoordinator().consumePendingInteractiveResult()

                if (result != null) {
                    isGoogleDriveAuthenticating = false
                    handleGoogleAuthResult(result)
                }
            }
        }

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

    private fun handleGoogleAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.google_drive_signed_in, result.accountName),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onAuthenticationSuccess()
            }

            is AuthResult.Cancelled -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.google_sign_in_cancelled),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onAuthenticationFailure()
            }

            is AuthResult.Error -> {
                val message = result.message.ifBlank {
                    context.getString(R.string.s0294_google_drive_browser_auth_failed_message)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                callbacks.onAuthenticationFailure()
            }
        }
    }

}
