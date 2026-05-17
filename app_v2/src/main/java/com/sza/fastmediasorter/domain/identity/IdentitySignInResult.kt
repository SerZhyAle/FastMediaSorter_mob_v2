package com.sza.fastmediasorter.domain.identity

/**
 * Outcome of an interactive sign-in attempt against [GoogleIdentityRepository].
 *
 * The repository emits its own state changes via [GoogleIdentityRepository.state]; this result is
 * the one-shot answer to a specific `signInPrimary` / `requestAdditionalScopes` /
 * `requestSecondaryAccount` call, allowing callers to react immediately without subscribing.
 */
sealed interface IdentitySignInResult {
    /** User completed sign-in. For primary path the account is also reflected in `state`. */
    data class Success(val account: PrimaryGoogleAccount) : IdentitySignInResult

    /** User dismissed the Credential Manager bottom sheet or back-pressed the consent dialog. */
    data object Cancelled : IdentitySignInResult

    /** Sign-in failed terminally. Caller should surface [reason] to UI. */
    data class Failed(val reason: IdentityFailureReason, val cause: Throwable? = null) : IdentitySignInResult
}
