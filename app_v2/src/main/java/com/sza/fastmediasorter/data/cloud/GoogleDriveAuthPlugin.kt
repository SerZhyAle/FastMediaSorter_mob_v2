package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.domain.identity.GoogleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Emits the terminal result of a Credential Manager sign-in attempt through [results].
 */
class GoogleDriveAuthPlugin @Inject constructor(
    @Suppress("unused") private val client: GoogleDriveRestClient,
    private val interactiveSignInCoordinator: GoogleDriveInteractiveSignInCoordinator
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE

    private val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _results = MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)
    override val results: SharedFlow<AuthResult> = _results.asSharedFlow()

    override fun startInteractiveSignIn(activity: Activity) {
        pluginScope.launch {
            when (val startResult = interactiveSignInCoordinator.start(activity)) {
                is GoogleDriveInteractiveSignInCoordinator.StartResult.Immediate -> {
                    _results.tryEmit(startResult.result)
                }

                GoogleDriveInteractiveSignInCoordinator.StartResult.AwaitResume -> {
                    Timber.d("GoogleDriveAuthPlugin: awaiting Google Drive browser auth completion on onResume")
                }
            }
        }
    }

    override suspend fun onIntentResult(data: Intent?) {
        // No-op: Credential Manager does not use Activity result.
    }

    override suspend fun onResume() {
        interactiveSignInCoordinator.consumePendingInteractiveResult()?.let { result ->
            _results.tryEmit(result)
        }
    }

    companion object {
        // Initial Drive scope set per strategic §3.1 - DRIVE + readonly + identity for the
        // Settings card email / avatar surface.
        val DRIVE_SIGN_IN_SCOPES: Set<GoogleScope> = setOf(
            GoogleScope.DRIVE,
            GoogleScope.DRIVE_READONLY,
            GoogleScope.EMAIL,
            GoogleScope.PROFILE,
            GoogleScope.OPENID
        )
    }
}
