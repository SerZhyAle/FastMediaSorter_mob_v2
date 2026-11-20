package com.sza.fastmediasorter.domain.usecase.ticket

import android.content.Context
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import javax.inject.Inject

/**
 * S4765: the user action behind a ticket the silent Drive sign-in could not send - shows the Google
 * consent screen, which no worker can show.
 *
 * A bound account takes the grant alone, because the account chooser of a full sign-in could replace
 * the binding with another account; an unbound phone has nothing to keep and takes the full sign-in.
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
        driveScopes: Set<GoogleScope>,
        signInScopes: Set<GoogleScope>
    ): IdentitySignInResult =
        if (identityRepository.state.value is PrimaryGoogleAccountState.Bound) {
            identityRepository.grantScopes(activityContext, driveScopes)
        } else {
            identityRepository.signInPrimary(activityContext, signInScopes)
        }
}
