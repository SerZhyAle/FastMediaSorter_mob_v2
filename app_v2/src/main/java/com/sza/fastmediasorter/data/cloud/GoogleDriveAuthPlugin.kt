package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
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
    private val identityRepository: GoogleIdentityRepository
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE

    private val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _results = MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)
    override val results: SharedFlow<AuthResult> = _results.asSharedFlow()

    override fun startInteractiveSignIn(activity: Activity) {
        Timber.d("S0243: GoogleDriveAuthPlugin.startInteractiveSignIn")
        pluginScope.launch {
            try {
                val result = identityRepository.signInPrimary(activity, DRIVE_SIGN_IN_SCOPES)
                when (result) {
                    is IdentitySignInResult.Success -> {
                        Timber.i("GoogleDriveAuthPlugin: signInPrimary succeeded for email=${result.account.email}")
                        _results.tryEmit(
                            AuthResult.Success(
                                accountName = result.account.email,
                                credentialsJson = result.account.email
                            )
                        )
                    }
                    IdentitySignInResult.Cancelled -> {
                        Timber.i("GoogleDriveAuthPlugin: signInPrimary cancelled by user")
                        _results.tryEmit(AuthResult.Cancelled)
                    }
                    is IdentitySignInResult.Failed -> {
                        Timber.e(result.cause, "GoogleDriveAuthPlugin: signInPrimary failed: ${result.reason}")
                        _results.tryEmit(
                            AuthResult.Error("Google sign-in failed: ${result.reason}")
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "GoogleDriveAuthPlugin: signInPrimary threw")
                _results.tryEmit(
                    AuthResult.Error(
                        "Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}"
                    )
                )
            }
        }
    }

    override suspend fun onIntentResult(data: Intent?) {
        // No-op: Credential Manager does not use Activity result.
    }

    override suspend fun onResume() {
        // No-op: Credential Manager produces its result inside the launched coroutine.
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

        @Suppress("unused")
        private val DEFAULT_FAILURE = IdentityFailureReason.UnknownError
    }
}
