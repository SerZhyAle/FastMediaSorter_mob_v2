package com.sza.fastmediasorter.domain.usecase.transfer

import android.content.Context
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import javax.inject.Inject

/**
 * S4427: the fallback "Allow" action of the exchange surfaces - asks Google for the app folder grant
 * alone, for the account that is already signed in. Sign-in asks for it up front (S3040), so this
 * runs only after the user declined it on the sign-in consent screen.
 */
class GrantDriveAppFolderAccessUseCase @Inject constructor(
    private val identityRepository: GoogleIdentityRepository
) {

    /** @param activityContext an Activity context - the consent screen is launched from it. */
    suspend operator fun invoke(activityContext: Context): Boolean {
        val result = identityRepository.grantScopes(activityContext, setOf(GoogleScope.DRIVE_APPDATA))
        return result is IdentitySignInResult.Success
    }
}
