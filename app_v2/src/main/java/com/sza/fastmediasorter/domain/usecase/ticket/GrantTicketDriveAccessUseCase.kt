package com.sza.fastmediasorter.domain.usecase.ticket

import android.content.Context
import com.sza.fastmediasorter.data.cloud.GoogleDriveAuthPlugin
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import timber.log.Timber
import javax.inject.Inject

/**
 * S4765: the user action behind a ticket the silent Drive sign-in could not send - shows the Google
 * consent screen, which no worker can show.
 *
 * A bound account takes the grant alone, because the account chooser of a full sign-in could replace
 * the binding with another account. Every other state has no binding to keep and takes the full sign-in
 * with the account chooser forced: the authorized-accounts-only first pass answers with the accounts Google
 * already lists for the app, which hides the account that needs the sign-in (S4814).
 */
class GrantTicketDriveAccessUseCase @Inject constructor(
    private val identityRepository: GoogleIdentityRepository
) {

    /**
     * @param activityContext an Activity context - the consent screen is launched from it.
     * @param driveScopes the Drive scopes asked on top of a bound account.
     * @param signInScopes the full sign-in set asked when no account is bound yet.
     */
    suspend operator fun invoke(
        activityContext: Context,
        driveScopes: Set<GoogleScope> = DRIVE_SCOPES,
        signInScopes: Set<GoogleScope> = GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES
    ): IdentitySignInResult =
        if (identityRepository.state.value is PrimaryGoogleAccountState.Bound) {
            identityRepository.grantScopes(activityContext, driveScopes)
        } else {
            Timber.d("S4814: ticket sign-in forces the account chooser")
            identityRepository.signInPrimary(activityContext, signInScopes, preferAccountChooser = true)
        }

    companion object {
        // The identity scopes are already bound with the account; only the Drive ones can be missing.
        val DRIVE_SCOPES: Set<GoogleScope> = GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES -
            setOf(GoogleScope.EMAIL, GoogleScope.PROFILE, GoogleScope.OPENID)
    }
}
