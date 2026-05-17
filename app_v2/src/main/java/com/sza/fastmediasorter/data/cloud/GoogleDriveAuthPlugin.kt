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
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles the interactive sign-in flow for Google Drive via the identity domain
 * (Credential Manager — S0200 Phase 04b).
 *
 * The `InteractiveCloudAuthenticator` contract still surfaces `startInteractiveSignIn(activity)`
 * as a synchronous void call (Dropbox / OneDrive plugins keep using it). This plugin bridges
 * that contract to the suspending `identityRepository.signInPrimary(..)` by launching a
 * background coroutine on a `SupervisorJob` scope. The result observable to the UI is the
 * identity repository's `state` Flow (consumed by Phase 06's Settings card) — there is no
 * Intent or `processIntentResult` round-trip anymore.
 */
class GoogleDriveAuthPlugin @Inject constructor(
    @Suppress("unused") private val client: GoogleDriveRestClient,
    private val identityRepository: GoogleIdentityRepository
) : InteractiveCloudAuthenticator {

    override val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE

    private val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile private var lastImmediateFailure: AuthResult.Error? = null

    override fun startInteractiveSignIn(activity: Activity) {
        lastImmediateFailure = null
        pluginScope.launch {
            try {
                val result = identityRepository.signInPrimary(activity, DRIVE_SIGN_IN_SCOPES)
                when (result) {
                    is IdentitySignInResult.Success ->
                        Timber.i("GoogleDriveAuthPlugin: signInPrimary succeeded for email=${result.account.email}")
                    IdentitySignInResult.Cancelled ->
                        Timber.i("GoogleDriveAuthPlugin: signInPrimary cancelled by user")
                    is IdentitySignInResult.Failed ->
                        Timber.e(result.cause, "GoogleDriveAuthPlugin: signInPrimary failed: ${result.reason}")
                }
            } catch (e: Exception) {
                Timber.e(e, "GoogleDriveAuthPlugin: signInPrimary threw")
                lastImmediateFailure = AuthResult.Error(
                    "Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    /**
     * S0200 Phase 04b: Credential Manager does not produce an Intent result. Returns null —
     * the contract default for plugins that do not use the activity-result channel.
     * The contract method itself stays on the interface so Dropbox / OneDrive plugins
     * still satisfy it.
     */
    @Suppress("UNUSED_PARAMETER")
    override suspend fun processIntentResult(data: Intent?): AuthResult? = null

    override suspend fun handleResume(): AuthResult? = null

    override fun consumeImmediateResult(): AuthResult? {
        val snapshot = lastImmediateFailure
        lastImmediateFailure = null
        return snapshot
    }

    companion object {
        // Initial Drive scope set per strategic §3.1 — DRIVE + readonly + identity for the
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
