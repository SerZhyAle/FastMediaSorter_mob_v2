package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles the interactive sign-in flow for OneDrive using MSAL.
 */
class OneDriveAuthPlugin @Inject constructor(
    private val client: OneDriveRestClient
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.ONEDRIVE

    private val _results = MutableSharedFlow<AuthResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val results: SharedFlow<AuthResult> = _results.asSharedFlow()

    override fun startInteractiveSignIn(activity: Activity) {
        Timber.d("S0243: OneDriveAuthPlugin.startInteractiveSignIn")
        Timber.d("OneDriveAuthPlugin: starting interactive sign-in")
        // Client encapsulates the MSAL UI flow and invokes the callback
        client.signIn(activity) { result ->
            Timber.d("OneDriveAuthPlugin: signIn callback received: $result")
            _results.tryEmit(result)
        }
    }

    override suspend fun onIntentResult(data: Intent?) {
        // No-op: MSAL routes Activity result internally.
    }

    override suspend fun onResume() {
        // No-op: MSAL callback emits the result directly.
    }
}
