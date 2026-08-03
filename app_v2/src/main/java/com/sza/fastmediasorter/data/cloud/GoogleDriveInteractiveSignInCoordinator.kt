package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.ui.cloudauth.GoogleDriveAuthResolutionTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveInteractiveSignInCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identityRepository: GoogleIdentityRepository,
    private val browserAuthManager: GoogleDriveBrowserAuthManager
) {

    sealed interface StartResult {
        data class Immediate(val result: AuthResult) : StartResult
        data object AwaitResume : StartResult
    }

    suspend fun start(activity: Activity): StartResult {
        return if (shouldUseBrowserAuth()) {
            startBrowserSignIn(activity)
        } else {
            StartResult.Immediate(runIdentitySignIn(activity))
        }
    }

    fun consumePendingInteractiveResult(): AuthResult? = browserAuthManager.consumePendingInteractiveResult()

    private suspend fun runIdentitySignIn(activity: Activity): AuthResult {
        return try {
            // S0744: force the Credential Manager account chooser so the user can pick the account
            // that is actually on the device, instead of silently reusing a previously authorized
            // account that may no longer mint a token (the removed-account dead-end).
            when (
                val result = identityRepository.signInPrimary(
                    activity,
                    GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES,
                    preferAccountChooser = true
                )
            ) {
                is IdentitySignInResult.Success -> {
                    val driveScopes = setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY)
                    val scopeObjects = driveScopes.map { Scope(it.value) }
                    val authorizationRequest = AuthorizationRequest.builder()
                        .setRequestedScopes(scopeObjects)
                        .build()
                    val authClient = Identity.getAuthorizationClient(activity)
                    val authResult = authClient.authorize(authorizationRequest).await()

                    val authorized = if (authResult.hasResolution()) {
                        val pendingIntent = authResult.pendingIntent
                        if (pendingIntent != null) {
                            Timber.i("GMS Drive authorization requires resolution, launching shadow activity")
                            GoogleDriveAuthResolutionTracker.resolvePendingIntent(activity, pendingIntent)
                        } else {
                            false
                        }
                    } else {
                        authResult.accessToken != null
                    }

                    if (authorized) {
                        AuthResult.Success(
                            accountName = result.account.email,
                            credentialsJson = result.account.email
                        )
                    } else {
                        Timber.e("GMS Drive authorization failed or was cancelled by user")
                        AuthResult.Cancelled
                    }
                }

                IdentitySignInResult.Cancelled -> AuthResult.Cancelled

                is IdentitySignInResult.Failed -> {
                    Timber.e(result.cause, "Google Drive Credential Manager sign-in failed: ${result.reason}")
                    AuthResult.Error("Google sign-in failed: ${result.reason}")
                }
            }
        } catch (exception: Exception) {
            exception.rethrowIfCancellation()
            Timber.e(exception, "Google Drive Credential Manager sign-in threw")
            AuthResult.Error(
                "Google sign-in failed: ${exception.javaClass.simpleName}: ${exception.message}"
            )
        }
    }

    private fun startBrowserSignIn(activity: Activity): StartResult {
        return try {
            browserAuthManager.startInteractiveSignIn(activity)
            StartResult.AwaitResume
        } catch (exception: GoogleDriveBrowserUnavailableException) {
            StartResult.Immediate(
                AuthResult.Error(
                    exception.message ?: context.getString(R.string.s0294_google_drive_browser_required_message)
                )
            )
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to start Google Drive browser sign-in")
            StartResult.Immediate(
                AuthResult.Error(context.getString(R.string.s0294_google_drive_browser_launch_failed_message))
            )
        }
    }

    private fun shouldUseBrowserAuth(): Boolean =
        GmsAvailabilityChecker.recheckFor(
            context,
            GmsAvailabilityChecker.MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER
        ) == GmsAvailabilityChecker.Status.UNAVAILABLE
}
