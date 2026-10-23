package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interactive Google Drive sign-in via GMS Identity, falling back to the browser flow.
 *
 * S0403: lives in `src/cloudSdk` beside the GMS identity code it drives (the Drive scope consent
 * itself runs in the identity repository since S4427). Shared code depends on the
 * [GoogleDriveInteractiveSignInCoordinator] contract in
 * `src/main`.
 */
@Singleton
class GoogleDriveInteractiveSignInCoordinatorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identityRepository: GoogleIdentityRepository,
    private val browserAuthManager: GoogleDriveBrowserAuthManager
) : GoogleDriveInteractiveSignInCoordinator {

    override suspend fun start(activity: Activity): GoogleDriveInteractiveSignInCoordinator.StartResult {
        return if (shouldUseBrowserAuth()) {
            startBrowserSignIn(activity)
        } else {
            GoogleDriveInteractiveSignInCoordinator.StartResult.Immediate(runIdentitySignIn(activity))
        }
    }

    override fun consumePendingInteractiveResult(): AuthResult? =
        browserAuthManager.consumePendingInteractiveResult()

    // Credential Manager and the GMS authorization client throw undeclared runtime types; this is
    // the sign-in boundary, so any failure must become an AuthResult rather than crash the picker.
    @Suppress("TooGenericExceptionCaught")
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
                    // S4427: the repository already asked one consent for the whole Drive sign-in set
                    // and recorded what Google granted; browsing needs at least one Drive scope.
                    val granted = result.account.grantedScopes
                    if (GoogleScope.DRIVE in granted || GoogleScope.DRIVE_READONLY in granted) {
                        AuthResult.Success(
                            accountName = result.account.email,
                            credentialsJson = result.account.email
                        )
                    } else {
                        Timber.w("GMS sign-in completed without any Drive scope granted")
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

    // Launching the Custom Tab can fail with any runtime type from the browser package; the user
    // gets the launch-failed message instead of a crash.
    @Suppress("TooGenericExceptionCaught")
    private fun startBrowserSignIn(activity: Activity): GoogleDriveInteractiveSignInCoordinator.StartResult {
        return try {
            browserAuthManager.startInteractiveSignIn(activity)
            GoogleDriveInteractiveSignInCoordinator.StartResult.AwaitResume
        } catch (exception: GoogleDriveBrowserUnavailableException) {
            GoogleDriveInteractiveSignInCoordinator.StartResult.Immediate(
                AuthResult.Error(
                    exception.message ?: context.getString(R.string.s0294_google_drive_browser_required_message)
                )
            )
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to start Google Drive browser sign-in")
            GoogleDriveInteractiveSignInCoordinator.StartResult.Immediate(
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
