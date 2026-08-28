package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inert [GoogleDriveBrowserAuthManager] for flavors built without the Google Drive auth path
 * (strategic S0403). Mounted via the `cloudNoSdk` source set.
 *
 * There is never a session and never a stored credential, so every probe answers negatively and
 * the two flow entry points return a refusal rather than starting anything.
 */
@Singleton
class NoOpGoogleDriveBrowserAuthManager @Inject constructor() : GoogleDriveBrowserAuthManager {

    override fun startInteractiveSignIn(activity: Activity) {
        throw GoogleDriveBrowserUnavailableException(UNAVAILABLE)
    }

    override suspend fun completeAuthorizationIntent(intent: Intent): AuthResult =
        AuthResult.Error(UNAVAILABLE)

    override fun consumePendingInteractiveResult(): AuthResult? = null

    override fun hasActiveSession(): Boolean = false

    override fun peekStoredAccountEmail(): String? = null

    override fun hasStoredCredentials(accountEmail: String?): Boolean = false

    override suspend fun ensureActiveFromStored(accountEmail: String?): Boolean = false

    override fun restoreCredentialBlob(credentialsJson: String): Boolean = false

    override suspend fun getFreshAccessToken(): String? = null

    private companion object {
        const val UNAVAILABLE = "Google Drive is not available in this build"
    }
}

/**
 * Inert [GoogleDriveInteractiveSignInCoordinator] for flavors built without Play Services auth
 * (strategic S0403). Mounted via the `cloudNoSdk` source set.
 */
@Singleton
class NoOpGoogleDriveInteractiveSignInCoordinator @Inject constructor() :
    GoogleDriveInteractiveSignInCoordinator {

    override suspend fun start(activity: Activity): GoogleDriveInteractiveSignInCoordinator.StartResult =
        GoogleDriveInteractiveSignInCoordinator.StartResult.Immediate(
            AuthResult.Error("Google Drive is not available in this build")
        )

    override fun consumePendingInteractiveResult(): AuthResult? = null
}
