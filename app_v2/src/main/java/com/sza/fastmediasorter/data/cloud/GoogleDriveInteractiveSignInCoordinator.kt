package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

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
            when (val result = identityRepository.signInPrimary(activity, GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES)) {
                is IdentitySignInResult.Success -> AuthResult.Success(
                    accountName = result.account.email,
                    credentialsJson = result.account.email
                )

                IdentitySignInResult.Cancelled -> AuthResult.Cancelled

                is IdentitySignInResult.Failed -> {
                    Timber.e(result.cause, "Google Drive Credential Manager sign-in failed: ${result.reason}")
                    AuthResult.Error("Google sign-in failed: ${result.reason}")
                }
            }
        } catch (exception: Exception) {
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