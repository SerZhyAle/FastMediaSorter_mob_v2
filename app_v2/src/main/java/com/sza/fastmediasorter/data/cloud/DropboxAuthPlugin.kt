package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles the interactive sign-in flow for Dropbox using its SDK.
 */
class DropboxAuthPlugin @Inject constructor(
    private val client: DropboxClient
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.DROPBOX

    private val _results = MutableSharedFlow<AuthResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val results: SharedFlow<AuthResult> = _results.asSharedFlow()

    // Need a flag to track if we started auth so we know when to check in onResume
    private var isAuthInProgress = false

    override fun startInteractiveSignIn(activity: Activity) {
        Timber.d("S0243: DropboxAuthPlugin.startInteractiveSignIn")
        val appKey = activity.getString(R.string.dropbox_app_key)
        Timber.d("DropboxAuthPlugin: starting OAuth2 PKCE authentication, appKey=${appKey.take(6)}...")
        try {
            client.startPkceAuthentication(activity, appKey)
            isAuthInProgress = true
            Timber.d("DropboxAuthPlugin: OAuth2 PKCE authentication started successfully")
        } catch (e: Exception) {
            Timber.e(e, "DropboxAuthPlugin: failed to start OAuth2 PKCE authentication")
            throw e  // re-throw so UnifiedCloudAuthManager.startInteractiveSignIn catches it
        }
    }

    override suspend fun onIntentResult(data: Intent?) {
        // No-op: Dropbox does not use Activity result.
    }

    override suspend fun onResume() {
        if (!isAuthInProgress) {
            return
        }

        isAuthInProgress = false
        Timber.d("DropboxAuthPlugin.onResume: finishing authentication")
        try {
            val result = client.finishAuthentication()
            Timber.d("DropboxAuthPlugin.onResume: result=$result")
            _results.tryEmit(result)
        } catch (e: Exception) {
            Timber.e(e, "DropboxAuthPlugin.onResume: finishAuthentication failed")
            _results.tryEmit(AuthResult.Error("Dropbox authentication failed: ${e.message}"))
        }
    }
}
