package com.sza.fastmediasorter.domain.identity

/**
 * Outcome of [GoogleIdentityRepository.issueAccessToken] (S4427).
 *
 * [ConsentRequired] is kept apart from [Unavailable] because the cure differs: the user grants the
 * missing scopes once, while the account itself stays signed in.
 */
sealed interface GoogleTokenResult {
    data class Granted(val token: GoogleAccessToken) : GoogleTokenResult

    /** The user has not granted [scopes]; the account state is unchanged. */
    data class ConsentRequired(val scopes: Set<GoogleScope>) : GoogleTokenResult

    /** No account is bound, the account left the device, or the token could not be refreshed. */
    data object Unavailable : GoogleTokenResult
}
